/*
 * どこで: libs/common/build.gradle.kts
 * 何を: common モジュールのビルド依存を定義
 * なぜ: 共通ライブラリの依存を明示し、再利用性を高めるため
 */
plugins {
    `java-library`
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.9")
    }
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind")
    api("org.springframework:spring-context")
}
