package de.mm20.launcher2.ui.settings.gestures

import android.content.res.Resources
import android.icu.text.ListFormatter
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.WidgetScreenTarget
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.SearchablePicker
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.ktx.animateShapeAsState
import de.mm20.launcher2.ui.ktx.toPixels
import kotlinx.coroutines.flow.combine
import org.koin.compose.koinInject
import kotlin.reflect.KClass

@Composable
internal fun GesturePreference(
    title: String,
    @DrawableRes icon: Int,
    value: GestureAction?,
    onValueChanged: (GestureAction, SavableSearchable?) -> Unit,
    options: Set<KClass<out GestureAction>>,
    shortcutOptions: List<SavableSearchable>,
    widgetOptions: List<WidgetPageOption>,
) {
    var showSheet by remember { mutableStateOf(false) }

    val value = if (value is GestureAction.Widgets && widgetOptions.none { it.id == value.target }) {
        GestureAction.NoAction
    } else {
        value
    }

    val iconService: IconService = koinInject()
    val iconSize = 24.dp.toPixels().toInt()

    val shortcut = remember(shortcutOptions, value) {
        if (value !is GestureAction.Launch) {
            null
        } else {
            shortcutOptions.find { value.key == it.key }
        }
    }

    val icons by remember(shortcutOptions) {
        combine(shortcutOptions.map {
            iconService.getIcon(it, iconSize)
        }) { it.toList() }
    }.collectAsStateWithLifecycle(emptyList())

    Preference(
        title = title,
        summary = getActionLabel(LocalResources.current, value, shortcutOptions),
        icon = icon,
        onClick = {
            showSheet = true
        },
    )

    DismissableBottomSheet(
        expanded = showSheet,
        onDismissRequest = { showSheet = false }
    ) {
        val contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        )
        var showShortcutPicker by remember { mutableStateOf(false) }
        AnimatedContent(
            showShortcutPicker
        ) { shortcutPicker ->
            if (shortcutPicker) {
                SearchablePicker(
                    value = shortcut,
                    onValueChanged = {
                        onValueChanged(GestureAction.Launch(it?.key), it)
                        showSheet = false
                    },
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = contentPadding,
                ) {
                    item {
                        PreferenceCategory {
                            GestureItem(
                                title = stringResource(R.string.gesture_action_none),
                                icon = R.drawable.close_24px,
                                selected = value is GestureAction.NoAction,
                                onClick = {
                                    onValueChanged(GestureAction.NoAction, null)
                                    showSheet = false
                                }
                            )
                        }
                    }
                    item {
                        PreferenceCategory(
                            title = stringResource(R.string.gesture_action_category_launcher)
                        ) {
                            if (options.contains(GestureAction.Search::class)) {
                                GestureItem(
                                    title = stringResource(R.string.gesture_action_open_search),
                                    icon = R.drawable.search_24px,
                                    selected = value is GestureAction.Search,
                                    onClick = {
                                        onValueChanged(GestureAction.Search, null)
                                        showSheet = false
                                    }
                                )
                            }
                            if (options.contains(GestureAction.Feed::class)) {
                                GestureItem(
                                    title = stringResource(R.string.gesture_action_feed),
                                    icon = R.drawable.news_24px,
                                    selected = value is GestureAction.Feed,
                                    onClick = {
                                        onValueChanged(GestureAction.Feed, null)
                                        showSheet = false
                                    }
                                )
                            }
                            if (options.contains(GestureAction.Widgets::class)) {
                                for (widget in widgetOptions) {
                                    GestureItem(
                                        title = if (widget.isNewPage) {
                                            stringResource(R.string.gesture_action_widgets_new)
                                        } else {
                                            getActionLabel(
                                                LocalResources.current,
                                                GestureAction.Widgets(widget.id),
                                                shortcutOptions
                                            )
                                        },
                                        icon = if (widget.isNewPage) R.drawable.widgets_add_24px else R.drawable.widgets_24px,
                                        selected = value is GestureAction.Widgets && value.target == widget.id,
                                        onClick = {
                                            onValueChanged(GestureAction.Widgets(widget.id), null)
                                            showSheet = false
                                        },
                                        summary = if (widget.isNewPage) {
                                            null
                                        } else if (widget.widgets.isEmpty()) {
                                            stringResource(R.string.gesture_action_widgets_empty)
                                        } else {
                                            ListFormatter.getInstance().format(
                                                widget.widgets.map { it.getLabel(LocalContext.current) }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                    item {
                        PreferenceCategory(
                            title = stringResource(R.string.gesture_action_category_system)
                        ) {
                            if (options.contains(GestureAction.Notifications::class)) {
                                GestureItem(
                                    title = stringResource(R.string.gesture_action_notifications),
                                    icon = R.drawable.notifications_24px,
                                    selected = value is GestureAction.Notifications,
                                    onClick = {
                                        onValueChanged(GestureAction.Notifications, null)
                                        showSheet = false
                                    }
                                )
                            }
                            if (options.contains(GestureAction.QuickSettings::class)) {
                                GestureItem(
                                    title = stringResource(R.string.gesture_action_quick_settings),
                                    icon = R.drawable.settings_24px,
                                    selected = value is GestureAction.QuickSettings,
                                    onClick = {
                                        onValueChanged(GestureAction.QuickSettings, null)
                                        showSheet = false
                                    }
                                )
                            }
                            if (options.contains(GestureAction.ScreenLock::class)) {
                                GestureItem(
                                    title = stringResource(R.string.gesture_action_lock_screen),
                                    icon = R.drawable.lock_24px,
                                    selected = value is GestureAction.ScreenLock,
                                    onClick = {
                                        onValueChanged(GestureAction.ScreenLock, null)
                                        showSheet = false
                                    }
                                )
                            }
                            if (options.contains(GestureAction.PowerMenu::class)) {
                                GestureItem(
                                    title = stringResource(R.string.gesture_action_power_menu),
                                    icon = R.drawable.power_settings_new_24px,
                                    selected = value is GestureAction.PowerMenu,
                                    onClick = {
                                        onValueChanged(GestureAction.PowerMenu, null)
                                        showSheet = false
                                    }
                                )
                            }
                            if (options.contains(GestureAction.Recents::class)) {
                                GestureItem(
                                    title = stringResource(R.string.gesture_action_recents),
                                    icon = R.drawable.amp_stories_24px,
                                    selected = value is GestureAction.Recents,
                                    onClick = {
                                        onValueChanged(GestureAction.Recents, null)
                                        showSheet = false
                                    }
                                )
                            }
                        }
                    }
                    item {
                        PreferenceCategory(
                            title = stringResource(R.string.gesture_action_category_apps)
                        ) {
                            for ((i, shortcut) in shortcutOptions.withIndex()) {
                                GestureItem(
                                    title = shortcut.labelOverride ?: shortcut.label,
                                    icon = R.drawable.android_24px,
                                    shortcutIcon = icons.getOrNull(i),
                                    selected = value is GestureAction.Launch && value.key == shortcut.key,
                                    onClick = {
                                        onValueChanged(GestureAction.Launch(shortcut.key), shortcut)
                                        showSheet = false
                                    }
                                )
                            }
                            GestureItem(
                                title = stringResource(R.string.gesture_action_launch_select_app),
                                icon = R.drawable.action_key_24px,
                                selected = false,
                                onClick = {
                                    showShortcutPicker = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureItem(
    title: String,
    summary: String? = null,
    @DrawableRes icon: Int,
    shortcutIcon: LauncherIcon? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val transition = updateTransition(selected)
    val backgroundColor by transition.animateColor {
        if (it) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceBright
    }
    val textColor by transition.animateColor {
        if (it) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface
    }
    val iconColor by transition.animateColor {
        if (it) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.primary
    }
    val shape by animateShapeAsState(
        if (selected) MaterialTheme.shapes.medium else MaterialTheme.shapes.extraSmall
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(
                start = 8.dp,
                end = if (selected) 8.dp else 16.dp,
            )
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .padding(end = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (shortcutIcon != null) {
                ShapedLauncherIcon(
                    size = 36.dp,
                    icon = { shortcutIcon },
                )
            } else {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = iconColor
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = title,
                color = textColor,
                style = MaterialTheme.typography.titleMedium,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.check_24px),
                    contentDescription = null,
                    tint = iconColor
                )
            }
        }
    }
}

private fun getActionLabel(
    resources: Resources,
    action: GestureAction?,
    shortcutOptions: List<SavableSearchable>
): String {
    return when (action) {
        GestureAction.Feed -> resources.getString(R.string.gesture_action_feed)
        is GestureAction.Launch -> {
            shortcutOptions.find { it.key == action.key }
                ?.let { it.labelOverride ?: it.label }
                ?: resources.getString(R.string.gesture_action_launch_app)
        }

        GestureAction.Notifications -> resources.getString(R.string.gesture_action_notifications)
        GestureAction.PowerMenu -> resources.getString(R.string.gesture_action_power_menu)
        GestureAction.QuickSettings -> resources.getString(R.string.gesture_action_quick_settings)
        GestureAction.Recents -> resources.getString(R.string.gesture_action_recents)
        GestureAction.ScreenLock -> resources.getString(R.string.gesture_action_lock_screen)
        GestureAction.Search -> resources.getString(R.string.gesture_action_open_search)
        is GestureAction.Widgets -> {
            when (action.target) {
                WidgetScreenTarget.Widgets1 -> resources.getString(R.string.gesture_action_widgets)
                WidgetScreenTarget.Widgets2 -> resources.getString(
                    R.string.gesture_action_widgets_indexed,
                    2
                )

                WidgetScreenTarget.Widgets3 -> resources.getString(
                    R.string.gesture_action_widgets_indexed,
                    3
                )

                WidgetScreenTarget.Widgets4 -> resources.getString(
                    R.string.gesture_action_widgets_indexed,
                    4
                )
            }
        }

        else -> resources.getString(R.string.gesture_action_none)
    }
}