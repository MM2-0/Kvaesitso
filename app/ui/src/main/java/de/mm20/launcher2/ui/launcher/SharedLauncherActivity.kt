package de.mm20.launcher2.ui.launcher

import android.app.WallpaperManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.SystemBarColors
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.component.NavBarEffects
import de.mm20.launcher2.ui.ktx.animateTo
import de.mm20.launcher2.ui.launcher.scaffold.ClockWidgetComponent
import de.mm20.launcher2.ui.launcher.scaffold.DismissComponent
import de.mm20.launcher2.ui.launcher.scaffold.Gesture
import de.mm20.launcher2.ui.launcher.scaffold.LaunchComponent
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffold
import de.mm20.launcher2.ui.launcher.scaffold.NotificationsComponent
import de.mm20.launcher2.ui.launcher.scaffold.PowerMenuComponent
import de.mm20.launcher2.ui.launcher.scaffold.QuickSettingsComponent
import de.mm20.launcher2.ui.launcher.scaffold.RecentsComponent
import de.mm20.launcher2.ui.launcher.scaffold.ScaffoldAnimation
import de.mm20.launcher2.ui.launcher.scaffold.ScaffoldConfiguration
import de.mm20.launcher2.ui.launcher.scaffold.ScaffoldGesture
import de.mm20.launcher2.ui.launcher.scaffold.ScreenOffComponent
import de.mm20.launcher2.ui.launcher.scaffold.SearchBarPosition
import de.mm20.launcher2.ui.launcher.scaffold.SearchComponent
import de.mm20.launcher2.ui.launcher.scaffold.SecretComponent
import de.mm20.launcher2.ui.launcher.scaffold.WidgetsComponent
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.sheets.LauncherBottomSheetManager
import de.mm20.launcher2.ui.launcher.sheets.LauncherBottomSheets
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.launcher.transitions.EnterHomeTransition
import de.mm20.launcher2.ui.launcher.transitions.EnterHomeTransitionManager
import de.mm20.launcher2.ui.launcher.transitions.LocalEnterHomeTransitionManager
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import de.mm20.launcher2.ui.locals.LocalWallpaperColors
import de.mm20.launcher2.ui.locals.LocalWindowSize
import de.mm20.launcher2.ui.overlays.OverlayHost
import de.mm20.launcher2.ui.theme.LauncherTheme
import de.mm20.launcher2.ui.theme.wallpaperColorsAsState
import kotlin.math.pow


abstract class SharedLauncherActivity(
    private val mode: LauncherActivityMode
) : BaseActivity() {

    private val viewModel: LauncherScaffoldVM by viewModels()

    internal val enterHomeTransitionManager = EnterHomeTransitionManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (isAtLeastApiLevel(29)) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        val wallpaperManager = WallpaperManager.getInstance(this)

        val windowSize = Resources.getSystem().displayMetrics.let {
            Size(it.widthPixels.toFloat(), it.heightPixels.toFloat())
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        viewModel.setSystemInDarkMode(resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)

        val bottomSheetManager = LauncherBottomSheetManager(this)

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
            ) {
                LauncherTheme {
                    ProvideCompositionLocals {
                        val statusBarColor by viewModel.statusBarColor.collectAsState()
                        val navBarColor by viewModel.navBarColor.collectAsState()

                        val chargingAnimation by viewModel.chargingAnimation.collectAsState()

                        val lightStatus =
                            !dimBackground && (statusBarColor == SystemBarColors.Dark || statusBarColor == SystemBarColors.Auto && wallpaperColors.supportsDarkText)
                        val lightNav =
                            !dimBackground && (navBarColor == SystemBarColors.Dark || navBarColor == SystemBarColors.Auto && wallpaperColors.supportsDarkText)

                        val hideStatus by viewModel.hideStatusBar.collectAsState()
                        val hideNav by viewModel.hideNavBar.collectAsState()
                        val bottomSearchBar by viewModel.bottomSearchBar.collectAsState()
                        val reverseSearchResults by viewModel.reverseSearchResults.collectAsState()
                        val fixedSearchBar by viewModel.fixedSearchBar.collectAsState()
                        val gestures by viewModel.gestureState.collectAsState()
                        val searchBarStyle by viewModel.searchBarStyle.collectAsState()

                        val fixedRotation by viewModel.fixedRotation.collectAsState()

                        val backgroundColor = MaterialTheme.colorScheme.surfaceContainer

                        if (gestures == null) return@ProvideCompositionLocals

                        LaunchedEffect(fixedRotation) {
                            requestedOrientation = if (fixedRotation) {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_USER
                            }
                        }

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

                        OverlayHost(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (dimBackground) Color.Black.copy(alpha = 0.30f) else Color.Transparent),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (chargingAnimation == true) {
                                NavBarEffects(modifier = Modifier.fillMaxSize())
                            }

                            val config = remember(
                                mode,
                                reverseSearchResults,
                                bottomSearchBar,
                                fixedSearchBar,
                                gestures,
                                searchBarStyle,
                                backgroundColor,
                                lightStatus,
                                lightNav,
                                hideStatus,
                                hideNav,
                            ) {
                                if (mode == LauncherActivityMode.Assistant) {
                                    val searchComponent = SearchComponent(
                                        reverse = reverseSearchResults,
                                    )
                                    val dismissComponent =
                                        DismissComponent(this@SharedLauncherActivity)
                                    ScaffoldConfiguration(
                                        homeComponent = searchComponent,
                                        searchComponent = searchComponent,
                                        swipeDown = ScaffoldGesture(
                                            component = dismissComponent,
                                            animation = ScaffoldAnimation.Push
                                        ),
                                        swipeUp = ScaffoldGesture(
                                            component = dismissComponent,
                                            animation = ScaffoldAnimation.Push
                                        ),
                                        fixedSearchBar = fixedSearchBar,
                                        searchBarStyle = searchBarStyle,
                                        searchBarPosition = if (bottomSearchBar) SearchBarPosition.Bottom else SearchBarPosition.Top,
                                        finishOnBack = true,
                                        drawBackgroundOnHome = true,
                                        backgroundColor = backgroundColor,
                                    )
                                } else {
                                    val searchComponent = SearchComponent(
                                        reverse = reverseSearchResults,
                                    )
                                    val widgetComponent by lazy { WidgetsComponent }

                                    fun getScaffoldGesture(
                                        action: GestureAction?,
                                        searchable: SavableSearchable?,
                                        gesture: Gesture
                                    ): ScaffoldGesture? {
                                        return when (action) {
                                            is GestureAction.Search -> ScaffoldGesture(
                                                component = searchComponent,
                                                animation = when (gesture) {
                                                    Gesture.SwipeDown -> ScaffoldAnimation.Rubberband
                                                    Gesture.LongPress -> ScaffoldAnimation.ZoomIn
                                                    Gesture.DoubleTap -> ScaffoldAnimation.ZoomIn
                                                    else -> ScaffoldAnimation.Push
                                                },
                                            )

                                            is GestureAction.Widgets -> ScaffoldGesture(
                                                component = widgetComponent,
                                                animation = if (gesture.orientation == null) ScaffoldAnimation.ZoomIn else ScaffoldAnimation.Push,
                                            )

                                            is GestureAction.Notifications -> ScaffoldGesture(
                                                component = NotificationsComponent,
                                                animation = if (gesture.orientation == null) ScaffoldAnimation.ZoomIn else ScaffoldAnimation.Push,
                                            )

                                            is GestureAction.QuickSettings -> ScaffoldGesture(
                                                component = QuickSettingsComponent,
                                                animation = if (gesture.orientation == null) ScaffoldAnimation.ZoomIn else ScaffoldAnimation.Push,
                                            )

                                            is GestureAction.Recents -> ScaffoldGesture(
                                                component = RecentsComponent,
                                                animation = if (gesture.orientation == null) ScaffoldAnimation.ZoomIn else ScaffoldAnimation.Push,
                                            )

                                            is GestureAction.PowerMenu -> ScaffoldGesture(
                                                component = PowerMenuComponent,
                                                animation = if (gesture.orientation == null) ScaffoldAnimation.ZoomIn else ScaffoldAnimation.Push,
                                            )

                                            is GestureAction.ScreenLock -> ScaffoldGesture(
                                                component = ScreenOffComponent,
                                                animation = if (gesture.orientation == null) ScaffoldAnimation.ZoomIn else ScaffoldAnimation.Push,
                                            )

                                            is GestureAction.Launch if (searchable != null) -> ScaffoldGesture(
                                                component = LaunchComponent(
                                                    this@SharedLauncherActivity,
                                                    searchable
                                                ),
                                                animation = if (gesture.orientation == null) ScaffoldAnimation.ZoomIn else ScaffoldAnimation.Push,
                                            )

                                            else -> null
                                        }
                                    }

                                    val gestures = gestures!!

                                    val config = ScaffoldConfiguration(
                                        homeComponent = ClockWidgetComponent,
                                        searchComponent = searchComponent,
                                        swipeUp = getScaffoldGesture(
                                            gestures.swipeUpAction,
                                            gestures.swipeUpApp,
                                            Gesture.SwipeUp,
                                        ),
                                        swipeDown = getScaffoldGesture(
                                            gestures.swipeDownAction,
                                            gestures.swipeDownApp,
                                            Gesture.SwipeDown,
                                        ),
                                        swipeLeft = getScaffoldGesture(
                                            gestures.swipeLeftAction,
                                            gestures.swipeLeftApp,
                                            Gesture.SwipeLeft,
                                        ),
                                        swipeRight = getScaffoldGesture(
                                            gestures.swipeRightAction,
                                            gestures.swipeRightApp,
                                            Gesture.SwipeRight,
                                        ),
                                        doubleTap = getScaffoldGesture(
                                            gestures.doubleTapAction,
                                            gestures.doubleTapApp,
                                            Gesture.DoubleTap,
                                        ),
                                        longPress = getScaffoldGesture(
                                            gestures.longPressAction,
                                            gestures.longPressApp,
                                            Gesture.LongPress,
                                        ),
                                        homeButton = getScaffoldGesture(
                                            gestures.homeButtonAction,
                                            gestures.homeButtonApp,
                                            Gesture.HomeButton,
                                        ),
                                        fixedSearchBar = fixedSearchBar,
                                        searchBarStyle = searchBarStyle,
                                        searchBarPosition = if (bottomSearchBar) SearchBarPosition.Bottom else SearchBarPosition.Top,
                                        darkStatusBarIcons = lightStatus,
                                        darkNavBarIcons = lightNav,
                                        backgroundColor = backgroundColor,
                                        showStatusBar = !hideStatus,
                                        showNavBar = !hideNav,
                                    )

                                    if (config.isUseless()) config.copy(
                                        homeComponent = SecretComponent,
                                        drawBackgroundOnHome = true
                                    ) else config
                                }
                            }

                            LauncherScaffold(
                                config = config,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX =
                                            0.5f + enterTransitionProgress.value * 0.5f
                                        scaleY =
                                            0.5f + enterTransitionProgress.value * 0.5f
                                        alpha = enterTransitionProgress.value
                                    }
                            )

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
                                        IntOffset(
                                            dX,
                                            dY
                                        )
                                    ) { enterTransitionProgress.value }
                                }
                            }
                            LauncherBottomSheets()
                        }
                    }
                }
            }
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