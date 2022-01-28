package de.mm20.launcher2.fragment

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import de.mm20.launcher2.R
import de.mm20.launcher2.icons.IconPackManager
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.icons.LauncherIcon
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class PreferencesAppearanceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_appearance)

        findPreference<Preference>("card_background")?.setOnPreferenceChangeListener { _, newValue ->
            requireActivity().recreate()
            true
        }

        findPreference<Preference>("wallpaper")?.setOnPreferenceClickListener {
            requireContext().startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null))
            true
        }
        findPreference<Preference>("cards")?.setOnPreferenceClickListener {
            requireFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.preference_fragment_child_enter, R.anim.preference_fragment_parent_exit,
                            R.anim.preference_fragment_parent_enter, R.anim.preference_fragment_child_exit)
                    .replace(android.R.id.content, PreferencesCardFragment())
                    .addToBackStack(null)
                    .commit()
            true
        }

        val iconPackManager: IconPackManager by inject()
        val iconRepository: IconRepository by inject()
        lifecycleScope.launch {
            val packs = iconPackManager.getInstalledIconPacks()
            findPreference<ListPreference>("icon_pack")?.apply {
                entries = packs.map { it.name }.toMutableList().apply { add(0, "System") }.toTypedArray()
                entryValues = (-1 until packs.size).map { it.toString() }.toTypedArray()
                if (packs.isEmpty()) {
                    isEnabled = false
                    setSummary(R.string.preference_icon_pack_summary_empty)
                } else {
                    isEnabled = true
                    summary = "%s"
                }
                setOnPreferenceChangeListener { _, newValue ->
                    val index = (newValue as String).toInt()
                    true
                }
            }
        }

        findPreference<Preference>("legacy_icon_bg")?.setOnPreferenceChangeListener { _, _ ->
            true
        }

        findPreference<Preference>("themed_icons")?.setOnPreferenceChangeListener { _, _ ->
            true
        }

        val shapePreference = findPreference<Preference>("icon_shape")!!
        shapePreference.setOnPreferenceClickListener {
            val launcherIcon = LauncherIcon(
                    foreground = requireContext().getDrawable(R.mipmap.ic_launcher_foreground)!!,
                    background = ColorDrawable(requireContext().getColor(R.color.ic_launcher_background))
            )
            val iconShapeList = LinearLayout(requireContext())
            iconShapeList.orientation = LinearLayout.VERTICAL
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            val dialog = MaterialDialog(requireContext())

            dialog.customView(view = iconShapeList, scrollable = true)
                    .title(R.string.preference_icon_shape)
                    .negativeButton(android.R.string.cancel) {
                        dialog.cancel()
                    }
                    .show()
            true
        }

        val systemBarsCategory = findPreference<PreferenceCategory>("system_bars")!!
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            systemBarsCategory.removePreference(findPreference("light_nav_bar"))
        }
    }


    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity).supportActionBar
                ?.setTitle(R.string.preference_screen_appearance)
    }
}