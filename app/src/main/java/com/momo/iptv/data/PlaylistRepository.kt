package com.momo.iptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class PlaylistRepository(context: Context) {
    val cache = PlaylistCache(context)

    suspend fun cached(account: Account): Playlist? = withContext(Dispatchers.IO) { cache.read(account.id) }

    suspend fun refresh(account: Account): Playlist = withContext(Dispatchers.IO) {
        val playlist = when (account.type) {
            SourceType.XTREAM -> XtreamSource.fetch(account)
            SourceType.M3U -> M3uSource.fetch(account)
        }
        if (playlist.channels.isEmpty()) throw IOException("aucune chaîne reçue")
        cache.write(account.id, playlist)
        playlist
    }
}
