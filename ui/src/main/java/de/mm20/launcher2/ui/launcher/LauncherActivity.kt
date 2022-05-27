package de.mm20.launcher2.ui.launcher

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.android.launcher3.GestureNavContract
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.icons.DynamicIconController
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideSettings
import de.mm20.launcher2.ui.component.NavBarEffects
import de.mm20.launcher2.ui.ktx.animateTo
import de.mm20.launcher2.ui.launcher.modals.EditFavoritesView
import de.mm20.launcher2.ui.launcher.modals.HiddenItemsSheet
import de.mm20.launcher2.ui.launcher.transitions.HomeTransitionManager
import de.mm20.launcher2.ui.launcher.transitions.HomeTransitionParams
import de.mm20.launcher2.ui.launcher.transitions.LocalHomeTransitionManager
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import de.mm20.launcher2.ui.locals.LocalWindowSize
import de.mm20.launcher2.ui.theme.LauncherTheme
import org.koin.android.ext.android.inject


class LauncherActivity : BaseActivity() {

    private val viewModel: LauncherActivityVM by viewModels()

    private val homeTransitionManager = HomeTransitionManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val windowSize = Resources.getSystem().displayMetrics.let {
            Size(it.widthPixels.toFloat(), it.heightPixels.toFloat())
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        viewModel.setDarkMode(resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            CompositionLocalProvider(
                LocalHomeTransitionManager provides homeTransitionManager,
                LocalWindowSize provides windowSize,
                LocalSnackbarHostState provides snackbarHostState
            ) {
                LauncherTheme {
                    ProvideSettings {
                        val lightStatus by viewModel.lightStatusBar.observeAsState(false)
                        val lightNav by viewModel.lightNavBar.observeAsState(false)
                        val hideStatus by viewModel.hideStatusBar.observeAsState(false)
                        val hideNav by viewModel.hideNavBar.observeAsState(false)
                        val dimBackground by viewModel.dimBackground.observeAsState(false)
                        val layout by viewModel.layout.observeAsState(null)

                        val systemUiController = rememberSystemUiController()

                        val enterTransition = remember { mutableStateOf(1f) }

                        LaunchedEffect(null) {
                            homeTransitionManager
                                .currentTransition
                                .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
                                .collect {
                                    enterTransition.value = 0f
                                    enterTransition.animateTo(1f)
                                }
                        }

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
                            when (layout) {
                                Settings.AppearanceSettings.Layout.PullDown -> {
                                    PullDownScaffold(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .systemBarsPadding()
                                            .graphicsLayer {
                                                scaleX = 0.5f + enterTransition.value * 0.5f
                                                scaleY = 0.5f + enterTransition.value * 0.5f
                                                alpha = enterTransition.value
                                            },
                                        darkStatusBarIcons = lightStatus,
                                        darkNavBarIcons = lightNav,
                                    )
                                }
                                Settings.AppearanceSettings.Layout.Pager -> {
                                    PagerScaffold(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .systemBarsPadding()
                                            .graphicsLayer {
                                                scaleX = enterTransition.value
                                                scaleY = enterTransition.value
                                                alpha = enterTransition.value
                                            },
                                        darkStatusBarIcons = lightStatus,
                                        darkNavBarIcons = lightNav,
                                    )
                                }
                                else -> {}
                            }
                            SnackbarHost(
                                snackbarHostState,
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .imePadding()
                            )
                        }
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
        val navContract = intent?.let { GestureNavContract.fromIntent(it) }
        if (navContract != null) {
            homeTransitionManager.resolve(navContract)
        } else {
            onBackPressed()
        }
    }
}