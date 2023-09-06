package de.mm20.launcher2.ui.launcher.search.apps

import android.app.PendingIntent
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.roundToIntRect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.google.accompanist.flowlayout.FlowRow
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.search.data.LauncherApp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.SubmenuToolbarAction
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.ToolbarAction
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import de.mm20.launcher2.ui.modifier.scale
import kotlinx.coroutines.launch
import kotlin.math.pow

@Composable
fun AppItem(
    modifier: Modifier = Modifier,
    app: LauncherApp,
    onBack: () -> Unit
) {
    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${app.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()

    LaunchedEffect(app) {
        viewModel.init(app, iconSize.toInt())
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = LocalSnackbarHostState.current

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

                val tags by viewModel.tags.collectAsState(emptyList())
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
                    val notifications by viewModel.notifications.collectAsState(emptyList())

                    for (not in notifications) {
                        val title = not.title?.takeIf { it.isNotBlank() }
                            ?: not.text?.takeIf { it.isNotBlank() }
                            ?: continue

                        val icon = remember(not.smallIcon) { not.smallIcon?.loadDrawable(context) }

                        InputChip(
                            modifier = Modifier.width(IntrinsicSize.Max),
                            selected = false,
                            label = {
                                Text(
                                    title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            },
                            avatar = {
                                Box(modifier = Modifier.background(Color(not.color))) {
                                    AsyncImage(
                                        modifier = Modifier
                                            .requiredSize(InputChipDefaults.AvatarSize)
                                            .padding(3.dp),
                                        model = icon,
                                        contentDescription = null
                                    )
                                }
                            },
                            trailingIcon = if (not.isClearable) {
                                {
                                    Icon(
                                        Icons.Rounded.Clear,
                                        null,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .size(InputChipDefaults.IconSize)
                                            .clickable {
                                                viewModel.clearNotification(not)
                                            },
                                    )
                                }
                            } else null,
                            onClick = {
                                try {
                                    not.contentIntent?.send()
                                } catch (e: PendingIntent.CanceledException) {
                                    CrashReporter.logException(e)
                                }
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

                        InputChip(
                            modifier = Modifier.width(IntrinsicSize.Max),
                            selected = false,
                            onClick = {
                                viewModel.launchShortcut(context, shortcut)
                            },
                            label = {
                                Text(
                                    title.toString(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            },
                            avatar = {
                                AsyncImage(
                                    model = icon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .size(InputChipDefaults.AvatarSize),
                                )
                            },
                            trailingIcon = if (LocalFavoritesEnabled.current) {
                                {
                                    Icon(
                                        if (isPinned) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                        null,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .requiredSize(InputChipDefaults.IconSize)
                                            .clickable {

                                                if (isPinned) {
                                                    viewModel.unpinShortcut(shortcut)
                                                } else {
                                                    viewModel.pinShortcut(shortcut)
                                                }
                                            },
                                    )
                                }
                            } else null
                        )

                    }
                }
            }
            val badge by viewModel.badge.collectAsStateWithLifecycle(null)
            val icon by viewModel.icon.collectAsStateWithLifecycle()
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
                app.openAppInfo(context)
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

        val sheetManager = LocalBottomSheetManager.current
        toolbarActions.add(DefaultToolbarAction(
            label = stringResource(R.string.menu_customize),
            icon = Icons.Rounded.Edit,
            action = { sheetManager.showCustomizeSearchableModal(app) }
        ))

        val storeDetails = remember(app) { app.getStoreDetails(context) }
        val shareAction = if (storeDetails == null) {
            DefaultToolbarAction(
                label = stringResource(R.string.menu_share),
                icon = Icons.Rounded.Share
            ) {
                scope.launch {
                    app.shareApkFile(context)
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
                            val shareIntent = Intent(Intent.ACTION_SEND)
                            shareIntent.putExtra(Intent.EXTRA_TEXT, storeDetails.url)
                            shareIntent.type = "text/plain"
                            context.startActivity(Intent.createChooser(shareIntent, null))
                        }
                    ),
                    DefaultToolbarAction(
                        label = stringResource(R.string.menu_share_apk_file),
                        icon = Icons.Rounded.Android
                    ) {
                        scope.launch {
                            app.shareApkFile(context)
                        }
                    }
                )
            )
        }
        toolbarActions.add(shareAction)

        if (app.canUninstall) {
            toolbarActions.add(
                DefaultToolbarAction(
                    label = stringResource(R.string.menu_uninstall),
                    icon = Icons.Rounded.Delete,
                ) {
                    app.uninstall(context)
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
}

@Composable
fun AppItemGridPopup(
    app: LauncherApp,
    show: MutableTransitionState<Boolean>,
    animationProgress: Float,
    origin: Rect,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        show,
        enter = expandIn(
            animationSpec = tween(300),
            expandFrom = Alignment.TopEnd,
        ) { origin.roundToIntRect().size },
        exit = shrinkOut(
            animationSpec = tween(300),
            shrinkTowards = Alignment.TopEnd,
        ) { origin.roundToIntRect().size },
    ) {
        AppItem(
            modifier = Modifier
                .fillMaxWidth()
                .scale(
                    1 - (1 - LocalGridSettings.current.iconSize / 84f) * (1 - animationProgress),
                    transformOrigin = TransformOrigin(1f, 0f)
                )
                .offset(
                    x = lerp(16.dp, 0.dp,  animationProgress),
                    y = lerp(-16.dp, 0.dp, animationProgress)
                ),
            app = app,
            onBack = onDismiss
        )
    }
}
