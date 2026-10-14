package de.matthiasfisch.audiodragon.core.platform

import de.matthiasfisch.audiodragon.core.buffer.InMemoryAudioBuffer
import de.matthiasfisch.audiodragon.core.model.*
import de.matthiasfisch.audiodragon.util.getId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.sound.sampled.AudioFormat

private val SOURCE = PipeWireAudioSource("output.monitor", "Monitor of Speakers", 48000)
private val BUFFER_FACTORY = AudioBufferFactory { InMemoryAudioBuffer(it, 4096) }

class PipeWireAudioPlatformTest : FunSpec({
    fun discover(json: String): List<AudioSource> = PipeWireAudioPlatform(
        { FakeProcess(ByteArrayInputStream(json.toByteArray()), exited = 0) }, "Linux"
    ).getAudioSources()

    test("Discovers distinct idle and suspended monitors with stable IDs and preferred formats") {
        val sources = discover("""[
            {"name":"a","description":"Speakers","monitor_source":"a.monitor","state":"SUSPENDED","sample_specification":"float32le 2ch 96000Hz"},
            {"name":"b","description":"Speakers","monitor_source":"b.monitor","state":"IDLE"},
            {"name":"a","description":"Duplicate","monitor_source":"a.monitor"}
        ]""")
        sources.size shouldBe 2
        sources.map { it.name } shouldBe listOf("Monitor of Speakers", "Monitor of Speakers")
        String(Base64.getDecoder().decode(sources[0].getId())) shouldBe "pipewire:a.monitor"
        (sources[0].getId() == sources[1].getId()) shouldBe false
        PipeWireAudioSource("a.monitor", "Renamed", 44100).getId() shouldBe sources[0].getId()
        sources[0].getAudioFormats().first().let {
            it.sampleRate shouldBe 96000f
            it.sampleSizeInBits shouldBe 32
            it.channels shouldBe 2
        }
        sources[1].getAudioFormats().first().sampleRate shouldBe 48000f
        sources[0].getAudioFormats().map { it.sampleSizeInBits }.toSet() shouldBe setOf(16, 24, 32)
    }

    test("Fallback label, rate and empty outputs") {
        discover("""[{"name":"HDMI","monitor_source":"hdmi.monitor","sample_specification":"invalid"}]""")
            .first().name shouldBe "Monitor of HDMI"
        discover("[]").size shouldBe 0
    }

    test("Java source IDs retain their original encoding") {
        val source = object : AudioSource("Legacy mixer") {
            override fun getAudioFormats() = emptyList<AudioFormat>()
        }
        source.getId() shouldBe Base64.getEncoder().encodeToString("Legacy mixer".toByteArray())
    }

    test("Malformed discovery responses fail explicitly") {
        for (json in listOf("not JSON", "{}", "null", "[{}]")) {
            shouldThrow<IllegalStateException> { discover(json) }
        }
    }

    test("Missing tools and unsupported systems fail explicitly") {
        shouldThrow<IllegalStateException> {
            PipeWireAudioPlatform({ throw IOException("missing") }, "Linux").getAudioSources()
        }.message shouldContain "pulseaudio-utils"
        shouldThrow<IllegalStateException> {
            PipeWireAudioPlatform({ error("Must not launch") }, "Windows").getAudioSources()
        }.message shouldContain "Linux only"
    }

    test("Server error includes bounded stderr and drains large output") {
        val process = FakeProcess(ByteArrayInputStream(ByteArray(0)), ByteArray(100000) { 'E'.code.toByte() }, 1)
        val error = shouldThrow<IllegalStateException> {
            PipeWireAudioPlatform({ process }, "Linux").getAudioSources()
        }
        error.message shouldContain "exit 1"
        (error.message!!.length < 8300) shouldBe true
        process.destroyed shouldBe true
    }

    test("Discovery timeout kills the process") {
        val process = FakeProcess(ByteArrayInputStream("[]".toByteArray()))
        shouldThrow<IllegalStateException> {
            PipeWireAudioPlatform({ process }, "Linux", 10).getAudioSources()
        }.message shouldContain "timed out"
        process.destroyed shouldBe true
    }

    for (bits in listOf(16, 24, 32)) {
        test("$bits-bit fragmented PCM is preserved byte-for-byte in power-of-two frame blocks") {
            val format = AudioFormat(48000f, bits, 2, true, false)
            val frames = Integer.highestOneBit(2048 / format.frameSize)
            val bytes = ByteArray(frames * format.frameSize * 2) { (it % 251).toByte() }
            val process = FakeProcess(FragmentedInput(bytes), ByteArray(100000) { 1 })
            var command = emptyList<String>()
            val platform = PipeWireAudioPlatform({ command = it; process }, "Linux")
            val recording = platform.createRecording(SOURCE, format, 2048, BUFFER_FACTORY)
            var chunks = 0
            recording.audioChunkFlowable().subscribe {
                it.pcmData.size shouldBe frames * format.frameSize
                if (++chunks == 2) recording.stopRecording()
            }
            recording.startRecording()
            recording.join(2000)
            recording.isAlive shouldBe false
            val audio = recording.stopRecording().get(1, TimeUnit.SECONDS)
            audio.get().toArray().map { it as Byte }.toByteArray() shouldBe bytes
            command shouldBe listOf("parec", "--raw", "--device=output.monitor", "--format=s${bits}le",
                "--rate=48000", "--channels=2", "--client-name=AudioDragon", "--stream-name=System-audio", "--latency-msec=50")
            process.destroyed shouldBe true
        }
    }

    test("Monitor names are passed as one argument without shell interpretation") {
        var command = emptyList<String>()
        val process = FakeProcess(ByteArrayInputStream(ByteArray(4)), exited = 0)
        val source = PipeWireAudioSource("output ; $(anything).monitor", "Name", 48000)
        val recording = PipeWireAudioPlatform({ command = it; process }, "Linux")
            .createRecording(source, AudioFormat(48000f, 16, 2, true, false), 1, BUFFER_FACTORY)
        recording.audioChunkFlowable().subscribe { recording.stopRecording() }
        recording.startRecording()
        recording.join(2000)
        command[2] shouldBe "--device=output ; $(anything).monitor"
        recording.stopRecording().get(1, TimeUnit.SECONDS).size() shouldBe 4
    }

    test("Stop unblocks a silent stream and repeated stops are harmless") {
        val input = BlockingInput()
        val process = FakeProcess(input)
        val recording = PipeWireAudioPlatform({ process }, "Linux")
            .createRecording(SOURCE, AudioFormat(48000f, 32, 2, true, false), 2048, BUFFER_FACTORY)
        recording.startRecording()
        input.reading.await(1, TimeUnit.SECONDS) shouldBe true
        recording.stopRecording().get(2, TimeUnit.SECONDS).size() shouldBe 0
        recording.stopRecording().get(1, TimeUnit.SECONDS).size() shouldBe 0
        recording.join(1000)
        recording.isAlive shouldBe false
        process.destroyed shouldBe true
    }

    test("Stop before launch never starts a child process") {
        val recording = PipeWireAudioPlatform({ error("Must not launch") }, "Linux")
            .createRecording(SOURCE, AudioFormat(48000f, 32, 2, true, false), 2048, BUFFER_FACTORY)
        val stopped = recording.stopRecording()
        recording.startRecording()
        stopped.get(1, TimeUnit.SECONDS).size() shouldBe 0
    }

    test("Missing recorder fails the completion future with installation guidance") {
        val recording = PipeWireAudioPlatform({ throw IOException("parec missing") }, "Linux")
            .createRecording(SOURCE, AudioFormat(48000f, 32, 2, true, false), 2048, BUFFER_FACTORY)
        recording.startRecording()
        recording.join(1000)
        shouldThrow<ExecutionException> {
            recording.stopRecording().get(1, TimeUnit.SECONDS)
        }.cause!!.message shouldContain "pulseaudio-utils"
    }

    test("Cleanup forces termination when a child ignores graceful shutdown") {
        val process = FakeProcess(ByteArrayInputStream("[]".toByteArray()), resistGracefulStop = true)
        shouldThrow<IllegalStateException> {
            PipeWireAudioPlatform({ process }, "Linux", 10).getAudioSources()
        }.message shouldContain "timed out"
        process.forced shouldBe true
        process.isAlive shouldBe false
    }

    test("Stopping a blocked recorder forces exit if graceful termination is ignored") {
        val input = BlockingInput()
        val process = FakeProcess(input, resistGracefulStop = true)
        val recording = PipeWireAudioPlatform({ process }, "Linux")
            .createRecording(SOURCE, AudioFormat(48000f, 32, 2, true, false), 2048, BUFFER_FACTORY)
        recording.startRecording()
        input.reading.await(1, TimeUnit.SECONDS) shouldBe true
        recording.stopRecording().get(2, TimeUnit.SECONDS).size() shouldBe 0
        recording.join(1000)
        recording.isAlive shouldBe false
        process.forced shouldBe true
    }

    test("Mono capture preserves samples with a block smaller than one frame") {
        val bytes = byteArrayOf(1, 2, 3)
        val process = FakeProcess(FragmentedInput(bytes))
        val recording = PipeWireAudioPlatform({ process }, "Linux")
            .createRecording(SOURCE, AudioFormat(48000f, 24, 1, true, false), 1, BUFFER_FACTORY)
        recording.audioChunkFlowable().subscribe { recording.stopRecording() }
        recording.startRecording()
        recording.join(1000)
        recording.stopRecording().get(1, TimeUnit.SECONDS)
            .get().toArray().map { it as Byte }.toByteArray() shouldBe bytes
    }

    test("Stop racing with launch cleans up the newly started child") {
        val enteringLaunch = CountDownLatch(1)
        val releaseLaunch = CountDownLatch(1)
        val process = FakeProcess(BlockingInput())
        val recording = PipeWireAudioPlatform({
            enteringLaunch.countDown()
            check(releaseLaunch.await(2, TimeUnit.SECONDS))
            process
        }, "Linux").createRecording(SOURCE, AudioFormat(48000f, 32, 2, true, false), 2048, BUFFER_FACTORY)
        recording.startRecording()
        enteringLaunch.await(1, TimeUnit.SECONDS) shouldBe true
        val stopped = recording.stopRecording()
        releaseLaunch.countDown()
        stopped.get(2, TimeUnit.SECONDS).size() shouldBe 0
        process.destroyed shouldBe true
    }

    test("Unexpected exit and truncated frames fail and preserve complete final frames") {
        val bytes = ByteArray(21) { it.toByte() }
        val process = FakeProcess(FragmentedInput(bytes), "server disconnected".toByteArray(), 1)
        val recording = PipeWireAudioPlatform({ process }, "Linux")
            .createRecording(SOURCE, AudioFormat(48000f, 24, 2, true, false), 2048, BUFFER_FACTORY)
        val chunkSizes = mutableListOf<Int>()
        recording.audioChunkFlowable().subscribe { chunkSizes.add(it.pcmData.size) }
        recording.startRecording()
        recording.join(2000)
        val error = shouldThrow<ExecutionException> { recording.stopRecording().get(1, TimeUnit.SECONDS) }
        error.cause!!.message shouldContain "exit 1"
        error.cause!!.message shouldContain "3 incomplete frame bytes"
        error.cause!!.message shouldContain "server disconnected"
        recording.getAudio().get().toArray().map { it as Byte }.toByteArray() shouldBe bytes.copyOf(18)
        chunkSizes shouldBe listOf(12, 6)
        process.destroyed shouldBe true
    }

    test("Invalid formats and block sizes fail before a process is launched") {
        val platform = PipeWireAudioPlatform({ error("Must not launch") }, "Linux")
        val invalid = listOf(
            AudioFormat(48000f, 8, 2, true, false),
            AudioFormat(48000f, 24, 3, true, false),
            AudioFormat(48000f, 24, 2, true, true),
            AudioFormat(48000f, 24, 2, false, false),
            AudioFormat(Float.NaN, 32, 2, true, false),
            AudioFormat(48000.5f, 32, 2, true, false),
            AudioFormat(384001f, 32, 2, true, false),
            AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000f, 24, 2, 8, 48000f, false),
            AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000f, 24, 2, 6, 44100f, false),
            AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 48000f, 32, 2, 8, 48000f, false)
        )
        invalid.forEach { format ->
            shouldThrow<IllegalArgumentException> { platform.createRecording(SOURCE, format, 2048, BUFFER_FACTORY) }
        }
        shouldThrow<IllegalArgumentException> {
            platform.createRecording(SOURCE, AudioFormat(48000f, 32, 2, true, false), 0, BUFFER_FACTORY)
        }
    }
})

private class FragmentedInput(bytes: ByteArray) : ByteArrayInputStream(bytes) {
    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len.coerceAtMost(3))
}

private class BlockingInput : InputStream() {
    val reading = CountDownLatch(1)
    private val closed = CountDownLatch(1)
    override fun read(): Int {
        reading.countDown()
        check(closed.await(3, TimeUnit.SECONDS)) { "Silent stream was not stopped" }
        return -1
    }
    override fun close() { closed.countDown() }
}

private class FakeProcess(
    private val stdout: InputStream,
    stderr: ByteArray = ByteArray(0),
    exited: Int? = null,
    private val resistGracefulStop: Boolean = false
) : Process() {
    private val stderr = ByteArrayInputStream(stderr)
    private val stdin = ByteArrayOutputStream()
    private val exit = CompletableFuture<Int>()
    @Volatile var destroyed = false
        private set
    @Volatile var forced = false
        private set
    init { exited?.let { exit.complete(it) } }
    override fun getInputStream() = stdout
    override fun getErrorStream() = stderr
    override fun getOutputStream() = stdin
    override fun waitFor(): Int = exit.get()
    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = try {
        exit.get(timeout, unit)
        true
    } catch (e: TimeoutException) { false }
    override fun exitValue(): Int = if (exit.isDone) exit.get() else throw IllegalThreadStateException()
    override fun isAlive() = !exit.isDone
    override fun destroy() {
        destroyed = true
        if (resistGracefulStop) return
        stdout.close()
        exit.complete(143)
    }
    override fun destroyForcibly(): Process {
        forced = true
        destroyed = true
        stdout.close()
        exit.complete(137)
        return this
    }
}
