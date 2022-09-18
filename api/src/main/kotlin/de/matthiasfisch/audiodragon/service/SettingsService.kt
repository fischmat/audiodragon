package de.matthiasfisch.audiodragon.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.matthiasfisch.audiodragon.types.Settings
import org.springframework.stereotype.Service
import java.nio.file.Paths
import kotlin.io.path.exists

private const val SETTINGS_FILE_NAME = ".audiodragon.json"
private const val DEFAULT_SETTINGS_FILE = "/default-settings.json"
private val OBJECT_MAPPER = jacksonObjectMapper()

@Service
class SettingsService {
    var settings = loadSettings()
        set(value) {
            OBJECT_MAPPER.writeValue(settingsFileLocation().toFile(), value)
            field = value
        }

    private fun loadSettings() = settingsFileLocation().toFile().let { settingsFile ->
        if (settingsFile.exists()) OBJECT_MAPPER.readValue(settingsFile, Settings::class.java) else defaultSettings()
    }

    private fun settingsFileLocation() =
        Paths.get(System.getProperty("user.home")).resolve(SETTINGS_FILE_NAME)

    private fun defaultSettings() =
        javaClass.getResourceAsStream(DEFAULT_SETTINGS_FILE).use {
            OBJECT_MAPPER.readValue(it, Settings::class.java)
        }
}