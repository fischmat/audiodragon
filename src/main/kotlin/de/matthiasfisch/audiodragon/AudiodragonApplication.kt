package de.matthiasfisch.audiodragon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.web.context.WebServerInitializedEvent
import org.springframework.context.ApplicationListener

@SpringBootApplication
class AudiodragonApplication

private const val SERVER_PORT_ANNOUNCEMENT_PREFIX = "AUDIODRAGON_SERVER_PORT="

/**
 * A stable, machine-readable line for desktop launchers. Spring Boot's normal
 * logging is intentionally left untouched for command-line users.
 */
fun serverPortAnnouncement(port: Int): String = "$SERVER_PORT_ANNOUNCEMENT_PREFIX$port"

fun main(args: Array<String>) {
	System.setProperty("org.jooq.no-tips", "true")
	runApplication<AudiodragonApplication>(*args) {
		addListeners(ApplicationListener<WebServerInitializedEvent> { event ->
			println(serverPortAnnouncement(event.webServer.port))
		})
	}
}
