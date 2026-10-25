package com.momo.iptv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory

class MomoApp : Application(), ImageLoaderFactory {
    // Logos gardés en cache disque sans tenir compte des en-têtes du serveur : affichage instantané.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
}
