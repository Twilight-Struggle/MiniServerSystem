package com.example.gateway_bff.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.gateway_bff.model.AuthenticatedUser;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionServiceTest {

  @Test
  void createFindDeleteSession() {
    final SessionService service = new SessionService(Clock.systemUTC());
    final AuthenticatedUser user = new AuthenticatedUser("user-1", "ACTIVE", List.of("USER"));

    final String sessionId = service.createSession(user);

    assertThat(service.findAuthenticatedUser(sessionId)).isPresent();

    service.deleteSession(sessionId);

    assertThat(service.findAuthenticatedUser(sessionId)).isEmpty();
  }
}
