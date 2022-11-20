package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.types.LibraryInitializedEvent
import de.matthiasfisch.audiodragon.types.LibraryRefreshedEvent
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

const val LIBRARY_EVENTS_TOPIC = "/de/matthiasfisch/audiodragon/library"

@Service
class LibraryEventBroker(val template: SimpMessagingTemplate) {

    fun sendLibraryInitialized() {
        template.convertAndSend(LIBRARY_EVENTS_TOPIC, LibraryInitializedEvent())
    }

    fun sendLibraryRefreshed() {
        template.convertAndSend(LIBRARY_EVENTS_TOPIC, LibraryRefreshedEvent())
    }
}