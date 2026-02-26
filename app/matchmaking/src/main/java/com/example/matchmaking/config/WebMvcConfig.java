/*
 * どこで: Matchmaking Web 設定
 * 何を: RequestMdcInterceptor を全リクエストへ適用する
 * なぜ: API ログへ運用キーを安定して埋め込むため
 */
package com.example.matchmaking.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

  private final RequestMdcInterceptor requestMdcInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(requestMdcInterceptor);
  }
}
