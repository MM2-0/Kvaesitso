package de.mm20.launcher2.plugin.contracts

import de.mm20.launcher2.weather.WeatherIcon

object WeatherPluginContract {
    object Paths {
        const val Forecasts = "forecasts"
        const val Locations = "locations"
    }

    object ForecastParams {
        const val Lat = "lat"
        const val Lon = "lon"
        const val Id = "id"
        const val LocationName = "location_name"
        const val Language = "lang"
    }

    object ForecastColumns : Columns() {
        val Timestamp = column<Long>("timestamp")
        val CreatedAt = column<Long>("created_at")
        val Temperature = column<Double>("temperature")
        val TemperatureMin = column<Double>("temperature_min")
        val TemperatureMax = column<Double>("temperature_max")
        val Pressure = column<Double>("pressure")
        val Humidity = column<Int>("humidity")
        val WindSpeed = column<Double>("wind_speed")
        val WindDirection = column<Double>("wind_direction")
        val Precipitation = column<Double>("precipitation")
        val RainProbability = column<Int>("rain_probability")
        val Clouds = column<Int>("clouds")
        val Location = column<String>("location")
        val Provider = column<String>("provider")
        val ProviderUrl = column<String>("provider_url")
        val Night = column<Boolean>("night")
        val Icon = column<WeatherIcon>("icon")
        val Condition = column<String>("condition")
    }

    object LocationParams {
        const val Query = "query"
        const val Language = "lang"
    }

    object LocationColumns : Columns() {
        val Id = column<String>("id")
        val Lat = column<Double>("lat")
        val Lon = column<Double>("lon")
        val Name = column<String>("name")
    }
}