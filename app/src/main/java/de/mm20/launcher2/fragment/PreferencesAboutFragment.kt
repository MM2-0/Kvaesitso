package de.mm20.launcher2.fragment

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.afollestad.materialdialogs.MaterialDialog
import de.mm20.launcher2.R
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.debug.DebugInformationDumper
import de.mm20.launcher2.licenses.AppLicense
import de.mm20.launcher2.licenses.OpenSourceLicenses


class PreferencesAboutFragment : PreferenceFragmentCompat() {

    private var easterEggCounter = 0

    @SuppressLint("ResourceType")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_about)
        val versionPref = findPreference<Preference>("version")!!
        try {
            val version = requireContext().packageManager.getPackageInfo(
                requireActivity().application.packageName,
                0
            ).versionName
            versionPref.summary = version
        } catch (e: PackageManager.NameNotFoundException) {
            //Should never happen
            versionPref.summary = "Ich mag Bockwurst-Bananen"
        }

        versionPref.setOnPreferenceClickListener {
            if (easterEggCounter in arrayOf(3, 4, 7)) Toast.makeText(
                context, when (easterEggCounter) {
                    3 -> R.string.easter_egg_1
                    4 -> R.string.easter_egg_2
                    7 -> R.string.easter_egg_3
                    else -> 0
                }, Toast.LENGTH_SHORT
            ).show()
            if (easterEggCounter == 8) {
                easterEggCounter = 0
                requireFragmentManager().beginTransaction()
                    .setCustomAnimations(
                        R.anim.preference_fragment_child_enter,
                        R.anim.preference_fragment_parent_exit,
                        R.anim.preference_fragment_parent_enter,
                        R.anim.preference_fragment_child_exit
                    )
                    .replace(
                        android.R.id.content,
                        PreferencesEasterEggFragment()
                    )
                    .addToBackStack(null)
                    .commit()
            }
            easterEggCounter++
            false
        }

        val licenses = findPreference<Preference>("category_licenses") as PreferenceCategory
        for (l in OpenSourceLicenses.sortedBy { it.name.lowercase() }) {
            val preference = Preference(activity, null, 0, R.style.Preference_Material)
            preference.title = l.name
            preference.summary = l.description
            preference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.preference_fragment_child_enter,
                        R.anim.preference_fragment_parent_exit,
                        R.anim.preference_fragment_parent_enter,
                        R.anim.preference_fragment_child_exit
                    )
                    .replace(android.R.id.content,
                        PreferencesLicenseFragment(l))
                    .addToBackStack(null)
                    .commit()
                true
            }
            licenses.addPreference(preference)
        }
        findPreference<Preference>("crash_reporter")?.setOnPreferenceClickListener {
            startActivity(CrashReporter.getLaunchIntent())
            true
        }
        findPreference<Preference>("export_debug")?.setOnPreferenceClickListener {
            Toast.makeText(
                activity,
                getString(
                    R.string.debug_export_information_file,
                    DebugInformationDumper().dump(requireContext())
                ),
                Toast.LENGTH_SHORT
            ).show()
            true
        }
        findPreference<Preference>("export_databases")?.setOnPreferenceClickListener {
            MaterialDialog(requireContext()).show {
                message(res = R.string.debug_export_databases_warning)
                positiveButton(res = R.string.dialog_continue, click = {
                    Toast.makeText(
                        activity,
                        getString(
                            R.string.debug_export_information_file,
                            DebugInformationDumper().exportDatabases(requireContext())
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    it.dismiss()
                })
                negativeButton(res = android.R.string.cancel, click = {
                    it.cancel()
                })
            }

            true
        }

        findPreference<Preference>("license")?.setOnPreferenceClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.preference_fragment_child_enter,
                    R.anim.preference_fragment_parent_exit,
                    R.anim.preference_fragment_parent_enter,
                    R.anim.preference_fragment_child_exit
                )
                .replace(android.R.id.content,
                    PreferencesLicenseFragment(AppLicense.get(requireContext())))
                .addToBackStack(null)
                .commit()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity).supportActionBar?.setTitle(R.string.preference_screen_about)
    }
}
