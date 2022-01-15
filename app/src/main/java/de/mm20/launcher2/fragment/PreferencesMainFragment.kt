package de.mm20.launcher2.fragment

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.mm20.launcher2.R

class PreferencesMainFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_main)
        findPreference<Preference>("screen_appearance")?.setOnPreferenceClickListener {
            setSettingsScreen(PreferencesAppearanceFragment())
            true
        }
        findPreference<Preference>("screen_services")?.setOnPreferenceClickListener {
            setSettingsScreen(PreferencesServicesFragment())
            true
        }
        findPreference<Preference>("screen_search")?.setOnPreferenceClickListener {
            setSettingsScreen(PreferencesSearchFragment())
            true
        }
    }

    private fun setSettingsScreen(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.preference_fragment_child_enter, R.anim.preference_fragment_parent_exit,
                        R.anim.preference_fragment_parent_enter, R.anim.preference_fragment_child_exit)
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit()
    }

    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity).supportActionBar?.setTitle(R.string.title_activity_settings)
    }
}