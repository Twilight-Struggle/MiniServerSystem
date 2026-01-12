/*
 * どこで: Entitlement アプリのエントリポイント
 * 何を: Spring Boot の起動と設定スキャンを行う
 * なぜ: 設定クラスとスケジュールをまとめて有効化するため
 */
package com.example.entitlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class EntitlementApplication {

    public static void main(String[] args) {
        SpringApplication.run(EntitlementApplication.class, args);
    }
}
