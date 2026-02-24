package com.example.account.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RequestMdcInterceptor implements HandlerInterceptor {

  private static final String ATTRIBUTE_KEYS = RequestMdcInterceptor.class.getName() + ".MDC_KEYS";

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    final List<String> keys = new ArrayList<>();
    put(keys, "request_id", resolveRequestId(request));
    put(keys, "http_method", request.getMethod());
    put(keys, "http_path", request.getRequestURI());
    put(keys, "client_ip", resolveClientIp(request));
    put(keys, "idempotency_key", request.getHeader("Idempotency-Key"));
    put(keys, "user_id", resolveUserId(request));
    request.setAttribute(ATTRIBUTE_KEYS, keys);
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      @Nullable Exception ex) {
    final Object attribute = request.getAttribute(ATTRIBUTE_KEYS);
    if (!(attribute instanceof List<?> rawKeys)) {
      return;
    }
    for (Object rawKey : rawKeys) {
      if (rawKey instanceof String key) {
        MDC.remove(key);
      }
    }
  }

  private String resolveRequestId(HttpServletRequest request) {
    final String requestId = request.getHeader("X-Request-Id");
    if (requestId != null && !requestId.isBlank()) {
      return requestId;
    }
    return UUID.randomUUID().toString();
  }

  private String resolveClientIp(HttpServletRequest request) {
    final String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor == null || xForwardedFor.isBlank()) {
      return request.getRemoteAddr();
    }
    final int commaIndex = xForwardedFor.indexOf(',');
    if (commaIndex < 0) {
      return xForwardedFor.trim();
    }
    return xForwardedFor.substring(0, commaIndex).trim();
  }

  private String resolveUserId(HttpServletRequest request) {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      final String name = authentication.getName();
      if (name != null && !name.isBlank() && !"anonymousUser".equals(name)) {
        return name;
      }
    }
    final String forwardedUserId = request.getHeader("X-User-Id");
    if (forwardedUserId != null && !forwardedUserId.isBlank()) {
      return forwardedUserId;
    }
    return null;
  }

  private void put(List<String> keys, String key, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    MDC.put(key, value);
    keys.add(key);
  }
}
