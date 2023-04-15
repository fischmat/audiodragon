package de.matthiasfisch.audiodragon.core.platform.pactl

import io.kotest.core.spec.style.FunSpec

class PactlTest : FunSpec({

    test("Get devices") {
        val platform = PulseAudioPlatform()
        platform.getAudioSources().forEach {
            print(it)
        }
    }
})
