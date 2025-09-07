package de.mm20.launcher2.weather.openweathermap

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
data class CurrentWeatherResult(
    val coord: WeatherResultCoords?,
    val weather: Array<WeatherResultWeather>?,
    val main: WeatherResultMain?,
    val wind: WeatherResultWind?,
    val clouds: WeatherResultClouds?,
    val rain: CurrentWeatherResultRain?,
    val snow: CurrentWeatherResultSnow?,
    val dt: Long?,
    val sys: WeatherResultSys?,
    val timezone: Long?,
    val id: Int?,
    val name: String?,
)

@Serializable
data class WeatherResultCoords(
    val lon: Double?,
    val lat: Double?,
)

@Serializable
data class WeatherResultWeather(
    val id: Int?,
    val main: String?,
    val description: String?,
    val icon: String?,
)

@Serializable
data class WeatherResultMain(
    val temp: Double?,
    @SerialName("feels_like") val feelsLike: Double?,
    val pressure: Double?,
    val humidity: Double?,
    @SerialName("temp_min") val tempMin: Double?,
    @SerialName("temp_max") val tempMax: Double?,
    @SerialName("sea_level") val seaLevel: Double?,
    @SerialName("grnd_level") val grndLevel: Double?,
)

@Serializable
data class WeatherResultWind(
    val speed: Double?,
    val deg: Double?,
    val gust: Double?
)

@Serializable
data class WeatherResultClouds(
    val all: Int?
)

@Serializable
data class CurrentWeatherResultRain(
    @SerialName("1h") val oneHour: Double,
    @SerialName("3h") val threeHours: Double,
)

@Serializable
data class CurrentWeatherResultSnow(
    @SerialName("1h") val oneHour: Double,
    @SerialName("3h") val threeHours: Double,
)

@Serializable
data class WeatherResultSys(
    val country: String?,
    val sunrise: Long?,
    val sunset: Long?,
)

@Serializable
data class ForecastResult(
    val cnt: Int?,
    val list: Array<ForecastResultList>?,
    val city: ForecastResultCity?,
)

@Serializable
data class ForecastResultList(
    val dt: Long?,
    val main: WeatherResultMain?,
    val weather: Array<WeatherResultWeather>?,
    val clouds: WeatherResultClouds?,
    val wind: WeatherResultWind?,
    val rain: ForecastResultRain?,
    val snow: ForecastResultSnow?,
    val sys: ForecastResultSys?,
    @SerialName("dt_txt") val dtTxt: String?,
)

@Serializable
data class ForecastResultRain(
    @SerialName("3h") val threeHours: Double?,
)

@Serializable
data class ForecastResultSnow(
    @SerialName("3h") val threeHours: Double?,
)

@Serializable
data class ForecastResultSys(
    val pod: String,
)

@Serializable
data class ForecastResultCity(
    val id: Int?,
    val name: String?,
    val coords: WeatherResultCoords?,
    val country: String?,
    val timezone: Long?,
)

@Serializable
data class GeocodeResult(
    val name: String?,
    val local_names: Map<String, String>?,
    val lat: Double?,
    val lon: Double?,
    val country: String?,
    val state: String?,
)

internal class OpenWeatherMapApi {

    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json.Lenient)
            }
            defaultRequest {
                url("https://api.openweathermap.org/")
            }
        }
    }

    suspend fun currentWeather(
        q: String? = null,
        id: Int? = null,
        lat: Double? = null,
        lon: Double? = null,
        appid: String,
        lang: String,
    ): CurrentWeatherResult {
        return httpClient.get {
            url {
                path("data", "2.5", "weather")
                parameter("q", q)
                parameter("id", id)
                parameter("lat", lat)
                parameter("lon", lon)
                parameter("appid", appid)
                parameter("lang", lang)
            }
        }.body()
    }

    suspend fun forecast5Day3Hour(
        q: String? = null,
        id: Int? = null,
        lat: Double? = null,
        lon: Double? = null,
        appid: String,
        lang: String,
    ): ForecastResult {
        return httpClient.get {
            url {
                path("data", "2.5", "forecast")
                parameter("q", q)
                parameter("id", id)
                parameter("lat", lat)
                parameter("lon", lon)
                parameter("appid", appid)
                parameter("lang", lang)
            }
        }.body()
    }

    suspend fun geocode(
        q: String,
        appid: String,
        limit: Int = 5,
    ): Array<GeocodeResult> {
        return httpClient.get {
            url {
                path("geo", "1.0", "direct")
                parameter("q", q)
                parameter("appid", appid)
                parameter("limit", limit)
            }
        }.body()
    }
}