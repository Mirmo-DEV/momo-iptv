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
