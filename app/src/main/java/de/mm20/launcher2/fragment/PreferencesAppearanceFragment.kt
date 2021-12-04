package de.mm20.launcher2.fragment

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import de.mm20.launcher2.preferences.IconShape
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.preferences.Themes
import de.mm20.launcher2.ui.legacy.view.LauncherIconView
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class PreferencesAppearanceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_appearance)
        findPreference<Preference>("theme")?.setOnPreferenceChangeListener { _, newValue ->
            val theme = Themes.byValue(newValue as String)
            @Suppress("DEPRECATION") // Still using MODE_NIGHT_AUTO
            AppCompatDelegate.setDefaultNightMode(when (theme) {
                Themes.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                Themes.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                Themes.AUTO -> AppCompatDelegate.MODE_NIGHT_AUTO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            })
            requireActivity().recreate()
            true
        }

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
                    value = packs.indexOfFirst { it.packageName == iconPackManager.selectedIconPack }.toString()
                }
                setOnPreferenceChangeListener { _, newValue ->
                    val index = (newValue as String).toInt()
                    if (index == -1) iconPackManager.selectIconPack("")
                    else {
                        iconPackManager.selectIconPack(packs[index].packageName)
                    }
                    iconRepository.recreate()
                    true
                }
            }
        }

        findPreference<Preference>("legacy_icon_bg")?.setOnPreferenceChangeListener { _, _ ->
            iconRepository.recreate()
            true
        }

        findPreference<Preference>("themed_icons")?.setOnPreferenceChangeListener { _, _ ->
            iconRepository.recreate()
            true
        }

        val shapePreference = findPreference<Preference>("icon_shape")!!
        shapePreference.summary = getShapeName()
        shapePreference.setOnPreferenceClickListener {
            val launcherIcon = LauncherIcon(
                    foreground = requireContext().getDrawable(R.mipmap.ic_launcher_foreground)!!,
                    background = ColorDrawable(requireContext().getColor(R.color.ic_launcher_background))
            )
            val iconShapeList = LinearLayout(requireContext())
            iconShapeList.orientation = LinearLayout.VERTICAL
            val shapes = arrayOf(
                    IconShape.PLATFORM_DEFAULT to R.string.preference_icon_shape_platform,
                    IconShape.CIRCLE to R.string.preference_icon_shape_circle,
                    IconShape.ROUNDED_SQUARE to R.string.preference_icon_shape_rounded_square,
                    IconShape.SQUARE to R.string.preference_icon_shape_square,
                    IconShape.SQUIRCLE to R.string.preference_icon_shape_squircle,
                    IconShape.HEXAGON to R.string.preference_icon_shape_hexagon,
                    IconShape.TRIANGLE to R.string.preference_icon_shape_triangle,
                    IconShape.PENTAGON to R.string.preference_icon_shape_pentagon
            )
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            val dialog = MaterialDialog(requireContext())
            shapes.forEachIndexed { i, shape ->
                val view = View.inflate(requireContext(), R.layout.preference_icon_shape_row, null)
                view.findViewById<LauncherIconView>(R.id.icon).also { iconView ->
                    iconView.icon = launcherIcon
                    iconView.shape = shape.first
                }
                view.findViewById<TextView>(R.id.label).also { labelView ->
                    labelView.setText(shape.second)
                }
                view.layoutParams = layoutParams
                iconShapeList.addView(view)
                view.setOnClickListener {
                    LauncherPreferences.instance.iconShape = shape.first
                    shapePreference.summary = getShapeName()
                    dialog.dismiss()
                }
            }

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

    private fun getShapeName(): String {
        return requireContext().getString(when (LauncherIconView.getDefaultShape(requireContext())) {
            IconShape.TRIANGLE -> R.string.preference_icon_shape_triangle
            IconShape.HEXAGON -> R.string.preference_icon_shape_hexagon
            IconShape.ROUNDED_SQUARE -> R.string.preference_icon_shape_rounded_square
            IconShape.SQUIRCLE -> R.string.preference_icon_shape_squircle
            IconShape.SQUARE -> R.string.preference_icon_shape_square
            IconShape.PENTAGON -> R.string.preference_icon_shape_pentagon
            IconShape.PLATFORM_DEFAULT -> R.string.preference_icon_shape_platform
            else -> R.string.preference_icon_shape_circle
        })
    }


    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity).supportActionBar
                ?.setTitle(R.string.preference_screen_appearance)
    }
}