/*
 * どこで: build.gradle.kts
 * 何を: ルートの共通ビルド設定とバージョン定数を定義
 * なぜ: 重複を避け、各モジュールの設定を簡潔にするため
 */
import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.spotbugs.snom.SpotBugsExtension
import org.gradle.api.plugins.quality.CheckstyleExtension

plugins {
    id("org.springframework.boot") version "3.5.9" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.google.cloud.tools.jib") version "3.5.2" apply false
    id("com.google.protobuf") version "0.9.4" apply false
    id("com.diffplug.spotless") version "8.2.1" apply false
    id("com.github.spotbugs") version "6.4.8" apply false
    java
    jacoco
}

// 共有バージョンは rootProject.extra で一元管理する。
extra.apply {
    set("jnatsVersion", "2.24.1")
    set("lombokVersion", "1.18.42")
    set("testcontainersBomVersion", "1.21.4")
    set("protobufVersion", "3.25.5")
    set("logstashLogbackEncoderVersion", "8.1")
}

allprojects {
    group = "com.example"
    version = "0.1.0"
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "checkstyle")
    apply(plugin = "jacoco")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "com.github.spotbugs")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    extensions.configure<SpotlessExtension> {
        java {
            googleJavaFormat("1.34.1")
            targetExclude("**/build/**", "**/generated/**")
        }
    }

    extensions.configure<CheckstyleExtension> {
        toolVersion = "10.17.0"
        isShowViolations = true
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    }
    tasks.withType<Checkstyle>().configureEach {
        doFirst {
            println("CheckstyleMain source roots = ${source.files.take(10)} ... total=${source.files.size}")
        }
        exclude("**/build/generated/**")
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    extensions.configure<SpotBugsExtension> {
        toolVersion.set("4.9.7")
        ignoreFailures.set(false)
        effort.set(com.github.spotbugs.snom.Effort.MAX)
        reportLevel.set(com.github.spotbugs.snom.Confidence.LOW)
        excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
    }
    tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
        reports.create("html") {
            required.set(true)
        }
        reports.create("xml") {
            required.set(true)
        }
    }

    jacoco {
        toolVersion = "0.8.14"
    }
    tasks.test {
        useJUnitPlatform()
        finalizedBy(tasks.jacocoTestReport)
    }
    tasks.jacocoTestReport {
        dependsOn(tasks.test)
        reports {
            xml.required.set(true)   // CI連携・集計向け
            html.required.set(true)  // 人間が見る用
            csv.required.set(false)
        }
    }

    tasks.check {
        dependsOn(
            "spotlessCheck",
            "spotbugsMain",
            "checkstyleMain",
            "checkstyleTest",
            "spotbugsMain",
            "spotbugsTest",
            "test",
            "jacocoTestReport"
        )
    }
}

project(":libs:proto") { tasks.withType<Checkstyle>().configureEach { enabled = false } }
