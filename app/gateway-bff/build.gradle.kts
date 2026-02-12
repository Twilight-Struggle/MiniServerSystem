plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.cloud.tools.jib")
}

val lombokVersion: String by rootProject.extra

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation(project(":libs:common"))
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}

val gatewayCoverageIncludes = listOf(
    "com/example/gateway_bff/service/**",
    "com/example/gateway_bff/api/AuthController.class",
    "com/example/gateway_bff/api/ProfileAggregateController.class"
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(sourceSets.main.get().output.asFileTree.matching { include(gatewayCoverageIncludes) })
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        files(sourceSets.main.get().output.asFileTree.matching { include(gatewayCoverageIncludes) })
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

jib {
    // baseimage 軽量イメージへの差し替えも可
    from {
        image = "eclipse-temurin:21-jre"
    }

    // to.image は skaffold/CI から -Djib.to.image=... で上書き
    to {
        image = "gateway-bff:dev"
    }

    container {
        ports = listOf("8080")
        // 任意: JVM オプション
        // jvmFlags = listOf("-XX:MaxRAMPercentage=75.0")
    }
}
