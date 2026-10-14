plugins {
    id("java")
}

group = "de.matthiasfisch.audiodragon"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.14.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.4")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}
