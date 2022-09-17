package de.matthiasfisch.audiodragon.buffer

import de.matthiasfisch.audiodragon.model.PcmData
import java.util.stream.Stream
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration

interface AudioBuffer {
    fun audioFormat(): AudioFormat

    fun add(data: PcmData)

    fun get(): Stream<Byte>

    fun get(offset: Duration) = get(offset, null)

    fun get(offset: Duration, length: Duration?): Stream<Byte>

    fun duration(): Duration

    fun size(): Long
}