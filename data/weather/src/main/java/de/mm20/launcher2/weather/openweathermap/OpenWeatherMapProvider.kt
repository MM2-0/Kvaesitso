package de.mm20.launcher2.weather.openweathermap

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.getDouble
import de.mm20.launcher2.ktx.putDouble
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.weather.R
import de.mm20.launcher2.weather.WeatherLocation
import de.mm20.launcher2.weather.WeatherProvider
import de.mm20.launcher2.weather.WeatherUpdateResult
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale


class OpenWeatherMapProvider(override val context: Context) :
    WeatherProvider<OpenWeatherMapLocation>() {

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val openWeatherMapService by lazy {
        retrofit.create(OpenWeatherMapApi::class.java)
    }

    override fun isUpdateRequired(): Boolean {
        return getLastUpdate() + (1000 * 60 * 60) <= System.currentTimeMillis()
    }

    override suspend fun lookupLocation(query: String): List<OpenWeatherMapLocation> {

        val response = try {
            openWeatherMapService.geocode(
                appid = getApiKey() ?: return emptyList(),
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
            OpenWeatherMapLatLonLocation(
                name = "$name, ${it.country}",
                lat = it.lat ?: return@mapNotNull null,
                lon = it.lon ?: return@mapNotNull null,
            )
        }
    }

    override suspend fun loadWeatherData(location: OpenWeatherMapLocation): WeatherUpdateResult<OpenWeatherMapLocation>? {
        return fetchWeatherData(location = location)
    }

    override suspend fun loadWeatherData(
        lat: Double,
        lon: Double
    ): WeatherUpdateResult<OpenWeatherMapLocation>? {
        return fetchWeatherData(lat = lat, lon = lon)
    }

    private suspend fun fetchWeatherData(
        lat: Double? = null,
        lon: Double? = null,
        location: OpenWeatherMapLocation? = null
    ): WeatherUpdateResult<OpenWeatherMapLocation>? {
        val lang = getLanguageCode()

        val currentWeather = try {
            when {
                location is OpenWeatherMapLatLonLocation -> openWeatherMapService.currentWeather(
                    appid = getApiKey() ?: return null,
                    lat = location.lat,
                    lon = location.lon,
                    lang = lang,
                )
                location is OpenWeatherMapLegacyLocation -> openWeatherMapService.currentWeather(
                    appid = getApiKey() ?: return null,
                    id = location.id,
                    lang = lang,
                )
                lat != null && lon != null -> openWeatherMapService.currentWeather(
                    appid = getApiKey() ?: return null,
                    lat = lat,
                    lon = lon,
                    lang = lang,
                )
                else -> {
                    Log.w("MM20", "OpenWeatherMapProvider returned no data because no location was provided")
                    return null
                }
            }
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
        val loc = location?.name ?: "$city, $country"

        val forecasts = try {
            openWeatherMapService.forecast5Day3Hour(
                lat = coords.lat,
                lon = coords.lon,
                appid = getApiKey() ?: return null,
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
        return WeatherUpdateResult(
            forecasts = forecastList,
            location = OpenWeatherMapLatLonLocation(
                name = loc,
                lat = coords.lat,
                lon = coords.lon,
            )
        )
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

    private fun getApiKey(): String? {
        val resId = getApiKeyResId()
        if (resId != 0) return context.getString(resId)
        return null
    }

    override fun isAvailable(): Boolean {
        return getApiKeyResId() != 0
    }

    override val name: String
        get() = context.getString(R.string.provider_openweathermap)

    private fun getApiKeyResId(): Int {
        return context.resources.getIdentifier("openweathermap_key", "string", context.packageName)
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

    override val preferences: SharedPreferences
        get() = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun setLocation(location: WeatherLocation?) {
        location as OpenWeatherMapLocation?
        preferences.edit {
            if (location == null) {
                remove(CITY_ID)
                remove(LAT)
                remove(LON)
                remove(LOCATION)
            } else {
                if (location is OpenWeatherMapLatLonLocation) {
                    putDouble(LAT, location.lat)
                    putDouble(LON, location.lon)
                    putString(LOCATION, location.name)
                } else if (location is OpenWeatherMapLegacyLocation) {
                    putInt(CITY_ID, location.id)
                    putString(LOCATION, location.name)
                }
            }
        }
    }

    override fun getLocation(): OpenWeatherMapLocation? {
        val lat = preferences.getDouble(LAT, Double.NaN).takeIf { !it.isNaN() }
        val lon = preferences.getDouble(LON, Double.NaN).takeIf { !it.isNaN() }
        val name = preferences.getString(LOCATION, null) ?: return null
        if (lat != null && lon != null) {
            return OpenWeatherMapLatLonLocation(
                name = name,
                lat = lat,
                lon = lon,
            )
        }
        val id = preferences.getInt(CITY_ID, -1).takeIf { it != -1 }
        if (id != null) {
            return OpenWeatherMapLegacyLocation(
                name = name,
                id = id,
            )
        }
        return null
    }

    override fun getLastLocation(): OpenWeatherMapLocation? {
        val lat = preferences.getDouble(LAST_LAT, Double.NaN).takeIf { !it.isNaN() }
        val lon = preferences.getDouble(LAST_LON, Double.NaN).takeIf { !it.isNaN() }
        val name = preferences.getString(LAST_LOCATION, null) ?: return null
        if (lat != null && lon != null) {
            return OpenWeatherMapLatLonLocation(
                name = name,
                lat = lat,
                lon = lon,
            )
        }
        val id = preferences.getInt(LAST_CITY_ID, -1).takeIf { it != -1 }
        if (id != null) {
            return OpenWeatherMapLegacyLocation(
                name = name,
                id = id,
            )
        }
        return null
    }

    override fun saveLastLocation(location: OpenWeatherMapLocation) {
        preferences.edit {
            if (location is OpenWeatherMapLatLonLocation) {
                putDouble(LAST_LAT, location.lat)
                putDouble(LAST_LON, location.lon)
                remove(LAST_CITY_ID)
                putString(LAST_LOCATION, location.name)
            } else if (location is OpenWeatherMapLegacyLocation) {
                putInt(LAST_CITY_ID, location.id)
                remove(LAST_LAT)
                remove(LAST_LON)
                putString(LAST_LOCATION, location.name)
            }
        }
    }

    companion object {
        private const val PREFERENCES = "openweathermap"

        @Deprecated("Use LAT and LON instead")
        private const val CITY_ID = "city_id"

        @Deprecated("Use LAST_LAT and LAST_LON instead")
        private const val LAST_CITY_ID = "last_city_id"
        private const val LAST_UPDATE = "last_update"
        private const val LOCATION = "location"
        private const val LAT = "lat"
        private const val LON = "lon"
        private const val LAST_LOCATION = "last_location"
        private const val LAST_LAT = "last_lat"
        private const val LAST_LON = "last_lon"
    }
}

sealed interface OpenWeatherMapLocation : WeatherLocation

data class OpenWeatherMapLatLonLocation(
    override val name: String,
    val lat: Double,
    val lon: Double,
) : OpenWeatherMapLocation

data class OpenWeatherMapLegacyLocation(
    override val name: String,
    val id: Int,
) : OpenWeatherMapLocation