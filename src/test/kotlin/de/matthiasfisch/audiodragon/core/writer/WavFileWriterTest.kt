package de.matthiasfisch.audiodragon.core.writer

import de.matthiasfisch.audiodragon.core.buffer.InMemoryAudioBuffer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

class WavFileWriterTest : FunSpec({
    test("writes the original PCM format as WAV") {
        val format = AudioFormat(48_000f, 16, 2, true, false)
        val buffer = InMemoryAudioBuffer(format)
        buffer.add(ByteArray(format.frameSize * 32))
        val directory = Files.createTempDirectory("audiodragon-wav-test")

        val output = WavFileWriter(directory).writeToFile(buffer, null)

        output.fileName.toString().endsWith(".wav") shouldBe true
        val writtenFormat = AudioSystem.getAudioFileFormat(output.toFile()).format
        writtenFormat.encoding shouldBe format.encoding
        writtenFormat.sampleRate shouldBe format.sampleRate
        writtenFormat.sampleSizeInBits shouldBe format.sampleSizeInBits
        writtenFormat.channels shouldBe format.channels
        writtenFormat.frameSize shouldBe format.frameSize
        writtenFormat.isBigEndian shouldBe format.isBigEndian
    }
})
