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
            capture.captureStartedEvents().subscribe {
                publishEvent(CaptureStartedEventDTO(captureDTO))
            },
            capture.captureStoppedEvents().subscribe {
                publishEvent(CaptureEndedEventDTO(captureDTO))
                disposeCaptureSubscriptions(capture)
            },
            capture.captureStopRequestedEvents().subscribe {
                publishEvent(CaptureEndRequestedEventDTO(captureDTO))
            },
            capture.trackStartEvents().subscribe {
                publishEvent(TrackStartedEventDTO(captureDTO))
            },
            capture.trackEndedEvents().subscribe {
                publishEvent(TrackEndedEventDTO(captureDTO))
            },
            capture.trackRecognizedEvents().subscribe {
                publishEvent(TrackRecognitionEventDTO(captureDTO, it))
            },
            capture.trackWrittenEvents().subscribe {
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

        val frequencyAccumulator = FrequencyAccumulator(capture.audioFormat, FFT_CHUNKS)

        return capture.audioChunksFlowable().subscribe { audioChunk ->
            val bufferStats = getBufferStats(capture)

            frequencyAccumulator.accumulate(audioChunk.pcmData)
            val frequencies = frequencyAccumulator.getFrequencies().toList()

            template.convertAndSend(
                METRICS_EVENTS_TOPIC, AudioMetricsEventDTO(
                    audioChunk.pcmData.getRMS(audioChunk.audioFormat),
                    capture.audio().duration().inWholeMilliseconds,
                    frequencies.slice(0..120),
                    bufferStats
                )
            )
        }
    }

    private fun getBufferStats(capture: Capture): BufferStats {
        return when (val buffer = capture.audio().backingBuffer()) {
            is InMemoryAudioBuffer -> InMemoryBufferStats(
                capture.audio().size(),
                Runtime.getRuntime().maxMemory()
            )

            is DiskSpillingAudioBuffer -> DiskSpillingBufferStats(
                capture.audio().size(),
                Runtime.getRuntime().maxMemory(),
                buffer.initiallyUsableDiskSpace
            )

            else -> throw IllegalStateException("Unknown buffer type ${capture.audio().backingBuffer().javaClass}")
        }
    }

    private fun publishEvent(event: EventDTO) {
        template.convertAndSend(
            CAPTURE_EVENTS_TOPIC, event
        )
    }
}