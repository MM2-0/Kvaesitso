package de.mm20.launcher2.plugin.contracts

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

    object ForecastColumns {
        const val Timestamp = "timestamp"
        const val CreatedAt = "created_at"
        const val Temperature = "temperature"
        const val TemperatureMin = "temperature_min"
        const val TemperatureMax = "temperature_max"
        const val Pressure = "pressure"
        const val Humidity = "humidity"
        const val WindSpeed = "wind_speed"
        const val WindDirection = "wind_direction"
        const val Precipitation = "precipitation"
        const val RainProbability = "rain_probability"
        const val Clouds = "clouds"
        const val Location = "location"
        const val Provider = "provider"
        const val ProviderUrl = "provider_url"
        const val Night = "night"
        const val Icon = "icon"
        const val Condition = "condition"
    }

    object LocationParams {
        const val Query = "query"
        const val Language = "lang"
    }

    object LocationColumns {
        const val Id = "id"
        const val Lat = "lat"
        const val Lon = "lon"
        const val Name = "name"
    }
}