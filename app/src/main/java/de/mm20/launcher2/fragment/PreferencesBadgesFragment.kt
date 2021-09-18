    package de.mm20.launcher2.fragment

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.mm20.launcher2.R
import de.mm20.launcher2.badges.BadgeProvider
import de.mm20.launcher2.notifications.NotificationService

class PreferencesBadgesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_badges)
        findPreference<Preference>("notification_badges")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                de.mm20.launcher2.notifications.NotificationService.getInstance()?.generateBadges()
            } else {
                BadgeProvider.getInstance(requireContext()).removeNotificationBadges()
            }
            true
        }
        findPreference<Preference>("suspended_badges")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                BadgeProvider.getInstance(requireContext()).addSuspendBadges()
            } else {
                BadgeProvider.getInstance(requireContext()).removeSuspendBadges()
            }
            true
        }
        findPreference<Preference>("cloud_badges")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                BadgeProvider.getInstance(requireContext()).addCloudBadges()
            } else {
                BadgeProvider.getInstance(requireContext()).removeCloudBadges()
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