package de.matthiasfisch.audiodragon.types

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.nio.file.Paths

@JsonIgnoreProperties(ignoreUnknown = true)
data class Settings(
    val recognition: RecognitionSettings,
    val splitting: SplittingSettings,
    val output: OutputSettings
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SplittingSettings(
    val splitAfterSilenceMillis: Long,
    val silenceRmsTolerance: Float
) {
    init {
        require(splitAfterSilenceMillis >= 0) { "Silence threshold must be non-negative." }
        require(silenceRmsTolerance >= 0) { "Silence RMS tolerance must be non-negative." }
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "recognizer")
@JsonSubTypes(JsonSubTypes.Type(value = ShazamRecognitionSettings::class, name = "shazam"))
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class RecognitionSettings(
    val secondsUntilRecognition: Int,
    val sampleSeconds: Int,
    val maxRetries: Int
) {
    init {
        require(secondsUntilRecognition >= 0) { "Seconds until recognition must be positive." }
        require(sampleSeconds > 0) { "Time of recognition samples must be positive." }
        require(maxRetries > 0) { "Maximum number of retries must be positive." }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class ShazamRecognitionSettings(
    val rapidApiToken: String,
    secondsUntilRecognition: Int,
    sampleSeconds: Int,
    maxRetries: Int
): RecognitionSettings(secondsUntilRecognition, sampleSeconds, maxRetries)

@JsonIgnoreProperties(ignoreUnknown = true)
class OutputSettings(
    @JsonProperty("location") location: String
) {
    @JsonIgnore val path = Paths.get(location.replace("~", System.getProperty("user.home")))
}