package com.example.notification.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.lang.Nullable;
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

  private void put(List<String> keys, String key, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    MDC.put(key, value);
    keys.add(key);
  }
}
