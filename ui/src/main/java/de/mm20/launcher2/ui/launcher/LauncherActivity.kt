package de.mm20.launcher2.ui.launcher

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.icons.DynamicIconController
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideSettings
import de.mm20.launcher2.ui.component.NavBarEffects
import de.mm20.launcher2.ui.launcher.modals.EditFavoritesView
import de.mm20.launcher2.ui.launcher.modals.HiddenItemsSheet
import de.mm20.launcher2.ui.theme.LauncherTheme
import org.koin.android.ext.android.inject


class LauncherActivity : BaseActivity() {

    private val viewModel: LauncherActivityVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        viewModel.setDarkMode(resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)

        setContent {
            LauncherTheme {
                ProvideSettings {
                    val lightStatus by viewModel.lightStatusBar.observeAsState(false)
                    val lightNav by viewModel.lightNavBar.observeAsState(false)
                    val hideStatus by viewModel.hideStatusBar.observeAsState(false)
                    val hideNav by viewModel.hideNavBar.observeAsState(false)
                    val dimBackground by viewModel.dimBackground.observeAsState(false)

                    val systemUiController = rememberSystemUiController()

                    LaunchedEffect(hideStatus) {
                        systemUiController.isStatusBarVisible = !hideStatus
                    }
                    LaunchedEffect(hideNav) {
                        systemUiController.isNavigationBarVisible = !hideNav
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (dimBackground) Color.Black.copy(alpha = 0.30f) else Color.Transparent),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        NavBarEffects(modifier = Modifier.fillMaxSize())
                        PullDownScaffold(
                            modifier = Modifier
                                .fillMaxSize()
                                .systemBarsPadding(),
                            darkStatusBarIcons = lightStatus,
                            darkNavBarIcons = lightNav,
                        )
                    }
                    val showHiddenItems by viewModel.isHiddenItemsShown.observeAsState(false)
                    if (showHiddenItems) {
                        HiddenItemsSheet(onDismiss = {
                            viewModel.hideHiddenItems()
                        })
                    }
                }
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

        val dynamicIconController: DynamicIconController by inject()

        lifecycle.addObserver(dynamicIconController)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val windowController = WindowCompat.getInsetsController(window, window.decorView.rootView)
        windowController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onResume() {
        super.onResume()

        /*binding.container.doOnNextLayout {
            WallpaperManager.getInstance(this).setWallpaperOffsets(it.windowToken, 0.5f, 0.5f)
        }*/
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        onBackPressed()
    }
}