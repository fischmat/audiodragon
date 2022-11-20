package de.matthiasfisch.audiodragon.types.settings

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.nio.file.Path
import java.nio.file.Paths

@JsonIgnoreProperties(ignoreUnknown = true)
class OutputSettings(
    val location: String,
    val encodingChunkLengthMs: Int,
    val coverartMaxDimension: Int
) {
    @JsonIgnore
    val path: Path = Paths.get(location.replace("~", System.getProperty("user.home")))
}