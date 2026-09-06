package com.leapauto.app

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Process-local HTTP inspector. Release builds include Chucker for diagnostics,
 * but recording is disabled by default and is never persisted as an app setting.
 */
object NetworkDebugController {
    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var client: OkHttpClient? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        rebuildClient()
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        rebuildClient()
    }

    fun disableAndClear() {
        enabled = false
        rebuildClient()
    }

    fun httpClient(): OkHttpClient {
        return client ?: synchronized(this) {
            client ?: buildClient().also { client = it }
        }
    }

    private fun rebuildClient() {
        synchronized(this) { client = buildClient() }
    }

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        if (enabled) {
            appContext?.let { builder.addInterceptor(ChuckerInterceptor.Builder(it).build()) }
        }
        return builder.build()
    }
}
