package de.matthiasfisch.audiodragon.controller

import de.matthiasfisch.audiodragon.service.SettingsService
import de.matthiasfisch.audiodragon.types.Settings
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/settings")
class SettingsController(val settingsService: SettingsService) {

    @GetMapping
    fun getSettings(): Settings = settingsService.settings

    @PatchMapping
    fun updateSettings(@RequestBody settings: Settings): Settings {
        settingsService.settings = settings
        return settings
    }
}