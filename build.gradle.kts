import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.ReportRenderer
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
	id("org.springframework.boot") version "3.5.16"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("com.github.jk1.dependency-license-report") version "3.1.4"
	id("org.owasp.dependencycheck") version "10.0.3" apply false
}

java.sourceCompatibility = JavaVersion.VERSION_21

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

allprojects {
	group = "de.matthiasfisch"
	version = "0.2.1-alpha"

	repositories {
		mavenCentral()
	}

	tasks.withType<KotlinCompile> {
		compilerOptions {
			freeCompilerArgs.add("-Xjsr305=strict")
			jvmTarget.set(JvmTarget.JVM_21)
		}
	}

	apply(plugin = "org.owasp.dependencycheck")
}

dependencies {
	implementation("com.github.wendykierp:JTransforms:3.1")

	// Spring
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework:spring-messaging")
	implementation("org.springframework:spring-websocket")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	// Utilities
	implementation("io.reactivex.rxjava2:rxkotlin:2.4.0")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("com.squareup.okhttp3:okhttp:5.5.0")
	implementation("com.jayway.jsonpath:json-path:3.0.0")
	// JsonPath 3 declares its default JSON provider as runtime-only; it is used directly here.
	implementation("net.minidev:json-smart:2.6.0")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

	// Audio file handling
	implementation("net.jthink:jaudiotagger:3.0.1")
	implementation(files("libs/java-lame-3.98.4.jar"))

	// Database
	implementation("org.jooq:jooq:3.20.20")
	implementation("org.flywaydb:flyway-core:13.10.0")
	implementation("org.xerial:sqlite-jdbc:3.53.4.0")

	// Testing
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("io.kotest:kotest-runner-junit5:6.2.6")
	testImplementation("io.mockk:mockk:1.14.11")
	testImplementation("io.kotest:kotest-extensions-spring:6.2.6")

	// CVE mitigation
	implementation("org.yaml:snakeyaml:2.7") {
		because("CVE-2022-38751 and subsequent SnakeYAML vulnerabilities")
	}
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.named<BootJar>("bootJar") {
	mainClass.set("de.matthiasfisch.audiodragon.AudiodragonApplicationKt")
}

licenseReport {
	renderers = arrayOf<ReportRenderer>(
		InventoryHtmlReportRenderer(
			"report.html",
			"Backend"
		)
	)
	filters = arrayOf<DependencyFilter>(LicenseBundleNormalizer())
	excludeBoms = true
	allowedLicensesFile = File("$projectDir/config/allowed-licenses.json")
}

configure<org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension> {
	format = "ALL"
}
