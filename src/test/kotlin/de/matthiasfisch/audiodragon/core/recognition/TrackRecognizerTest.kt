package de.matthiasfisch.audiodragon.core.recognition

import de.matthiasfisch.audiodragon.core.buffer.InMemoryAudioBuffer
import de.matthiasfisch.audiodragon.core.model.PcmData
import de.matthiasfisch.audiodragon.core.model.TrackData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.IOException
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.milliseconds

class TrackRecognizerTest : FunSpec({
    test("retries transient recognition failures without failing the capture") {
        val recognizer = FlakyRecognizer()
        val format = AudioFormat(44_100f, 16, 1, true, false)
        val buffer = InMemoryAudioBuffer(format)

        val result = recognizer.recognizeTrack { buffer }.get()

        result?.title shouldBe "Recovered track"
        recognizer.calls shouldBe 2
    }
})

private class FlakyRecognizer : TrackRecognizer(
    0.milliseconds,
    1.milliseconds,
    3,
    emptyList()
) {
    var calls = 0

    override fun recognizeTrackInternal(
        sample: PcmData,
        audioFormat: AudioFormat,
        previousSamples: List<PcmData>
    ): TrackData? {
        calls++
        if (calls == 1) throw IOException("temporary provider failure")
        return TrackData(title = "Recovered track")
    }
}
