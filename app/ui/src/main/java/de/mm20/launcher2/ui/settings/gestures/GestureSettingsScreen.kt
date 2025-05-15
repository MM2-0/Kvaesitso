package de.mm20.launcher2.ui.settings.gestures

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
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
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.BaseLayout
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.SearchablePicker
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.ktx.toPixels

@Composable
fun GestureSettingsScreen() {
    val viewModel: GestureSettingsScreenVM = viewModel()

    val layout by viewModel.baseLayout.collectAsStateWithLifecycle(null)
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle(null)

    val options = buildList {
        add(stringResource(R.string.gesture_action_none) to GestureAction.NoAction)
        add(stringResource(R.string.gesture_action_notifications) to GestureAction.Notifications)
        add(stringResource(R.string.gesture_action_quick_settings) to GestureAction.QuickSettings)
        if (isAtLeastApiLevel(28)) add(stringResource(R.string.gesture_action_lock_screen) to GestureAction.ScreenLock)
        add(stringResource(R.string.gesture_action_recents) to GestureAction.Recents)
        add(stringResource(R.string.gesture_action_power_menu) to GestureAction.PowerMenu)
        add(stringResource(R.string.gesture_action_open_search) to GestureAction.Search)
        add(stringResource(R.string.gesture_action_launch_app) to GestureAction.Launch(null))
    }

    val context = LocalContext.current
    PreferenceScreen(title = stringResource(R.string.preference_screen_gestures)) {
        item {
            PreferenceCategory {
                val baseLayout by viewModel.baseLayout.collectAsStateWithLifecycle(null)
                ListPreference(title = stringResource(R.string.preference_layout_open_search),
                    items = listOf(
                        stringResource(R.string.open_search_pull_down) to BaseLayout.PullDown,
                        stringResource(R.string.open_search_swipe_left) to BaseLayout.Pager,
                        stringResource(R.string.open_search_swipe_right) to BaseLayout.PagerReversed,
                    ),
                    value = baseLayout,
                    onValueChanged = {
                        if (it != null) viewModel.setBaseLayout(it)
                    },
                )
            }
        }
        item {
            val appIconSize = 32.dp.toPixels()
            PreferenceCategory {
                val doubleTap by viewModel.doubleTap.collectAsStateWithLifecycle(null)
                AnimatedVisibility(hasPermission == false && requiresAccessibilityService(doubleTap)) {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(16.dp),
                        text = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                        onClick = { viewModel.requestPermission(context as AppCompatActivity) }
                    )
                }
                val doubleTapApp by viewModel.doubleTapApp.collectAsState(null)
                val doubleTapAppIcon by remember(doubleTapApp?.key) {
                    viewModel.getIcon(doubleTapApp, appIconSize.toInt())
                }.collectAsState(null)
                GesturePreference(
                    title = stringResource(R.string.preference_gesture_double_tap),
                    value = doubleTap,
                    onValueChanged = { viewModel.setDoubleTap(it) },
                    isOpenSearch = false,
                    options = options,
                    app = doubleTapApp,
                    appIcon = doubleTapAppIcon,
                    onAppChanged = { viewModel.setDoubleTapApp(it) }
                )

                val longPress by viewModel.longPress.collectAsStateWithLifecycle(null)
                AnimatedVisibility(hasPermission == false && requiresAccessibilityService(longPress)) {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(16.dp),
                        text = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                        onClick = { viewModel.requestPermission(context as AppCompatActivity) }
                    )
                }
                val longPressApp by viewModel.longPressApp.collectAsState(null)
                val longPressAppIcon by remember(longPressApp?.key) {
                    viewModel.getIcon(longPressApp, appIconSize.toInt())
                }.collectAsState(null)
                GesturePreference(
                    title = stringResource(R.string.preference_gesture_long_press),
                    value = longPress,
                    onValueChanged = { viewModel.setLongPress(it) },
                    isOpenSearch = false,
                    options = options,
                    app = longPressApp,
                    appIcon = longPressAppIcon,
                    onAppChanged = { viewModel.setLongPressApp(it) }
                )

                val swipeDown by viewModel.swipeDown.collectAsStateWithLifecycle(null)
                val swipeDownIsSearch = layout == BaseLayout.PullDown
                AnimatedVisibility(hasPermission == false && requiresAccessibilityService(swipeDown) && !swipeDownIsSearch) {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(16.dp),
                        text = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                        onClick = { viewModel.requestPermission(context as AppCompatActivity) }
                    )
                }
                val swipeDownApp by viewModel.swipeDownApp.collectAsState(null)
                val swipeDownAppIcon by remember(swipeDownApp?.key) {
                    viewModel.getIcon(swipeDownApp, appIconSize.toInt())
                }.collectAsState(null)
                GesturePreference(
                    title = stringResource(R.string.preference_gesture_swipe_down),
                    value = swipeDown,
                    onValueChanged = { viewModel.setSwipeDown(it) },
                    isOpenSearch = swipeDownIsSearch,
                    options = options,
                    app = swipeDownApp,
                    appIcon = swipeDownAppIcon,
                    onAppChanged = { viewModel.setSwipeDownApp(it) }
                )

                val swipeLeft by viewModel.swipeLeft.collectAsStateWithLifecycle(null)
                val swipeLeftIsSearch = layout == BaseLayout.Pager
                AnimatedVisibility(hasPermission == false && requiresAccessibilityService(swipeLeft) && !swipeLeftIsSearch) {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(16.dp),
                        text = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                        onClick = { viewModel.requestPermission(context as AppCompatActivity) }
                    )
                }
                val swipeLeftApp by viewModel.swipeLeftApp.collectAsState(null)
                val swipeLeftAppIcon by remember(swipeLeftApp?.key) {
                    viewModel.getIcon(swipeLeftApp, appIconSize.toInt())
                }.collectAsState(null)
                GesturePreference(
                    title = stringResource(R.string.preference_gesture_swipe_left),
                    value = swipeLeft,
                    onValueChanged = { viewModel.setSwipeLeft(it) },
                    isOpenSearch = swipeLeftIsSearch,
                    options = options,
                    app = swipeLeftApp,
                    appIcon = swipeLeftAppIcon,
                    onAppChanged = { viewModel.setSwipeLeftApp(it) }
                )

                val swipeRight by viewModel.swipeRight.collectAsStateWithLifecycle(null)
                val swipeRightIsSearch = layout == BaseLayout.PagerReversed
                AnimatedVisibility(hasPermission == false && requiresAccessibilityService(swipeRight) && !swipeRightIsSearch) {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(16.dp),
                        text = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                        onClick = { viewModel.requestPermission(context as AppCompatActivity) }
                    )
                }
                val swipeRightApp by viewModel.swipeRightApp.collectAsState(null)
                val swipeRightAppIcon by remember(swipeRightApp?.key) {
                    viewModel.getIcon(swipeRightApp, appIconSize.toInt())
                }.collectAsState(null)
                GesturePreference(
                    title = stringResource(R.string.preference_gesture_swipe_right),
                    value = swipeRight,
                    onValueChanged = { viewModel.setSwipeRight(it) },
                    isOpenSearch = swipeRightIsSearch,
                    options = options,
                    app = swipeRightApp,
                    appIcon = swipeRightAppIcon,
                    onAppChanged = { viewModel.setSwipeRightApp(it) }
                )

                val homeButton by viewModel.homeButton.collectAsStateWithLifecycle(null)
                AnimatedVisibility(hasPermission == false && requiresAccessibilityService(homeButton)) {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(16.dp),
                        text = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                        onClick = { viewModel.requestPermission(context as AppCompatActivity) }
                    )
                }
                val homeButtonApp by viewModel.homeButtonApp.collectAsState(null)
                val homeButtonAppIcon by remember(homeButtonApp?.key) {
                    viewModel.getIcon(homeButtonApp, appIconSize.toInt())
                }.collectAsState(null)
                GesturePreference(
                    title = stringResource(R.string.preference_gesture_home_button),
                    value = homeButton,
                    onValueChanged = { viewModel.setHomeButton(it) },
                    isOpenSearch = false,
                    options = options,
                    app = homeButtonApp,
                    appIcon = homeButtonAppIcon,
                    onAppChanged = { viewModel.setHomeButtonApp(it) }
                )
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
    value: GestureAction?,
    onValueChanged: (GestureAction) -> Unit,
    isOpenSearch: Boolean,
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
                enabled = !isOpenSearch,
                items = options,
                value = if (isOpenSearch) GestureAction.Search else value,
                summary = options.find { value?.javaClass == it.second.javaClass }?.first,
                onValueChanged = { if (it != null) onValueChanged(it) }
            )
        }

        if (value is GestureAction.Launch && !isOpenSearch) {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .width(1.dp)
                    .alpha(0.38f)
                    .background(LocalContentColor.current)
            )
            Box(modifier = Modifier
                .clickable { showAppPicker = true }
                .padding(12.dp)) {
                ShapedLauncherIcon(size = 32.dp, icon = { appIcon })
            }
        }
    }

    if (!isOpenSearch && value is GestureAction.Launch && (showAppPicker || app == null)) {
        SearchablePicker(
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