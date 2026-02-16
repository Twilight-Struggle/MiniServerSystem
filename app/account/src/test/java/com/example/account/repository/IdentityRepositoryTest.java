package com.example.account.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.account.model.AccountStatus;
import com.example.account.model.IdentityRecord;
import com.example.account.model.UserRecord;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class IdentityRepositoryTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.hikari.schema", () -> "account");
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("spring.flyway.default-schema", () -> "account");
    registry.add("spring.flyway.schemas", () -> "account");
    registry.add("spring.flyway.create-schemas", () -> "true");
    registry.add("spring.flyway.table", () -> "flyway_schema_history_account");
  }

  @Autowired private IdentityRepository identityRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanup() {
    jdbcTemplate.update("DELETE FROM account.identities", new MapSqlParameterSource());
    jdbcTemplate.update("DELETE FROM account.account_roles", new MapSqlParameterSource());
    jdbcTemplate.update("DELETE FROM account.users", new MapSqlParameterSource());
  }

  @Test
  void insertAndFindByProviderAndSubject() {
    userRepository.insert(
        new UserRecord("user-1", "n", "ja-JP", AccountStatus.ACTIVE, Instant.now(), Instant.now()));
    identityRepository.insert(
        new IdentityRecord("google", "sub-1", "user-1", "a@example.com", true, Instant.now()));

    final Optional<IdentityRecord> found =
        identityRepository.findByProviderAndSubject("google", "sub-1");

    assertThat(found).isPresent();
    assertThat(found.get().userId()).isEqualTo("user-1");
  }
}
