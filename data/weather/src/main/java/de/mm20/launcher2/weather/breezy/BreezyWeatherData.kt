package de.mm20.launcher2.weather.breezy

import kotlinx.serialization.Serializable

@Serializable
internal data class BreezyWeatherData(
    val timestamp: Long? = null,
    val location: String? = null,
    val currentTemp: Double? = null,
    /**
     * According to the spec this can be any OWM weather code (see https://openweathermap.org/weather-conditions),
     * but in reality, only the following codes are ever used:
     * 800, 801, 803, 500, 600, 771, 741, 751, 611, 511, 210, 211, 3200
     * (see https://github.com/breezy-weather/breezy-weather/blob/main/app/src/main/java/org/breezyweather/sources/gadgetbridge/GadgetbridgeService.kt#L37)
     */
    val currentConditionCode: Int? = null,
    val currentCondition: String? = null,
    val currentHumidity: Int? = null,
    val todayMaxTemp: Int? = null,
    val todayMinTemp: Int? = null,
    val windSpeed: Float? = null,
    val windDirection: Int? = null,
    val uvIndex: Float? = null,
    val precipProbability: Int? = null,
    val dewPoint: Int? = null,
    val pressure: Float? = null,
    val cloudCover: Int? = null,
    val visibility: Float? = null,
    val sunRise: Int? = null,
    val sunSet: Int? = null,
    val moonRise: Int? = null,
    val moonSet: Int? = null,
    val moonPhase: Int? = null,
    val feelsLikeTemp: Int? = null,
    val forecasts: List<DailyForecast>? = null,
    val hourly: List<HourlyForecast>? = null,
    val airQuality: AirQuality? = null,
) {
    @Serializable
    data class AirQuality(
        val aqi: Int? = null,
        val co: Float? = null,
        val no2: Float? = null,
        val o3: Float? = null,
        val pm10: Float? = null,
        val pm25: Float? = null,
        val so2: Float? = null,
        val coAqi: Int? = null,
        val no2Aqi: Int? = null,
        val o3Aqi: Int? = null,
        val pm10Aqi: Int? = null,
        val pm25Aqi: Int? = null,
        val so2Aqi: Int? = null,
    )

    @Serializable
    data class DailyForecast(
        val minTemp: Int? = null,
        val maxTemp: Int? = null,
        val conditionCode: Int? = null,
        val humidity: Int? = null,
        val windSpeed: Float? = null,
        val windDirection: Int? = null,
        val uvIndex: Float? = null,
        val precipProbability: Int? = null,
        val sunRise: Int? = null,
        val sunSet: Int? = null,
        val moonRise: Int? = null,
        val moonSet: Int? = null,
        val moonPhase: Int? = null,
        val airQuality: AirQuality? = null,
    )

    @Serializable
    data class HourlyForecast(
        val timestamp: Int? = null,
        val temp: Int? = null,
        val conditionCode: Int? = null,
        val humidity: Int? = null,
        val windSpeed: Float? = null,
        val windDirection: Int? = null,
        val uvIndex: Float? = null,
        val precipProbability: Int? = null,
    )
}