package com.momo.iptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class PlaylistRepository(context: Context) {
    val cache = PlaylistCache(context)
    val vodCache = VodCache(context)
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

    suspend fun shortEpg(account: Account, channel: Channel): List<Programme> = withContext(Dispatchers.IO) {
        when (account.type) {
            SourceType.XTREAM -> XtreamSource.shortEpg(account, channel)
            SourceType.STALKER -> stalker(account).shortEpg(channel)
            SourceType.M3U -> emptyList()
        }
    }

    // ---- Films et séries ----

    fun cachedVodCategories(account: Account, kind: VodKind) = vodCache.readCategories("${account.id}_${kind}_categories")

    suspend fun vodCategories(account: Account, kind: VodKind): List<Category> = withContext(Dispatchers.IO) {
        val list = when (account.type) {
            SourceType.XTREAM -> XtreamSource.vodCategories(account, kind)
            SourceType.STALKER -> stalker(account).vodCategories(kind)
            SourceType.M3U -> emptyList()
        }
        vodCache.writeCategories("${account.id}_${kind}_categories", list)
        list
    }

    fun cachedVodItems(account: Account, kind: VodKind, categoryId: String) =
        vodCache.readItems("${account.id}_${kind}_$categoryId")

    suspend fun vodItems(account: Account, kind: VodKind, categoryId: String): List<VodItem> = withContext(Dispatchers.IO) {
        val list = when (account.type) {
            SourceType.XTREAM -> XtreamSource.vodItems(account, kind, categoryId)
            SourceType.STALKER -> stalker(account).vodItems(kind, categoryId)
            SourceType.M3U -> emptyList()
        }
        vodCache.writeItems("${account.id}_${kind}_$categoryId", list)
        list
    }

    /** Recherche de films ou séries. Xtream : catalogue complet gardé 12 h, filtré sur l'appareil. */
    suspend fun searchVod(account: Account, kind: VodKind, query: String): List<VodItem> = withContext(Dispatchers.IO) {
        when (account.type) {
            SourceType.XTREAM -> {
                val name = "${account.id}_${kind}_all"
                val cached = vodCache.readItems(name)?.takeIf { System.currentTimeMillis() - it.updatedAt < 12 * 3600_000L }?.items
                val all = cached ?: XtreamSource.vodItems(account, kind, null).also { vodCache.writeItems(name, it) }
                val q = normalize(query)
                all.filter { normalize(it.name).contains(q) }.take(80)
            }
            SourceType.STALKER -> stalker(account).search(kind, query).take(80)
            SourceType.M3U -> emptyList()
        }
    }

    suspend fun movieDetails(account: Account, movie: VodItem): VodItem = withContext(Dispatchers.IO) {
        when (account.type) {
            SourceType.XTREAM -> XtreamSource.movieDetails(account, movie)
            else -> movie
        }
    }

    suspend fun seasons(account: Account, series: VodItem): List<Season> = withContext(Dispatchers.IO) {
        when (account.type) {
            SourceType.XTREAM -> XtreamSource.seasons(account, series)
            SourceType.STALKER -> stalker(account).seasons(series)
            SourceType.M3U -> emptyList()
        }
    }

    suspend fun resolveVod(account: Account, playable: Playable): StreamRequest = withContext(Dispatchers.IO) {
        when (account.type) {
            SourceType.STALKER -> stalker(account).resolveVod(playable.url, playable.stalkerSeries)
            else -> StreamRequest(playable.url)
        }
    }

    /** Une session par compte, gardée en mémoire tant que le compte ne change pas. */
    private fun stalker(account: Account): StalkerClient = synchronized(stalkerClients) {
        stalkerClients[account.id]?.takeIf { it.account == account }
            ?: StalkerClient(account).also { stalkerClients[account.id] = it }
    }
}
