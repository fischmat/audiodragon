package de.matthiasfisch.audiodragon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AudiodragonApplication

fun main(args: Array<String>) {
	runApplication<AudiodragonApplication>(*args)
}
