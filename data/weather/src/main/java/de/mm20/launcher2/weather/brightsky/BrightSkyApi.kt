package de.mm20.launcher2.weather.brightsky

import de.mm20.launcher2.serialization.Json
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BrightSkyResult(
    val weather: Array<BrightSkyResultWeather>
)

@Serializable
data class BrightSkyResultWeather(
    val timestamp: String?,
    @SerialName("source_id") val sourceId: Int?,
    @SerialName("cloud_cover") val cloudCover: Double?,
    val condition: String?,
    @SerialName("dew_point") val dewPoint: Double?,
    val icon: String?,
    val precipitation: Double?,
    @SerialName("pressure_msl") val pressureMsl: Double?,
    @SerialName("relative_humidity") val relativeHumidity: Double?,
    val sunshine: Double?,
    val temperature: Double?,
    val visibility: Double?,
    @SerialName("wind_direction") val windDirection: Double?,
    @SerialName("wind_speed") val windSpeed: Double?,
    @SerialName("wind_gust_direction") val windGustDirection: Double?,
    @SerialName("wind_gust_speed") val windGustSpeed: Double?,
)

internal class BrightSkyApi {

    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json.Lenient)
            }
            defaultRequest {
                url("https://api.brightsky.dev/")
            }
        }
    }

    suspend fun weather(
        date: String, lastDate: String, lat: Double, lon: Double,
    ): BrightSkyResult {
        return httpClient.get {
            url {
                path("weather")
                parameter("units", "si")
                parameter("date", date)
                parameter("last_date", lastDate)
                parameter("lat", lat)
                parameter("lon", lon)
            }
        }.body()
    }
}