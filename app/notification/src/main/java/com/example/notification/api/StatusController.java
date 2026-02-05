/*
 * どこで: Notification API
 * 何を: ルートの簡易ヘルスレスポンスを返す
 * なぜ: 既存の動作確認エンドポイントを維持するため
 */
package com.example.notification.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

  @GetMapping("/")
  public String home() {
    return "notification: ok";
  }
}
