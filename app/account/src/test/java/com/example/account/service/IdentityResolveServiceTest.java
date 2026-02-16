package com.example.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class IdentityResolveServiceTest {

  @Mock private IdentityRepository identityRepository;
  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;

  private IdentityResolveService service;

  @BeforeEach
  void setUp() {
    service =
        new IdentityResolveService(
            identityRepository,
            userRepository,
            roleRepository,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void resolveReturnsExistingUserWhenIdentityExists() {
    final IdentityResolveRequest request =
        new IdentityResolveRequest("google", "sub-1", "a@example.com", true, "name", "pic");
    final IdentityRecord identity =
        new IdentityRecord("google", "sub-1", "user-1", "a@example.com", true, Instant.now());
    final UserRecord user =
        new UserRecord(
            "user-1", "name", "ja-JP", AccountStatus.ACTIVE, Instant.now(), Instant.now());

    when(identityRepository.findByProviderAndSubject("google", "sub-1"))
        .thenReturn(Optional.of(identity));
    when(userRepository.findByUserId("user-1")).thenReturn(Optional.of(user));
    when(roleRepository.findRolesByUserId("user-1")).thenReturn(List.of("USER"));

    final IdentityResolveResponse response = service.resolve(request);

    assertThat(response.userId()).isEqualTo("user-1");
    assertThat(response.accountStatus()).isEqualTo("ACTIVE");
    assertThat(response.roles()).containsExactly("USER");
    verify(identityRepository).updateClaims("google", "sub-1", "a@example.com", true);
  }

  @Test
  void resolveCreatesNewUserWhenIdentityDoesNotExist() {
    final IdentityResolveRequest request =
        new IdentityResolveRequest("google", "sub-2", "b@example.com", true, "new user", "pic");

    when(identityRepository.findByProviderAndSubject("google", "sub-2"))
        .thenReturn(Optional.empty());
    when(roleRepository.findRolesByUserId(any())).thenReturn(List.of("USER"));
    lenient()
        .when(userRepository.findByUserId(any()))
        .thenAnswer(
            invocation -> {
              final String userId = invocation.getArgument(0);
              return Optional.of(
                  new UserRecord(
                      userId,
                      "new user",
                      "ja-JP",
                      AccountStatus.ACTIVE,
                      Instant.now(),
                      Instant.now()));
            });

    final ArgumentCaptor<UserRecord> userCaptor = ArgumentCaptor.forClass(UserRecord.class);
    when(userRepository.insertIfAbsent(userCaptor.capture())).thenReturn(1);

    final ArgumentCaptor<IdentityRecord> identityCaptor =
        ArgumentCaptor.forClass(IdentityRecord.class);
    when(identityRepository.insert(identityCaptor.capture()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    final IdentityResolveResponse response = service.resolve(request);

    assertThat(response.accountStatus()).isEqualTo("ACTIVE");
    assertThat(response.roles()).containsExactly("USER");
    assertThat(userCaptor.getValue().displayName()).isEqualTo("new user");
    assertThat(userCaptor.getValue().userId())
        .isEqualTo(
            UUID.nameUUIDFromBytes("google:sub-2".getBytes(StandardCharsets.UTF_8)).toString());
    assertThat(identityCaptor.getValue().provider()).isEqualTo("google");
    assertThat(identityCaptor.getValue().subject()).isEqualTo("sub-2");
    assertThat(identityCaptor.getValue().userId()).isEqualTo(userCaptor.getValue().userId());
    verify(roleRepository).grantInitialUserRole(userCaptor.getValue().userId());
  }

  @Test
  void resolveFallbacksToReadAfterConflictWhenConcurrentInsertOccurs() {
    final IdentityResolveRequest request =
        new IdentityResolveRequest("google", "sub-3", "c@example.com", true, "n", "p");
    final IdentityRecord conflicted =
        new IdentityRecord("google", "sub-3", "user-9", "c@example.com", true, Instant.now());
    final UserRecord user =
        new UserRecord("user-9", "n", "ja-JP", AccountStatus.ACTIVE, Instant.now(), Instant.now());

    when(identityRepository.findByProviderAndSubject("google", "sub-3"))
        .thenReturn(Optional.empty(), Optional.of(conflicted));
    final ArgumentCaptor<UserRecord> userCaptor = ArgumentCaptor.forClass(UserRecord.class);
    when(userRepository.insertIfAbsent(userCaptor.capture())).thenReturn(1);
    doThrow(new DataIntegrityViolationException("dup")).when(identityRepository).insert(any());
    when(userRepository.findByUserId("user-9")).thenReturn(Optional.of(user));
    when(roleRepository.findRolesByUserId("user-9")).thenReturn(List.of("USER"));

    final IdentityResolveResponse response = service.resolve(request);

    assertThat(response.userId()).isEqualTo("user-9");
    verify(roleRepository).grantInitialUserRole("user-9");
    assertThat(userCaptor.getValue().userId())
        .isEqualTo(
            UUID.nameUUIDFromBytes("google:sub-3".getBytes(StandardCharsets.UTF_8)).toString());
  }
}
