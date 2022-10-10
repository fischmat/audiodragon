package de.matthiasfisch.audiodragon.recording

import de.matthiasfisch.audiodragon.buffer.AudioBuffer
import de.matthiasfisch.audiodragon.buffer.ResettableAudioBuffer
import de.matthiasfisch.audiodragon.model.AudioSource
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import mu.KotlinLogging
import java.util.concurrent.CompletableFuture
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

private val LOGGER = KotlinLogging.logger {}

open class JavaAudioSystemRecording(
    val audioSource: AudioSource,
    val audioFormat: AudioFormat,
    bufferFactory: (AudioFormat) -> AudioBuffer,
    private val blockSize: Int,
) : Thread(), Recording {
    private val chunkPublisher = PublishProcessor.create<AudioChunk>()
    private val audioBuffer = ResettableAudioBuffer { bufferFactory(audioFormat) }
    private val stopFuture = CompletableFuture<AudioBuffer>()

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
            stopFuture.complete(audioBuffer)
        } catch (e: Throwable) {
            stopFuture.completeExceptionally(e)
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

    override fun reset() = audioBuffer.reset()

    override fun audioChunkFlowable() = Flowable.fromPublisher(chunkPublisher)

    override fun startRecording() = start()

    override fun stopRecording(): CompletableFuture<AudioBuffer> {
        interrupt()
        return stopFuture.copy()
    }

    override fun getAudio() = audioBuffer
}