package de.matthiasfisch.audiodragon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AudiodragonApplication

fun main(args: Array<String>) {
	System.setProperty("org.jooq.no-tips", "true")
	runApplication<AudiodragonApplication>(*args)
}
