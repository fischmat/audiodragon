package de.matthiasfisch.audiodragon.core.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.shouldBe
import kotlin.math.PI
import kotlin.math.sin

class FrequencySpectrumTest : FunSpec({
    test("accepts a non-power-of-two sample buffer and locates a bin-centred tone") {
        val sampleCount = 1_000
        val toneBin = 37
        val samples = FloatArray(sampleCount) { sample ->
            sin(2.0 * PI * toneBin * sample / sampleCount).toFloat()
        }

        val spectrum = FrequencySpectrum.magnitudes(samples)

        spectrum.size shouldBe sampleCount / 2
        spectrum.indices.maxBy { spectrum[it] } shouldBe toneBin
        spectrum[toneBin].shouldBeBetween(0.99, 1.01, 0.0)
    }

    test("keeps silence at zero") {
        FrequencySpectrum.magnitudes(FloatArray(999)).all { it == 0.0 } shouldBe true
    }
})
