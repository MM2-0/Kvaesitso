package de.mm20.launcher2.weather.openweathermap

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.edit
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.weather.R
import de.mm20.launcher2.weather.WeatherProvider
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.lang.Exception
import java.util.*


class OpenWeatherMapProvider(val context: Context) : WeatherProvider() {

    val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val openWeatherMapService by lazy {
        retrofit.create(OpenWeatherMapApi::class.java)
    }

    override fun resetLastUpdate() {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putLong(LAST_UPDATE, 0)
        }
    }

    override fun isUpdateRequired(): Boolean {
        return getLastUpdate() + (1000 * 60 * 60) <= System.currentTimeMillis()
    }

    override suspend fun lookupLocation(query: String): List<Pair<Any?, String>> {
        val lang = Locale.getDefault().language

        val response = try {
            openWeatherMapService.currentWeather(
                appid = getApiKey() ?: return emptyList(),
                q = query,
                lang = lang
            )
        } catch (e: Exception) {
            CrashReporter.logException(e)
            return emptyList()
        }

        val city = response.name ?: return emptyList()
        val country = response.sys?.country ?: ""
        val cityId = response.id ?: return emptyList()
        val loc = "$city, $country"
        return listOf(cityId.toString() to loc)
    }

    override fun setLocation(locationId: Any?, locationName: String) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putInt(CITY_ID, locationId as? Int ?: -1)
            putString(LAST_LOCATION, locationName)
        }
    }


    override var autoLocation: Boolean
        get() {
            return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(AUTO_LOCATION, true)
        }
        set(value) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit {
                    putBoolean(AUTO_LOCATION, value)
                }
        }

    override val supportsAutoLocation: Boolean = true
    override val supportsManualLocation: Boolean = true

    override fun getLastUpdate(): Long {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getLong(LAST_UPDATE, 0)
    }

    override fun getLastLocation(): String {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LAST_LOCATION, "")!!
    }

    override suspend fun fetchNewWeatherData(): List<Forecast>? {
        Log.d("MM20", "Updating weather data… (OpenWeatherMap)")
        var cityId = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getInt(CITY_ID, -1)
        val lastCityId = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getInt(LAST_CITY_ID, -1)
        if (cityId == -1) cityId = lastCityId
        val lm = context.getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager
        var location: Location? = null
        if (cityId == -1 && !context.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            Log.w("MM20", "Location permission is missing")
            return null
        }
        if (context.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION) && autoLocation) {
            location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }
        val lang = Locale.getDefault().language

        val currentWeather = try {
            openWeatherMapService.currentWeather(
                appid = getApiKey() ?: return null,
                id = cityId.takeIf { it != -1 && location == null },
                lat = location?.latitude,
                lon = location?.longitude,
                lang = lang,
            )
        } catch (e: Exception) {
            CrashReporter.logException(e)
            return null
        }

        val forecastList = mutableListOf<Forecast>()

        val city = currentWeather.name
        val country = currentWeather.sys?.country ?: return null
        cityId = currentWeather.id ?: return null
        val loc = "$city, $country"

        val forecasts = try {
            openWeatherMapService.forecast5Day3Hour(
                id = cityId,
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

        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putInt(CITY_ID, cityId)
            putInt(LAST_CITY_ID, cityId)
            putLong(LAST_UPDATE, System.currentTimeMillis())
            putString(LAST_LOCATION, loc)
        }

        return forecastList


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

    companion object {
        private const val PREFERENCES = "openweathermap"
        private const val CITY_ID = "city_id"
        private const val LAST_CITY_ID = "last_city_id"
        private const val LAST_UPDATE = "last_update"
        private const val LAST_LOCATION = "last_location"
        private const val AUTO_LOCATION = "auto_location"
    }
}