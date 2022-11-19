package de.matthiasfisch.audiodragon.util

import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class ApiConfig(
    val apiBaseUrlOverride: String?,
    val userAgent: String?,
    val proxy: Proxy?,
    val callTimeoutMillis: Long?,
    val connectTimeoutMillis: Long?,
    val readTimeoutMillis: Long?,
    val writeTimeoutMillis: Long?
) {
    companion object {
        fun unconfigured() = ApiConfig(
            apiBaseUrlOverride = null,
            userAgent = null,
            proxy = null,
            callTimeoutMillis = null,
            connectTimeoutMillis = null,
            readTimeoutMillis = null,
            writeTimeoutMillis = null
        )
    }

    fun configure(clientBuilder: OkHttpClient.Builder): OkHttpClient.Builder {
        var applied = clientBuilder
        proxy?.let { applied = applied.proxy(proxy) }
        callTimeoutMillis?.let { applied = applied.callTimeout(it, TimeUnit.MILLISECONDS) }
        connectTimeoutMillis?.let { applied = applied.connectTimeout(it, TimeUnit.MILLISECONDS) }
        readTimeoutMillis?.let { applied = applied.readTimeout(it, TimeUnit.MILLISECONDS) }
        writeTimeoutMillis?.let { applied = applied.writeTimeout(it, TimeUnit.MILLISECONDS) }
        return applied
    }
}