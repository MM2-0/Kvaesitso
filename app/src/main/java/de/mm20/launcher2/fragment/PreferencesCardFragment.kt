package de.mm20.launcher2.fragment

import android.animation.Animator
import android.animation.ObjectAnimator
import android.app.WallpaperManager
import android.graphics.*
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.mm20.launcher2.R
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.translate
import de.mm20.launcher2.preferences.CardBackground
import de.mm20.launcher2.preferences.LauncherPreferences
import kotlinx.android.synthetic.main.fragment_card_settings.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class PreferencesCardFragment : Fragment(R.layout.fragment_card_settings) {

    val preferences = LauncherPreferences.instance

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        previewCard.strokeOpacity = 0xFF

        val context = requireContext()


        val previewCard = previewCard
        val prefFragment = PreferenesCardInnerFragment()

        prefFragment.onPreferencesReady = {
            findPreference<Preference>("card_radius")?.let {
                it.summary = preferences.cardRadius.toString()
                it.setOnPreferenceChangeListener { pref, newValue ->
                    val value = newValue as Int
                    previewCard.radius = value * dp
                    pref.summary = value.toString()
                    true
                }
            }
            findPreference<Preference>("card_opacity")?.let {
                it.summary = preferences.cardOpacity.toString()
                it.setOnPreferenceChangeListener { pref, newValue ->
                    val value = newValue as Int
                    previewCard.backgroundOpacity = value
                    previewCard.cardElevation = if (value == 0xFF) resources.getDimension(R.dimen.card_elevation) else 0f
                    pref.summary = value.toString()
                    true
                }
            }
            findPreference<Preference>("card_stroke_width")?.let {
                it.summary = preferences.cardRadius.toString()
                it.setOnPreferenceChangeListener { pref, newValue ->
                    val value = newValue as Int
                    previewCard.strokeWidth = (value * dp).roundToInt()
                    pref.summary = value.toString()
                    true
                }
            }
            findPreference<Preference>("card_background")?.let {
                it.setOnPreferenceChangeListener { preference, newValue ->
                    val background = CardBackground.byValue(newValue as String)
                    var color = when (background) {
                        CardBackground.BLACK -> context.getColor(R.color.cardview_background_black)
                        else -> context.getColor(R.color.cardview_background)
                    }
                    color = color and ((previewCard.backgroundOpacity shl 24) or 0xFFFFFF)
                    previewCard.setCardBackgroundColor(color)
                    true
                }
            }
        }

        childFragmentManager.beginTransaction()
                .replace(R.id.preferencesView, prefFragment)
                .commit()


    }

    private var blurBitmap: Bitmap? = null

    private var animator: Animator? = null
    override fun onStart() {
        super.onStart()
        val content = activity?.findViewById<View>(android.R.id.content) ?: return
        animator = ObjectAnimator.ofArgb(content, "backgroundColor", ResourcesCompat.getColor(resources, R.color.settings_window_background, null), Color.TRANSPARENT)
                .apply {
                    duration = 200
                    startDelay = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
                    start()
                }


        content.doOnNextLayout {
            WallpaperManager.getInstance(requireContext()).setWallpaperOffsets(it.windowToken, 0.5f, 0.5f)
        }
    }

    override fun onStop() {
        super.onStop()
        if (animator?.isRunning == true) animator?.end()
        activity?.findViewById<View>(android.R.id.content)?.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.settings_window_background, null))
    }
}

class PreferenesCardInnerFragment : PreferenceFragmentCompat() {
    var onPreferencesReady: (PreferenesCardInnerFragment.() -> Unit)? = null
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_cards)
        onPreferencesReady?.invoke(this)
    }

}