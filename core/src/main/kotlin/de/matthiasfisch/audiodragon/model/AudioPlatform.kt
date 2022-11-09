package de.matthiasfisch.audiodragon.model

import de.matthiasfisch.audiodragon.buffer.AudioBuffer
import de.matthiasfisch.audiodragon.buffer.ResettableAudioBuffer
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat

interface AudioPlatform {
    val platformId: String

    fun getAudioSources(): List<AudioSource>

    fun createRecording(
        audioSource: AudioSource,
        audioFormat: AudioFormat,
        blockSize: Int,
        bufferFactory: (AudioFormat) -> AudioBuffer
    ): Recording<*>
}

abstract class AudioSource(val name: String) {

    abstract fun getAudioFormats(): List<AudioFormat>
}

abstract class Recording<T: AudioSource>(
    val audioSource: T,
    val audioFormat: AudioFormat,
    val blockSize: Int,
    bufferFactory: (AudioFormat) -> AudioBuffer
) : Thread("recording-${audioSource.name}") {

    private val audioBuffer = ResettableAudioBuffer { bufferFactory(audioFormat) }
    private val chunkPublisher = PublishProcessor.create<AudioChunk>()
    private val stopFuture = CompletableFuture<AudioBuffer>()
    private val stopRequested = AtomicBoolean(false)

    protected abstract fun record(chunkPublisher: PublishProcessor<AudioChunk>)

    override fun run() {
        chunkPublisher.subscribe {
            audioBuffer.add(it.pcmData)
        }

        kotlin.runCatching {
            record(chunkPublisher)
        }.onFailure {
            stopFuture.completeExceptionally(it)
        }.onSuccess {
            stopFuture.complete(audioBuffer)
        }
    }

    fun isStopRequested() = stopRequested.get()

    fun startRecording() {
        start()
    }

    fun stopRecording(): CompletableFuture<AudioBuffer> {
        stopRequested.set(true)
        return stopFuture.copy()
    }

    fun getAudio(): ResettableAudioBuffer = audioBuffer

    fun reset(): AudioBuffer = audioBuffer.reset()

    fun audioChunkFlowable(): Flowable<AudioChunk> = Flowable.fromPublisher(chunkPublisher)
}

data class AudioChunk(
    val pcmData: PcmData,
    val audioSource: AudioSource,
    val audioFormat: AudioFormat
)