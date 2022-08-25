package de.mm20.launcher2.ui.launcher.search.shortcut


import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.animation.animateTextStyleAsState
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.ToolbarAction
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
fun AppShortcutItem(
    modifier: Modifier = Modifier,
    shortcut: AppShortcut,
    showDetails: Boolean = false,
    onBack: () -> Unit
) {
    val viewModel = remember { ShortcutItemVM(shortcut) }
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = LocalSnackbarHostState.current

    var requestDelete by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }

    val transition = updateTransition(showDetails, label = "AppShortcutItem")

    Column(
        modifier = modifier
    ) {
        Row {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                val titleStyle by animateTextStyleAsState(if (showDetails) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall)
                Text(
                    text = shortcut.label,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val textSpace by transition.animateDp(label = "textSpace") {
                    if (it) 4.dp else 2.dp
                }
                Text(
                    text = stringResource(R.string.shortcut_summary, shortcut.appName),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = textSpace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val badge by viewModel.badge.collectAsState(null)
            val size by animateDpAsState(if (showDetails) 84.dp else 48.dp)
            val iconSize = 84.dp.toPixels().toInt()
            val icon by remember(shortcut.key) { viewModel.getIcon(iconSize) }.collectAsState(null)

            val padding by transition.animateDp(label = "iconPadding") {
                if (it) 16.dp else 8.dp
            }
            ShapedLauncherIcon(
                size = size,
                modifier = Modifier
                    .padding(padding),
                badge = badge,
                icon = icon,
            )
        }


        AnimatedVisibility(showDetails) {

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



            toolbarActions.add(DefaultToolbarAction(
                label = stringResource(R.string.menu_customize),
                icon = Icons.Rounded.Edit,
                action = { edit = true }
            ))

            if (viewModel.canDelete) {
                toolbarActions.add(DefaultToolbarAction(
                    label = stringResource(R.string.menu_delete),
                    icon = Icons.Rounded.Delete,
                    action = { requestDelete = true }
                ))
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
                                message = context.getString(R.string.msg_item_hidden, shortcut.label),
                                actionLabel = context.getString(R.string.action_undo),
                                duration = SnackbarDuration.Short,
                                )
                            if(result == SnackbarResult.ActionPerformed) {
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

    if (requestDelete) {
        AlertDialog(
            onDismissRequest = { requestDelete = false },
            text = { Text(stringResource(R.string.alert_delete_shortcut, shortcut.label)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteShortcut()
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

    if (edit) {
        CustomizeSearchableSheet(
            searchable = shortcut,
            onDismiss = { edit = false }
        )
    }
}

@Composable
fun ShortcutItemGridPopup(
    shortcut: AppShortcut,
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
            AppShortcutItem(
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
                shortcut = shortcut,
                showDetails = true,
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