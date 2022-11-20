package de.matthiasfisch.audiodragon.core.buffer

import de.matthiasfisch.audiodragon.core.model.PcmData
import java.util.concurrent.locks.ReentrantLock
import java.util.stream.Stream
import javax.sound.sampled.AudioFormat
import kotlin.concurrent.withLock
import kotlin.time.Duration

class ResettableAudioBuffer(private val bufferFactory: () -> AudioBuffer):
    AudioBuffer {
    private var buffer = bufferFactory()
    private val lock = ReentrantLock()

    fun reset(): AudioBuffer = lock.withLock {
        val oldBuffer = buffer
        buffer = bufferFactory()
        oldBuffer
    }

    override fun audioFormat(): AudioFormat = lock.withLock { buffer.audioFormat() }

    override fun add(data: PcmData) = lock.withLock { buffer.add(data) }

    override fun get(): Stream<Byte> = lock.withLock { buffer.get() }

    override fun get(offset: Duration, length: Duration?): Stream<Byte> = lock.withLock { buffer.get(offset, length) }

    override fun duration(): Duration = lock.withLock { buffer.duration() }

    override fun size(): Long = lock.withLock { buffer.size() }

    fun backingBuffer() = lock.withLock { buffer }

    override fun close() = lock.withLock { buffer.close() }
}