package de.mm20.launcher2.ui.settings.gestures

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.Settings.GestureSettings.GestureAction
import de.mm20.launcher2.preferences.Settings.LayoutSettings.Layout
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen

@Composable
fun GestureSettingsScreen() {
    val viewModel: GestureSettingsScreenVM = viewModel()

    val layout by viewModel.layout.observeAsState()
    val hasPermission by viewModel.hasPermission.observeAsState()

    val options = buildList {
        add(stringResource(R.string.gesture_action_none) to GestureAction.None)
        add(stringResource(R.string.gesture_action_notifications) to GestureAction.OpenNotificationDrawer)
        add(stringResource(R.string.gesture_action_quick_settings) to GestureAction.OpenQuickSettings)
        if (isAtLeastApiLevel(28)) add(stringResource(R.string.gesture_action_lock_screen) to GestureAction.LockScreen)
        add(stringResource(R.string.gesture_action_recents) to GestureAction.OpenRecents)
        add(stringResource(R.string.gesture_action_power_menu) to GestureAction.OpenPowerDialog)
        add(stringResource(R.string.gesture_action_open_search) to GestureAction.OpenSearch)
    }

    val context = LocalContext.current
    PreferenceScreen(title = stringResource(R.string.preference_screen_gestures)) {
        item {
            PreferenceCategory {
                val doubleTap by viewModel.doubleTap.observeAsState()
                AnimatedVisibility(hasPermission == false && requiresAccessibilityService(doubleTap)) {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(16.dp),
                        text = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                        onClick = { viewModel.requestPermission(context as AppCompatActivity) }
                    )
                }
                ListPreference(
                    title = stringResource(R.string.preference_gesture_double_tap),
                    items = options,
                    value = doubleTap,
                    onValueChanged = { if (it != null) viewModel.setDoubleTap(it) }
                )
                val longPress by viewModel.longPress.observeAsState()
                AnimatedVisibility(hasPermission == false && requiresAccessibilityService(longPress)) {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(16.dp),
                        text = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                        onClick = { viewModel.requestPermission(context as AppCompatActivity) }
                    )
                }
                ListPreference(
                    title = stringResource(R.string.preference_gesture_long_press),
                    items = options,
                    value = longPress,
                    onValueChanged = { if (it != null) viewModel.setLongPress(it) }
                )
                val swipeDown by viewModel.swipeDown.observeAsState()
                AnimatedVisibility(hasPermission == false && requiresAccessibilityService(swipeDown)) {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(16.dp),
                        text = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                        onClick = { viewModel.requestPermission(context as AppCompatActivity) }
                    )
                }
                ListPreference(
                    title = stringResource(R.string.preference_gesture_swipe_down),
                    enabled = layout != Layout.PullDown,
                    items = options,
                    value = if (layout == Layout.PullDown) GestureAction.OpenSearch else swipeDown,
                    onValueChanged = { if (it != null) viewModel.setSwipeDown(it) }
                )
                val swipeLeft by viewModel.swipeLeft.observeAsState()
                AnimatedVisibility(hasPermission == false && requiresAccessibilityService(swipeLeft)) {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(16.dp),
                        text = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                        onClick = { viewModel.requestPermission(context as AppCompatActivity) }
                    )
                }
                ListPreference(
                    title = stringResource(R.string.preference_gesture_swipe_left),
                    enabled = layout != Layout.Pager,
                    items = options,
                    value = if (layout == Layout.Pager) GestureAction.OpenSearch else swipeLeft,
                    onValueChanged = { if (it != null) viewModel.setSwipeLeft(it) }
                )
                val swipeRight by viewModel.swipeRight.observeAsState()
                AnimatedVisibility(hasPermission == false && requiresAccessibilityService(swipeRight)) {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(16.dp),
                        text = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                        onClick = { viewModel.requestPermission(context as AppCompatActivity) }
                    )
                }
                ListPreference(
                    title = stringResource(R.string.preference_gesture_swipe_right),
                    enabled = layout != Layout.PagerReversed,
                    items = options,
                    value = if (layout == Layout.PagerReversed) GestureAction.OpenSearch else swipeRight,
                    onValueChanged = { if (it != null) viewModel.setSwipeRight(it) }
                )
            }
        }
    }
}

fun requiresAccessibilityService(action: GestureAction?) : Boolean{
    return when(action) {
        GestureAction.OpenNotificationDrawer,
        GestureAction.LockScreen,
        GestureAction.OpenQuickSettings,
        GestureAction.OpenRecents,
        GestureAction.OpenPowerDialog -> true
        else -> false
    }
}