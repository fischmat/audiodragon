package de.matthiasfisch.audiodragon.recording

import de.matthiasfisch.audiodragon.model.PcmData
import de.matthiasfisch.audiodragon.buffer.AudioBuffer
import de.matthiasfisch.audiodragon.buffer.ResettableAudioBuffer
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import mu.KotlinLogging
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

private val LOGGER = KotlinLogging.logger {}
private const val DEFAULT_BLOCK_SIZE = 2048

fun AudioSource.record(audioFormat: AudioFormat, bufferFactory: () -> AudioBuffer, blockSize: Int = 2048) =
    Recording(this, audioFormat, bufferFactory, blockSize)

open class Recording(
    private val audioSource: AudioSource,
    private val audioFormat: AudioFormat,
    bufferFactory: () -> AudioBuffer,
    private val blockSize: Int,
) : Thread() {
    private val chunkPublisher = PublishProcessor.create<AudioChunk>()
    private val audioBuffer = ResettableAudioBuffer(bufferFactory)

    init {
        check(blockSize > 0) { "Block size must be positive." }
        require(audioFormat.frameSize == AudioSystem.NOT_SPECIFIED || blockSize % audioFormat.frameSize == 0) {
            "Block size must be an integral multiple if the frame size of ${audioFormat.frameSize} bytes."
        }
    }

    override fun run() {
        val workingBuffer = ByteArray(blockSize)

        LOGGER.info { "Starting recording of ${audioSource.name} with format $audioFormat." }
        val dataLine = AudioSystem.getTargetDataLine(audioFormat, audioSource.mixer.mixerInfo)
        dataLine.open(audioFormat, blockSize)
        dataLine.start()
        val input = AudioInputStream(dataLine)

        try {
            while (!isInterrupted) {
                val bytesRead = input.read(workingBuffer)
                if (bytesRead == -1) {
                    LOGGER.debug { "Audio input stream returned -1. Stopping recording." }
                    return
                }

                val pcmData = workingBuffer.slice(0 until bytesRead).toByteArray()
                audioBuffer.add(pcmData)

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
            LOGGER.debug { "Recording of audio source ${audioSource.name} stopped." }
        }
    }

    fun reset() = audioBuffer.reset()

    fun getAudio() = audioBuffer

    fun toFlowable() = Flowable.fromPublisher(chunkPublisher)
}

data class AudioChunk(
    val pcmData: PcmData,
    val audioSource: AudioSource,
    val audioFormat: AudioFormat
)