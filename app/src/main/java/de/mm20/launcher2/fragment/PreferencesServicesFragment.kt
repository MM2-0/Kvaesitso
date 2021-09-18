package de.mm20.launcher2.fragment

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.mm20.launcher2.R
import de.mm20.launcher2.gservices.GoogleApiHelper
import de.mm20.launcher2.msservices.MicrosoftGraphApiHelper
import de.mm20.launcher2.nextcloud.NextcloudApiHelper
import de.mm20.launcher2.owncloud.OwncloudClient
import kotlinx.coroutines.launch

class PreferencesServicesFragment : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                updateGooglePreferences()
                updateMicrosoftPreferences()
                updateNextcloudPreferences()
            }
        }
    }


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_services)
    }

    private suspend fun updateGooglePreferences() {
        val pref = findPreference<Preference>("google_signin")!!
        val googleApiHelper = GoogleApiHelper.getInstance(requireContext())
        if (!googleApiHelper.isAvailable()) {
            pref.isEnabled = false
            pref.summary = context?.getString(R.string.feature_not_available, context?.getString(R.string.app_name))
            return
        }
        val account = googleApiHelper.getAccount()
        if (account == null) {
            pref.apply {
                setTitle(R.string.preference_google_signin)
                setSummary(R.string.preference_google_signin_summary)
                setOnPreferenceClickListener {
                    googleApiHelper.login(requireActivity())
                    true
                }
            }
        } else {
            pref.apply {
                title = context.getString(R.string.preference_signin_logout)
                summary = context.getString(R.string.preference_signin_user, account.name)
                setOnPreferenceClickListener {
                    googleApiHelper.logout()
                    lifecycleScope.launch {
                        updateGooglePreferences()
                    }
                    true
                }
            }
        }
    }

    private suspend fun updateMicrosoftPreferences() {
        val pref = findPreference<Preference>("ms_signin")!!
        val msApiHelper = MicrosoftGraphApiHelper.getInstance(requireContext())
        if (!msApiHelper.isAvailable()) {
            pref.isEnabled = false
            pref.summary = context?.getString(R.string.feature_not_available, context?.getString(R.string.app_name))
            return
        }
        val user = MicrosoftGraphApiHelper.getInstance(requireContext()).getUser()
        if (user == null) {
            pref.setTitle(R.string.preference_ms_signin)
            pref.setSummary(R.string.preference_ms_signin_summary)
            pref.setOnPreferenceClickListener {
                lifecycleScope.launch {
                    msApiHelper.login(requireActivity())
                    updateMicrosoftPreferences()
                }
                true
            }
        } else {
            pref.setTitle(R.string.preference_signin_logout)
            pref.summary = context?.getString(R.string.preference_signin_user, user.name)
            pref.setOnPreferenceClickListener {
                lifecycleScope.launch {
                    msApiHelper.logout()
                    updateMicrosoftPreferences()
                }
                true
            }

        }
    }

    private suspend fun updateNextcloudPreferences() {
        val nextcloud = NextcloudApiHelper(requireContext())
        val user = nextcloud.getLoggedInUser()
        if (user == null) {
            findPreference<Preference>("nextcloud_signin")?.let {
                it.setOnPreferenceClickListener {
                    nextcloud.login(requireActivity())
                    true
                }
                it.setTitle(R.string.preference_nextcloud_signin)
                it.setSummary(R.string.preference_nextcloud_signin_summary)
            }
        } else {
            findPreference<Preference>("nextcloud_signin")?.let {
                it.setOnPreferenceClickListener {
                    lifecycleScope.launch {
                        nextcloud.logout()
                        updateNextcloudPreferences()
                    }
                    true
                }
                it.setTitle(R.string.preference_signin_logout)
                it.summary = context?.getString(
                    R.string.preference_signin_user_nextcloud,
                    user.displayName
                )
            }
        }
    }

    private fun updateOwncloudPreferences() {
        val client = OwncloudClient(context ?: return)
        lifecycleScope.launch {
            val user = client.getLoggedInUser()
            if (user == null) {
                findPreference<Preference>("owncloud_signin")?.let {
                    it.setOnPreferenceClickListener {
                        OwncloudClient(requireContext()).login(
                            requireActivity(),
                            REQUEST_OWNCLOUD_LOGIN
                        )
                        true
                    }
                    it.setTitle(R.string.preference_owncloud_signin)
                    it.setSummary(R.string.preference_owncloud_signin_summary)
                }
            } else {
                findPreference<Preference>("owncloud_signin")?.let {
                    it.setOnPreferenceClickListener {
                        OwncloudClient(requireContext()).logout()
                        updateOwncloudPreferences()
                        true
                    }
                    it.setTitle(R.string.preference_signin_logout)
                    it.summary = context?.getString(
                        R.string.preference_signin_user_nextcloud,
                        user.displayName,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity).supportActionBar?.setTitle(R.string.preference_screen_services)
        updateOwncloudPreferences()
    }

    companion object {
        const val REQUEST_OWNCLOUD_LOGIN = 581
    }
}