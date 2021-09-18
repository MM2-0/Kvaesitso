package de.mm20.launcher2.fragment

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.mm20.launcher2.R
import de.mm20.launcher2.gservices.GoogleApiHelper
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.msservices.MicrosoftGraphApiHelper
import de.mm20.launcher2.nextcloud.NextcloudApiHelper
import de.mm20.launcher2.owncloud.OwncloudClient
import de.mm20.launcher2.preferences.LauncherPreferences
import kotlinx.coroutines.launch

class PreferencesSearchFragment : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                updateGoogleDrive()
                updateOneDrive()
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_search)
        findPreference<Preference>("search_activities")?.summary =
            getString(
                R.string.preference_search_activities_summary,
                requireActivity().componentName.flattenToShortString()
            )
        findPreference<Preference>("search_files")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == true &&
                requireContext().checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    0
                )
            }
            true
        }
        findPreference<Preference>("search_edit_websearch")?.setOnPreferenceClickListener {
            setSettingsScreen(PreferencesWebSearchesFragment())
            true
        }
    }

    private suspend fun updateGoogleDrive() {
        val googleApiHelper = GoogleApiHelper.getInstance(context ?: return)
        val account = googleApiHelper.getAccount()
        val pref = findPreference<Preference>("search_gdrive")!!
        if (account == null) {
            pref.apply {
                setSummary(R.string.preference_summary_not_logged_in)
            }
        } else {
            pref.apply {
                summary = context.getString(R.string.preference_search_gdrive_summary, account.name)
            }
        }
        val isSignedIn = account != null
        pref.setOnPreferenceChangeListener { _, value ->
            val newVal = value as Boolean
            if (newVal && !isSignedIn) {
                googleLogin()
            }
            true
        }
    }

    private suspend fun updateOneDrive() {
        val oneDrivePref = findPreference<Preference>("search_onedrive")!!
        val user = MicrosoftGraphApiHelper.getInstance(requireContext()).getUser()
        if (user == null) {
            oneDrivePref.setSummary(R.string.preference_summary_not_logged_in)
            oneDrivePref.setOnPreferenceChangeListener { _, value ->
                if (value as Boolean) {
                    lifecycleScope.launch launch2@{
                        MicrosoftGraphApiHelper.getInstance(requireContext())
                            .login(requireActivity())
                        updateOneDrive()
                    }
                }
                true
            }
        } else {
            oneDrivePref.summary =
                context?.getString(R.string.preference_search_onedrive_summary, user.name)
        }
    }

    private fun updateNextcloud() {
        val nextcloudPref = findPreference<Preference>("search_nextcloud")!!
        val client = NextcloudApiHelper(context ?: return)
        lifecycleScope.launch {
            val user = client.getLoggedInUser()
            if (user == null) {
                nextcloudPref.setSummary(R.string.preference_summary_not_logged_in)
                LauncherPreferences.instance.searchNextcloud = false
                nextcloudPref.setOnPreferenceChangeListener { _, value ->
                    if (value as Boolean) {
                        lifecycleScope.launch launch2@{
                            updateNextcloud()
                        }
                    }
                    true
                }
            } else {
                nextcloudPref.summary = context?.getString(
                    R.string.preference_search_nextcloud_summary,
                    user.displayName
                )
            }
        }
    }

    private fun updateOwncloud() {
        val owncloudPref = findPreference<Preference>("search_owncloud")!!
        lifecycleScope.launch {
            val client = OwncloudClient(context ?: return@launch)
            val user = client.getLoggedInUser()
            if (user == null) {
                owncloudPref.setSummary(R.string.preference_summary_not_logged_in)
                LauncherPreferences.instance.searchOwncloud = false
                owncloudPref.setOnPreferenceChangeListener { _, value ->
                    if (value as Boolean) {
                        lifecycleScope.launch launch2@{
                            client.login(requireActivity(), 0)
                            updateOwncloud()
                        }
                    }
                    true
                }
            } else {
                owncloudPref.summary = context?.getString(
                    R.string.preference_search_nextcloud_summary,
                    user.displayName,
                )
            }
        }
    }

    private fun googleLogin() {
        GoogleApiHelper.getInstance(requireContext()).login(requireActivity())
    }

    private fun setSettingsScreen(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.preference_fragment_child_enter, R.anim.preference_fragment_parent_exit,
                R.anim.preference_fragment_parent_enter, R.anim.preference_fragment_child_exit
            )
            .replace(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity).supportActionBar?.setTitle(R.string.preference_screen_search)
        updateNextcloud()
        updateOwncloud()
    }
}