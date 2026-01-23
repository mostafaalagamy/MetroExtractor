buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
    }
}

plugins {
    id("com.squareup.wire") version "5.4.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("maven-publish")
}

version = "5.0.0-rc"
group = "project.pipepipe"

wire {
    java {
    }
}

dependencies {
    implementation("project.pipepipe:shared:5.0.0-rc")

    implementation("org.jsoup:jsoup:1.21.1")
    implementation("com.fasterxml.jackson.core:jackson-core:2.20.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
    implementation("org.nibor.autolink:autolink:0.12.0")
    implementation("com.github.spotbugs:spotbugs-annotations:4.9.6")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("org.brotli:dec:0.1.2")
    implementation("org.apache.commons:commons-lang3:3.19.0")
    implementation("org.cache2k:cache2k-api:2.6.1.Final")
    implementation("org.cache2k:cache2k-core:2.6.1.Final")
    implementation("io.ktor:ktor-client-core:3.3.1")
    implementation("io.ktor:ktor-client-cio:3.3.1")
    implementation("io.lettuce:lettuce-core:6.8.0.RELEASE")

    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.0")
}

kotlin {
    jvmToolchain(24)
}
