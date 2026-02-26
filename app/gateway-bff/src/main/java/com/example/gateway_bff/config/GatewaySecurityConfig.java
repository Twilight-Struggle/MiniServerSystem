package com.example.gateway_bff.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

@Configuration
public class GatewaySecurityConfig {
  private static final Logger logger = LoggerFactory.getLogger(GatewaySecurityConfig.class);
  private final boolean csrfEnabled;

  public GatewaySecurityConfig(@Value("${app.security.csrf-enabled:true}") boolean csrfEnabled) {
    this.csrfEnabled = csrfEnabled;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    if (csrfEnabled) {
      http.csrf(Customizer.withDefaults());
    } else {
      http.csrf(csrf -> csrf.disable());
    }
    http.sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/",
                        "/login",
                        "/oauth2/authorization/**",
                        "/login/oauth2/code/**",
                        "/error",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers("/v1/me")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .oauth2Login(
            oauth2 ->
                oauth2
                    .loginPage("/login")
                    .failureHandler(
                        (request, response, exception) -> {
                          logger.warn("oauth2 login failed: {}", exception.getMessage(), exception);
                          response.sendRedirect("/login?error");
                        }))
        .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()))
        .logout(
            logout ->
                logout
                    .logoutUrl("/logout")
                    .logoutSuccessHandler(
                        new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID"));

    return http.build();
  }

  @Bean
  AuthenticationEntryPoint authenticationEntryPoint() {
    return new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
  }
}
