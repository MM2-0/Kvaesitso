package de.mm20.launcher2.ui.launcher.search.files

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDp
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
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
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
import de.mm20.launcher2.search.data.File
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.animation.animateTextStyleAsState
import de.mm20.launcher2.ui.component.DefaultToolbarAction
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
import java.text.DecimalFormat

@Composable
fun FileItem(
    modifier: Modifier = Modifier,
    file: File,
    showDetails: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${file.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()

    LaunchedEffect(file) {
        viewModel.init(file, iconSize.toInt())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = LocalSnackbarHostState.current

    val transition = updateTransition(showDetails, label = "ContactItem")

    Column(
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                val textStyle by animateTextStyleAsState(
                    if (showDetails) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.titleSmall
                )
                Text(
                    text = file.labelOverride ?: file.label,
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedVisibility(!showDetails) {
                    Text(
                        file.getFileType(context),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                AnimatedVisibility(showDetails) {
                    Column {
                        val tags by viewModel.tags.collectAsState(emptyList())
                        if (tags.isNotEmpty()) {
                            Text(
                                modifier = Modifier.padding(top = 1.dp, bottom = 4.dp),
                                text = tags.joinToString(separator = " #", prefix = "#"),
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.file_meta_type,
                                file.mimeType
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        for ((k, v) in file.metaData) {
                            Text(
                                text = stringResource(k, v),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.file_meta_path,
                                file.path
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!file.isDirectory) {
                            Text(
                                text = stringResource(
                                    R.string.file_meta_size,
                                    formatFileSize(file.size)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                }
            }

            val icon by viewModel.icon.collectAsStateWithLifecycle()
            val badge by viewModel.badge.collectAsState(null)
            val padding by transition.animateDp(label = "iconPadding") {
                if (it) 16.dp else 8.dp
            }
            ShapedLauncherIcon(
                size = 48.dp,
                modifier = Modifier
                    .padding(end = padding, top = padding, bottom = padding),
                icon = { icon },
                badge = { badge }
            )

        }

        AnimatedVisibility(showDetails) {
            Column {

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
                        label = stringResource(R.string.menu_open_file),
                        icon = Icons.Rounded.OpenInNew,
                        action = {
                            viewModel.launch(context)
                        }
                    )
                )

                if (file.canShare) {
                    toolbarActions.add(DefaultToolbarAction(
                        label = stringResource(R.string.menu_share),
                        icon = Icons.Rounded.Share,
                        action = {
                            file.share(context)
                        }
                    ))
                }

                if (file.isDeletable) {
                    var showConfirmDialog by remember { mutableStateOf(false) }
                    toolbarActions.add(DefaultToolbarAction(
                        label = stringResource(R.string.menu_delete),
                        icon = Icons.Rounded.Delete,
                        action = {
                            showConfirmDialog = true
                        }
                    ))
                    if (showConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showConfirmDialog = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.delete()
                                    showConfirmDialog = false
                                }) {
                                    Text(stringResource(android.R.string.ok))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showConfirmDialog = false
                                }) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                            },
                            text = {
                                Text(
                                    if (file.isDirectory) stringResource(
                                        R.string.alert_delete_directory,
                                        file.label
                                    )
                                    else stringResource(R.string.alert_delete_file, file.label)
                                )
                            }
                        )
                    }
                }

                val sheetManager = LocalBottomSheetManager.current
                toolbarActions.add(DefaultToolbarAction(
                    label = stringResource(R.string.menu_customize),
                    icon = Icons.Rounded.Edit,
                    action = { sheetManager.showCustomizeSearchableModal(file) }
                ))

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
                                    message = context.getString(
                                        R.string.msg_item_hidden,
                                        file.label
                                    ),
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
    }
}

@Composable
fun FileItemGridPopup(
    file: File,
    show: Boolean,
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
        FileItem(
            modifier = Modifier
                .fillMaxWidth()
                .scale(
                    1 - (1 - LocalGridSettings.current.iconSize / 48f) * (1 - animationProgress),
                    transformOrigin = TransformOrigin(1f, 0f)
                )
                .offset(
                    x = 16.dp * (1 - animationProgress),
                    y = -16.dp * (1 - animationProgress)
                ),
            file = file,
            showDetails = true,
            onBack = onDismiss
        )
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1000L -> "$size Bytes"
        size < 1000000L -> "${DecimalFormat("#,##0.#").format(size / 1000.0)} kB"
        size < 1000000000L -> "${DecimalFormat("#,##0.#").format(size / 1000000.0)} MB"
        size < 1000000000000L -> "${DecimalFormat("#,##0.#").format(size / 1000000000.0)} GB"
        else -> "${DecimalFormat("#,##0.#").format(size / 1000000000000.0)} TB"
    }
}