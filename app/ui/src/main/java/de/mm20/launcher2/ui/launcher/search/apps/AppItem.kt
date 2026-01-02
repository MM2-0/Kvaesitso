package de.mm20.launcher2.ui.launcher.search.apps

import android.app.PendingIntent
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.sendWithBackgroundPermission
import de.mm20.launcher2.search.Application
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
import kotlinx.coroutines.launch

@Composable
fun AppItem(
    modifier: Modifier = Modifier,
    app: Application,
    showDetails: Boolean,
    onBack: () -> Unit
) {
    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${app.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp
    val iconSizePixel = iconSize.toPixels().toInt()

    val badge by viewModel.badge.collectAsStateWithLifecycle(null)
    val icon by viewModel.icon.collectAsStateWithLifecycle()

    LaunchedEffect(app) {
        viewModel.init(app, iconSizePixel)
    }

    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    SharedTransitionLayout(modifier = modifier) {
        AnimatedContent(showDetails) { showDetails ->
            if (showDetails) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Row {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = app.labelOverride ?: app.label,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .sharedBounds(
                                        rememberSharedContentState("label"),
                                        this@AnimatedContent,
                                    ),
                            )

                            if (!app.isPrivate) {

                                val tags by viewModel.tags.collectAsState(emptyList())
                                if (tags.isNotEmpty()) {
                                    Text(
                                        modifier = Modifier.padding(top = 1.dp, bottom = 4.dp),
                                        text = tags.joinToString(separator = " #", prefix = "#"),
                                        color = MaterialTheme.colorScheme.secondary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }


                                app.versionName?.let {
                                    Text(
                                        text = stringResource(R.string.app_info_version, it),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = app.componentName.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 1.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    stringResource(R.string.profile_private_profile_state_locked),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp),
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }

                        }
                        ShapedLauncherIcon(
                            size = iconSize,
                            modifier = Modifier
                                .padding(16.dp),
                            badge = { badge },
                            icon = { icon },
                        )
                    }
                    val notifications by viewModel.notifications.collectAsState(emptyList())

                    AnimatedVisibility(notifications.isNotEmpty()) {
                        var showAllNotifications by remember { mutableStateOf(false) }
                        AnimatedContent(
                            showAllNotifications || notifications.size == 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    MaterialTheme.shapes.small
                                )
                                .clip(MaterialTheme.shapes.small)
                        ) { showAll ->
                            if (showAll) {
                                Column(
                                    modifier = Modifier.animateContentSize()
                                ) {
                                    for ((i, not) in notifications.withIndex()) {
                                        val icon =
                                            remember(not.smallIcon) {
                                                not.smallIcon?.loadDrawable(
                                                    context
                                                )
                                            }

                                        if (not.title == null && not.text == null) continue

                                        if (i > 0) {
                                            HorizontalDivider()
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clickable {
                                                    try {
                                                        not.contentIntent?.sendWithBackgroundPermission(
                                                            context
                                                        )
                                                    } catch (e: PendingIntent.CanceledException) {
                                                        CrashReporter.logException(e)
                                                    }
                                                }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(horizontal = 12.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(not.color))
                                                    .size(32.dp)
                                                    .padding(8.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                AsyncImage(
                                                    modifier = Modifier.fillMaxSize(),
                                                    model = icon,
                                                    contentDescription = null
                                                )
                                            }
                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (not.title != null) {
                                                    Text(
                                                        not.title!!,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                if (not.text != null) {
                                                    Text(
                                                        not.text!!,
                                                        modifier = Modifier.padding(top = 2.dp),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            if (not.isClearable) {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.clearNotification(not)
                                                    }
                                                ) {
                                                    Icon(painterResource(R.drawable.close_24px), null)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .clickable {
                                            showAllNotifications = true
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painterResource(R.drawable.notifications_24px),
                                        null,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    Text(
                                        pluralStringResource(
                                            R.plurals.app_info_notifications,
                                            notifications.size,
                                            notifications.size
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        painterResource(R.drawable.chevron_forward_24px),
                                        null,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                    }

                    val shortcuts by viewModel.children.collectAsState(emptyList())
                    if (shortcuts.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    MaterialTheme.shapes.small
                                )
                                .clip(MaterialTheme.shapes.small)
                        ) {
                            for ((i, shortcut) in shortcuts.withIndex()) {
                                val isPinned by remember(shortcut) {
                                    viewModel.isChildPinned(
                                        shortcut
                                    )
                                }.collectAsState(
                                    false
                                )

                                val iconSizePx = 32.dp.toPixels()

                                val icon by
                                remember {
                                    viewModel.getChildIcon(
                                        shortcut,
                                        iconSizePx.toInt()
                                    )
                                }.collectAsState(null)
                                if (i > 0) {
                                    HorizontalDivider()
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.launchChild(context, shortcut)
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    ShapedLauncherIcon(
                                        size = 32.dp,
                                        icon = { icon },
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp)
                                            .size(32.dp),
                                    )

                                    Text(
                                        shortcut.labelOverride ?: shortcut.label,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(
                                        onClick = {
                                            if (isPinned) {
                                                viewModel.unpinChild(shortcut)
                                            } else {
                                                viewModel.pinChild(shortcut)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            painterResource(
                                                if (isPinned) R.drawable.star_24px_filled
                                                else R.drawable.star_24px
                                            ),
                                            stringResource(if (isPinned) R.string.menu_favorites_unpin else R.string.menu_favorites_pin),
                                        )
                                    }
                                }

                            }
                        }
                    }

                    val toolbarActions = mutableListOf<ToolbarAction>()

                    if (LocalFavoritesEnabled.current) {
                        val isPinned by viewModel.isPinned.collectAsState(false)
                        val favAction = if (isPinned) {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_favorites_unpin),
                                icon = R.drawable.star_24px_filled,
                                action = {
                                    viewModel.unpin()
                                }
                            )
                        } else {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_favorites_pin),
                                icon = R.drawable.star_24px,
                                action = {
                                    viewModel.pin()
                                })
                        }
                        toolbarActions.add(favAction)
                    }

                    if (!app.isPrivate) {
                        toolbarActions.add(
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_app_info),
                                icon = R.drawable.info_24px,
                            ) {
                                app.openAppDetails(context)
                            })
                    }

                    toolbarActions.add(
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_launch),
                            icon = R.drawable.open_in_new_24px,
                            action = {
                                viewModel.launch(context)
                            }
                        )
                    )

                    val sheetManager = LocalBottomSheetManager.current
                    if (!app.isPrivate) {
                        toolbarActions.add(
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_customize),
                                icon = R.drawable.tune_24px,
                                action = { sheetManager.showCustomizeSearchableModal(app) }
                            ))
                    }

                    if (!app.isPrivate) {
                        val storeDetails = remember(app) { app.getStoreDetails(context) }
                        val shareAction = if (storeDetails == null) {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_share),
                                icon = R.drawable.share_24px,
                            ) {
                                scope.launch {
                                    app.shareApkFile(context)
                                }
                            }
                        } else {
                            SubmenuToolbarAction(
                                label = stringResource(R.string.menu_share),
                                icon = R.drawable.share_24px,
                                children = listOf(
                                    DefaultToolbarAction(
                                        label = stringResource(
                                            R.string.menu_share_store_link,
                                            storeDetails.label
                                        ),
                                        icon = R.drawable.link_24px,
                                        action = {
                                            val shareIntent = Intent(Intent.ACTION_SEND)
                                            shareIntent.putExtra(
                                                Intent.EXTRA_TEXT,
                                                storeDetails.url
                                            )
                                            shareIntent.type = "text/plain"
                                            context.startActivity(
                                                Intent.createChooser(
                                                    shareIntent,
                                                    null
                                                )
                                            )
                                        }
                                    ),
                                    DefaultToolbarAction(
                                        label = stringResource(R.string.menu_share_apk_file),
                                        icon = R.drawable.apk_document_24px,
                                    ) {
                                        scope.launch {
                                            app.shareApkFile(context)
                                        }
                                    }
                                )
                            )
                        }
                        toolbarActions.add(shareAction)
                    }

                    if (app.canUninstall) {
                        toolbarActions.add(
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_uninstall),
                                icon = R.drawable.delete_24px,
                            ) {
                                app.uninstall(context)
                                onBack()
                            }
                        )
                    }

                    Toolbar(
                        leftActions = listOf(
                            DefaultToolbarAction(
                                label = stringResource(id = R.string.menu_back),
                                icon = R.drawable.arrow_back_24px,
                            ) {
                                onBack()
                            }
                        ),
                        rightActions = toolbarActions
                    )
                }
            } else {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (LocalGridSettings.current.showListIcons) {
                        ShapedLauncherIcon(
                            size = LocalGridSettings.current.iconSize.dp,
                            modifier = Modifier
                                .padding(end = 16.dp),
                            badge = { badge },
                            icon = { icon },
                        )
                    }
                    Text(
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        text = app.labelOverride ?: app.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .sharedBounds(
                                rememberSharedContentState("label"),
                                this@AnimatedContent,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
fun AppItemGridPopup(
    app: Application,
    show: MutableTransitionState<Boolean>,
    animationProgress: Float,
    origin: IntRect,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        show,
        enter = expandIn(
            animationSpec = tween(300),
            expandFrom = Alignment.TopEnd,
        ) { origin.size },
        exit = shrinkOut(
            animationSpec = tween(300),
            shrinkTowards = Alignment.TopEnd,
        ) { origin.size },
    ) {
        AppItem(
            modifier = Modifier
                .fillMaxWidth()
                .offset(
                    x = lerp(16.dp, 0.dp, animationProgress),
                    y = lerp(-16.dp, 0.dp, animationProgress)
                ),
            app = app,
            showDetails = true,
            onBack = onDismiss
        )
    }
}
