/*
 * どこで: app/entitlement/build.gradle.kts
 * 何を: entitlement モジュールのビルド/テスト依存を定義
 * なぜ: 依存関係を明示し、安定したビルドとテスト実行を保証するため
 */
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.cloud.tools.jib")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("io.nats:jnats:2.24.1")
    implementation(project(":libs:proto"))
    implementation(project(":libs:common"))
    runtimeOnly("org.postgresql:postgresql")
    compileOnly("org.projectlombok:lombok:1.18.42")
	annotationProcessor("org.projectlombok:lombok:1.18.42")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.4"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}

jib {
    // baseimage 軽量イメージへの差し替えも可
    from {
        image = "eclipse-temurin:21-jre"
    }

    // to.image は skaffold/CI から -Djib.to.image=... で上書き
    to {
        image = "account:dev"
    }

    container {
        ports = listOf("8080")
        // 任意: JVM オプション
        // jvmFlags = listOf("-XX:MaxRAMPercentage=75.0")
    }
}
