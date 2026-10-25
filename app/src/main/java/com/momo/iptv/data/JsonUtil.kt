package com.momo.iptv.data

import android.util.JsonReader
import android.util.JsonToken

/**
 * Lit un objet JSON en ne gardant que ses valeurs simples (texte, nombre, booléen),
 * toutes converties en texte : les serveurs IPTV mélangent souvent les deux.
 */
internal fun readFlatObject(reader: JsonReader): Map<String, String?> {
    if (reader.peek() != JsonToken.BEGIN_OBJECT) {
        reader.skipValue()
        return emptyMap()
    }
    val map = HashMap<String, String?>()
    reader.beginObject()
    while (reader.hasNext()) {
        val key = reader.nextName()
        when (reader.peek()) {
            JsonToken.STRING, JsonToken.NUMBER -> map[key] = reader.nextString()
            JsonToken.BOOLEAN -> map[key] = reader.nextBoolean().toString()
            JsonToken.NULL -> {
                reader.nextNull()
                map[key] = null
            }
            else -> reader.skipValue()
        }
    }
    reader.endObject()
    return map
}

/** Avance dans l'objet courant jusqu'à la clé [name]. Renvoie false si elle n'existe pas. */
internal fun JsonReader.seekKey(name: String): Boolean {
    while (hasNext()) {
        if (nextName() == name) return true
        skipValue()
    }
    return false
}
