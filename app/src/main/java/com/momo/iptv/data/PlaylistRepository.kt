package com.momo.iptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class PlaylistRepository(context: Context) {
    val cache = PlaylistCache(context)
    private val stalkerClients = HashMap<String, StalkerClient>()

    suspend fun cached(account: Account): Playlist? = withContext(Dispatchers.IO) { cache.read(account.id) }

    suspend fun refresh(account: Account): Playlist = withContext(Dispatchers.IO) {
        val playlist = when (account.type) {
            SourceType.XTREAM -> XtreamSource.fetch(account)
            SourceType.M3U -> M3uSource.fetch(account)
            SourceType.STALKER -> stalker(account).fetchPlaylist()
        }
        if (playlist.channels.isEmpty()) throw IOException("aucune chaîne reçue")
        cache.write(account.id, playlist)
        playlist
    }

    /** Lien de lecture d'une chaîne (Stalker : demandé au portail à chaque fois). */
    suspend fun resolve(account: Account, channel: Channel): StreamRequest = withContext(Dispatchers.IO) {
        when (account.type) {
            SourceType.STALKER -> stalker(account).resolve(channel.url)
            else -> StreamRequest(channel.url)
        }
    }

    /** Une session par compte, gardée en mémoire tant que le compte ne change pas. */
    private fun stalker(account: Account): StalkerClient = synchronized(stalkerClients) {
        stalkerClients[account.id]?.takeIf { it.account == account }
            ?: StalkerClient(account).also { stalkerClients[account.id] = it }
    }
}
