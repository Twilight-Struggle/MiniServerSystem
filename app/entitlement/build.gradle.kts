plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.cloud.tools.jib")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
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