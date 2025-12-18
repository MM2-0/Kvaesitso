package de.mm20.launcher2.preferences

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal class LauncherSettingsDataSerializer(private val context: Context) : Serializer<LauncherSettingsData> {

    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    override val defaultValue: LauncherSettingsData
        get() = LauncherSettingsData(context)

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun readFrom(input: InputStream): LauncherSettingsData {
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

    override suspend fun writeTo(t: LauncherSettingsData, output: OutputStream) {
        json.encodeToStream(t, output)
    }
}
