package de.matthiasfisch.audiodragon.core.platform.pactl

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import javax.sound.sampled.AudioFormat

class SampleSpecificationTest : FunSpec({

    context("Create from sample specification") {
        test("Should return correct result for valid specifications") {
            PactlSampleSpecification("s32le 2ch 48000Hz") shouldBe PactlSampleSpecification(
                AudioFormat.Encoding.PCM_SIGNED,
                32,
                2,
                48000.0f,
                false
            )

            PactlSampleSpecification("s16le 2ch 48000Hz") shouldBe PactlSampleSpecification(
                AudioFormat.Encoding.PCM_SIGNED,
                16,
                2,
                48000.0f,
                false
            )

            PactlSampleSpecification("float16be 5ch 41000Hz") shouldBe PactlSampleSpecification(
                AudioFormat.Encoding.PCM_FLOAT,
                16,
                5,
                41000.0f,
                true
            )

            PactlSampleSpecification("u8 1ch 8000Hz") shouldBe PactlSampleSpecification(
                AudioFormat.Encoding.PCM_UNSIGNED,
                8,
                1,
                8000.0f,
                false
            )
        }
    }
})
