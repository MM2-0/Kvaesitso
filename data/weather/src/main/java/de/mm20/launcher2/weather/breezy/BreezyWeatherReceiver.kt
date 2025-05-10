package de.mm20.launcher2.weather.breezy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.weather.WeatherSettings
import de.mm20.launcher2.serialization.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BreezyWeatherReceiver: BroadcastReceiver(), KoinComponent {

    private val settings: WeatherSettings by inject()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        scope.launch {
            val provider = settings.providerId.first()
            if (provider != BreezyWeatherProvider.Id) {
                return@launch
            }
            val weatherJson = intent.getStringExtra("WeatherJson")

            if (weatherJson == null) {
                Log.e("BreezyWeatherPlugin", "Broadcast was received but WeatherJson was null")
                return@launch
            }

            val weatherData = try {
                Json.Lenient.decodeFromString<BreezyWeatherData>(weatherJson)
            } catch (e: SerializationException) {
                CrashReporter.logException(e)
                return@launch
            }

            BreezyWeatherProvider(context).pushWeatherData(weatherData)

        }
    }
}