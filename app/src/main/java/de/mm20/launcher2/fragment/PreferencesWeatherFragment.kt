package de.mm20.launcher2.fragment

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import de.mm20.launcher2.R
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.preferences.WeatherProviders
import de.mm20.launcher2.weather.WeatherLocation
import de.mm20.launcher2.weather.WeatherProvider
import de.mm20.launcher2.weather.WeatherViewModel
import de.mm20.launcher2.weather.here.HereProvider
import de.mm20.launcher2.weather.metno.MetNoProvider
import de.mm20.launcher2.weather.openweathermap.OpenWeatherMapProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PreferencesWeatherFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_weather)
        findPreference<Preference>("location")?.setOnPreferenceChangeListener { _, newValue ->
            lifecycleScope.launch {
                val locations = withContext(Dispatchers.IO) {
                    WeatherProvider.getInstance(requireContext())
                        ?.lookupLocation(newValue as String)
                } ?: return@launch
                onLookupCompleted(locations)
            }
            false
        }
        /*findPreference<Preference>("weather_provider")?.setOnPreferenceChangeListener { pref, newValue ->
            val newProvider = WeatherProviders.byValue(newValue as String)
            LauncherPreferences.instance.weatherProvider = newProvider
            WeatherProvider.getInstance(requireContext())?.resetLastUpdate()
            ViewModelProvider(this).get(WeatherViewModel::class.java).requestUpdate(requireContext())
            updateProviderPreferences()
            true
        }*/
        val providerPref = findPreference<Preference>("weather_provider")!!
        val context = requireContext()

        val providers = mutableListOf<Pair<WeatherProviders, String>>()
        OpenWeatherMapProvider(context).takeIf { it.isAvailable() }?.let {
            providers.add(WeatherProviders.OPENWEATHERMAP to it.name)
        }
        HereProvider(context).takeIf { it.isAvailable() }?.let {
            providers.add(WeatherProviders.HERE to it.name)
        }
        MetNoProvider(context).takeIf { it.isAvailable() }?.let {
            providers.add(WeatherProviders.MET_NO to it.name)
        }

        if (providers.isEmpty()) {
            providerPref.summary = context.getString(
                R.string.feature_not_available,
                context.getString(R.string.app_name)
            )
            providerPref.isEnabled = false
        } else {
            providerPref.setOnPreferenceClickListener {
                MaterialDialog(context).show {
                    title(R.string.preference_weather_provider)
                    listItemsSingleChoice(
                        items = providers.map { it.second },
                        initialSelection = providers.indexOfFirst { it.first == LauncherPreferences.instance.weatherProvider }
                    ) { dialog, index, text ->
                        LauncherPreferences.instance.weatherProvider = providers[index].first
                        WeatherProvider.getInstance(requireContext())?.resetLastUpdate()
                        ViewModelProvider(this@PreferencesWeatherFragment)
                            .get(WeatherViewModel::class.java)
                            .requestUpdate(requireContext())
                        updateProviderPreferences()
                        dialog.dismiss()
                    }
                }
                true
            }
        }
        findPreference<Preference>("auto_location")?.setOnPreferenceChangeListener { _, newValue ->
            val autoLocation = newValue as Boolean
            val provider = WeatherProvider.getInstance(requireContext())
            provider?.autoLocation = autoLocation
            provider?.resetLastUpdate()
            provider?.setLocation(null)
            ViewModelProvider(this).get(WeatherViewModel::class.java)
                .requestUpdate(requireContext())
            updateProviderPreferences()
            true
        }
        updateProviderPreferences()
    }

    private fun updateProviderPreferences() {
        val provider = WeatherProvider.getInstance(requireContext())
        val autoLocationPref = findPreference<SwitchPreference>("auto_location")!!
        val locationPref = findPreference<Preference>("location")!!
        val unitsPref = findPreference<Preference>("imperial_units")!!
        val providerPref = findPreference<Preference>("weather_provider")!!

        locationPref.parent?.isVisible = provider != null
        unitsPref.isVisible = provider != null

        provider ?: return

        providerPref.summary = provider.name
        autoLocationPref.isChecked = provider.autoLocation
        locationPref.summary =
            if (provider.autoLocation) provider.getLastLocation()?.name else provider.getLocation()?.name
    }

    private fun onLookupCompleted(results: List<WeatherLocation>) {
        MaterialDialog(requireContext())
            .listItems(
                items = results.map { it.name },
                waitForPositiveButton = false
            ) { dialog, index, _ ->
                val provider = WeatherProvider.getInstance(requireContext())
                    ?: return@listItems dialog.dismiss()
                provider.resetLastUpdate()
                provider.setLocation(results[index])
                findPreference<Preference>("location")?.summary = results[index].name
                ViewModelProvider(this).get(WeatherViewModel::class.java)
                    .requestUpdate(requireContext())
                dialog.dismiss()
            }
            .negativeButton {
                it.cancel()
            }
            .show()
    }


    override fun onResume() {
        super.onResume()

        (activity as AppCompatActivity).supportActionBar?.setTitle(R.string.preference_screen_weather)
    }

}
