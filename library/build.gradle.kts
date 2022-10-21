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
    implementation(project(":core"))

    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("org.jooq:jooq:3.14.16")
    implementation("org.flywaydb:flyway-core:9.5.1")
    implementation("org.xerial:sqlite-jdbc:3.39.3.0")
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