package de.mm20.launcher2.weather.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


@Serializable
data class LatLon(
    val lat: Double,
    val lon: Double,
)

@Serializable
data class ProviderSettings(
    val locationId: String? = null,
    val locationName: String? = null,
)

@Serializable
data class WeatherSettingsData(
    val schemaVersion: Int = 1,
    val provider: String = "metno",
    val autoLocation: Boolean = true,
    val location: LatLon? = null,
    val locationName: String? = null,
    val lastLocation: LatLon? = null,
    val lastUpdate: Long = 0L,
    val providerSettings: Map<String, ProviderSettings> = emptyMap(),
)

internal object WeatherSettingsSerializer : Serializer<WeatherSettingsData>{
    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: WeatherSettingsData
        get() = WeatherSettingsData()

    override suspend fun readFrom(input: InputStream): WeatherSettingsData {
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

    override suspend fun writeTo(t: WeatherSettingsData, output: OutputStream) {
        json.encodeToStream(t, output)
    }
}