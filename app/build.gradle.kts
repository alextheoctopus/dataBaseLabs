plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    kotlin("plugin.serialization") version "2.2.0"
    id("com.google.protobuf") version "0.9.4"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(libs.guava)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation(kotlin("stdlib"))
    implementation("io.arrow-kt:arrow-core:1.2.4")

    // ✅ библиотека для Protobuf
    implementation("com.google.protobuf:protobuf-java:3.25.1")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "com.customDB.MainKt"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    }
}

sourceSets {
    main {
        proto {
            // где искать .proto файлы
            srcDir("./src/main/proto")
        }
        java {
            // куда будут генерироваться java-классы
            srcDirs("build/generated/source/proto/main/java")
        }
    }
}
