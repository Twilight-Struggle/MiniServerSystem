package com.example.account.repository;

import com.example.account.model.IdentityRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("EI_EXPOSE_REP2")
@RequiredArgsConstructor
public class IdentityRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public Optional<IdentityRecord> findByProviderAndSubject(String provider, String subject) {
    final String sql =
        """
        SELECT provider, subject, user_id, email, email_verified, created_at
        FROM identities
        WHERE provider = :provider AND subject = :subject
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("provider", provider).addValue("subject", subject);
    return jdbcTemplate.query(sql, params, this::mapRow).stream().findFirst();
  }

  public IdentityRecord insert(IdentityRecord identity) {
    final String sql =
        """
        INSERT INTO identities (provider, subject, user_id, email, email_verified, created_at)
        VALUES (:provider, :subject, :userId, :email, :emailVerified, :createdAt)
        RETURNING provider, subject, user_id, email, email_verified, created_at
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("provider", identity.provider())
            .addValue("subject", identity.subject())
            .addValue("userId", identity.userId())
            .addValue("email", identity.email())
            .addValue("emailVerified", identity.emailVerified())
            .addValue("createdAt", Timestamp.from(identity.createdAt()));
    return jdbcTemplate.queryForObject(sql, params, this::mapRow);
  }

  public void updateClaims(String provider, String subject, String email, boolean emailVerified) {
    final String sql =
        """
        UPDATE identities
        SET email = :email,
            email_verified = :emailVerified
        WHERE provider = :provider AND subject = :subject
        """;
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("provider", provider)
            .addValue("subject", subject)
            .addValue("email", email)
            .addValue("emailVerified", emailVerified);
    jdbcTemplate.update(sql, params);
  }

  private IdentityRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new IdentityRecord(
        rs.getString("provider"),
        rs.getString("subject"),
        rs.getString("user_id"),
        rs.getString("email"),
        rs.getBoolean("email_verified"),
        rs.getTimestamp("created_at").toInstant());
  }
}
