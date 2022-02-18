package de.mm20.launcher2.ui.launcher.search.apps

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import coil.compose.rememberImagePainter
import com.google.accompanist.flowlayout.FlowRow
import de.mm20.launcher2.search.data.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.*
import de.mm20.launcher2.ui.ktx.toDp
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.modifier.scale
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppItem(
    modifier: Modifier = Modifier,
    app: Application,
    onBack: () -> Unit
) {
    val viewModel = remember { AppItemVM(app) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
    ) {
        Row {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Text(text = app.label, style = MaterialTheme.typography.titleMedium)
                app.version?.let {
                    Text(
                        text = stringResource(R.string.app_info_version, it),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = app.`package`,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                FlowRow(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .animateContentSize(),
                    mainAxisSpacing = 16.dp,
                    crossAxisSpacing = 8.dp
                ) {
                    val notifications by viewModel.notifications.collectAsState(initial = emptyList())

                    for (not in notifications) {
                        val title =
                            not.notification.extras.getString(NotificationCompat.EXTRA_TITLE, null)
                                ?.takeIf { it.isNotBlank() }
                                ?: not.notification.extras.getString(
                                    NotificationCompat.EXTRA_TEXT,
                                    null
                                )
                                    ?.takeIf { it.isNotBlank() }
                                ?: continue

                        val icon =
                            remember { not.notification.smallIcon?.loadDrawable(context) }?.let {
                                rememberImagePainter(
                                    it,
                                    builder = {
                                        crossfade(false)
                                    }
                                )
                            }

                        Chip(
                            text = title,
                            icon = icon,
                            rightIcon = Icons.Rounded.Clear,
                            rightAction = {
                                viewModel.clearNotification(not)
                            },
                            onClick = {
                                viewModel.openNotification(not)
                            }
                        )
                    }

                    for (shortcut in app.shortcuts.subList(0, min(app.shortcuts.size, 5))) {
                        val title =
                            shortcut.launcherShortcut.shortLabel
                                ?: shortcut.launcherShortcut.longLabel
                                ?: continue
                        val isPinned by remember(shortcut) { viewModel.isShortcutPinned(shortcut) }.collectAsState(
                            false
                        )

                        val icon =
                            remember {
                                viewModel.getShortcutIcon(
                                    context,
                                    shortcut.launcherShortcut
                                )
                            }
                                ?.let {
                                    rememberImagePainter(it,
                                        builder = {
                                            crossfade(false)
                                        })
                                }

                        Chip(
                            text = title.toString(),
                            icon = icon,
                            rightIcon = if (LocalFavoritesEnabled.current) {
                                if (isPinned) Icons.Rounded.Star else Icons.Rounded.StarOutline
                            } else null,
                            rightAction = {
                                if (isPinned) {
                                    viewModel.unpinShortcut(shortcut)
                                } else {
                                    viewModel.pinShortcut(shortcut)
                                }
                            },
                            onClick = {
                                viewModel.launchShortcut(context, shortcut)
                            }
                        )
                    }
                }
            }
            val badge by viewModel.badge.collectAsState(null)
            val iconSize = 84.dp.toPixels().toInt()
            val icon by remember(app) { viewModel.getIcon(iconSize) }.collectAsState(null)
            ShapedLauncherIcon(
                size = 84.dp,
                modifier = Modifier
                    .padding(16.dp),
                badge = badge,
                icon = icon,
            )
        }

        val toolbarActions = mutableListOf<ToolbarAction>()

        if (LocalFavoritesEnabled.current) {
            val isPinned by viewModel.isPinned.collectAsState(false)
            val favAction = if (isPinned) {
                DefaultToolbarAction(
                    label = stringResource(R.string.favorites_menu_unpin),
                    icon = Icons.Rounded.Star,
                    action = {
                        viewModel.unpin()
                    }
                )
            } else {
                DefaultToolbarAction(
                    label = stringResource(R.string.favorites_menu_pin),
                    icon = Icons.Rounded.StarOutline,
                    action = {
                        viewModel.pin()
                    })
            }
            toolbarActions.add(favAction)
        }

        toolbarActions.add(
            DefaultToolbarAction(
                label = stringResource(R.string.menu_app_info),
                icon = Icons.Rounded.Info
            ) {
                viewModel.openAppInfo(context)
            })

        toolbarActions.add(
            DefaultToolbarAction(
                label = stringResource(R.string.menu_launch),
                icon = Icons.Rounded.OpenInNew,
                action = {
                    viewModel.launch(context)
                }
            )
        )

        val storeDetails = remember(app) { app.getStoreDetails(context) }
        val shareAction = if (storeDetails == null) {
            DefaultToolbarAction(
                label = stringResource(R.string.menu_share),
                icon = Icons.Rounded.Share
            ) {
                scope.launch {
                    viewModel.shareApkFile(context)
                }
            }
        } else {
            SubmenuToolbarAction(
                label = stringResource(R.string.menu_share),
                icon = Icons.Rounded.Share,
                children = listOf(
                    DefaultToolbarAction(
                        label = stringResource(R.string.share_menu_store_link, storeDetails.label),
                        icon = Icons.Rounded.Share,
                        action = {
                            viewModel.shareStoreLink(context, storeDetails.url)
                        }
                    ),
                    DefaultToolbarAction(
                        label = stringResource(R.string.share_menu_apk_file),
                        icon = Icons.Rounded.Share
                    ) {
                        scope.launch {
                            viewModel.shareApkFile(context)
                        }
                    }
                )
            )
        }
        toolbarActions.add(shareAction)

        if (viewModel.canUninstall) {
            toolbarActions.add(
                DefaultToolbarAction(
                    label = stringResource(R.string.menu_uninstall),
                    icon = Icons.Rounded.Delete,
                ) {
                    viewModel.uninstall(context)
                    onBack()
                }
            )
        }

        val isHidden by viewModel.isHidden.collectAsState(false)
        val hideAction = if (isHidden) {
            DefaultToolbarAction(
                label = stringResource(R.string.menu_unhide),
                icon = Icons.Rounded.Visibility,
                action = {
                    viewModel.unhide()
                    onBack()
                }
            )
        } else {
            DefaultToolbarAction(
                label = stringResource(R.string.menu_hide),
                icon = Icons.Rounded.VisibilityOff,
                action = {
                    viewModel.hide()
                    onBack()
                })
        }

        toolbarActions.add(hideAction)

        Toolbar(
            leftActions = listOf(
                DefaultToolbarAction(
                    label = stringResource(id = R.string.menu_back),
                    icon = Icons.Rounded.ArrowBack
                ) {
                    onBack()
                }
            ),
            rightActions = toolbarActions
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppItemGridPopup(
    app: Application,
    show: Boolean,
    animationProgress: Float,
    origin: Rect,
    onDismiss: () -> Unit
) {
    AnimatedContent(
        targetState = show,
        transitionSpec = {
            slideInHorizontally(
                tween(300),
                initialOffsetX = { -it + origin.width.roundToInt() }) with
                    slideOutHorizontally(
                        tween(300),
                        targetOffsetX = { -it + origin.width.roundToInt() }) + fadeOut(snap(400)) using
                    SizeTransform { _, _ ->
                        tween(300)
                    }
        }
    ) { targetState ->
        if (targetState) {
            AppItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(
                        1 - (1 - 48.dp / 84.dp) * (1 - animationProgress),
                        transformOrigin = TransformOrigin(1f, 0f)
                    )
                    .offset(
                        x = 16.dp * (1 - animationProgress).pow(10),
                        y = -16.dp * (1 - animationProgress),
                    ),
                app = app,
                onBack = onDismiss
            )
        } else {
            Box(
                modifier = Modifier
                    .requiredWidth(origin.width.toDp())
                    .requiredHeight(origin.height.toDp())
            )
        }
    }
}