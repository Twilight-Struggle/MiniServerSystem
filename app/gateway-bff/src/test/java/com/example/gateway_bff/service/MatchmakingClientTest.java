package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.gateway_bff.config.MatchmakingClientProperties;
import com.example.gateway_bff.service.dto.MatchmakingJoinTicketRequest;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class MatchmakingClientTest {

  @Test
  void joinTicketCallsMatchmakingWithUserIdHeader() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://matchmaking.test/v1/matchmaking/queues/casual/tickets"))
        .andExpect(method(POST))
        .andExpect(header("X-User-Id", "user-1"))
        .andRespond(
            withSuccess(
                """
                {"ticket_id":"ticket-1","status":"QUEUED","expires_at":"2026-02-24T12:01:00Z"}
                """,
                MediaType.APPLICATION_JSON));

    final var response =
        fixture.client.joinTicket(
            "casual", "user-1", new MatchmakingJoinTicketRequest(1, Map.of(), "idem-1"));

    assertThat(response.ticketId()).isEqualTo("ticket-1");
    fixture.server.verify();
  }

  @Test
  void getTicketStatusCallsMatchmakingWithUserIdHeader() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://matchmaking.test/v1/matchmaking/tickets/ticket-1"))
        .andExpect(method(GET))
        .andExpect(header("X-User-Id", "user-1"))
        .andRespond(
            withSuccess(
                """
                {"ticket_id":"ticket-1","status":"QUEUED","expires_at":"2026-02-24T12:01:00Z"}
                """,
                MediaType.APPLICATION_JSON));

    final var response = fixture.client.getTicketStatus("ticket-1", "user-1");

    assertThat(response.status()).isEqualTo("QUEUED");
  }

  @Test
  void cancelTicketCallsMatchmakingWithUserIdHeader() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://matchmaking.test/v1/matchmaking/tickets/ticket-1"))
        .andExpect(method(DELETE))
        .andExpect(header("X-User-Id", "user-1"))
        .andRespond(
            withSuccess(
                """
                {"ticket_id":"ticket-1","status":"CANCELLED"}
                """,
                MediaType.APPLICATION_JSON));

    final var response = fixture.client.cancelTicket("ticket-1", "user-1");

    assertThat(response.status()).isEqualTo("CANCELLED");
  }

  @Test
  void joinTicketMaps403ToForbidden() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://matchmaking.test/v1/matchmaking/queues/casual/tickets"))
        .andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThatThrownBy(
            () ->
                fixture.client.joinTicket(
                    "casual", "user-1", new MatchmakingJoinTicketRequest(1, Map.of(), "idem-1")))
        .isInstanceOf(MatchmakingIntegrationException.class)
        .extracting(ex -> ((MatchmakingIntegrationException) ex).reason())
        .isEqualTo(MatchmakingIntegrationException.Reason.FORBIDDEN);
  }

  @Test
  void joinTicketMaps404ToNotFound() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://matchmaking.test/v1/matchmaking/queues/casual/tickets"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(
            () ->
                fixture.client.joinTicket(
                    "casual", "user-1", new MatchmakingJoinTicketRequest(1, Map.of(), "idem-1")))
        .isInstanceOf(MatchmakingIntegrationException.class)
        .extracting(ex -> ((MatchmakingIntegrationException) ex).reason())
        .isEqualTo(MatchmakingIntegrationException.Reason.NOT_FOUND);
  }

  @Test
  void joinTicketMaps5xxToBadGateway() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://matchmaking.test/v1/matchmaking/queues/casual/tickets"))
        .andRespond(withServerError());

    assertThatThrownBy(
            () ->
                fixture.client.joinTicket(
                    "casual", "user-1", new MatchmakingJoinTicketRequest(1, Map.of(), "idem-1")))
        .isInstanceOf(MatchmakingIntegrationException.class)
        .extracting(ex -> ((MatchmakingIntegrationException) ex).reason())
        .isEqualTo(MatchmakingIntegrationException.Reason.BAD_GATEWAY);
  }

  @Test
  void joinTicketMapsMalformedBodyToInvalidResponse() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://matchmaking.test/v1/matchmaking/queues/casual/tickets"))
        .andRespond(withSuccess("{\"foo\":\"bar\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(
            () ->
                fixture.client.joinTicket(
                    "casual", "user-1", new MatchmakingJoinTicketRequest(1, Map.of(), "idem-1")))
        .isInstanceOf(MatchmakingIntegrationException.class)
        .extracting(ex -> ((MatchmakingIntegrationException) ex).reason())
        .isEqualTo(MatchmakingIntegrationException.Reason.INVALID_RESPONSE);
  }

  @Test
  void joinTicketMapsTimeoutToTimeout() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://matchmaking.test/v1/matchmaking/queues/casual/tickets"))
        .andRespond(
            request -> {
              throw new ResourceAccessException(
                  "read timeout", new SocketTimeoutException("Read timed out"));
            });

    assertThatThrownBy(
            () ->
                fixture.client.joinTicket(
                    "casual", "user-1", new MatchmakingJoinTicketRequest(1, Map.of(), "idem-1")))
        .isInstanceOf(MatchmakingIntegrationException.class)
        .extracting(ex -> ((MatchmakingIntegrationException) ex).reason())
        .isEqualTo(MatchmakingIntegrationException.Reason.TIMEOUT);
  }

  @Test
  void joinTicketMapsConnectionFailureToBadGateway() {
    final ClientFixture fixture = newFixture();
    fixture
        .server
        .expect(requestTo("http://matchmaking.test/v1/matchmaking/queues/casual/tickets"))
        .andRespond(
            request -> {
              throw new ResourceAccessException(
                  "connection refused", new ConnectException("Connection refused"));
            });

    assertThatThrownBy(
            () ->
                fixture.client.joinTicket(
                    "casual", "user-1", new MatchmakingJoinTicketRequest(1, Map.of(), "idem-1")))
        .isInstanceOf(MatchmakingIntegrationException.class)
        .extracting(ex -> ((MatchmakingIntegrationException) ex).reason())
        .isEqualTo(MatchmakingIntegrationException.Reason.BAD_GATEWAY);
  }

  @Test
  void joinTicketRejectsBlankMode() {
    final ClientFixture fixture = newFixture();

    assertThatThrownBy(
            () ->
                fixture.client.joinTicket(
                    " ", "user-1", new MatchmakingJoinTicketRequest(1, Map.of(), "idem-1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("mode is required");
  }

  @Test
  void joinTicketRejectsBlankUserId() {
    final ClientFixture fixture = newFixture();

    assertThatThrownBy(
            () ->
                fixture.client.joinTicket(
                    "casual", " ", new MatchmakingJoinTicketRequest(1, Map.of(), "idem-1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("userId is required");
  }

  private ClientFixture newFixture() {
    final RestClient.Builder builder = RestClient.builder();
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    final RestClient restClient = builder.baseUrl("http://matchmaking.test").build();
    final MatchmakingClientProperties properties =
        new MatchmakingClientProperties(
            "http://matchmaking.test",
            "/v1/matchmaking/queues/{mode}/tickets",
            "/v1/matchmaking/tickets/{ticketId}",
            "/v1/matchmaking/tickets/{ticketId}",
            "X-User-Id");
    return new ClientFixture(new MatchmakingClient(restClient, properties), server);
  }

  private record ClientFixture(MatchmakingClient client, MockRestServiceServer server) {}
}
