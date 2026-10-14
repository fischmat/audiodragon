package de.matthiasfisch.audiodragon.util

import de.matthiasfisch.audiodragon.core.model.AudioSource
import java.nio.charset.StandardCharsets
import java.util.*

typealias AudioSourceId = String

fun AudioSource.getId(): AudioSourceId = Base64.getEncoder().encodeToString(identity.toByteArray(StandardCharsets.UTF_8))
