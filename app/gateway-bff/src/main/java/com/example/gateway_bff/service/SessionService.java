package com.example.gateway_bff.service;

import com.example.gateway_bff.model.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionService {

  private static final long TTL_SECONDS = 3600;
  private final Clock clock;
  private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();

  public String createSession(AuthenticatedUser user) {
    final String sessionId = UUID.randomUUID().toString();
    sessions.put(sessionId, new SessionEntry(user, Instant.now(clock).plusSeconds(TTL_SECONDS)));
    return sessionId;
  }

  public Optional<AuthenticatedUser> findAuthenticatedUser(String sessionId) {
    final SessionEntry entry = sessions.get(sessionId);
    if (entry == null) {
      return Optional.empty();
    }
    if (entry.expiresAt().isBefore(Instant.now(clock))) {
      sessions.remove(sessionId);
      return Optional.empty();
    }
    return Optional.of(entry.user());
  }

  public void deleteSession(String sessionId) {
    sessions.remove(sessionId);
  }

  private record SessionEntry(AuthenticatedUser user, Instant expiresAt) {}
}
