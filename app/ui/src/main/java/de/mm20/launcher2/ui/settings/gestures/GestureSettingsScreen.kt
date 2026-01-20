package de.mm20.launcher2.ui.settings.gestures

import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.SearchablePicker
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.ktx.toPixels
import kotlinx.serialization.Serializable

@Serializable
data object GesturesSettingsRoute: NavKey

@Composable
fun GestureSettingsScreen() {
    val viewModel: GestureSettingsScreenVM = viewModel()

    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle(null)
    val allowWidgetGesture by viewModel.allowWidgetGesture.collectAsStateWithLifecycle(null)

    val options = buildList {
        add(stringResource(R.string.gesture_action_none) to GestureAction.NoAction)
        add(stringResource(R.string.gesture_action_notifications) to GestureAction.Notifications)
        add(stringResource(R.string.gesture_action_quick_settings) to GestureAction.QuickSettings)
        if (isAtLeastApiLevel(28)) add(stringResource(R.string.gesture_action_lock_screen) to GestureAction.ScreenLock)
        add(stringResource(R.string.gesture_action_recents) to GestureAction.Recents)
        add(stringResource(R.string.gesture_action_power_menu) to GestureAction.PowerMenu)
        add(stringResource(R.string.gesture_action_open_search) to GestureAction.Search)
        if (allowWidgetGesture == true) add(stringResource(R.string.gesture_action_widgets) to GestureAction.Widgets)
        add(stringResource(R.string.gesture_action_launch_app) to GestureAction.Launch(null))
    }

    val optionsWithFeed = options + (stringResource(R.string.gesture_action_feed) to GestureAction.Feed)


    val context = LocalContext.current
    PreferenceScreen(title = stringResource(R.string.preference_screen_gestures)) {
        item {
            val appIconSize = 32.dp.toPixels()
            PreferenceCategory {
                val swipeDown by viewModel.swipeDown.collectAsStateWithLifecycle(null)
                val swipeDownApp by viewModel.swipeDownApp.collectAsState(null)
                val swipeDownAppIcon by remember(swipeDownApp?.key) {
                    viewModel.getIcon(swipeDownApp, appIconSize.toInt())
                }.collectAsState(null)

                GuardedPreference(
                    locked = hasPermission == false && requiresAccessibilityService(swipeDown),
                    description = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                    onUnlock = { viewModel.requestPermission(context as AppCompatActivity) },
                ) {
                    GesturePreference(
                        title = stringResource(R.string.preference_gesture_swipe_down),
                        icon = R.drawable.swipe_down_alt_24px,
                        value = swipeDown,
                        onValueChanged = { viewModel.setSwipeDown(it) },
                        options = options,
                        app = swipeDownApp,
                        appIcon = swipeDownAppIcon,
                        onAppChanged = { viewModel.setSwipeDownApp(it) }
                    )
                }

                val swipeLeft by viewModel.swipeLeft.collectAsStateWithLifecycle(null)
                val swipeLeftApp by viewModel.swipeLeftApp.collectAsState(null)
                val swipeLeftAppIcon by remember(swipeLeftApp?.key) {
                    viewModel.getIcon(swipeLeftApp, appIconSize.toInt())
                }.collectAsState(null)
                GuardedPreference(
                    locked = hasPermission == false && requiresAccessibilityService(swipeLeft),
                    description = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                    onUnlock = { viewModel.requestPermission(context as AppCompatActivity) },
                ) {
                    GesturePreference(
                        title = stringResource(R.string.preference_gesture_swipe_left),
                        icon = R.drawable.swipe_left_alt_24px,
                        value = swipeLeft,
                        onValueChanged = { viewModel.setSwipeLeft(it) },
                        options = options,
                        app = swipeLeftApp,
                        appIcon = swipeLeftAppIcon,
                        onAppChanged = { viewModel.setSwipeLeftApp(it) }
                    )
                }

                val swipeRight by viewModel.swipeRight.collectAsStateWithLifecycle(null)
                val swipeRightApp by viewModel.swipeRightApp.collectAsState(null)
                val swipeRightAppIcon by remember(swipeRightApp?.key) {
                    viewModel.getIcon(swipeRightApp, appIconSize.toInt())
                }.collectAsState(null)
                GuardedPreference(
                    locked = hasPermission == false && requiresAccessibilityService(swipeRight),
                    description = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                    onUnlock = { viewModel.requestPermission(context as AppCompatActivity) },
                ) {
                    GesturePreference(
                        title = stringResource(R.string.preference_gesture_swipe_right),
                        icon = R.drawable.swipe_right_alt_24px,
                        value = swipeRight,
                        onValueChanged = { viewModel.setSwipeRight(it) },
                        options = optionsWithFeed,
                        app = swipeRightApp,
                        appIcon = swipeRightAppIcon,
                        onAppChanged = { viewModel.setSwipeRightApp(it) }
                    )
                }

                val swipeUp by viewModel.swipeUp.collectAsStateWithLifecycle(null)
                val swipeUpApp by viewModel.swipeUpApp.collectAsState(null)
                val swipeUpAppIcon by remember(swipeUpApp?.key) {
                    viewModel.getIcon(swipeUpApp, appIconSize.toInt())
                }.collectAsState(null)
                GuardedPreference(
                    locked = hasPermission == false && requiresAccessibilityService(swipeUp),
                    description = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                    onUnlock = { viewModel.requestPermission(context as AppCompatActivity) },
                ) {
                    GesturePreference(
                        title = stringResource(R.string.preference_gesture_swipe_up),
                        icon = R.drawable.swipe_up_alt_24px,
                        value = swipeUp,
                        onValueChanged = { viewModel.setSwipeUp(it) },
                        options = options,
                        app = swipeUpApp,
                        appIcon = swipeUpAppIcon,
                        onAppChanged = { viewModel.setSwipeUpApp(it) }
                    )
                }

                val doubleTap by viewModel.doubleTap.collectAsStateWithLifecycle(null)
                val doubleTapApp by viewModel.doubleTapApp.collectAsState(null)
                val doubleTapAppIcon by remember(doubleTapApp?.key) {
                    viewModel.getIcon(doubleTapApp, appIconSize.toInt())
                }.collectAsState(null)
                GuardedPreference(
                    locked = hasPermission == false && requiresAccessibilityService(doubleTap),
                    description = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                    onUnlock = { viewModel.requestPermission(context as AppCompatActivity) },
                ) {
                    GesturePreference(
                        title = stringResource(R.string.preference_gesture_double_tap),
                        icon = R.drawable.adjust_24px,
                        value = doubleTap,
                        onValueChanged = { viewModel.setDoubleTap(it) },
                        options = options,
                        app = doubleTapApp,
                        appIcon = doubleTapAppIcon,
                        onAppChanged = { viewModel.setDoubleTapApp(it) }
                    )
                }

                val longPress by viewModel.longPress.collectAsStateWithLifecycle(null)
                val longPressApp by viewModel.longPressApp.collectAsState(null)
                val longPressAppIcon by remember(longPressApp?.key) {
                    viewModel.getIcon(longPressApp, appIconSize.toInt())
                }.collectAsState(null)
                GuardedPreference(
                    locked = hasPermission == false && requiresAccessibilityService(longPress),
                    description = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                    onUnlock = { viewModel.requestPermission(context as AppCompatActivity) },
                ) {
                    GesturePreference(
                        title = stringResource(R.string.preference_gesture_long_press),
                        icon = R.drawable.circle_24px,
                        value = longPress,
                        onValueChanged = { viewModel.setLongPress(it) },
                        options = options,
                        app = longPressApp,
                        appIcon = longPressAppIcon,
                        onAppChanged = { viewModel.setLongPressApp(it) }
                    )
                }
                val homeButton by viewModel.homeButton.collectAsStateWithLifecycle(null)
                val homeButtonApp by viewModel.homeButtonApp.collectAsState(null)
                val homeButtonAppIcon by remember(homeButtonApp?.key) {
                    viewModel.getIcon(homeButtonApp, appIconSize.toInt())
                }.collectAsState(null)
                GuardedPreference(
                    locked = hasPermission == false && requiresAccessibilityService(homeButton),
                    description = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                    onUnlock = { viewModel.requestPermission(context as AppCompatActivity) },
                ) {
                    GesturePreference(
                        title = stringResource(R.string.preference_gesture_home_button),
                        icon = R.drawable.home_24px,
                        value = homeButton,
                        onValueChanged = { viewModel.setHomeButton(it) },
                        options = options,
                        app = homeButtonApp,
                        appIcon = homeButtonAppIcon,
                        onAppChanged = { viewModel.setHomeButtonApp(it) }
                    )
                }
            }
        }
    }
}

fun requiresAccessibilityService(action: GestureAction?): Boolean {
    return when (action) {
        is GestureAction.Notifications,
        is GestureAction.ScreenLock,
        is GestureAction.QuickSettings,
        is GestureAction.Recents,
        is GestureAction.PowerMenu -> true

        else -> false
    }
}

@Composable
fun GesturePreference(
    title: String,
    @DrawableRes icon: Int,
    value: GestureAction?,
    onValueChanged: (GestureAction) -> Unit,
    options: List<Pair<String, GestureAction>>,
    app: SavableSearchable?,
    appIcon: LauncherIcon?,
    onAppChanged: (SavableSearchable?) -> Unit,
) {
    var showAppPicker by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = (Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier.weight(1f)
        ) {
            ListPreference(
                title = title,
                icon = icon,
                items = options,
                value = value,
                summary = options.find { value?.javaClass == it.second.javaClass }?.first
                    ?: stringResource(R.string.gesture_action_none),
                onValueChanged = { if (it != null) onValueChanged(it) }
            )
        }

        if (value is GestureAction.Launch) {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .width(1.dp)
                    .alpha(0.38f)
                    .background(LocalContentColor.current)
            )
            Box(
                modifier = Modifier
                    .clickable { showAppPicker = true }
                    .padding(12.dp)) {
                ShapedLauncherIcon(size = 32.dp, icon = { appIcon })
            }
        }
    }

    if (value is GestureAction.Launch) {
        SearchablePicker(
            expanded = (showAppPicker || app == null),
            onDismissRequest = {
                showAppPicker = false
                if (app == null) onValueChanged(GestureAction.NoAction)
            },
            value = app,
            onValueChanged = {
                showAppPicker = false
                onAppChanged(it)
            }
        )
    }

}