package de.matthiasfisch.audiodragon.controller

import de.matthiasfisch.audiodragon.types.AppInfoDTO
import de.matthiasfisch.audiodragon.types.JREInfo
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/info")
class InfoController {
    @GetMapping fun getAppInfo() = AppInfoDTO(
        maxMemory = Runtime.getRuntime().maxMemory(),
        jreInfo = getJREInfo()
    )

    private fun getJREInfo(): JREInfo {
        val vendor = System.getProperty("java.vm.vendor")?: "Unknown"
        val name = System.getProperty("java.vm.name")?: "Unknown"
        val isSupported =
            vendor.equals("Oracle Corporation", ignoreCase = true) && name.contains("Java HotSpot", ignoreCase = true) ||
                    vendor.equals("Oracle Corporation", ignoreCase = true) && name.contains("OpenJDK", ignoreCase = true)
        return JREInfo(isSupported, name, vendor)
    }
}