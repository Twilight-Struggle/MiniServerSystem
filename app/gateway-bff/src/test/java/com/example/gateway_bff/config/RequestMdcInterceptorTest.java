package com.example.gateway_bff.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RequestMdcInterceptorTest {

  private final RequestMdcInterceptor interceptor = new RequestMdcInterceptor();

  @AfterEach
  void cleanup() {
    MDC.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void putAndRemoveMdcValuesAroundRequestLifecycle() {
    final TestingAuthenticationToken authentication =
        new TestingAuthenticationToken("user-123", "N/A");
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/users/user-123");
    request.addHeader("X-Request-Id", "req-1");
    request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
    request.addHeader("Idempotency-Key", "idem-1");
    final MockHttpServletResponse response = new MockHttpServletResponse();

    try {
      interceptor.preHandle(request, response, new Object());
    } catch (Exception ex) {
      fail("preHandle should not throw", ex);
    }

    assertThat(MDC.get("request_id")).isEqualTo("req-1");
    assertThat(MDC.get("http_method")).isEqualTo("POST");
    assertThat(MDC.get("http_path")).isEqualTo("/v1/users/user-123");
    assertThat(MDC.get("client_ip")).isEqualTo("10.0.0.1");
    assertThat(MDC.get("idempotency_key")).isEqualTo("idem-1");
    assertThat(MDC.get("user_id")).isEqualTo("user-123");

    interceptor.afterCompletion(request, response, new Object(), null);

    assertThat(MDC.get("request_id")).isNull();
    assertThat(MDC.get("user_id")).isNull();
  }
}
