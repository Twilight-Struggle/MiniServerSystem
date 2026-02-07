/*
 * どこで: app/notification/build.gradle.kts
 * 何を: notification モジュールのビルド/依存関係を定義
 * なぜ: アプリ固有の依存を明示し、再現性のあるビルドを保証するため
 */
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.cloud.tools.jib")
}

// ルートで管理するバージョンを参照して重複を防ぐ。
val jnatsVersion: String by rootProject.extra
val lombokVersion: String by rootProject.extra
val testcontainersBomVersion: String by rootProject.extra

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("io.nats:jnats:$jnatsVersion")
    implementation("com.google.guava:guava:33.5.0-jre")
    implementation(project(":libs:proto"))
    implementation(project(":libs:common"))
    // SpotBugs ツール側に annotations を載せるため、エンジン本体と併せて明示指定する。
    spotbugs("com.github.spotbugs:spotbugs:4.9.7")
    spotbugs("com.github.spotbugs:spotbugs-annotations:4.9.7")
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.7")
    runtimeOnly("org.postgresql:postgresql")
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:$testcontainersBomVersion"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")
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
