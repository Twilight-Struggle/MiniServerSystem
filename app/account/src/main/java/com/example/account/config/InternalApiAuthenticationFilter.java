package com.example.account.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class InternalApiAuthenticationFilter extends OncePerRequestFilter {

  private final AccountInternalApiProperties properties;

  public InternalApiAuthenticationFilter(AccountInternalApiProperties properties) {
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !("POST".equals(request.getMethod())
        && "/identities:resolve".equals(request.getRequestURI()));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // 内部トークン一致時のみ ROLE_INTERNAL を付与する。
    final String actualToken = request.getHeader(properties.headerName());
    if (actualToken != null
        && actualToken.equals(properties.token())
        && !properties.token().isBlank()) {
      final UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(
              "gateway-bff-internal", "N/A", List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    filterChain.doFilter(request, response);
  }
}
