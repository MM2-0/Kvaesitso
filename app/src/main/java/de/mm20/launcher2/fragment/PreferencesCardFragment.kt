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
import de.mm20.launcher2.LauncherApplication
import de.mm20.launcher2.R
import de.mm20.launcher2.ktx.castTo
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.translate
import de.mm20.launcher2.preferences.CardBackground
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.ui.legacy.helper.WallpaperBlur
import kotlinx.android.synthetic.main.fragment_card_settings.*
import kotlinx.coroutines.CoroutineScope
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
        previewCardBlur.clipToOutline = true

        previewCardBlur.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline?) {
                val radius = preferences.cardRadius
                outline?.setRoundRect(0, 0, view.width, view.height, radius * dp)
            }
        }

        val context = requireContext()


        val previewCardBlur = previewCardBlur
        val previewCard = previewCard
        val prefFragment = PreferenesCardInnerFragment()

        prefFragment.onPreferencesReady = {
            findPreference<Preference>("card_radius")?.let {
                it.summary = preferences.cardRadius.toString()
                it.setOnPreferenceChangeListener { pref, newValue ->
                    val value = newValue as Int
                    previewCard.radius = value * dp
                    previewCardBlur.invalidateOutline()
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
            findPreference<Preference>("blur_cards")?.let {
                if (WallpaperManager.getInstance(requireContext()).wallpaperInfo != null) {
                    it.isEnabled = false
                    it.setSummary(R.string.preference_blur_cards_summary_lwp)
                    previewCardBlur.visibility = View.INVISIBLE
                } else {
                    previewCardBlur.visibility = if (preferences.blurCards) {
                        View.VISIBLE
                    } else {
                        View.INVISIBLE
                    }
                    it.setOnPreferenceChangeListener { pref, newValue ->
                        previewCardBlur.visibility = if (newValue as Boolean) {
                            View.VISIBLE
                        } else {
                            View.INVISIBLE
                        }
                        true
                    }
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

        if (preferences.blurCards && preferences.cardOpacity < 0xFF) {
            lifecycleScope.launch {
                val wallpaper = withContext(Dispatchers.IO) {
                    WallpaperBlur.getCachedBitmap(requireContext())
                }
                LauncherApplication.instance.blurredWallpaper = wallpaper
            }
        }


        content.doOnNextLayout {
            WallpaperManager.getInstance(requireContext()).setWallpaperOffsets(it.windowToken, 0.5f, 0.5f)
        }

        val activity = requireActivity()

        lifecycleScope.launch {
            val viewPosition = intArrayOf(0, 0)
            val rect = Rect(0, 0, previewCardBlur.width, previewCardBlur.height)
            val screen = Point()
            activity.windowManager.defaultDisplay.getRealSize(screen)
            previewCardBlur.getLocationOnScreen(viewPosition)
            val file = File(requireContext().cacheDir, "wallpaper")
            if (!file.exists()) return@launch
            blurBitmap = withContext(Dispatchers.IO) {
                val wallpaperWidth: Int
                val wallpaperHeight: Int
                val decoder = BitmapRegionDecoder
                        .newInstance(file.absolutePath, false)
                wallpaperHeight = decoder.height
                wallpaperWidth = decoder.width

                if (wallpaperWidth >= screen.x && wallpaperHeight >= screen.y) {
                    val translateX = (wallpaperWidth - previewCardBlur.width) / 2f
                    val translateY = (wallpaperHeight - screen.y) / 2f + viewPosition[1]
                    rect.translate(translateX.roundToInt(),
                            translateY.roundToInt())
                }

                decoder.decodeRegion(rect, null)
            }
            previewCardBlur.setImageBitmap(blurBitmap)
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