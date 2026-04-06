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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.FeatureFlags
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.WidgetScreenTarget
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
data object GesturesSettingsRoute : NavKey

@Composable
fun GestureSettingsScreen() {
    val viewModel: GestureSettingsScreenVM = viewModel()

    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle(null)

    val options = buildSet {
        add(GestureAction.NoAction::class)
        add(GestureAction.Notifications::class)
        add(GestureAction.QuickSettings::class)
        if (isAtLeastApiLevel(28)) add(GestureAction.ScreenLock::class)
        add(GestureAction.Recents::class)
        add(GestureAction.PowerMenu::class)
        add(GestureAction.Search::class)
        add(GestureAction.Widgets::class)
        add(GestureAction.Launch::class)
    }

    val shortcutOptions by viewModel.shortcutOptions.collectAsStateWithLifecycle(emptyList())
    val widgetOptions by viewModel.widgetOptions.collectAsStateWithLifecycle(emptyList())

    val optionsWithFeed =
        if (FeatureFlags.feed) {
            options + GestureAction.Feed::class
        } else options


    val context = LocalContext.current
    PreferenceScreen(title = stringResource(R.string.preference_screen_gestures)) {
        item {
            PreferenceCategory {
                val swipeDown by viewModel.swipeDown.collectAsStateWithLifecycle(null)
                GuardedPreference(
                    locked = hasPermission == false && requiresAccessibilityService(swipeDown),
                    description = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                    onUnlock = { viewModel.requestPermission(context as AppCompatActivity) },
                ) {
                    GesturePreference(
                        title = stringResource(R.string.preference_gesture_swipe_down),
                        icon = R.drawable.swipe_down_alt_24px,
                        value = swipeDown,
                        onValueChanged = viewModel::setSwipeDown,
                        options = options,
                        shortcutOptions = shortcutOptions,
                        widgetOptions = widgetOptions,
                    )
                }

                val swipeLeft by viewModel.swipeLeft.collectAsStateWithLifecycle(null)
                GuardedPreference(
                    locked = hasPermission == false && requiresAccessibilityService(swipeLeft),
                    description = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                    onUnlock = { viewModel.requestPermission(context as AppCompatActivity) },
                ) {
                    GesturePreference(
                        title = stringResource(R.string.preference_gesture_swipe_left),
                        icon = R.drawable.swipe_left_alt_24px,
                        value = swipeLeft,
                        onValueChanged = viewModel::setSwipeLeft,
                        options = options,
                        shortcutOptions = shortcutOptions,
                        widgetOptions = widgetOptions,
                    )
                }

                val swipeRight by viewModel.swipeRight.collectAsStateWithLifecycle(null)
                GuardedPreference(
                    locked = hasPermission == false && requiresAccessibilityService(swipeRight),
                    description = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                    onUnlock = { viewModel.requestPermission(context as AppCompatActivity) },
                ) {
                    GesturePreference(
                        title = stringResource(R.string.preference_gesture_swipe_right),
                        icon = R.drawable.swipe_right_alt_24px,
                        value = swipeRight,
                        onValueChanged = viewModel::setSwipeRight,
                        options = optionsWithFeed,
                        shortcutOptions = shortcutOptions,
                        widgetOptions = widgetOptions,
                    )
                }

                val swipeUp by viewModel.swipeUp.collectAsStateWithLifecycle(null)
                GuardedPreference(
                    locked = hasPermission == false && requiresAccessibilityService(swipeUp),
                    description = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                    onUnlock = { viewModel.requestPermission(context as AppCompatActivity) },
                ) {
                    GesturePreference(
                        title = stringResource(R.string.preference_gesture_swipe_up),
                        icon = R.drawable.swipe_up_alt_24px,
                        value = swipeUp,
                        onValueChanged = viewModel::setSwipeUp,
                        options = options,
                        shortcutOptions = shortcutOptions,
                        widgetOptions = widgetOptions,
                    )
                }

                val doubleTap by viewModel.doubleTap.collectAsStateWithLifecycle(null)
                GuardedPreference(
                    locked = hasPermission == false && requiresAccessibilityService(doubleTap),
                    description = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                    onUnlock = { viewModel.requestPermission(context as AppCompatActivity) },
                ) {
                    GesturePreference(
                        title = stringResource(R.string.preference_gesture_double_tap),
                        icon = R.drawable.adjust_24px,
                        value = doubleTap,
                        onValueChanged = viewModel::setDoubleTap,
                        options = options,
                        shortcutOptions = shortcutOptions,
                        widgetOptions = widgetOptions,
                    )
                }

                val longPress by viewModel.longPress.collectAsStateWithLifecycle(null)
                GuardedPreference(
                    locked = hasPermission == false && requiresAccessibilityService(longPress),
                    description = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                    onUnlock = { viewModel.requestPermission(context as AppCompatActivity) },
                ) {
                    GesturePreference(
                        title = stringResource(R.string.preference_gesture_long_press),
                        icon = R.drawable.circle_24px,
                        value = longPress,
                        onValueChanged = viewModel::setLongPress,
                        options = options,
                        shortcutOptions = shortcutOptions,
                        widgetOptions = widgetOptions,
                    )
                }
                val homeButton by viewModel.homeButton.collectAsStateWithLifecycle(null)
                GuardedPreference(
                    locked = hasPermission == false && requiresAccessibilityService(homeButton),
                    description = stringResource(R.string.missing_permission_accessibility_gesture_settings),
                    onUnlock = { viewModel.requestPermission(context as AppCompatActivity) },
                ) {
                    GesturePreference(
                        title = stringResource(R.string.preference_gesture_home_button),
                        icon = R.drawable.home_24px,
                        value = homeButton,
                        onValueChanged = viewModel::setHomeButton,
                        options = options,
                        shortcutOptions = shortcutOptions,
                        widgetOptions = widgetOptions,
                    )
                }
            }
        }
    }
}

fun requiresAccessibilityService(action: GestureAction?): Boolean {
    return when (action) {
        is GestureAction.ScreenLock,
        is GestureAction.Recents,
        is GestureAction.PowerMenu,
            -> true

        else -> false
    }
}

//@Composable
//fun GesturePreference(
//    title: String,
//    @DrawableRes icon: Int,
//    value: GestureAction?,
//    onValueChanged: (GestureAction) -> Unit,
//    options: List<Pair<String, GestureAction>>,
//    app: SavableSearchable?,
//    appIcon: LauncherIcon?,
//    onAppChanged: (SavableSearchable?) -> Unit,
//) {
//    var showAppPicker by remember { mutableStateOf(false) }
//    Row(
//        verticalAlignment = (Alignment.CenterVertically),
//    ) {
//        Box(
//            modifier = Modifier.weight(1f),
//        ) {
//            ListPreference(
//                title = title,
//                icon = icon,
//                items = options,
//                value = value,
//                summary = options.find { option ->
//                    when {
//                        value is GestureAction.Widgets && option.second is GestureAction.Widgets -> {
//                            val valueTarget = value.target
//                            val optionTarget = (option.second as GestureAction.Widgets).target
//                            valueTarget == optionTarget
//                        }
//
//                        else -> value?.javaClass == option.second.javaClass
//                    }
//                }?.first ?: stringResource(R.string.gesture_action_none),
//                onValueChanged = { if (it != null) onValueChanged(it) },
//            )
//        }
//
//        if (value is GestureAction.Launch) {
//            Box(
//                modifier = Modifier
//                    .height(36.dp)
//                    .width(1.dp)
//                    .background(MaterialTheme.colorScheme.outlineVariant),
//            )
//            Box(
//                modifier = Modifier
//                    .clickable { showAppPicker = true }
//                    .padding(12.dp),
//            ) {
//                ShapedLauncherIcon(size = 32.dp, icon = { appIcon })
//            }
//        }
//        if (value is GestureAction.Widgets) {
//            Box(
//                modifier = Modifier
//                    .height(36.dp)
//                    .width(1.dp)
//                    .background(MaterialTheme.colorScheme.outlineVariant),
//            )
//            IconButton(
//                modifier = Modifier.padding(4.dp),
//                onClick = {}
//            ) {
//                Icon(painterResource(R.drawable.tune_24px), "")
//            }
//        }
//    }
//
//    if (value is GestureAction.Launch) {
//        SearchablePicker(
//            expanded = (showAppPicker || app == null),
//            onDismissRequest = {
//                showAppPicker = false
//                if (app == null) onValueChanged(GestureAction.NoAction)
//            },
//            value = app,
//            onValueChanged = {
//                showAppPicker = false
//                onAppChanged(it)
//            },
//        )
//    }
//
//}
