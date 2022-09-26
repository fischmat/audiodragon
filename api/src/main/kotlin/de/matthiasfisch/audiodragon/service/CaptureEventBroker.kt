package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.buffer.DiskSpillingAudioBuffer
import de.matthiasfisch.audiodragon.buffer.InMemoryAudioBuffer
import de.matthiasfisch.audiodragon.model.getFrequencies
import de.matthiasfisch.audiodragon.model.getRMS
import de.matthiasfisch.audiodragon.recording.AudioChunk
import de.matthiasfisch.audiodragon.recording.Capture
import de.matthiasfisch.audiodragon.types.*
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.nio.file.FileSystems
import java.nio.file.Files

const val CAPTURE_EVENTS_TOPIC = "/capture"
const val METRICS_EVENTS_TOPIC = "/metrics"

@Service
class CaptureEventBroker(val template: SimpMessagingTemplate) {

    fun monitor(capture: Capture) {
        val captureDTO = CaptureDTO(capture)
        capture.captureStartedEvents().subscribe {
            publishEvent(CaptureStartedEventDTO(captureDTO), template)
        }
        capture.captureStoppedEvents().subscribe {
            publishEvent(CaptureEndedEventDTO(captureDTO), template)
        }
        capture.captureStopRequestedEvents().subscribe {
            publishEvent(CaptureEndRequestedEventDTO(captureDTO), template)
        }
        capture.trackStartEvents().subscribe {
            publishEvent(TrackStartedEventDTO(captureDTO), template)
        }
        capture.trackEndedEvents().subscribe {
            publishEvent(TrackEndedEventDTO(captureDTO), template)
        }
        capture.trackRecognizedEvents().subscribe {
            publishEvent(TrackRecognitionEventDTO(captureDTO, it), template)
        }
        capture.audioChunksFlowable().subscribe {
            publishMetricsEvent(capture, it, template)
        }
    }

    private fun publishMetricsEvent(capture: Capture, audioChunk: AudioChunk, template: SimpMessagingTemplate) {
        val bufferStats = when (capture.audio().backingBuffer()) {
            is InMemoryAudioBuffer -> InMemoryBufferStats(
                capture.audio().size(),
                Runtime.getRuntime().maxMemory()
            )

            is DiskSpillingAudioBuffer -> DiskSpillingBufferStats(
                capture.audio().size(),
                Runtime.getRuntime().maxMemory(),
                Files.getFileStore(FileSystems.getDefault().rootDirectories.first()).usableSpace
            )

            else -> throw IllegalStateException("Unknown buffer type ${capture.audio().backingBuffer().javaClass}")
        }

        template.convertAndSend(
            METRICS_EVENTS_TOPIC, AudioMetricsEventDTO(
                audioChunk.pcmData.getRMS(audioChunk.audioFormat),
                capture.audio().duration().inWholeMilliseconds,
                audioChunk.pcmData.getFrequencies(audioChunk.audioFormat).toList(),
                bufferStats
            )
        )
    }

    private fun publishEvent(event: EventDTO, template: SimpMessagingTemplate) = template.convertAndSend(
        CAPTURE_EVENTS_TOPIC, event
    )
}