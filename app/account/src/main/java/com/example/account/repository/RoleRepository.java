package com.example.account.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("EI_EXPOSE_REP2")
@RequiredArgsConstructor
public class RoleRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public List<String> findRolesByUserId(String userId) {
    final String sql =
        """
        SELECT role
        FROM account_roles
        WHERE user_id = :userId
        ORDER BY role ASC
        """;
    return jdbcTemplate.query(
        sql,
        new MapSqlParameterSource().addValue("userId", userId),
        (rs, rowNum) -> rs.getString("role"));
  }

  public void grantInitialUserRole(String userId) {
    final String sql =
        """
        INSERT INTO account_roles (user_id, role)
        VALUES (:userId, 'USER')
        ON CONFLICT (user_id, role) DO NOTHING
        """;
    jdbcTemplate.update(sql, new MapSqlParameterSource().addValue("userId", userId));
  }
}
