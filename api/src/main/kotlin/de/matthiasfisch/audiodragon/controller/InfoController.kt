package de.matthiasfisch.audiodragon.controller

import de.matthiasfisch.audiodragon.types.AppInfoDTO
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/info")
class InfoController {
    @GetMapping fun getAppInfo() = AppInfoDTO(Runtime.getRuntime().maxMemory())
}