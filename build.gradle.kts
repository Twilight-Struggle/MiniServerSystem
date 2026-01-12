/*
 * どこで: build.gradle.kts
 * 何を: ルートの共通ビルド設定とバージョン定数を定義
 * なぜ: 重複を避け、各モジュールの設定を簡潔にするため
 */
plugins {
    id("org.springframework.boot") version "3.5.9" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.google.cloud.tools.jib") version "3.5.2" apply false
    id("com.google.protobuf") version "0.9.4" apply false
    java
}

// 共有バージョンは rootProject.extra で一元管理する。
extra.apply {
    set("jnatsVersion", "2.24.1")
    set("lombokVersion", "1.18.42")
    set("testcontainersBomVersion", "1.21.4")
    set("protobufVersion", "3.25.5")
}

allprojects {
    group = "com.example"
    version = "0.1.0"
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
