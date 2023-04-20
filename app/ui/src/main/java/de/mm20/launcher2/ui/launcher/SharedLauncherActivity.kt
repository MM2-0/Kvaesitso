package de.mm20.launcher2.ui.launcher

import android.app.WallpaperManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.SystemBarsSettings.SystemBarColors
import de.mm20.launcher2.ui.assistant.AssistantScaffold
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCurrentTime
import de.mm20.launcher2.ui.base.ProvideSettings
import de.mm20.launcher2.ui.component.NavBarEffects
import de.mm20.launcher2.ui.gestures.GestureDetector
import de.mm20.launcher2.ui.gestures.LocalGestureDetector
import de.mm20.launcher2.ui.ktx.animateTo
import de.mm20.launcher2.ui.launcher.gestures.LauncherGestureHandler
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.sheets.LauncherBottomSheets
import de.mm20.launcher2.ui.launcher.sheets.LauncherBottomSheetManager
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.launcher.transitions.EnterHomeTransition
import de.mm20.launcher2.ui.launcher.transitions.EnterHomeTransitionManager
import de.mm20.launcher2.ui.launcher.transitions.LocalEnterHomeTransitionManager
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import de.mm20.launcher2.ui.locals.LocalWallpaperColors
import de.mm20.launcher2.ui.locals.LocalWindowSize
import de.mm20.launcher2.ui.theme.LauncherTheme
import de.mm20.launcher2.ui.theme.wallpaperColorsAsState
import kotlin.math.pow


abstract class SharedLauncherActivity(
    private val mode: LauncherActivityMode
) : BaseActivity() {

    private val viewModel: LauncherScaffoldVM by viewModels()
    private val searchVM: SearchVM by viewModels()

    internal val enterHomeTransitionManager = EnterHomeTransitionManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wallpaperManager = WallpaperManager.getInstance(this)

        val windowSize = Resources.getSystem().displayMetrics.let {
            Size(it.widthPixels.toFloat(), it.heightPixels.toFloat())
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        viewModel.setSystemInDarkMode(resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)

        val bottomSheetManager = LauncherBottomSheetManager(this)
        val gestureDetector = GestureDetector()

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            val wallpaperColors by wallpaperColorsAsState()
            val dimBackground by viewModel.dimBackground.collectAsState()
            CompositionLocalProvider(
                LocalEnterHomeTransitionManager provides enterHomeTransitionManager,
                LocalWindowSize provides windowSize,
                LocalSnackbarHostState provides snackbarHostState,
                LocalWallpaperColors provides wallpaperColors,
                LocalPreferDarkContentOverWallpaper provides (!dimBackground && wallpaperColors.supportsDarkText),
                LocalBottomSheetManager provides bottomSheetManager,
                LocalGestureDetector provides gestureDetector,
            ) {
                LauncherTheme {
                    ProvideCurrentTime {
                        ProvideSettings {
                            val statusBarColor by viewModel.statusBarColor.collectAsState()
                            val navBarColor by viewModel.navBarColor.collectAsState()

                            val lightStatus =
                                !dimBackground && (statusBarColor == SystemBarColors.Dark || statusBarColor == SystemBarColors.Auto && wallpaperColors.supportsDarkText)
                            val lightNav =
                                !dimBackground && (navBarColor == SystemBarColors.Dark || navBarColor == SystemBarColors.Auto && wallpaperColors.supportsDarkText)

                            val hideStatus by viewModel.hideStatusBar.collectAsState()
                            val hideNav by viewModel.hideNavBar.collectAsState()
                            val layout by viewModel.baseLayout.collectAsState(null)
                            val bottomSearchBar by viewModel.bottomSearchBar.collectAsState()
                            val reverseSearchResults by viewModel.reverseSearchResults.collectAsState()
                            val fixedSearchBar by viewModel.fixedSearchBar.collectAsState()

                            val fixedRotation by viewModel.fixedRotation.collectAsState()

                            LaunchedEffect(fixedRotation) {
                                requestedOrientation = if (fixedRotation) {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_USER
                                }
                            }


                            val systemUiController = rememberSystemUiController()

                            val enterTransitionProgress = remember { mutableStateOf(1f) }
                            var enterTransition by remember {
                                mutableStateOf<EnterHomeTransition?>(
                                    null
                                )
                            }

                            LaunchedEffect(null) {
                                enterHomeTransitionManager
                                    .currentTransition
                                    .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
                                    .collect {
                                        if (it != null) {
                                            enterTransitionProgress.value = 0f
                                            enterTransition = it
                                            enterTransitionProgress.animateTo(1f)
                                            enterTransition = null
                                        }
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
                                if (mode == LauncherActivityMode.Assistant) {
                                    key(bottomSearchBar, reverseSearchResults) {
                                        AssistantScaffold(
                                            modifier = Modifier
                                                .fillMaxSize(),
                                            darkStatusBarIcons = lightStatus,
                                            darkNavBarIcons = lightNav,
                                            bottomSearchBar = bottomSearchBar,
                                            reverseSearchResults = reverseSearchResults,
                                            fixedSearchBar = fixedSearchBar,
                                        )
                                    }
                                } else {
                                    when (layout) {
                                        Settings.LayoutSettings.Layout.PullDown -> {
                                            key(bottomSearchBar, reverseSearchResults) {
                                                PullDownScaffold(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer {
                                                            scaleX =
                                                                0.5f + enterTransitionProgress.value * 0.5f
                                                            scaleY =
                                                                0.5f + enterTransitionProgress.value * 0.5f
                                                            alpha = enterTransitionProgress.value
                                                        },
                                                    darkStatusBarIcons = lightStatus,
                                                    darkNavBarIcons = lightNav,
                                                    bottomSearchBar = bottomSearchBar,
                                                    reverseSearchResults = reverseSearchResults,
                                                    fixedSearchBar = fixedSearchBar,
                                                )
                                            }
                                        }

                                        Settings.LayoutSettings.Layout.Pager,
                                        Settings.LayoutSettings.Layout.PagerReversed -> {
                                            key(bottomSearchBar, reverseSearchResults) {
                                                PagerScaffold(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer {
                                                            scaleX = enterTransitionProgress.value
                                                            scaleY = enterTransitionProgress.value
                                                            alpha = enterTransitionProgress.value
                                                        },
                                                    darkStatusBarIcons = lightStatus,
                                                    darkNavBarIcons = lightNav,
                                                    reverse = layout == Settings.LayoutSettings.Layout.PagerReversed,
                                                    bottomSearchBar = bottomSearchBar,
                                                    reverseSearchResults = reverseSearchResults,
                                                    fixedSearchBar = fixedSearchBar,
                                                )
                                            }
                                        }

                                        else -> {}
                                    }
                                }
                                SnackbarHost(
                                    snackbarHostState,
                                    modifier = Modifier
                                        .navigationBarsPadding()
                                        .imePadding()
                                )
                                enterTransition?.let {
                                    if (it.startBounds == null || it.targetBounds == null) return@let
                                    val dX = it.startBounds.center.x - it.targetBounds.center.x
                                    val dY = it.startBounds.center.y - it.targetBounds.center.y
                                    val s =
                                        (it.startBounds.minDimension / it.targetBounds.minDimension - 1f) * 0.5f
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .graphicsLayer {
                                                val p = (enterTransitionProgress.value).pow(2f)
                                                transformOrigin = TransformOrigin.Center
                                                translationX = it.targetBounds.left + dX * (1 - p)
                                                translationY = it.targetBounds.top + dY * (1 - p)
                                                alpha = enterTransitionProgress.value
                                                scaleX = 1f + s * (1 - p)
                                                scaleY = 1f + s * (1 - p)
                                            }) {
                                        it.icon?.invoke(
                                            Offset(
                                                dX,
                                                dY
                                            )
                                        ) { enterTransitionProgress.value }
                                    }
                                }
                                LauncherBottomSheets()
                            }
                            LauncherGestureHandler()
                        }
                    }
                }
            }
        }
    }

    private var pauseTime = 0L
    override fun onPause() {
        super.onPause()
        pauseTime = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        if (System.currentTimeMillis() - pauseTime > 60000) {
            viewModel.closeSearch()
            searchVM.search("")
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val windowController = WindowCompat.getInsetsController(window, window.decorView.rootView)
        windowController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    enum class LauncherActivityMode {
        Launcher,
        Assistant
    }
}