package de.matthiasfisch.audiodragon.platform

import de.matthiasfisch.audiodragon.buffer.AudioBuffer
import de.matthiasfisch.audiodragon.model.AudioChunk
import de.matthiasfisch.audiodragon.model.AudioPlatform
import de.matthiasfisch.audiodragon.model.AudioSource
import de.matthiasfisch.audiodragon.model.Recording
import io.reactivex.processors.PublishProcessor
import mu.KotlinLogging
import javax.sound.sampled.*

private val LOGGER = KotlinLogging.logger {}

class JavaAudioPlatform: AudioPlatform {
    override val platformId = "java-audio"

    override fun getAudioSources(): List<AudioSource> {
        return AudioSystem.getMixerInfo()
            .map { AudioSystem.getMixer(it) }
            .filter { isRecordable(it) }
            .map { JASAudioSource(it) }
    }

    override fun createRecording(
        audioSource: AudioSource,
        audioFormat: AudioFormat,
        blockSize: Int,
        bufferFactory: (AudioFormat) -> AudioBuffer
    ): Recording<*> {
        require(audioSource is JASAudioSource) { "Audio source type ${audioSource.javaClass} is not supported by this platform." }
        return JASRecording(audioSource, audioFormat, blockSize, bufferFactory)
    }

    private fun isRecordable(mixer: Mixer) = mixer.targetLineInfo.filterIsInstance<DataLine.Info>().isNotEmpty()
}

class JASAudioSource(val mixer: Mixer): AudioSource(mixer.mixerInfo.name) {

    override fun getAudioFormats(): List<AudioFormat> {
        val dataLineInfos = mixer.targetLineInfo.filterIsInstance<DataLine.Info>()
        check(dataLineInfos.isNotEmpty()) { "No target data lines available for mixer ${name}." }
        return dataLineInfos
            .flatMap { it.formats.toList() }
            .distinct()
    }
}

private class JASRecording(
    audioSource: JASAudioSource,
    audioFormat: AudioFormat,
    blockSize: Int,
    bufferFactory: (AudioFormat) -> AudioBuffer
): Recording<JASAudioSource>(audioSource, audioFormat, blockSize, bufferFactory) {

    override fun record(chunkPublisher: PublishProcessor<AudioChunk>) {
        val workingBuffer = ByteArray(blockSize)

        LOGGER.info { "Starting recording of ${audioSource.name} with format $audioFormat." }
        val dataLine = AudioSystem.getTargetDataLine(audioFormat, audioSource.mixer.mixerInfo)
        dataLine.open(audioFormat, blockSize)
        dataLine.start()
        val input = AudioInputStream(dataLine)

        try {
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
        } finally {
            kotlin.runCatching {
                input.close()
                dataLine.close()
            }.onFailure {
                LOGGER.warn(it) { "Could not close data line for audio source ${audioSource.name}." }
            }
            LOGGER.info { "Recording of audio source ${audioSource.name} stopped." }
        }
    }
}