package de.mm20.launcher2.ui.launcher

import android.app.WallpaperManager
import android.content.Intent
import android.content.res.Configuration
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
import de.mm20.launcher2.ui.legacy.helper.ActivityStarter
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

        val iconRepository: IconRepository by inject()
        iconRepository.recreate()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        viewModel.setDarkMode(resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)

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

        val windowController = WindowInsetsControllerCompat(window, binding.rootView)
        viewModel.lightStatusBar.observe(this) {
            windowController.isAppearanceLightStatusBars = it
        }
        viewModel.lightNavBar.observe(this) {
            windowController.isAppearanceLightNavigationBars = it
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

        val dynamicIconController: DynamicIconController by inject()

        lifecycle.addObserver(dynamicIconController)
    }

    override fun onResume() {
        super.onResume()
        ActivityStarter.create(binding.rootView)
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