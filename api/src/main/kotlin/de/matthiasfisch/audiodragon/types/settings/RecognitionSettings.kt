package de.matthiasfisch.audiodragon.types.settings

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "recognizer")
@JsonSubTypes(JsonSubTypes.Type(value = ShazamRecognitionSettings::class, name = "shazam"))
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class RecognitionSettings(
    val secondsUntilRecognition: Int,
    val sampleSeconds: Int,
    val maxRetries: Int,
    val postprocessors: List<RecognitionPostprocessorConfig>
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
    maxRetries: Int,
    postprocessors: List<RecognitionPostprocessorConfig>
): RecognitionSettings(secondsUntilRecognition, sampleSeconds, maxRetries, postprocessors)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(JsonSubTypes.Type(value = MusicBrainzPostprocessorConfig::class, name = "musicbrainz"))
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class RecognitionPostprocessorConfig(
    val preferInput: Boolean
)

@JsonIgnoreProperties(ignoreUnknown = true)
class MusicBrainzPostprocessorConfig(
    val minScore: Int,
    val userAgent: String,
    preferInput: Boolean
): RecognitionPostprocessorConfig(preferInput)