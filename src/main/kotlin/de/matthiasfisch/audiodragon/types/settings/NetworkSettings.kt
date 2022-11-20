package de.matthiasfisch.audiodragon.types.settings

import com.fasterxml.jackson.annotation.JsonProperty
import de.matthiasfisch.audiodragon.core.util.ApiConfig
import java.net.InetSocketAddress
import java.net.Proxy

data class ProxyConfig(
    val type: ProxyType,
    val host: String,
    val port: Int
) {
    init {
        require(host.isNotBlank()) { "Proxy hostname must not be blank." }
        require(port in 1..65535) { "Proxy port must be in range [0, 65535]." }
    }

    fun proxy() = Proxy(type.javaType, InetSocketAddress(host, port))
}

enum class ProxyType(val javaType: Proxy.Type) {
    @JsonProperty("http") HTTP(Proxy.Type.HTTP),
    @JsonProperty("socks") SOCKS(Proxy.Type.SOCKS)
}

data class NetworkSettings(
    val proxy: ProxyConfig?,
    val callTimeoutMillis: Long?,
    val connectTimeoutMillis: Long?,
    val readTimeoutMillis: Long?,
    val writeTimeoutMillis: Long?,
    val userAgent: String?
)

interface APISettings {
    val apiBaseUrl: String?
    val network: NetworkSettings?

    fun apiConfig() = ApiConfig(
        apiBaseUrlOverride = apiBaseUrl,
        userAgent = network?.userAgent,
        proxy = network?.proxy?.proxy(),
        callTimeoutMillis = network?.callTimeoutMillis,
        connectTimeoutMillis = network?.connectTimeoutMillis,
        readTimeoutMillis = network?.readTimeoutMillis,
        writeTimeoutMillis = network?.writeTimeoutMillis
    )
}