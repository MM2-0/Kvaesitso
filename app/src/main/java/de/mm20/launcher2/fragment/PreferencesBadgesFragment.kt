package de.mm20.launcher2.fragment

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.mm20.launcher2.R
import de.mm20.launcher2.badges.BadgeProvider
import de.mm20.launcher2.notifications.NotificationService
import org.koin.android.ext.android.inject

class PreferencesBadgesFragment : PreferenceFragmentCompat() {

    private val badgesProvider: BadgeProvider by inject()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_badges)
        findPreference<Preference>("notification_badges")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                NotificationService.getInstance()?.generateBadges()
            } else {
                badgesProvider.removeNotificationBadges()
            }
            true
        }
        findPreference<Preference>("suspended_badges")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                badgesProvider.addSuspendBadges()
            } else {
                badgesProvider.removeSuspendBadges()
            }
            true
        }
        findPreference<Preference>("cloud_badges")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                badgesProvider.addCloudBadges()
            } else {
                badgesProvider.removeCloudBadges()
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