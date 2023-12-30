package de.mm20.launcher2.badges.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import de.mm20.launcher2.files.settings.FileSearchSettingsData
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class BadgeSettingsData(
    val notifications: Boolean = true,
    val suspendedApps: Boolean = true,
    val cloudFiles: Boolean = true,
    val shortcuts: Boolean = true,
    val plugins: Boolean = true,
    val schemaVersion: Int = 1,
)

internal object BadgeSettingsDataSerializer : Serializer<BadgeSettingsData> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: BadgeSettingsData
        get() = BadgeSettingsData(schemaVersion = 0)

    override suspend fun readFrom(input: InputStream): BadgeSettingsData {
        try {
            return json.decodeFromStream(input)
        } catch (e: IllegalArgumentException) {
            throw (CorruptionException("Cannot read json.", e))
        } catch (e: SerializationException) {
            throw (CorruptionException("Cannot read json.", e))
        } catch (e: IOException) {
            throw (CorruptionException("Cannot read json.", e))
        }
    }

    override suspend fun writeTo(t: BadgeSettingsData, output: OutputStream) {
        json.encodeToStream(t, output)
    }
}