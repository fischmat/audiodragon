import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.ReportRenderer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
	id("org.springframework.boot") version "2.7.3"
	id("io.spring.dependency-management") version "1.0.13.RELEASE"
	kotlin("jvm") version "1.6.21"
	kotlin("plugin.spring") version "1.6.21"
	id("com.github.jk1.dependency-license-report") version "2.0"
}

java.sourceCompatibility = JavaVersion.VERSION_17

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

allprojects {
	group = "de.matthiasfisch"
	version = "0.0.1-SNAPSHOT"

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
}

dependencies {
	implementation(project(":api"))
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("io.github.microutils:kotlin-logging-jvm:3.0.4")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("io.kotest:kotest-runner-junit5:5.5.4")
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