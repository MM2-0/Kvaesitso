package de.mm20.launcher2.weather.here

import retrofit2.http.GET
import retrofit2.http.Query

data class HereWeatherResult(
    val hourlyForecasts: HereWeatherResultForecasts?
)

data class HereWeatherResultForecasts(
    val forecastLocation: HereWeatherResultForecastsLocation?
)

data class HereWeatherResultForecastsLocation(
    val forecast: Array<HereWeatherResultForecastsLocationForecast>?,
    val country: String?,
    val state: String?,
    val city: String?,
    val latitude: Double?,
    val longitude: Double?,
)

data class HereWeatherResultForecastsLocationForecast(
    val daylight: String?,
    val description: String?,
    val skyInfo: String?,
    val skyDescription: String?,
    val temperature: String?,
    val temperatureDesc: String?,
    val comfort: String?,
    val humidity: String?,
    val dewPoint: String?,
    val precipitationProbability: String?,
    val precipitationDesc: String?,
    val rainFall: String?,
    val snowFall: String?,
    val airInfo: String?,
    val airDescription: String?,
    val windSpeed: String?,
    val windDirection: String?,
    val windDesc: String?,
    val windDescShort: String?,
    val visibility: String?,
    val icon: String?,
    val iconName: String?,
    val iconLink: String?,
    val dayOfWeek: String?,
    val weekday: String?,
    val utcTime: String?,
    val localTime: String?,
    val localTimeFormat: String?,
)

interface HereWeatherApi {
    @GET("report.json?product=forecast_hourly")
    suspend fun report(
        @Query("apiKey") apiKey: String,
        @Query("language") language: String,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): HereWeatherResult
}