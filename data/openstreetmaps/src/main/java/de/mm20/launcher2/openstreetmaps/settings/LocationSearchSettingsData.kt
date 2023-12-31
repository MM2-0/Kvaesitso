package de.mm20.launcher2.openstreetmaps.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import de.mm20.launcher2.openstreetmaps.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class LocationSearchSettingsData(
    val enabled: Boolean = false,
    val searchRadius: Int = 1500,
    val hideUncategorized: Boolean = true,
    val overpassUrl: String = LocationSearchSettings.DefaultOverpassUrl,
    val tileServer: String = LocationSearchSettings.DefaultTileServerUrl,
    val imperialUnits: Boolean = false,
    val showMap: Boolean = false,
    val showPositionOnMap: Boolean = false,
    val themeMap: Boolean = true,
    val schemaVersion: Int = 1,
) {
    constructor(
        context: Context,
        enabled: Boolean = false,
        searchRadius: Int = 1500,
        hideUncategorized: Boolean = true,
        overpassUrl: String = LocationSearchSettings.DefaultOverpassUrl,
        tileServer: String = LocationSearchSettings.DefaultTileServerUrl,
        showMap: Boolean = false,
        showPositionOnMap: Boolean = false,
        themeMap: Boolean = true,
    ): this(
        enabled = enabled,
        searchRadius = searchRadius,
        hideUncategorized = hideUncategorized,
        overpassUrl = overpassUrl,
        tileServer = tileServer,
        imperialUnits = context.resources.getBoolean(R.bool.default_imperialUnits),
        showMap = showMap,
        showPositionOnMap = showPositionOnMap,
        themeMap = themeMap,
        schemaVersion = 1,
    )
}

internal class LocationSearchSettingsDataSerializer(
    private val context: Context,
) : Serializer<LocationSearchSettingsData> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: LocationSearchSettingsData
        get() = LocationSearchSettingsData(context = context)

    override suspend fun readFrom(input: InputStream): LocationSearchSettingsData {
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

    override suspend fun writeTo(t: LocationSearchSettingsData, output: OutputStream) {
        json.encodeToStream(t, output)
    }
}