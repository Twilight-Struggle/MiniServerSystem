package com.example.account.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class InternalApiAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger logger =
      LoggerFactory.getLogger(InternalApiAuthenticationFilter.class);
  private static final String INTERNAL_ROLE = "ROLE_INTERNAL";
  private static final String ADMIN_ROLE = "ROLE_ADMIN";

  private final AccountInternalApiProperties properties;

  public InternalApiAuthenticationFilter(AccountInternalApiProperties properties) {
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !isInternalProtectedPath(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (shouldRejectUsersRequestForMissingUserId(request)) {
      logger.warn(
          "internal users request rejected: missing required header {} on path={}",
          properties.userIdHeaderName(),
          request.getRequestURI());
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    final UsernamePasswordAuthenticationToken authentication = resolveAuthentication(request);
    if (authentication != null) {
      logger.debug(
          "internal authentication established for path={} authorities={}",
          request.getRequestURI(),
          authentication.getAuthorities());
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } else if (isInternalProtectedPath(request)) {
      logger.debug(
          "internal authentication not established for protected path={}", request.getRequestURI());
    }
    filterChain.doFilter(request, response);
  }

  private boolean isInternalProtectedPath(HttpServletRequest request) {
    return isResolveIdentityRequest(request) || isUsersRequest(request);
  }

  private boolean isResolveIdentityRequest(HttpServletRequest request) {
    return "POST".equals(request.getMethod())
        && "/identities:resolve".equals(request.getRequestURI());
  }

  private boolean isUsersRequest(HttpServletRequest request) {
    final String method = request.getMethod();
    final String uri = request.getRequestURI();
    final boolean targetMethod = "GET".equals(method) || "PATCH".equals(method);
    return targetMethod && uri != null && uri.startsWith("/users/");
  }

  private boolean shouldRejectUsersRequestForMissingUserId(HttpServletRequest request) {
    if (!isUsersRequest(request)) {
      return false;
    }
    if (!isValidInternalToken(request.getHeader(properties.headerName()))) {
      return false;
    }
    final String forwardedUserId = request.getHeader(properties.userIdHeaderName());
    return forwardedUserId == null || forwardedUserId.isBlank();
  }

  private UsernamePasswordAuthenticationToken resolveAuthentication(HttpServletRequest request) {
    final String actualToken = request.getHeader(properties.headerName());
    if (!isValidInternalToken(actualToken)) {
      return null;
    }

    if (isResolveIdentityRequest(request)) {
      return new UsernamePasswordAuthenticationToken(
          "gateway-bff-internal", "N/A", List.of(new SimpleGrantedAuthority(INTERNAL_ROLE)));
    }

    if (isUsersRequest(request)) {
      final String forwardedUserId = request.getHeader(properties.userIdHeaderName());
      if (forwardedUserId == null || forwardedUserId.isBlank()) {
        return null;
      }
      return new UsernamePasswordAuthenticationToken(
          forwardedUserId,
          "N/A",
          buildAuthorities(request.getHeader(properties.userRolesHeaderName())));
    }

    return null;
  }

  private boolean isValidInternalToken(String actualToken) {
    return actualToken != null
        && actualToken.equals(properties.token())
        && !properties.token().isBlank();
  }

  private List<SimpleGrantedAuthority> buildAuthorities(String forwardedRoles) {
    final List<SimpleGrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority(INTERNAL_ROLE));

    if (forwardedRoles == null || forwardedRoles.isBlank()) {
      return authorities;
    }

    for (String role : forwardedRoles.split(",")) {
      final String normalized = role == null ? "" : role.trim();
      if ("ADMIN".equals(normalized) || ADMIN_ROLE.equals(normalized)) {
        authorities.add(new SimpleGrantedAuthority(ADMIN_ROLE));
      }
    }
    return authorities;
  }
}
