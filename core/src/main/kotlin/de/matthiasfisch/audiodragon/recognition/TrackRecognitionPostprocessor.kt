package de.matthiasfisch.audiodragon.recognition

import de.matthiasfisch.audiodragon.model.TrackData

interface TrackRecognitionPostprocessor {
    fun augment(track: TrackData): TrackData
}