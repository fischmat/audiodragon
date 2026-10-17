package de.matthiasfisch.audiodragon.service

import de.matthiasfisch.audiodragon.core.buffer.InMemoryAudioBuffer
import de.matthiasfisch.audiodragon.core.model.AudioBufferFactory
import de.matthiasfisch.audiodragon.core.model.AudioSource
import de.matthiasfisch.audiodragon.core.model.FrequencyAccumulator
import de.matthiasfisch.audiodragon.core.model.Recording
import de.matthiasfisch.audiodragon.core.model.getRMS
import de.matthiasfisch.audiodragon.types.AudioMetricsEventDTO
import de.matthiasfisch.audiodragon.types.InMemoryBufferStats
import io.reactivex.disposables.Disposable
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import javax.sound.sampled.AudioFormat

/** An ephemeral, memory-only stream for verifying that a source has audio. */
@Service
class AudioPreviewService(
    private val audioPlatformService: AudioPlatformService,
    private val template: SimpMessagingTemplate
) {
    private var recording: Recording<*>? = null
    private var metricsSubscription: Disposable? = null

    @Synchronized
    fun start(source: AudioSource, format: AudioFormat) {
        stop()
        val previewRecording = audioPlatformService.getAudioPlatform().createRecording(
            source, format, PREVIEW_BLOCK_SIZE,
            AudioBufferFactory { previewFormat -> InMemoryAudioBuffer(previewFormat, PREVIEW_BLOCK_SIZE) }
        )
        val accumulator = FrequencyAccumulator(format, FFT_CHUNKS)
        metricsSubscription = previewRecording.audioChunkFlowable().subscribe { chunk ->
            accumulator.accumulate(chunk.pcmData)
            template.convertAndSend(METRICS_EVENTS_TOPIC, AudioMetricsEventDTO(
                rms = chunk.pcmData.getRMS(format),
                trackTime = 0,
                frequencies = accumulator.getFrequencies().take(121),
                buffer = InMemoryBufferStats(0, Runtime.getRuntime().maxMemory()),
                sourceId = source.identity,
                preview = true
            ))
            // Recording requires a backing buffer; retain no more than the active audio block.
            previewRecording.reset().close()
        }
        recording = previewRecording
        previewRecording.startRecording()
    }

    @Synchronized
    fun stop() {
        metricsSubscription?.dispose()
        metricsSubscription = null
        recording?.stopRecording()?.thenAccept { it.close() }
        recording = null
    }

    private companion object {
        const val PREVIEW_BLOCK_SIZE = 4096
        const val FFT_CHUNKS = 16
    }
}
