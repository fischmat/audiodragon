package de.matthiasfisch.audiodragon.core.platform

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.matthiasfisch.audiodragon.core.buffer.InMemoryAudioBuffer
import de.matthiasfisch.audiodragon.core.model.AudioBufferFactory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import kotlin.math.PI
import kotlin.math.sin

/** Opt in with AUDIODRAGON_TEST_PIPEWIRE=1; uses an isolated, inaudible null sink. */
class PipeWireLiveTest : FunSpec({
    test("Records real PipeWire playback at every width and repeatedly stops during silence")
        .config(enabled = System.getenv("AUDIODRAGON_TEST_PIPEWIRE") == "1") {
            fun command(vararg arguments: String): String {
                val process = ProcessBuilder(arguments.toList()).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                check(process.waitFor(5, TimeUnit.SECONDS)) { "Timed out: ${arguments.toList()}" }
                check(process.exitValue() == 0) { output }
                return output.trim()
            }

            val sink = "audiodragon_test_${System.nanoTime()}"
            val module = command("pactl", "load-module", "module-null-sink", "sink_name=$sink",
                "rate=48000", "channels=2", "format=s32le")
            try {
                val platform = PipeWireAudioPlatform()
                val source = platform.getAudioSources().filterIsInstance<PipeWireAudioSource>()
                    .single { it.monitorName == "$sink.monitor" }
                source.getAudioFormats().first().sampleSizeInBits shouldBe 32
                val playbackBytes = ByteArray(24000 * 8)
                for (frame in 0 until 24000) {
                    val sample = (sin(2 * PI * 440 * frame / 48000) * 0x20000000).toInt()
                    for (channel in 0..1) for (byte in 0..3) {
                        playbackBytes[frame * 8 + channel * 4 + byte] = (sample shr (byte * 8)).toByte()
                    }
                }
                for (bits in listOf(16, 24, 32)) {
                    val signal = CountDownLatch(1)
                    val format = AudioFormat(48000f, bits, 2, true, false)
                    val recording = platform.createRecording(source, format, 2048,
                        AudioBufferFactory { InMemoryAudioBuffer(it, 4096) })
                    val subscription = recording.audioChunkFlowable().subscribe { chunk ->
                        chunk.pcmData.size % format.frameSize shouldBe 0
                        if (chunk.pcmData.any { it != 0.toByte() }) signal.countDown()
                    }
                    var playback: Process? = null
                    try {
                        recording.startRecording()
                        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                        var ready = false
                        while (!ready && System.nanoTime() < deadline) {
                            ready = jacksonObjectMapper().readTree(command("pactl", "--format=json", "list", "source-outputs"))
                                .any { it.path("properties").path("application.name").asText() == "AudioDragon" }
                            if (!ready) Thread.sleep(20)
                        }
                        ready shouldBe true
                        val player = ProcessBuilder("paplay", "--raw", "--device=$sink", "--format=s32le",
                            "--rate=48000", "--channels=2").start()
                        playback = player
                        player.outputStream.use { it.write(playbackBytes) }
                        player.waitFor(5, TimeUnit.SECONDS) shouldBe true
                        player.exitValue() shouldBe 0
                        signal.await(3, TimeUnit.SECONDS) shouldBe true
                        val audio = recording.stopRecording().get(2, TimeUnit.SECONDS)
                        (audio.size() > 0) shouldBe true
                        recording.join(1000)
                        recording.isAlive shouldBe false
                    } finally {
                        playback?.destroyForcibly()
                        recording.stopRecording().get(2, TimeUnit.SECONDS).close()
                        subscription.dispose()
                    }
                }
                // Both backends remain usable for discovery in the same desktop session.
                JavaAudioPlatform().getAudioSources().isNotEmpty() shouldBe true
            } finally {
                command("pactl", "unload-module", module)
            }
        }
})
