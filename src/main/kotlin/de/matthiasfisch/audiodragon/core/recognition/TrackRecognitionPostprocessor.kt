package de.matthiasfisch.audiodragon.core.recognition

import de.matthiasfisch.audiodragon.core.model.TrackData

interface TrackRecognitionPostprocessor {
    fun augment(track: TrackData): TrackData
}