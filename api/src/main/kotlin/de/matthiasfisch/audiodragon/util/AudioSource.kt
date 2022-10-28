package de.matthiasfisch.audiodragon.util

import de.matthiasfisch.audiodragon.model.AudioSource
import de.matthiasfisch.audiodragon.model.getAudioPlatform
import java.nio.charset.StandardCharsets
import java.util.*

typealias AudioSourceId = String

fun AudioSource.getId(): AudioSourceId = Base64.getEncoder().encodeToString(name.toByteArray(StandardCharsets.UTF_8))

fun AudioSourceId.getAudioSource() = getAudioPlatform().getRecordableAudioSources().singleOrNull { it.getId() == this } ?: throw IllegalArgumentException("No audio source with id $this exists.")