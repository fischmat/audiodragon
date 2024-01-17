package de.matthiasfisch.audiodragon.types

data class AppInfoDTO(
    val maxMemory: Long,
    val jreInfo: JREInfo
)

data class JREInfo(
    val isSupported: Boolean,
    val name: String,
    val vendor: String
)