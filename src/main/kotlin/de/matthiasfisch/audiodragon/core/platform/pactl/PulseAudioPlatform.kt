package de.matthiasfisch.audiodragon.core.platform.pactl

import de.matthiasfisch.audiodragon.core.model.*
import io.reactivex.processors.PublishProcessor
import mu.KotlinLogging
import org.apache.commons.lang3.SystemUtils
import javax.sound.sampled.AudioFormat

private val LOGGER = KotlinLogging.logger {}

class PulseAudioPlatform() : AudioPlatform {
    override val platformId: String = "pactl-audio"

    override fun getAudioSources(): List<AudioSource> =
        Pactl.getPcmSources().map { PulseAudioSource(it) }

    override fun createRecording(
        audioSource: AudioSource,
        audioFormat: AudioFormat,
        blockSize: Int,
        bufferFactory: AudioBufferFactory
    ): Recording<*> {
        require(audioSource is PulseAudioSource) { "Audio source type ${audioSource.javaClass} is not supported by this platform." }
        return PactlRecording(audioSource, audioFormat, blockSize, bufferFactory)
    }

    override fun isSupported(): Boolean {
        return SystemUtils.IS_OS_LINUX && kotlin.runCatching { Pactl.isPactlInstalled() }.getOrElse { false }
    }
}

class PulseAudioSource(val device: PulseAudioDevice): AudioSource(device.name) {
    override fun getAudioFormats(): List<AudioFormat> =
        listOf(device.sampleSpecification.toAudioFormat())
}

private class PactlRecording(
    audioSource: PulseAudioSource,
    audioFormat: AudioFormat,
    blockSize: Int,
    bufferFactory: AudioBufferFactory
): Recording<PulseAudioSource>(audioSource, audioFormat, blockSize, bufferFactory) {

    override fun record(chunkPublisher: PublishProcessor<AudioChunk>) {
        val workingBuffer = ByteArray(blockSize)

        Pactl.record(audioSource.name, audioFormat, blockSize).use { input ->
            while (!isStopRequested()) {
                val bytesRead = input.read(workingBuffer)
                if (bytesRead == -1) {
                    LOGGER.warn { "Audio input stream returned -1. Stopping recording." }
                    return
                }

                val pcmData = workingBuffer.slice(0 until bytesRead).toByteArray()
                chunkPublisher.onNext(
                    AudioChunk(
                        pcmData,
                        audioSource,
                        audioFormat
                    )
                )
            }
        }
    }

}