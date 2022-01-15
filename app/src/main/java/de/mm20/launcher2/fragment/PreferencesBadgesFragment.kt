package de.mm20.launcher2.fragment

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.mm20.launcher2.R

class PreferencesBadgesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_badges)
        findPreference<Preference>("notification_badges")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
            } else {
            }
            true
        }
        findPreference<Preference>("suspended_badges")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
            } else {
            }
            true
        }
        findPreference<Preference>("cloud_badges")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
            } else {
            }
            true
        }
    }


    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity).supportActionBar
            ?.setTitle(R.string.preference_screen_badges)
    }
}