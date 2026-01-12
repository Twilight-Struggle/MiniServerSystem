plugins {
    `java-library`
    id("com.google.protobuf")
}

dependencies {
    api("com.google.protobuf:protobuf-java:3.25.5")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                java {}
            }
        }
    }
}
