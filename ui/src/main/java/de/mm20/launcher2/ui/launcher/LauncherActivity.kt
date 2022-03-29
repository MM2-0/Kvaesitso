package de.mm20.launcher2.ui.launcher

import android.app.WallpaperManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.*
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import de.mm20.launcher2.icons.DynamicIconController
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.databinding.ActivityLauncherBinding
import de.mm20.launcher2.ui.launcher.modals.EditFavoritesView
import de.mm20.launcher2.ui.launcher.modals.HiddenItemsView
import org.koin.android.ext.android.inject


class LauncherActivity : BaseActivity() {

    private val viewModel: LauncherActivityVM by viewModels()

    private lateinit var binding: ActivityLauncherBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        viewModel.setDarkMode(resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)

        val iconRepository: IconRepository by inject()
        iconRepository.applyTheme(theme)

        binding = ActivityLauncherBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        viewModel.dimBackground.observe(this) { dim ->
            window.attributes = window.attributes.also {
                if (dim) {
                    binding.rootView.setBackgroundColor(0x4C000000)
                } else {
                    binding.rootView.setBackgroundColor(0)
                }
            }
        }

        viewModel.lightStatusBar.observe(this) {
            val windowController = WindowCompat.getInsetsController(window, binding.rootView)
            windowController.isAppearanceLightStatusBars = it
        }

        viewModel.lightNavBar.observe(this) {
            val windowController = WindowCompat.getInsetsController(window, binding.rootView)
            windowController.isAppearanceLightNavigationBars = it
        }

        viewModel.hideStatusBar.observe(this) {
            val windowController = WindowCompat.getInsetsController(window, binding.rootView)
            if (it) {
                windowController.hide(WindowInsetsCompat.Type.statusBars())
            } else {
                windowController.show(WindowInsetsCompat.Type.statusBars())
            }
        }
        viewModel.hideNavBar.observe(this) {
            val windowController = WindowCompat.getInsetsController(window, binding.rootView)
            if (it) {
                windowController.hide(WindowInsetsCompat.Type.navigationBars())
            } else {
                windowController.show(WindowInsetsCompat.Type.navigationBars())
            }
        }

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

        var hiddenItemsView: HiddenItemsView? = null
        viewModel.isHiddenItemsShown.observe(this) {
            if (it) {
                if (hiddenItemsView != null) return@observe
                hiddenItemsView = HiddenItemsView(this).apply {
                    onDismiss = {
                        viewModel.hideHiddenItems()
                    }
                }
                binding.rootView.addView(hiddenItemsView)
            } else {
                if (hiddenItemsView == null) return@observe
                binding.rootView.removeView(hiddenItemsView)
                hiddenItemsView = null
            }
        }

        val dynamicIconController: DynamicIconController by inject()

        lifecycle.addObserver(dynamicIconController)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val windowController = WindowCompat.getInsetsController(window, binding.rootView)
        windowController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onResume() {
        super.onResume()
        binding.activityStartOverlay.visibility = View.INVISIBLE

        binding.container.doOnNextLayout {
            WallpaperManager.getInstance(this).setWallpaperOffsets(it.windowToken, 0.5f, 0.5f)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        onBackPressed()
    }
}