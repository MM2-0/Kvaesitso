package de.mm20.launcher2.weather.openweathermap

import android.content.Context
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.weather.WeatherLocation
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.weather.R
import de.mm20.launcher2.weather.WeatherProvider
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale

internal class OpenWeatherMapProvider(
    private val context: Context,
): WeatherProvider {

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val openWeatherMapService by lazy {
        retrofit.create(OpenWeatherMapApi::class.java)
    }
    override suspend fun getWeatherData(location: WeatherLocation): List<Forecast>? {
        return when (location) {
            is WeatherLocation.LatLon -> getWeatherData(location.lat, location.lon, location.name)
            else -> {
                Log.e("OpenWeatherMapProvider", "Unsupported location type: $location")
                null
            }
        }
    }

    override suspend fun getWeatherData(lat: Double, lon: Double): List<Forecast>? {
        return getWeatherData(lat, lon, null)
    }

    private suspend fun getWeatherData(lat: Double, lon: Double, locationName: String?): List<Forecast>? {
        val lang = getLanguageCode()

        val currentWeather = try {
            openWeatherMapService.currentWeather(
                appid = getApiKey(context) ?: return null,
                lat = lat,
                lon = lon,
                lang = lang,
            )
        } catch (e: Exception) {
            CrashReporter.logException(e)
            return null
        }

        val forecastList = mutableListOf<Forecast>()

        val city = currentWeather.name
        val country = currentWeather.sys?.country ?: return null
        val cityId = currentWeather.id ?: return null
        val coords = currentWeather.coord ?: return null
        if (coords.lat == null || coords.lon == null) return null
        val loc = locationName ?: "$city, $country"

        val forecasts = try {
            openWeatherMapService.forecast5Day3Hour(
                lat = coords.lat,
                lon = coords.lon,
                appid = getApiKey(context) ?: return null,
                lang = lang
            )
        } catch (e: Exception) {
            CrashReporter.logException(e)
            return null
        }

        forecasts.list ?: return null

        forecastList.add(
            Forecast(
                timestamp = currentWeather.dt?.times(1000) ?: return null,
                condition = currentWeather.weather?.getOrNull(0)?.description ?: "Unknown",
                temperature = currentWeather.main?.temp ?: return null,
                minTemp = currentWeather.main.tempMin ?: -1.0,
                maxTemp = currentWeather.main.tempMax ?: -1.0,
                pressure = currentWeather.main.pressure ?: -1.0,
                humidity = currentWeather.main.humidity ?: -1.0,
                precipitation = (currentWeather.rain?.threeHours
                    ?: 0.0) + (currentWeather.snow?.threeHours ?: 0.0),
                icon = iconForId(currentWeather.weather?.getOrNull(0)?.id ?: 0),
                clouds = currentWeather.clouds?.all ?: 0,
                windSpeed = currentWeather.wind?.speed ?: 0.0,
                windDirection = currentWeather.wind?.deg ?: -1.0,
                night = run {
                    val sunrise = currentWeather.sys.sunrise ?: 0
                    val sunset = currentWeather.sys.sunset ?: 0
                    currentWeather.dt > sunset || currentWeather.dt < sunrise
                },
                location = loc,
                provider = context.getString(R.string.provider_openweathermap),
                providerUrl = "https://openweathermap.org/city/$cityId",
                updateTime = System.currentTimeMillis()
            )
        )

        forecastList.addAll(
            forecasts.list.map {
                Forecast(
                    timestamp = it.dt?.times(1000) ?: return null,
                    icon = iconForId(it.weather?.getOrNull(0)?.id ?: 0),
                    condition = it.weather?.getOrNull(0)?.description ?: "Unknown",
                    temperature = it.main?.temp ?: return null,
                    minTemp = it.main.tempMin ?: -1.0,
                    maxTemp = it.main.tempMax ?: -1.0,
                    pressure = it.main.pressure ?: -1.0,
                    humidity = it.main.humidity ?: -1.0,
                    precipitation = (it.rain?.threeHours ?: 0.0) + (currentWeather.snow?.threeHours
                        ?: 0.0),
                    clouds = it.clouds?.all ?: 0,
                    windSpeed = it.wind?.speed ?: 0.0,
                    windDirection = it.wind?.deg ?: -1.0,
                    night = it.sys?.pod == "n",
                    location = loc,
                    provider = context.getString(R.string.provider_openweathermap),
                    providerUrl = "https://openweathermap.org/city/$cityId",
                    updateTime = System.currentTimeMillis()
                )
            }
        )
        return forecastList
    }

    override suspend fun findLocation(query: String): List<WeatherLocation> {
        val response = try {
            openWeatherMapService.geocode(
                appid = getApiKey(context) ?: return emptyList(),
                q = query,
            )
        } catch (e: Exception) {
            CrashReporter.logException(e)
            return emptyList()
        }

        // Here, OWM uses the correct language codes, so we don't need to map anything
        val lang = Locale.getDefault().language

        return response.mapNotNull {
            val name = it.local_names?.get(lang) ?: it.name ?: return@mapNotNull null
            WeatherLocation.LatLon(
                name = "$name, ${it.country}",
                lat = it.lat ?: return@mapNotNull null,
                lon = it.lon ?: return@mapNotNull null,
            )
        }
    }

    private fun getLanguageCode(): String {
        val lang = Locale.getDefault().language
        // OWM incorrectly expects country codes instead of language codes for some languages
        // see https://openweathermap.org/current#multi
        when (lang) {
            "cs" -> return "cz"
            "al" -> return "sq"
            "kr" -> return "ko"
            "lv" -> return "la"
            else -> return lang
        }
    }



    private fun iconForId(id: Int): Int {
        return when (id) {
            200, 201, in 230..232 -> Forecast.THUNDERSTORM_WITH_RAIN
            202 -> Forecast.HEAVY_THUNDERSTORM_WITH_RAIN
            210, 211 -> Forecast.THUNDERSTORM
            212, 221 -> Forecast.HEAVY_THUNDERSTORM
            in 300..302, in 310..312 -> Forecast.DRIZZLE
            313, 314, 321, in 500..504, 511, in 520..522, 531 -> Forecast.SHOWERS
            in 600..602 -> Forecast.SNOW
            611, 612, 615, 616, in 620..622 -> Forecast.SLEET
            701, 711, 731, 741, 761, 762 -> Forecast.FOG
            721 -> Forecast.HAZE
            771, 781, in 900..902, in 958..962 -> Forecast.STORM
            800 -> Forecast.CLEAR
            801 -> Forecast.PARTLY_CLOUDY
            802 -> Forecast.MOSTLY_CLOUDY
            803 -> Forecast.BROKEN_CLOUDS
            804, 951 -> Forecast.CLOUDY
            903 -> Forecast.COLD
            904 -> Forecast.HOT
            905, in 952..957 -> Forecast.WIND
            906 -> Forecast.HAIL
            else -> Forecast.NONE
        }
    }

    companion object {
        fun isAvailable(context: Context): Boolean {
            return getApiKeyResId(context) != 0
        }

        private fun getApiKey(context: Context): String? {
            val resId = getApiKeyResId(context)
            if (resId != 0) return context.getString(resId)
            return null
        }

        private fun getApiKeyResId(context: Context): Int {
            return context.resources.getIdentifier("openweathermap_key", "string", context.packageName)
        }

        internal const val Id = "owm"
    }
}