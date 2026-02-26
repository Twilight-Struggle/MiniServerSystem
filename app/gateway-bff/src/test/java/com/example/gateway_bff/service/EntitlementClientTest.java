package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.gateway_bff.config.EntitlementClientProperties;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class EntitlementClientTest {

  @Test
  void getUserEntitlementsCallsEntitlement() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://entitlement.test/v1/users/user-1/entitlements"))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                {"user_id":"user-1","entitlements":[
                {"stock_keeping_unit":"sku-1","status":"ACTIVE","version":1,"updated_at":"2026-02-26T00:00:00Z"}]}
                """,
                MediaType.APPLICATION_JSON));

    final var response = fixture.client.getUserEntitlements("user-1");

    assertThat(response.userId()).isEqualTo("user-1");
    assertThat(response.entitlements()).hasSize(1);
  }

  @Test
  void getUserEntitlementsMaps404ToNotFound() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://entitlement.test/v1/users/user-404/entitlements"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> fixture.client.getUserEntitlements("user-404"))
        .isInstanceOf(EntitlementIntegrationException.class)
        .extracting(ex -> ((EntitlementIntegrationException) ex).reason())
        .isEqualTo(EntitlementIntegrationException.Reason.NOT_FOUND);
  }

  @Test
  void getUserEntitlementsMaps5xxToBadGateway() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://entitlement.test/v1/users/user-1/entitlements"))
        .andRespond(withServerError());

    assertThatThrownBy(() -> fixture.client.getUserEntitlements("user-1"))
        .isInstanceOf(EntitlementIntegrationException.class)
        .extracting(ex -> ((EntitlementIntegrationException) ex).reason())
        .isEqualTo(EntitlementIntegrationException.Reason.BAD_GATEWAY);
  }

  @Test
  void getUserEntitlementsMapsMalformedBodyToInvalidResponse() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://entitlement.test/v1/users/user-1/entitlements"))
        .andRespond(withSuccess("{\"foo\":\"bar\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> fixture.client.getUserEntitlements("user-1"))
        .isInstanceOf(EntitlementIntegrationException.class)
        .extracting(ex -> ((EntitlementIntegrationException) ex).reason())
        .isEqualTo(EntitlementIntegrationException.Reason.INVALID_RESPONSE);
  }

  @Test
  void getUserEntitlementsMapsTimeoutToTimeout() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://entitlement.test/v1/users/user-1/entitlements"))
        .andRespond(
            request -> {
              throw new ResourceAccessException(
                  "read timeout", new SocketTimeoutException("Read timed out"));
            });

    assertThatThrownBy(() -> fixture.client.getUserEntitlements("user-1"))
        .isInstanceOf(EntitlementIntegrationException.class)
        .extracting(ex -> ((EntitlementIntegrationException) ex).reason())
        .isEqualTo(EntitlementIntegrationException.Reason.TIMEOUT);
  }

  @Test
  void getUserEntitlementsMapsConnectionFailureToBadGateway() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://entitlement.test/v1/users/user-1/entitlements"))
        .andRespond(
            request -> {
              throw new ResourceAccessException(
                  "connection refused", new ConnectException("Connection refused"));
            });

    assertThatThrownBy(() -> fixture.client.getUserEntitlements("user-1"))
        .isInstanceOf(EntitlementIntegrationException.class)
        .extracting(ex -> ((EntitlementIntegrationException) ex).reason())
        .isEqualTo(EntitlementIntegrationException.Reason.BAD_GATEWAY);
  }

  private ClientFixture newFixture() {
    final RestClient.Builder builder = RestClient.builder();
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    final RestClient restClient = builder.baseUrl("http://entitlement.test").build();
    final EntitlementClientProperties properties =
        new EntitlementClientProperties(
            "http://entitlement.test", "/v1/users/{userId}/entitlements");
    return new ClientFixture(new EntitlementClient(restClient, properties), server);
  }

  private record ClientFixture(EntitlementClient client, MockRestServiceServer server) {}
}
