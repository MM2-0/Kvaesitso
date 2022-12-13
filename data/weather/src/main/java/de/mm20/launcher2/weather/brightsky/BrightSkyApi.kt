package de.mm20.launcher2.weather.brightsky

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

data class BrightSkyResult(
    val weather: Array<BrightSkyResultWeather>
)

data class BrightSkyResultWeather(
    val timestamp: String?,
    @SerializedName("source_id") val sourceId: Int?,
    @SerializedName("cloud_cover") val cloudCover: Double?,
    val condition: String?,
    @SerializedName("dew_point") val dewPoint: Double?,
    val icon: String?,
    val precipitation: Double?,
    @SerializedName("pressure_msl") val pressureMsl: Double?,
    @SerializedName("relative_humidity") val relativeHumidity: Double?,
    val sunshine: Double?,
    val temperature: Double?,
    val visibility: Double?,
    @SerializedName("wind_direction") val windDirection: Double?,
    @SerializedName("wind_speed") val windSpeed: Double?,
    @SerializedName("wind_gust_direction") val windGustDirection: Double?,
    @SerializedName("wind_gust_speed") val windGustSpeed: Double?,
)

interface BrightSkyApi {
    @GET("/weather?units=si")
    suspend fun weather(
        @Query("date") date: String,
        @Query("last_date") lastDate: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
    ): BrightSkyResult
}