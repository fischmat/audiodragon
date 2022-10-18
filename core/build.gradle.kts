import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.21"
}

group = "de.matthiasfisch.audiodragon"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":fft"))
    implementation("io.reactivex.rxjava2:rxkotlin:2.4.0")
    implementation("com.jayway.jsonpath:json-path:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("com.cloudburst:java-lame:3.98.4")
    implementation("io.github.microutils:kotlin-logging-jvm:2.1.23")
    testImplementation("io.kotest:kotest-runner-junit5:5.4.2")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}