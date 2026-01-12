/*
 * どこで: Entitlement アプリの設定バインド
 * 何を: NATS publish 先 subject と JetStream stream 設定を保持する
 * なぜ: publish と重複排除の前提となる stream を環境で揃えるため
 */
package com.example.entitlement.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "entitlement.nats")
public record EntitlementNatsProperties(
                @NotBlank String subject,
                @NotBlank String stream,
                @NotNull Duration duplicateWindow) {
}
