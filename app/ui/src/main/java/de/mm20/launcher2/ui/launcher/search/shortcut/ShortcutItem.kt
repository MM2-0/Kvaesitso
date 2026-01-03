package de.mm20.launcher2.ui.launcher.search.shortcut


import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.ToolbarAction
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridSettings
import kotlin.math.pow

@Composable
fun AppShortcutItem(
    modifier: Modifier = Modifier,
    shortcut: AppShortcut,
    showDetails: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${shortcut.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()

    LaunchedEffect(shortcut, iconSize) {
        viewModel.init(shortcut, iconSize.toInt())
    }

    val badge by viewModel.badge.collectAsState(null)
    val icon by viewModel.icon.collectAsStateWithLifecycle()

    var requestDelete by remember { mutableStateOf(false) }

    SharedTransitionLayout(
        modifier = modifier,
    ) {
        AnimatedContent(showDetails) { showDetails ->
            if (showDetails) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 16.dp,
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 8.dp
                            ),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                modifier = Modifier.sharedBounds(
                                    rememberSharedContentState("label"),
                                    this@AnimatedContent,
                                ),
                                text = shortcut.labelOverride ?: shortcut.label,
                                style = MaterialTheme.typography.titleMedium,
                            )

                            val children by viewModel.children.collectAsState(emptyList())

                            for (app in children) {
                                val title =
                                    app.labelOverride ?: app.label
                                val isPinned by remember(app) { viewModel.isChildPinned(app) }.collectAsState(
                                    false
                                )

                                val iconSizePx = InputChipDefaults.AvatarSize.toPixels()

                                val childIcon by
                                remember {
                                    viewModel.getChildIcon(
                                        app,
                                        iconSizePx.toInt()
                                    )
                                }.collectAsState(null)

                                InputChip(
                                    modifier = Modifier
                                        .width(IntrinsicSize.Max)
                                        .padding(top = 8.dp)
                                        .semantics { role = Role.Button },
                                    selected = false,
                                    onClick = {
                                        viewModel.launchChild(context, app)
                                    },
                                    label = {
                                        Text(
                                            title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    },
                                    avatar = {
                                        ShapedLauncherIcon(
                                            size = InputChipDefaults.AvatarSize,
                                            icon = { childIcon },
                                            shape = CircleShape,
                                        )
                                    },
                                    trailingIcon = if (LocalFavoritesEnabled.current) {
                                        {
                                            Icon(
                                                painterResource(if (isPinned) R.drawable.star_20px_filled else R.drawable.star_20px),
                                                stringResource(if (isPinned) R.string.menu_favorites_unpin else R.string.menu_favorites_pin),
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .requiredSize(InputChipDefaults.IconSize)
                                                    .clickable {

                                                        if (isPinned) {
                                                            viewModel.unpinChild(app)
                                                        } else {
                                                            viewModel.pinChild(app)
                                                        }
                                                    },
                                            )
                                        }
                                    } else null
                                )

                            }
                        }
                        ShapedLauncherIcon(
                            size = 48.dp,
                            modifier = Modifier
                                .sharedElement(
                                    rememberSharedContentState("icon"),
                                    this@AnimatedContent,
                                ),
                            badge = { badge },
                            icon = { icon },
                        )
                    }

                    if (shortcut.isUnavailable) {
                        MissingPermissionBanner(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                            text = stringResource(
                                R.string.shortcut_unavailable_description,
                                stringResource(R.string.app_name)
                            ),
                            onClick = {
                                viewModel.requestShortcutPermission(context as AppCompatActivity)
                            }
                        )
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

                    val packageName = shortcut.packageName
                    if (packageName != null) {
                        toolbarActions.add(
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_app_info),
                                icon = R.drawable.info_24px,
                            ) {
                                context.tryStartActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:$packageName")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            })
                    }


                    val sheetManager = LocalBottomSheetManager.current
                    toolbarActions.add(
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_customize),
                            icon = R.drawable.tune_24px,
                            action = { sheetManager.showCustomizeSearchableModal(shortcut) }
                        ))

                    if (shortcut.canDelete) {
                        toolbarActions.add(
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_delete),
                                icon = R.drawable.delete_24px,
                                action = { requestDelete = true }
                            ))
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            top = 8.dp,
                            bottom = 8.dp,
                            end = 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            modifier = Modifier.sharedBounds(
                                rememberSharedContentState("label"),
                                this@AnimatedContent,
                            ),
                            text = shortcut.labelOverride ?: shortcut.label,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        shortcut.appName?.let {
                            Text(
                                text = stringResource(R.string.shortcut_summary, it),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    ShapedLauncherIcon(
                        size = 48.dp,
                        modifier = Modifier
                            .padding(8.dp)
                            .sharedElement(
                                rememberSharedContentState("icon"),
                                this@AnimatedContent,
                            ),
                        badge = { badge },
                        icon = { icon },
                    )
                }
            }
        }
    }

    if (requestDelete) {
        AlertDialog(
            onDismissRequest = { requestDelete = false },
            text = { Text(stringResource(R.string.alert_delete_shortcut, shortcut.label)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(context)
                    requestDelete = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    requestDelete = false
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun ShortcutItemGridPopup(
    shortcut: AppShortcut,
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
        AppShortcutItem(
            modifier = Modifier
                .fillMaxWidth()
                .offset(
                    x = 16.dp * (1 - animationProgress).pow(10),
                    y = -16.dp * (1 - animationProgress),
                ),
            shortcut = shortcut,
            showDetails = true,
            onBack = onDismiss
        )
    }
}

