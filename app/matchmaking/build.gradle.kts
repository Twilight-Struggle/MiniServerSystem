plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.cloud.tools.jib")
}

val jnatsVersion: String by rootProject.extra
val lombokVersion: String by rootProject.extra
val logstashLogbackEncoderVersion: String by rootProject.extra

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.nats:jnats:$jnatsVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    implementation(project(":libs:common"))
    implementation(project(":libs:proto"))
    spotbugs("com.github.spotbugs:spotbugs:4.9.7")
    spotbugs("com.github.spotbugs:spotbugs-annotations:4.9.7")
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.7")
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

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
        image = "matchmaking:dev"
    }

    container {
        ports = listOf("8080")
        // 任意: JVM オプション
        // jvmFlags = listOf("-XX:MaxRAMPercentage=75.0")
    }
}
