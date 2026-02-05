/*
 * どこで: Notification アプリのエントリポイント
 * 何を: Spring Boot の起動と設定スキャンを行う
 * なぜ: 設定クラスとスケジュールをまとめて有効化するため
 */
package com.example.notification;

import com.example.common.config.TimeConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@Import(TimeConfig.class)
public class NotificationApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationApplication.class, args);
	}
}
