package de.matthiasfisch.audiodragon.recognition

import de.matthiasfisch.audiodragon.buffer.AudioBuffer
import de.matthiasfisch.audiodragon.model.PcmData
import de.matthiasfisch.audiodragon.model.TrackData
import mu.KotlinLogging
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val LOGGER = KotlinLogging.logger {}

abstract class TrackRecognizer(delayUntilRecognition: Duration, private val sampleDuration: Duration,  private val maxRetriesForRecognition: Int) {
    private val delayedExecutor = CompletableFuture.delayedExecutor(delayUntilRecognition.inWholeMilliseconds, TimeUnit.MILLISECONDS)

    fun recognizeTrack(audioProvider: () -> AudioBuffer): CompletableFuture<TrackData?> =
        CompletableFuture.supplyAsync({ recognizeTrack(audioProvider, listOf()) }, delayedExecutor)
            .thenCompose { it }

    private fun recognizeTrack(audioProvider: () -> AudioBuffer, previousSamples: List<PcmData>): CompletableFuture<TrackData?> {
        check(previousSamples.size + 1 < maxRetriesForRecognition) { "Maximum retries reached unexpectedly." }

        val audioBuffer = audioProvider()
        val sample = takeSample(audioBuffer)
        val track = try {
            recognizeTrackInternal(sample, audioBuffer.audioFormat(), previousSamples)
        } catch (e: IOException) {
            return CompletableFuture.failedFuture(e)
        }

        if (track != null) {
            LOGGER.info { "Track was recognized as ${track.title} by ${track.artist}." }
            return CompletableFuture.completedFuture(track)
        }

        val retries = previousSamples.size + 1
        if (retries == maxRetriesForRecognition) {
            LOGGER.info { "Track could not be recognized after $retries retries. Giving up... :(" }
            return CompletableFuture.completedFuture(null)
        }

        LOGGER.debug { "Track could not be recognized at retry $retries. Rescheduling recognition." }
        return CompletableFuture.supplyAsync({ recognizeTrack(audioProvider, previousSamples + sample) }, delayedExecutor)
            .thenCompose { it }
    }

    protected abstract fun recognizeTrackInternal(
        sample: PcmData,
        audioFormat: AudioFormat,
        previousSamples: List<PcmData>
    ): TrackData?

    private fun takeSample(audioBuffer: AudioBuffer): PcmData {
        val time = audioBuffer.duration()
        val sampleStartTime = if (sampleDuration > time) 0.milliseconds else time.minus(sampleDuration)
        return audioBuffer.get(sampleStartTime, sampleDuration).toList().toByteArray()
    }
}