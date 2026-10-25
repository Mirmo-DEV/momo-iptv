package com.momo.iptv.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Http {
    /** Beaucoup de fournisseurs bloquent les lecteurs inconnus : on se présente comme VLC. */
    const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()
    }
}
