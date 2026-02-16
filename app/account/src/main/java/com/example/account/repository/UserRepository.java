package com.example.account.repository;

import com.example.account.model.AccountStatus;
import com.example.account.model.UserRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("EI_EXPOSE_REP2")
@RequiredArgsConstructor
public class UserRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public Optional<UserRecord> findByUserId(String userId) {
    final String sql =
        """
                SELECT user_id, display_name, locale, status, created_at, updated_at
                FROM users
                WHERE user_id = :userId
                """;
    final MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", userId);
    return jdbcTemplate.query(sql, params, this::mapRow).stream().findFirst();
  }

  public UserRecord insert(UserRecord user) {
    final String sql =
        """
                INSERT INTO users (user_id, display_name, locale, status, created_at, updated_at)
                VALUES (:userId, :displayName, :locale, :status, :createdAt, :updatedAt)
                RETURNING user_id, display_name, locale, status, created_at, updated_at
                """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("userId", user.userId())
            .addValue("displayName", user.displayName())
            .addValue("locale", user.locale())
            .addValue("status", user.status().name())
            .addValue("createdAt", Timestamp.from(user.createdAt()))
            .addValue("updatedAt", Timestamp.from(user.updatedAt()));
    return jdbcTemplate.queryForObject(sql, params, this::mapRow);
  }

  public int insertIfAbsent(UserRecord user) {
    final String sql =
        """
        INSERT INTO users (user_id, display_name, locale, status, created_at, updated_at)
        VALUES (:userId, :displayName, :locale, :status, :createdAt, :updatedAt)
        ON CONFLICT (user_id) DO NOTHING
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("userId", user.userId())
            .addValue("displayName", user.displayName())
            .addValue("locale", user.locale())
            .addValue("status", user.status().name())
            .addValue("createdAt", Timestamp.from(user.createdAt()))
            .addValue("updatedAt", Timestamp.from(user.updatedAt()));
    return jdbcTemplate.update(sql, params);
  }

  public UserRecord updateProfile(String userId, String displayName, String locale) {
    final String sql =
        """
                UPDATE users
                SET display_name = :displayName,
                    locale = :locale,
                    updated_at = :updatedAt
                WHERE user_id = :userId
                RETURNING user_id, display_name, locale, status, created_at, updated_at
                """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("displayName", displayName)
            .addValue("locale", locale)
            .addValue("updatedAt", Timestamp.from(Instant.now()));
    return jdbcTemplate.queryForObject(sql, params, this::mapRow);
  }

  public Optional<UserRecord> updateStatus(String userId, String status) {
    final String sql =
        """
                UPDATE users
                SET status = :status,
                    updated_at = :updatedAt
                WHERE user_id = :userId
                RETURNING user_id, display_name, locale, status, created_at, updated_at
                """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("status", status)
            .addValue("updatedAt", Timestamp.from(Instant.now()));
    return jdbcTemplate.query(sql, params, this::mapRow).stream().findFirst();
  }

  private UserRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new UserRecord(
        rs.getString("user_id"),
        rs.getString("display_name"),
        rs.getString("locale"),
        AccountStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
