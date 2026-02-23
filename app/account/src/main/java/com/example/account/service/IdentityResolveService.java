package com.example.account.service;

import com.example.account.api.request.IdentityResolveRequest;
import com.example.account.api.response.IdentityResolveResponse;
import com.example.account.model.AccountStatus;
import com.example.account.model.IdentityRecord;
import com.example.account.model.UserRecord;
import com.example.account.repository.IdentityRepository;
import com.example.account.repository.RoleRepository;
import com.example.account.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdentityResolveService {

  private final IdentityRepository identityRepository;
  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final Clock clock;

  @Transactional
  public IdentityResolveResponse resolve(@NonNull IdentityResolveRequest request) {
    validateRequest(request);

    final Optional<IdentityRecord> existing =
        identityRepository.findByProviderAndSubject(request.provider(), request.subject());
    if (existing.isPresent()) {
      identityRepository.updateClaims(
          request.provider(),
          request.subject(),
          request.email(),
          Boolean.TRUE.equals(request.emailVerified()));
      return buildResponse(existing.get().userId());
    }

    final Instant now = Instant.now(clock);
    final String userId = resolveDeterministicUserId(request.provider(), request.subject());
    final UserRecord user =
        new UserRecord(userId, request.name(), null, AccountStatus.ACTIVE, now, now);

    userRepository.insertIfAbsent(user);
    final IdentityRecord identity =
        new IdentityRecord(
            request.provider(),
            request.subject(),
            userId,
            request.email(),
            Boolean.TRUE.equals(request.emailVerified()),
            now);

    try {
      identityRepository.insert(identity);
    } catch (DataIntegrityViolationException ex) {
      final IdentityRecord winner =
          identityRepository
              .findByProviderAndSubject(request.provider(), request.subject())
              .orElseThrow(() -> ex);
      roleRepository.grantInitialUserRole(winner.userId());
      return buildResponse(winner.userId());
    }

    roleRepository.grantInitialUserRole(userId);
    return buildResponse(userId);
  }

  private void validateRequest(IdentityResolveRequest request) {
    if (isBlank(request.provider())) {
      throw new IllegalArgumentException("provider is required");
    }
    if (isBlank(request.subject())) {
      throw new IllegalArgumentException("subject is required");
    }
  }

  private IdentityResolveResponse buildResponse(String userId) {
    final UserRecord user =
        userRepository
            .findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("resolved user is missing"));
    final List<String> roles = roleRepository.findRolesByUserId(userId);
    return new IdentityResolveResponse(user.userId(), user.status().name(), roles);
  }

  private String resolveDeterministicUserId(String provider, String subject) {
    final String key = provider + ":" + subject;
    return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
