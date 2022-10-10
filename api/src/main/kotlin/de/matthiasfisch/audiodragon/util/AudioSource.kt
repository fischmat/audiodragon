package de.matthiasfisch.audiodragon.util

import de.matthiasfisch.audiodragon.model.AudioSource
import de.matthiasfisch.audiodragon.model.getRecordableAudioSources
import java.nio.charset.StandardCharsets
import java.util.*

typealias AudioSourceId = String

fun AudioSource.getId(): AudioSourceId = Base64.getEncoder().encodeToString("$name|$vendor|$version".toByteArray(StandardCharsets.UTF_8))

fun AudioSourceId.getAudioSource() = getRecordableAudioSources().single { it.getId() == this }