import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.ReportRenderer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
	id("org.springframework.boot") version "2.7.5"
	id("io.spring.dependency-management") version "1.0.15.RELEASE"
	kotlin("jvm") version "1.6.21"
	kotlin("plugin.spring") version "1.6.21"
	id("com.github.jk1.dependency-license-report") version "2.0"
	id("org.owasp.dependencycheck") version "7.3.2" apply false
}

java.sourceCompatibility = JavaVersion.VERSION_17

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

allprojects {
	group = "de.matthiasfisch"
	version = "0.1.0-alpha"

	repositories {
		mavenCentral()
		jcenter() {
			content {
				includeModule("com.cloudburst", "java-lame")
			}
		}
	}

	tasks.withType<KotlinCompile> {
		kotlinOptions {
			freeCompilerArgs = listOf("-Xjsr305=strict")
			jvmTarget = "17"
		}
	}

	apply(plugin = "org.owasp.dependencycheck")
}

dependencies {
	implementation(project(":fft"))

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
	implementation("com.squareup.okhttp3:okhttp:4.10.0")
	implementation("com.jayway.jsonpath:json-path:2.7.0")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("io.github.microutils:kotlin-logging-jvm:3.0.4")

	// Audio file handling
	implementation("net.jthink:jaudiotagger:3.0.1")
	implementation("com.cloudburst:java-lame:3.98.4")

	// Database
	implementation("org.jooq:jooq:3.17.5")
	implementation("org.flywaydb:flyway-core:9.8.1")
	implementation("org.xerial:sqlite-jdbc:3.39.3.0")

	// Testing
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("io.kotest:kotest-runner-junit5:5.5.4")
	testImplementation("io.mockk:mockk:1.13.2")

	// CVE mitigation
	implementation("org.yaml:snakeyaml:1.33") {
		because("CVE-2022-38751")
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
	format = org.owasp.dependencycheck.reporting.ReportGenerator.Format.ALL
}