package com.momo.iptv.data

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File

private val json = Json { ignoreUnknownKeys = true }

/** Comptes IPTV, dans le stockage privé de l'appli. */
class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("accounts", Context.MODE_PRIVATE)

    fun load(): List<Account> =
        prefs.getString("list", null)
            ?.let { runCatching { json.decodeFromString<List<Account>>(it) }.getOrNull() }
            ?: emptyList()

    fun save(accounts: List<Account>) {
        prefs.edit().putString("list", json.encodeToString(accounts)).apply()
    }

    var lastAccountId: String?
        get() = prefs.getString("last", null)
        set(value) = prefs.edit().putString("last", value).apply()
}

/** Favoris par compte, dans l'ordre d'ajout. */
class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun load(accountId: String): List<String> =
        prefs.getString(accountId, null)
            ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
            ?: emptyList()

    fun save(accountId: String, ids: List<String>) {
        prefs.edit().putString(accountId, json.encodeToString(ids)).apply()
    }

    fun remove(accountId: String) {
        prefs.edit().remove(accountId).apply()
    }
}

/** Position de reprise des films et épisodes, par compte. */
class ResumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("resume", Context.MODE_PRIVATE)

    data class Position(val positionMs: Long, val durationMs: Long) {
        val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val finished: Boolean get() = durationMs > 0 && positionMs >= durationMs * 95 / 100
    }

    fun get(accountId: String, key: String): Position? {
        val raw = prefs.getString("$accountId|$key", null) ?: return null
        val parts = raw.split(',')
        val pos = parts.getOrNull(0)?.toLongOrNull() ?: return null
        return Position(pos, parts.getOrNull(1)?.toLongOrNull() ?: 0L)
    }

    fun set(accountId: String, key: String, positionMs: Long, durationMs: Long) {
        prefs.edit().putString("$accountId|$key", "$positionMs,$durationMs").apply()
    }
}

/** Copie locale des catalogues films/séries, par catégorie. */
class VodCache(context: Context) {
    private val dir = File(context.filesDir, "vod").apply { mkdirs() }

    private fun file(name: String) = File(dir, name.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json")

    fun readItems(name: String): CachedList<VodItem>? = runCatching {
        json.decodeFromString(CachedList.serializer(VodItem.serializer()), file(name).readText())
    }.getOrNull()

    fun writeItems(name: String, items: List<VodItem>) = runCatching {
        file(name).writeText(json.encodeToString(CachedList.serializer(VodItem.serializer()), CachedList(System.currentTimeMillis(), items)))
    }

    fun readCategories(name: String): CachedList<Category>? = runCatching {
        json.decodeFromString(CachedList.serializer(Category.serializer()), file(name).readText())
    }.getOrNull()

    fun writeCategories(name: String, items: List<Category>) = runCatching {
        file(name).writeText(json.encodeToString(CachedList.serializer(Category.serializer()), CachedList(System.currentTimeMillis(), items)))
    }

    fun deleteAccount(accountId: String) {
        dir.listFiles()?.filter { it.name.startsWith(accountId) }?.forEach { it.delete() }
    }
}

/** Copie locale des listes de chaînes : affichage instantané au démarrage. */
@OptIn(ExperimentalSerializationApi::class)
class PlaylistCache(context: Context) {
    private val dir = File(context.filesDir, "playlists").apply { mkdirs() }

    private fun file(accountId: String) = File(dir, "$accountId.json")

    fun read(accountId: String): Playlist? = runCatching {
        val f = file(accountId)
        if (!f.exists()) null else f.inputStream().buffered().use { json.decodeFromStream<Playlist>(it) }
    }.getOrNull()

    fun write(accountId: String, playlist: Playlist) {
        val tmp = File(dir, "$accountId.tmp")
        tmp.outputStream().buffered().use { json.encodeToStream(playlist, it) }
        tmp.renameTo(file(accountId))
    }

    fun delete(accountId: String) {
        file(accountId).delete()
    }
}
