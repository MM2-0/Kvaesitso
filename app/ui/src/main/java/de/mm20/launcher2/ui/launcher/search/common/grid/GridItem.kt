package de.mm20.launcher2.ui.launcher.search.common.grid

import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.Searchable
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.search.data.Contact
import de.mm20.launcher2.search.data.File
import de.mm20.launcher2.search.data.LauncherApp
import de.mm20.launcher2.search.data.Website
import de.mm20.launcher2.search.data.Wikipedia
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.LocalIconShape
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.toDp
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.apps.AppItemGridPopup
import de.mm20.launcher2.ui.launcher.search.calendar.CalendarItemGridPopup
import de.mm20.launcher2.ui.launcher.search.contacts.ContactItemGridPopup
import de.mm20.launcher2.ui.launcher.search.files.FileItemGridPopup
import de.mm20.launcher2.ui.launcher.search.shortcut.ShortcutItemGridPopup
import de.mm20.launcher2.ui.launcher.search.website.WebsiteItemGridPopup
import de.mm20.launcher2.ui.launcher.search.wikipedia.WikipediaItemGridPopup
import de.mm20.launcher2.ui.launcher.transitions.HandleEnterHomeTransition
import de.mm20.launcher2.ui.launcher.transitions.EnterHomeTransitionParams
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.locals.LocalWindowPosition
import de.mm20.launcher2.ui.locals.LocalWindowSize
import kotlinx.coroutines.delay


@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    item: SavableSearchable,
    showLabels: Boolean = true,
    highlight: Boolean = false
) {
    val viewModel = remember(item.key) { GridItemVM(item) }

    val context = LocalContext.current

    var showPopup by remember(item.key) { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val badge by remember(item.key) { viewModel.badge }.collectAsState(null)
        val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()
        val icon by remember(item.key) { viewModel.getIcon(iconSize.toInt()) }.collectAsState(null)

        val launchOnPress = !item.preferDetailsOverLaunch

        val windowSize = LocalWindowSize.current

        if (item is LauncherApp) {
            HandleEnterHomeTransition {
                val cn = ComponentName(item.`package`, item.activity)
                if (
                    it.componentName == cn &&
                    bounds.right > 0f && bounds.left < windowSize.width &&
                    bounds.bottom > 0f && bounds.top < windowSize.height
                ) {
                    return@HandleEnterHomeTransition EnterHomeTransitionParams(
                        bounds
                    ) { _, _ ->
                        ShapedLauncherIcon(
                            size = LocalGridSettings.current.iconSize.dp,
                            icon = { icon })
                    }
                }
                return@HandleEnterHomeTransition null
            }
        }

        val hapticFeedback = LocalHapticFeedback.current
        val iconShape = LocalIconShape.current

        Box(
            modifier = if (highlight) {
                Modifier
                    .border(
                        4.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        iconShape
                    )
            } else Modifier,
        ) {
            ShapedLauncherIcon(
                modifier = Modifier
                    .padding(4.dp)
                    .onGloballyPositioned {
                        bounds = it.boundsInWindow()
                    },
                size = LocalGridSettings.current.iconSize.dp,
                badge = { badge },
                icon = { icon },
                onClick = {
                    if (!launchOnPress || !viewModel.launch(context, bounds)) {
                        showPopup = true
                    }
                },
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    showPopup = true
                }
            )
        }
        if (showLabels) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                text = item.labelOverride ?: item.label,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showPopup) {
            ItemPopup(origin = bounds, searchable = item, onDismissRequest = { showPopup = false })
        }
    }
}

@Composable
fun ItemPopup(origin: Rect, searchable: Searchable, onDismissRequest: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(null) {
        show = true
    }
    LaunchedEffect(show) {
        if (!show) {
            delay(300L)
            onDismissRequest()
        }
    }
    BackHandler {
        show = false
    }

    val animationProgress by animateFloatAsState(if (show) 1f else 0f, tween(300))
    Popup(
        properties = PopupProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        ),
        alignment = Alignment.TopCenter,
        onDismissRequest = {
            show = false
        },
        offset = IntOffset(-origin.left.toInt(), 0)
    ) {
        CompositionLocalProvider(LocalWindowPosition provides origin.top) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                LauncherCard(
                    elevation = 8.dp * animationProgress,
                    backgroundOpacity = 1f,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .absoluteOffset(
                            x = ((1 - animationProgress) * origin.left).toDp() - 16.dp * (1 - animationProgress),
                        )
                        .wrapContentSize()
                        .padding(4.dp)
                ) {
                    when (searchable) {
                        is LauncherApp -> {
                            AppItemGridPopup(
                                app = searchable,
                                show = show,
                                animationProgress = animationProgress,
                                origin = origin,
                                onDismiss = {
                                    show = false
                                }
                            )
                        }

                        is Website -> {
                            WebsiteItemGridPopup(
                                website = searchable,
                                show = show,
                                animationProgress = animationProgress,
                                origin = origin,
                                onDismiss = {
                                    show = false
                                }
                            )
                        }

                        is Wikipedia -> {
                            WikipediaItemGridPopup(
                                wikipedia = searchable,
                                show = show,
                                animationProgress = animationProgress,
                                origin = origin,
                                onDismiss = {
                                    show = false
                                }
                            )
                        }

                        is Contact -> {
                            ContactItemGridPopup(
                                contact = searchable,
                                show = show,
                                animationProgress = animationProgress,
                                origin = origin,
                                onDismiss = {
                                    show = false
                                }
                            )
                        }

                        is File -> {
                            FileItemGridPopup(
                                file = searchable,
                                show = show,
                                animationProgress = animationProgress,
                                origin = origin,
                                onDismiss = {
                                    show = false
                                }
                            )
                        }

                        is CalendarEvent -> {
                            CalendarItemGridPopup(
                                calendar = searchable,
                                show = show,
                                animationProgress = animationProgress,
                                origin = origin,
                                onDismiss = {
                                    show = false
                                }
                            )
                        }

                        is AppShortcut -> {
                            ShortcutItemGridPopup(
                                shortcut = searchable,
                                show = show,
                                animationProgress = animationProgress,
                                origin = origin,
                                onDismiss = {
                                    show = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

}