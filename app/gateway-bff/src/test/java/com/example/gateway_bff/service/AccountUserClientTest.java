package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.gateway_bff.api.request.UserPatchRequest;
import com.example.gateway_bff.api.response.UserResponse;
import com.example.gateway_bff.config.AccountClientProperties;
import com.example.gateway_bff.model.AuthenticatedUser;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class AccountUserClientTest {

  private static final AuthenticatedUser REQUESTER =
      new AuthenticatedUser("user-1", "ACTIVE", List.of("USER"));
  private static final AuthenticatedUser ADMIN_REQUESTER =
      new AuthenticatedUser("admin-1", "ACTIVE", List.of("ADMIN", "USER"));

  @Test
  void getUserRejectsBlankTargetUserId() {
    final ClientFixture fixture = newFixture();

    assertThatThrownBy(() -> fixture.client.getUser(" ", REQUESTER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("targetUserId is required");
  }

  @Test
  void getUserRejectsRequesterWithoutUserId() {
    final ClientFixture fixture = newFixture();
    final AuthenticatedUser invalid = new AuthenticatedUser(" ", "ACTIVE", List.of("USER"));

    assertThatThrownBy(() -> fixture.client.getUser("user-1", invalid))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("requester userId is required");
  }

  @Test
  void getUserCallsAccountWithInternalHeaders() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://account.test/users/user-1"))
        .andExpect(method(GET))
        .andExpect(header("X-Internal-Token", "token-x"))
        .andExpect(header("X-User-Id", "admin-1"))
        .andExpect(header("X-User-Roles", "ADMIN,USER"))
        .andRespond(
            withSuccess(
                """
                {"userId":"user-1","displayName":"n","locale":"ja","status":"ACTIVE","roles":["USER"]}
                """,
                MediaType.APPLICATION_JSON));

    final UserResponse response = fixture.client.getUser("user-1", ADMIN_REQUESTER);

    assertThat(response.userId()).isEqualTo("user-1");
    assertThat(response.displayName()).isEqualTo("n");
    assertThat(response.status()).isEqualTo("ACTIVE");
    fixture.server.verify();
  }

  @Test
  void patchUserCallsAccountWithInternalHeaders() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://account.test/users/user-1"))
        .andExpect(method(PATCH))
        .andExpect(header("X-Internal-Token", "token-x"))
        .andExpect(header("X-User-Id", "user-1"))
        .andRespond(
            withSuccess(
                """
                {"userId":"user-1","displayName":"new","locale":"en","status":"ACTIVE","roles":["USER"]}
                """,
                MediaType.APPLICATION_JSON));

    final UserResponse response =
        fixture.client.patchUser("user-1", new UserPatchRequest("new", "en"), REQUESTER);

    assertThat(response.displayName()).isEqualTo("new");
    fixture.server.verify();
  }

  @Test
  void getUserMaps401ToUnauthorizedException() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://account.test/users/user-1"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> fixture.client.getUser("user-1", REQUESTER))
        .isInstanceOf(AccountIntegrationException.class)
        .extracting(ex -> ((AccountIntegrationException) ex).reason())
        .isEqualTo(AccountIntegrationException.Reason.UNAUTHORIZED);
  }

  @Test
  void getUserMaps403ToForbiddenException() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://account.test/users/user-1"))
        .andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThatThrownBy(() -> fixture.client.getUser("user-1", REQUESTER))
        .isInstanceOf(AccountIntegrationException.class)
        .extracting(ex -> ((AccountIntegrationException) ex).reason())
        .isEqualTo(AccountIntegrationException.Reason.FORBIDDEN);
  }

  @Test
  void getUserMaps404ToNotFoundException() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://account.test/users/user-1"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> fixture.client.getUser("user-1", REQUESTER))
        .isInstanceOf(AccountIntegrationException.class)
        .extracting(ex -> ((AccountIntegrationException) ex).reason())
        .isEqualTo(AccountIntegrationException.Reason.NOT_FOUND);
  }

  @Test
  void getUserMaps5xxToBadGatewayException() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://account.test/users/user-1"))
        .andRespond(withServerError());

    assertThatThrownBy(() -> fixture.client.getUser("user-1", REQUESTER))
        .isInstanceOf(AccountIntegrationException.class)
        .extracting(ex -> ((AccountIntegrationException) ex).reason())
        .isEqualTo(AccountIntegrationException.Reason.BAD_GATEWAY);
  }

  @Test
  void getUserMapsMalformedBodyToInvalidResponseException() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://account.test/users/user-1"))
        .andRespond(withSuccess("{\"foo\":\"bar\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> fixture.client.getUser("user-1", REQUESTER))
        .isInstanceOf(AccountIntegrationException.class)
        .extracting(ex -> ((AccountIntegrationException) ex).reason())
        .isEqualTo(AccountIntegrationException.Reason.INVALID_RESPONSE);
  }

  @Test
  void getUserMapsTimeoutToTimeoutException() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://account.test/users/user-1"))
        .andRespond(
            request -> {
              throw new ResourceAccessException(
                  "read timeout", new SocketTimeoutException("Read timed out"));
            });

    assertThatThrownBy(() -> fixture.client.getUser("user-1", REQUESTER))
        .isInstanceOf(AccountIntegrationException.class)
        .extracting(ex -> ((AccountIntegrationException) ex).reason())
        .isEqualTo(AccountIntegrationException.Reason.TIMEOUT);
  }

  @Test
  void getUserMapsConnectionFailureToBadGatewayException() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://account.test/users/user-1"))
        .andRespond(
            request -> {
              throw new ResourceAccessException(
                  "connection refused", new ConnectException("Connection refused"));
            });

    assertThatThrownBy(() -> fixture.client.getUser("user-1", REQUESTER))
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
            "http://account.test",
            "token-x",
            "X-Internal-Token",
            "/identities:resolve",
            "/users/{userId}",
            "/users/{userId}",
            "X-User-Id",
            "X-User-Roles");
    return new ClientFixture(new AccountUserClient(restClient, properties), server);
  }

  private record ClientFixture(AccountUserClient client, MockRestServiceServer server) {}
}
