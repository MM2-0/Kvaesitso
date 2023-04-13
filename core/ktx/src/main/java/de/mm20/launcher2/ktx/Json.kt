package de.mm20.launcher2.ktx

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

inline fun <reified T>Json.decodeFromStringOrNull(json: String?): T? {
    if (json == null) return null
    return try {
        decodeFromString(json)
    } catch (e: SerializationException) {
        null
    }
}