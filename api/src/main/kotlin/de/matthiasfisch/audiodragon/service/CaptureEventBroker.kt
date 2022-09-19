package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.model.getFrequencies
import de.matthiasfisch.audiodragon.model.getRMS
import de.matthiasfisch.audiodragon.recording.AudioChunk
import de.matthiasfisch.audiodragon.recording.Capture
import de.matthiasfisch.audiodragon.types.*
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

const val CAPTURE_EVENTS_TOPIC = "/capture"
const val METRICS_EVENTS_TOPIC = "/metrics"

@Service
class CaptureEventBroker(val template: SimpMessagingTemplate) {

    fun monitor(capture: Capture) {
        val captureDTO = CaptureDTO(capture)
        capture.captureStartedEvents().doOnNext {
            publishEvent(CaptureStartedEventDTO(captureDTO), template)
        }
        capture.captureStoppedEvents().doOnNext {
            publishEvent(CaptureEndedEventDTO(captureDTO), template)
        }
        capture.captureStopRequestedEvents().doOnNext {
            publishEvent(CaptureEndRequestedEventDTO(captureDTO), template)
        }
        capture.trackStartEvents().doOnNext {
            publishEvent(TrackStartedEventDTO(captureDTO), template)
        }
        capture.trackEndedEvents().doOnNext {
            publishEvent(TrackEndedEventDTO(captureDTO), template)
        }
        capture.trackRecognizedEvents().doOnNext {
            publishEvent(TrackRecognitionEventDTO(captureDTO, it), template)
        }
        capture.audioChunksFlowable().doOnNext {
            publishMetricsEvent(capture, it, template)
        }
    }

    private fun publishMetricsEvent(capture: Capture, audioChunk: AudioChunk, template: SimpMessagingTemplate) {
        publishEvent(
            AudioMetricsEventDTO(
                audioChunk.pcmData.getRMS(audioChunk.audioFormat),
                capture.audio().duration().inWholeMilliseconds,
                capture.audio().size(),
                audioChunk.pcmData.getFrequencies(audioChunk.audioFormat).toList()
            ),
            template
        )
    }

    private fun publishEvent(event: EventDTO, template: SimpMessagingTemplate) = template.convertAndSend(
        CAPTURE_EVENTS_TOPIC, event
    )
}