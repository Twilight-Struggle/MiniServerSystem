package com.example.account.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Configuration
@EnableConfigurationProperties(AccountInternalApiProperties.class)
public class AccountSecurityConfig {

  @Bean
  InternalApiAuthenticationFilter internalApiAuthenticationFilter(
      AccountInternalApiProperties properties) {
    return new InternalApiAuthenticationFilter(properties);
  }

  @Bean
  AuthorizationManager<RequestAuthorizationContext> userOwnershipAuthorizationManager() {
    return new UserOwnershipAuthorizationManager();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      InternalApiAuthenticationFilter internalApiAuthenticationFilter,
      AuthorizationManager<RequestAuthorizationContext> userOwnershipAuthorizationManager)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(internalApiAuthenticationFilter, AuthorizationFilter.class)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/",
                        "/error",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/identities:resolve")
                    .hasRole("INTERNAL")
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/users/{userId}")
                    .access(userOwnershipAuthorizationManager)
                    .requestMatchers(HttpMethod.PATCH, "/users/{userId}")
                    .access(userOwnershipAuthorizationManager)
                    .anyRequest()
                    .authenticated());
    return http.build();
  }
}
