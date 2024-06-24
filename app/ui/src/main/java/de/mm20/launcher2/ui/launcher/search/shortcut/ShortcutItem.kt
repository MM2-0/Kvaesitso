package de.mm20.launcher2.ui.launcher.search.shortcut


import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.animation.animateTextStyleAsState
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
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import de.mm20.launcher2.ui.modifier.scale
import kotlinx.coroutines.launch
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

    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = LocalSnackbarHostState.current

    var requestDelete by remember { mutableStateOf(false) }

    val transition = updateTransition(showDetails, label = "AppShortcutItem")

    Column(
        modifier = modifier
    ) {
        AnimatedVisibility(showDetails && shortcut.isUnavailable) {
            MissingPermissionBanner(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                text = stringResource(R.string.shortcut_unavailable_description, stringResource(R.string.app_name)),
                onClick = {
                    viewModel.requestShortcutPermission(context as AppCompatActivity)
                }
            )
        }
        Row {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                val titleStyle by animateTextStyleAsState(if (showDetails) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall)
                Text(
                    text = shortcut.labelOverride ?: shortcut.label,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                AnimatedVisibility(showDetails) {
                    val tags by viewModel.tags.collectAsState(emptyList())
                    if (tags.isNotEmpty()) {
                        Text(
                            modifier = Modifier.padding(top = 1.dp, bottom = 2.dp),
                            text = tags.joinToString(separator = " #", prefix = "#"),
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                val textSpace by transition.animateDp(label = "textSpace") {
                    if (it) 4.dp else 2.dp
                }
                shortcut.appName?.let {
                    Text(
                        text = stringResource(R.string.shortcut_summary, it),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = textSpace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            val badge by viewModel.badge.collectAsState(null)
            val size by animateDpAsState(if (showDetails) 84.dp else 48.dp)
            val icon by viewModel.icon.collectAsStateWithLifecycle()

            val padding by transition.animateDp(label = "iconPadding") {
                if (it) 16.dp else 8.dp
            }
            ShapedLauncherIcon(
                size = size,
                modifier = Modifier
                    .padding(padding),
                badge = { badge },
                icon = { icon },
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

            val packageName = shortcut.packageName
            if (packageName != null) {
                toolbarActions.add(
                    DefaultToolbarAction(
                        label = stringResource(R.string.menu_app_info),
                        icon = Icons.Rounded.Info
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
            toolbarActions.add(DefaultToolbarAction(
                label = stringResource(R.string.menu_customize),
                icon = Icons.Rounded.Tune,
                action = { sheetManager.showCustomizeSearchableModal(shortcut) }
            ))

            if (shortcut.canDelete) {
                toolbarActions.add(DefaultToolbarAction(
                    label = stringResource(R.string.menu_delete),
                    icon = Icons.Rounded.Delete,
                    action = { requestDelete = true }
                ))
            }

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
        AppShortcutItem(
            modifier = Modifier
                .fillMaxWidth()
                .scale(
                    1 - (1 - LocalGridSettings.current.iconSize / 84f) * (1 - animationProgress),
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
    }
}

