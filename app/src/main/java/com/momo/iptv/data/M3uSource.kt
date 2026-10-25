package com.momo.iptv.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException

object M3uSource {

    private const val NO_GROUP = "Sans catégorie"
    private val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun fetch(account: Account): Playlist {
        val url = account.m3uUrl.trim().toHttpUrlOrNull() ?: throw IOException("URL M3U invalide")
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("le serveur a répondu HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("réponse vide")
            return parse(body.charStream().buffered())
        }
    }

    fun parse(reader: BufferedReader): Playlist {
        val builder = PlaylistBuilder()
        var name: String? = null
        var attrs: Map<String, String> = emptyMap()
        var extGroup: String? = null

        while (true) {
            val line = reader.readLine()?.removePrefix("﻿")?.trim() ?: break
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    attrs = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = extinfName(line)
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> extGroup = line.substring(8).trim()
                line.startsWith("#") -> Unit
                else -> {
                    // V1 : chaînes en direct uniquement (les listes Xtream « m3u_plus » contiennent aussi films et séries)
                    if ("/movie/" !in line && "/series/" !in line) {
                        val group = attrs["group-title"]?.trim()?.ifEmpty { null } ?: extGroup ?: NO_GROUP
                        val channelName = name?.ifEmpty { null } ?: attrs["tvg-name"]?.ifEmpty { null } ?: "Chaîne"
                        builder.category(group, group)
                        builder.channel(
                            baseId = "$group|$channelName",
                            name = channelName,
                            categoryId = group,
                            url = line,
                            logo = attrs["tvg-logo"],
                            epgId = attrs["tvg-id"],
                        )
                    }
                    name = null
                    attrs = emptyMap()
                    extGroup = null
                }
            }
        }
        return builder.build()
    }

    /** Le nom de la chaîne suit la première virgule qui n'est pas entre guillemets. */
    private fun extinfName(line: String): String {
        var inQuotes = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return line.substring(i + 1).trim()
            }
        }
        return ""
    }
}
