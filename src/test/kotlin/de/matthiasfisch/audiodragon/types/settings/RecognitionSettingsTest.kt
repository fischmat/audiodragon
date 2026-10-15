package de.matthiasfisch.audiodragon.types.settings

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RecognitionSettingsTest : FunSpec({
    test("environment token overrides the stored RapidAPI token") {
        val settings = ShazamRecognitionSettings(
            rapidApiToken = "stored-token",
            secondsUntilRecognition = 0,
            sampleSeconds = 1,
            maxRetries = 1,
            postprocessors = emptyList(),
            apiBaseUrl = null,
            network = null
        )

        settings.resolveRapidApiToken(mapOf(DEFAULT_RAPID_API_TOKEN_ENVIRONMENT to "environment-token")) shouldBe "environment-token"
    }
})
