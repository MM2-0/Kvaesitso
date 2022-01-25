package de.mm20.launcher2.ui.launcher

import android.app.WallpaperManager
import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.activity.viewModels
import androidx.core.view.*
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import de.mm20.launcher2.icons.DynamicIconController
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.databinding.ActivityLauncherBinding
import de.mm20.launcher2.ui.launcher.modals.EditFavoritesView
import de.mm20.launcher2.ui.launcher.modals.HiddenItemsView
import de.mm20.launcher2.ui.legacy.helper.ThemeHelper
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetViewModel
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import java.util.*


class LauncherActivity : BaseActivity() {

    private val viewModel: LauncherActivityVM by viewModels()

    private val preferences = LauncherPreferences.instance

    private var windowBackgroundBlur: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!isAtLeastApiLevel(31)) return
            window.attributes = window.attributes.also {
                if (value) {
                    it.blurBehindRadius = (32 * dp).toInt()
                    it.flags = it.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                } else {
                    it.blurBehindRadius = 0
                    it.flags = it.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                }
            }
        }


    private fun updateSystemBarAppearance() {
        val allowLightSystemBars = allowsLightSystemBars()
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightNavigationBars =
            allowLightSystemBars && preferences.lightNavBar
        insetsController.isAppearanceLightStatusBars =
            allowLightSystemBars && preferences.lightStatusBar
    }

    private lateinit var binding: ActivityLauncherBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val iconRepository: IconRepository by inject()
        iconRepository.recreate()
        ThemeHelper.applyTheme(theme)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityLauncherBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        var editFavoritesDialog: MaterialDialog? = null
        viewModel.isEditFavoritesShown.observe(this) {
            if (it) {
                val view = EditFavoritesView(this@LauncherActivity)
                editFavoritesDialog =
                    MaterialDialog(this, BottomSheet(LayoutMode.MATCH_PARENT)).show {
                        customView(view = view)
                        title(res = R.string.menu_item_edit_favs)
                        positiveButton(res = R.string.close) {
                            viewModel.hideEditFavorites()
                            it.dismiss()
                        }
                        onDismiss {
                            view.save()
                            viewModel.hideEditFavorites()
                        }
                    }
            } else {
                editFavoritesDialog?.dismiss()
                editFavoritesDialog = null
            }
        }

        var hiddenItemsDialog: MaterialDialog? = null
        viewModel.isHiddenItemsShown.observe(this) {
            if (it) {
                val view = HiddenItemsView(this)
                hiddenItemsDialog = MaterialDialog(this, BottomSheet(LayoutMode.MATCH_PARENT))
                    .show {
                        title(R.string.menu_hidden_items)
                        customView(view = view)
                        negativeButton(R.string.close) { dismiss() }
                        onDismiss {
                            viewModel.hideHiddenItems()
                        }
                    }
            } else {
                hiddenItemsDialog?.dismiss()
                hiddenItemsDialog = null
            }
        }

        if (LauncherPreferences.instance.dimWallpaper) {
            binding.dimWallpaper.setBackgroundColor(getColor(R.color.wallpaper_dim))
        }

        val dynamicIconController: DynamicIconController by inject()

        lifecycle.addObserver(dynamicIconController)
    }

    override fun onResume() {
        super.onResume()
        ActivityStarter.resume()
        ActivityStarter.create(binding.rootView)
        binding.activityStartOverlay.visibility = View.INVISIBLE

        updateSystemBarAppearance()

        binding.container.doOnNextLayout {
            WallpaperManager.getInstance(this).setWallpaperOffsets(it.windowToken, 0.5f, 0.5f)
        }
    }

    private fun allowsLightSystemBars(): Boolean {
        val dimWallpaper = LauncherPreferences.instance.dimWallpaper
        val isDarkTheme = resources.getBoolean(R.bool.is_dark_theme)
        return !(isDarkTheme && dimWallpaper)
    }

    override fun onPause() {
        super.onPause()
        ActivityStarter.pause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        ActivityStarter.destroy()
    }
}