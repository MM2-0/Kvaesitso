package de.mm20.launcher2.ui.launcher.search.apps

import androidx.compose.animation.*
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.flowlayout.FlowRow
import de.mm20.launcher2.search.data.LauncherApp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.*
import de.mm20.launcher2.ui.ktx.toDp
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.customattrs.CustomizeSearchableSheet
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridIconSize
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import de.mm20.launcher2.ui.modifier.scale
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun AppItem(
    modifier: Modifier = Modifier,
    app: LauncherApp,
    onBack: () -> Unit
) {
    val viewModel = remember { AppItemVM(app) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = LocalSnackbarHostState.current

    var edit by remember { mutableStateOf(false) }

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
                Text(
                    text = app.labelOverride ?: app.label,
                    style = MaterialTheme.typography.titleMedium
                )

                val tags by remember(viewModel) { viewModel.getTags() }.collectAsState(emptyList())
                if (tags.isNotEmpty()) {
                    Text(
                        modifier = Modifier.padding(top = 1.dp, bottom = 4.dp),
                        text = tags.joinToString(separator = " #", prefix = "#"),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }


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
                    modifier = Modifier.padding(top = 1.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                FlowRow(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .animateContentSize(),
                    mainAxisSpacing = 12.dp,
                    crossAxisSpacing = 0.dp
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
                                rememberAsyncImagePainter(
                                    it
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

                    val shortcuts by viewModel.shortcuts.collectAsState(emptyList())

                    for (shortcut in shortcuts) {
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
                                    rememberAsyncImagePainter(it)
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
                badge = { badge },
                icon = { icon },
            )
        }

        val toolbarActions = mutableListOf<ToolbarAction>()

        if (LocalFavoritesEnabled.current) {
            val isPinned by viewModel.isPinned.collectAsState(false)
            val favAction = if (isPinned) {
                DefaultToolbarAction(
                    label = stringResource(R.string.menu_favorites_unpin),
                    icon = Icons.Rounded.Star,
                    action = {
                        viewModel.unpin()
                    }
                )
            } else {
                DefaultToolbarAction(
                    label = stringResource(R.string.menu_favorites_pin),
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

        toolbarActions.add(DefaultToolbarAction(
            label = stringResource(R.string.menu_customize),
            icon = Icons.Rounded.Edit,
            action = { edit = true }
        ))

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
                        label = stringResource(R.string.menu_share_store_link, storeDetails.label),
                        icon = Icons.Rounded.Link,
                        action = {
                            viewModel.shareStoreLink(context, storeDetails.url)
                        }
                    ),
                    DefaultToolbarAction(
                        label = stringResource(R.string.menu_share_apk_file),
                        icon = Icons.Rounded.Android
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
                    lifecycleOwner.lifecycleScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.msg_item_hidden, app.label),
                            actionLabel = context.getString(R.string.action_undo),
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.unhide()
                        }
                    }
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

    if (edit) {
        CustomizeSearchableSheet(
            searchable = app,
            onDismiss = { edit = false }
        )
    }
}

@Composable
fun AppItemGridPopup(
    app: LauncherApp,
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
                        1 - (1 - LocalGridIconSize.current / 84.dp) * (1 - animationProgress),
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