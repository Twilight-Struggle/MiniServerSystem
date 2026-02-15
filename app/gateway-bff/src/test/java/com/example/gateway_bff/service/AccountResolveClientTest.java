package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.gateway_bff.config.AccountClientProperties;
import com.example.gateway_bff.model.AuthenticatedUser;
import com.example.gateway_bff.model.OidcClaims;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class AccountResolveClientTest {

  private static final OidcClaims CLAIMS =
      new OidcClaims(
          "keycloak", "user-sub-1", "a@example.com", true, "n", "p", "iss", "aud", 1L, null);

  @Test
  void resolveRejectsNullClaims() {
    final ClientFixture fixture = newFixture();
    assertThatThrownBy(() -> fixture.client.resolveIdentity(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("claims is required");
  }

  @Test
  void resolveRejectsBlankProvider() {
    final ClientFixture fixture = newFixture();
    final OidcClaims claims =
        new OidcClaims(" ", "sub-x", "a@example.com", true, "n", null, "iss", "aud", 1L, null);

    assertThatThrownBy(() -> fixture.client.resolveIdentity(claims))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("provider is required");
  }

  @Test
  void resolveRejectsBlankSubject() {
    final ClientFixture fixture = newFixture();
    final OidcClaims claims =
        new OidcClaims("keycloak", "", "a@example.com", true, "n", null, "iss", "aud", 1L, null);

    assertThatThrownBy(() -> fixture.client.resolveIdentity(claims))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("subject is required");
  }

  @Test
  void resolveCallsAccountAndReturnsAuthenticatedUser() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://account.test/identities:resolve"))
        .andExpect(method(POST))
        .andExpect(header("X-Internal-Token", "token-x"))
        .andRespond(
            withSuccess(
                """
                {"userId":"user-1","accountStatus":"ACTIVE","roles":["USER"]}
                """,
                MediaType.APPLICATION_JSON));

    final AuthenticatedUser user = fixture.client.resolveIdentity(CLAIMS);

    assertThat(user.userId()).isEqualTo("user-1");
    assertThat(user.accountStatus()).isEqualTo("ACTIVE");
    assertThat(user.roles()).containsExactly("USER");
    fixture.server.verify();
  }

  @Test
  void resolveMaps401ToUnauthorizedException() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://account.test/identities:resolve"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> fixture.client.resolveIdentity(CLAIMS))
        .isInstanceOf(AccountIntegrationException.class)
        .extracting(ex -> ((AccountIntegrationException) ex).reason())
        .isEqualTo(AccountIntegrationException.Reason.UNAUTHORIZED);
  }

  @Test
  void resolveMaps403ToForbiddenException() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://account.test/identities:resolve"))
        .andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThatThrownBy(() -> fixture.client.resolveIdentity(CLAIMS))
        .isInstanceOf(AccountIntegrationException.class)
        .extracting(ex -> ((AccountIntegrationException) ex).reason())
        .isEqualTo(AccountIntegrationException.Reason.FORBIDDEN);
  }

  @Test
  void resolveMaps5xxToBadGatewayException() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://account.test/identities:resolve"))
        .andRespond(withServerError());

    assertThatThrownBy(() -> fixture.client.resolveIdentity(CLAIMS))
        .isInstanceOf(AccountIntegrationException.class)
        .extracting(ex -> ((AccountIntegrationException) ex).reason())
        .isEqualTo(AccountIntegrationException.Reason.BAD_GATEWAY);
  }

  @Test
  void resolveMapsMalformedBodyToInvalidResponseException() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://account.test/identities:resolve"))
        .andRespond(withSuccess("{\"foo\":\"bar\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> fixture.client.resolveIdentity(CLAIMS))
        .isInstanceOf(AccountIntegrationException.class)
        .extracting(ex -> ((AccountIntegrationException) ex).reason())
        .isEqualTo(AccountIntegrationException.Reason.INVALID_RESPONSE);
  }

  @Test
  void resolveMapsTimeoutToTimeoutException() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://account.test/identities:resolve"))
        .andRespond(
            request -> {
              throw new ResourceAccessException(
                  "read timeout", new SocketTimeoutException("Read timed out"));
            });

    assertThatThrownBy(() -> fixture.client.resolveIdentity(CLAIMS))
        .isInstanceOf(AccountIntegrationException.class)
        .extracting(ex -> ((AccountIntegrationException) ex).reason())
        .isEqualTo(AccountIntegrationException.Reason.TIMEOUT);
  }

  @Test
  void resolveMapsConnectionFailureToBadGatewayException() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://account.test/identities:resolve"))
        .andRespond(
            request -> {
              throw new ResourceAccessException(
                  "connection refused", new ConnectException("Connection refused"));
            });

    assertThatThrownBy(() -> fixture.client.resolveIdentity(CLAIMS))
        .isInstanceOf(AccountIntegrationException.class)
        .extracting(ex -> ((AccountIntegrationException) ex).reason())
        .isEqualTo(AccountIntegrationException.Reason.BAD_GATEWAY);
  }

  private ClientFixture newFixture() {
    final RestClient.Builder builder = RestClient.builder();
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    final RestClient restClient = builder.baseUrl("http://account.test").build();
    final AccountClientProperties properties =
        new AccountClientProperties(
            "http://account.test", "token-x", "X-Internal-Token", "/identities:resolve");
    return new ClientFixture(new AccountResolveClient(restClient, properties), server);
  }

  private record ClientFixture(AccountResolveClient client, MockRestServiceServer server) {}
}
