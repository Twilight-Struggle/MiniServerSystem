/*
 * どこで: libs/proto/build.gradle.kts
 * 何を: proto モジュールのビルド/生成設定を定義
 * なぜ: protobuf 依存とコード生成を明示し、安定したビルドを確保するため
 */
plugins {
    `java-library`
    id("com.google.protobuf")
}

// ルートで管理するバージョンを参照して重複を防ぐ。
val protobufVersion: String by rootProject.extra

dependencies {
    api("com.google.protobuf:protobuf-java:$protobufVersion")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                java {}
            }
        }
    }
}
