package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.core.buffer.DiskSpillingAudioBuffer
import de.matthiasfisch.audiodragon.core.buffer.InMemoryAudioBuffer
import de.matthiasfisch.audiodragon.core.capture.Capture
import de.matthiasfisch.audiodragon.core.model.FrequencyAccumulator
import de.matthiasfisch.audiodragon.core.model.getRMS
import de.matthiasfisch.audiodragon.types.*
import io.reactivex.disposables.Disposable
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import kotlin.io.path.absolutePathString

const val CAPTURE_EVENTS_TOPIC = "/capture"
const val METRICS_EVENTS_TOPIC = "/metrics"
private const val FFT_CHUNKS = 32

@Service
class CaptureEventBroker(val template: SimpMessagingTemplate) {
    private val subscriptions = mutableMapOf<Capture, List<Disposable>>()

    fun monitor(capture: Capture) {
        val captureDTO = CaptureDTO(capture)
        subscriptions[capture] = listOf(
            capture.events.captureStartedEvents().subscribe {
                publishEvent(CaptureStartedEventDTO(captureDTO))
            },
            capture.events.captureStoppedEvents().subscribe {
                publishEvent(CaptureEndedEventDTO(captureDTO))
                disposeCaptureSubscriptions(capture)
            },
            capture.events.captureStopRequestedEvents().subscribe {
                publishEvent(CaptureEndRequestedEventDTO(captureDTO))
            },
            capture.events.trackStartEvents().subscribe {
                publishEvent(TrackStartedEventDTO(captureDTO))
            },
            capture.events.trackEndedEvents().subscribe {
                publishEvent(TrackEndedEventDTO(captureDTO))
            },
            capture.events.trackRecognizedEvents().subscribe {
                publishEvent(TrackRecognitionEventDTO(captureDTO, it))
            },
            capture.events.trackWrittenEvents().subscribe {
                publishEvent(TrackWrittenEventDTO(captureDTO, it.absolutePathString()))
            },
            publishMetrics(capture)
        )
    }

    private fun disposeCaptureSubscriptions(capture: Capture) {
        subscriptions[capture]?.forEach {
            it.dispose()
        }
    }

    private fun publishMetrics(capture: Capture): Disposable {

        val frequencyAccumulator = FrequencyAccumulator(capture.recording.audioFormat, FFT_CHUNKS)

        return capture.recording.audioChunkFlowable().subscribe { audioChunk ->
            val bufferStats = getBufferStats(capture)

            frequencyAccumulator.accumulate(audioChunk.pcmData)
            val frequencies = frequencyAccumulator.getFrequencies().toList()

            template.convertAndSend(
                METRICS_EVENTS_TOPIC, AudioMetricsEventDTO(
                    audioChunk.pcmData.getRMS(audioChunk.audioFormat),
                    capture.recording.getAudio().duration().inWholeMilliseconds,
                    frequencies.slice(0..120),
                    bufferStats
                )
            )
        }
    }

    private fun getBufferStats(capture: Capture): BufferStats {
        return when (val buffer = capture.recording.getAudio().backingBuffer()) {
            is InMemoryAudioBuffer -> InMemoryBufferStats(
                capture.recording.getAudio().size(),
                Runtime.getRuntime().maxMemory()
            )

            is DiskSpillingAudioBuffer -> DiskSpillingBufferStats(
                capture.recording.getAudio().size(),
                Runtime.getRuntime().maxMemory(),
                buffer.initiallyUsableDiskSpace
            )

            else -> throw IllegalStateException("Unknown buffer type ${capture.recording.getAudio().backingBuffer().javaClass}")
        }
    }

    private fun publishEvent(event: EventDTO) {
        template.convertAndSend(
            CAPTURE_EVENTS_TOPIC, event
        )
    }
}