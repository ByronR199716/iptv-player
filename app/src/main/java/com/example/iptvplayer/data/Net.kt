package com.example.iptvplayer.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Net {
    // Muchos paneles IPTV rechazan User-Agents desconocidos.
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) IPTVPlayer/1.0"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                )
            }
            .build()
    }
}
