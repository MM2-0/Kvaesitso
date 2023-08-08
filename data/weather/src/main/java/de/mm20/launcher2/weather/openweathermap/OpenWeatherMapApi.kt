package de.mm20.launcher2.weather.openweathermap

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

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

data class WeatherResultCoords(
    val lon: Double?,
    val lat: Double?,
)

data class WeatherResultWeather(
    val id: Int?,
    val main: String?,
    val description: String?,
    val icon: String?,
)

data class WeatherResultMain(
    val temp: Double?,
    @SerializedName("feels_like") val feelsLike: Double?,
    val pressure: Double?,
    val humidity: Double?,
    @SerializedName("temp_min") val tempMin: Double?,
    @SerializedName("temp_max") val tempMax: Double?,
    @SerializedName("sea_level") val seaLevel: Double?,
    @SerializedName("grnd_level") val grndLevel: Double?,
)

data class WeatherResultWind(
    val speed: Double?,
    val deg: Double?,
    val gust: Double?
)

data class WeatherResultClouds(
    val all: Int?
)

data class CurrentWeatherResultRain(
    @SerializedName("1h") val oneHour: Double,
    @SerializedName("3h") val threeHours: Double,
)

data class CurrentWeatherResultSnow(
    @SerializedName("1h") val oneHour: Double,
    @SerializedName("3h") val threeHours: Double,
)

data class WeatherResultSys(
    val country: String?,
    val sunrise: Long?,
    val sunset: Long?,
)

data class ForecastResult(
    val cnt: Int?,
    val list: Array<ForecastResultList>?,
    val city: ForecastResultCity?,
)

data class ForecastResultList(
    val dt: Long?,
    val main: WeatherResultMain?,
    val weather: Array<WeatherResultWeather>?,
    val clouds: WeatherResultClouds?,
    val wind: WeatherResultWind?,
    val rain: ForecastResultRain?,
    val snow: ForecastResultSnow?,
    val sys: ForecastResultSys?,
    @SerializedName("dt_txt") val dtTxt: String?,
)

data class ForecastResultRain(
    @SerializedName("3h") val threeHours: Double?,
)

data class ForecastResultSnow(
    @SerializedName("3h") val threeHours: Double?,
)

data class ForecastResultSys(
    val pod: String,
)

data class ForecastResultCity(
    val id: Int?,
    val name: String?,
    val coords: WeatherResultCoords?,
    val country: String?,
    val timezone: Long?,
)

data class GeocodeResult(
    val name: String?,
    val local_names: Map<String, String>?,
    val lat: Double?,
    val lon: Double?,
    val country: String?,
    val state: String?,
)
interface OpenWeatherMapApi {

    @GET("data/2.5/weather")
    suspend fun currentWeather(
        @Query("q") q: String? = null,
        @Query("id") id: Int? = null,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null,
        @Query("appid") appid: String,
        @Query("lang") lang: String,
    ): CurrentWeatherResult

    @GET("data/2.5/forecast")
    suspend fun forecast5Day3Hour(
        @Query("q") q: String? = null,
        @Query("id") id: Int? = null,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null,
        @Query("appid") appid: String,
        @Query("lang") lang: String,
    ): ForecastResult

    @GET("geo/1.0/direct")
    suspend fun geocode(
        @Query("q") q: String,
        @Query("appid") appid: String,
        @Query("limit") limit: Int = 5,
    ): Array<GeocodeResult>
}