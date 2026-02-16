package com.example.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.account.api.request.UserPatchRequest;
import com.example.account.api.response.UserResponse;
import com.example.account.model.AccountStatus;
import com.example.account.model.UserRecord;
import com.example.account.repository.RoleRepository;
import com.example.account.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;

  @InjectMocks private UserService service;

  @Test
  void getUserThrowsWhenNotFound() {
    when(userRepository.findByUserId("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getUser("missing"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void patchUserReturnsUpdatedUser() {
    final UserRecord updated =
        new UserRecord(
            "user-1", "new", "ja-JP", AccountStatus.ACTIVE, Instant.now(), Instant.now());
    when(userRepository.updateProfile("user-1", "new", "ja-JP")).thenReturn(updated);
    when(roleRepository.findRolesByUserId("user-1")).thenReturn(List.of("USER"));

    final UserResponse response = service.patchUser("user-1", new UserPatchRequest("new", "ja-JP"));

    assertThat(response.userId()).isEqualTo("user-1");
    assertThat(response.roles()).containsExactly("USER");
  }
}
