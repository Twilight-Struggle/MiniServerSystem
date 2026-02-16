package com.example.account.service;

import com.example.account.api.request.UserPatchRequest;
import com.example.account.api.response.UserResponse;
import com.example.account.model.UserRecord;
import com.example.account.repository.RoleRepository;
import com.example.account.repository.UserRepository;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@SuppressWarnings("EI_EXPOSE_REP2")
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;

  public UserResponse getUser(String userId) {
    final UserRecord user =
        userRepository
            .findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("user not found"));
    final List<String> roles = roleRepository.findRolesByUserId(userId);
    return toResponse(user, roles);
  }

  public UserResponse patchUser(String userId, @NonNull UserPatchRequest request) {
    final UserRecord updated =
        userRepository.updateProfile(userId, request.displayName(), request.locale());
    final List<String> roles = roleRepository.findRolesByUserId(userId);
    return toResponse(updated, roles);
  }

  private UserResponse toResponse(UserRecord user, List<String> roles) {
    return new UserResponse(
        user.userId(), user.displayName(), user.locale(), user.status().name(), roles);
  }
}
