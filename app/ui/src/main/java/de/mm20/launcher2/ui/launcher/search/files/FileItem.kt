package de.mm20.launcher2.ui.launcher.search.files

import android.icu.text.DateFormat
import android.icu.util.ULocale
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.preferences.TimeFormat
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.file.FileMetadata
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.ToolbarAction
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.locals.LocalCalendarSystems
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.locals.LocalTimeFormat
import de.mm20.launcher2.ui.modifier.scale
import de.mm20.launcher2.ui.utils.formatFileSize
import java.time.ZoneId
import java.util.Date

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

    val icon by viewModel.icon.collectAsStateWithLifecycle()
    val badge by viewModel.badge.collectAsState(null)

    SharedTransitionLayout(
        modifier = modifier,
    ) {
        AnimatedContent(showDetails) { showDetails ->
            if (showDetails) {
                Column {
                    Row(
                        modifier = Modifier
                            .padding(
                                top = 16.dp,
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 8.dp
                            )
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                modifier = Modifier.sharedBounds(
                                    rememberSharedContentState("label"),
                                    this@AnimatedContent,
                                ),
                                text = file.labelOverride ?: file.label,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val tags by viewModel.tags.collectAsState(emptyList())
                            if (tags.isNotEmpty()) {
                                Text(
                                    modifier = Modifier.padding(top = 1.dp),
                                    text = tags.joinToString(separator = " #", prefix = "#"),
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(
                                modifier = Modifier.padding(top = 8.dp),
                                text = stringResource(
                                    R.string.file_meta_type,
                                    file.mimeType
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            FileMetadata(file.metaData)
                            if (file.path != null) {
                                Text(
                                    text = stringResource(
                                        R.string.file_meta_path,
                                        file.path!!
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (!file.isDirectory) {
                                Text(
                                    text = stringResource(
                                        R.string.file_meta_size,
                                        formatFileSize(LocalContext.current, file.size)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        ShapedLauncherIcon(
                            modifier = Modifier
                                .sharedElement(
                                    rememberSharedContentState("icon"),
                                    this@AnimatedContent,
                                ),
                            size = 48.dp,
                            icon = { icon },
                            badge = { badge }
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

                    toolbarActions.add(
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_open_file),
                            icon = R.drawable.open_in_new_24px,
                            action = {
                                viewModel.launch(context)
                            }
                        )
                    )

                    if (file.canShare) {
                        toolbarActions.add(
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_share),
                                icon = R.drawable.share_24px,
                                action = {
                                    file.share(context)
                                }
                            ))
                    }

                    if (file.isDeletable) {
                        var showConfirmDialog by remember { mutableStateOf(false) }
                        toolbarActions.add(
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_delete),
                                icon = R.drawable.delete_24px,
                                action = {
                                    showConfirmDialog = true
                                }
                            ))
                        if (showConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showConfirmDialog = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.delete(context)
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
                    toolbarActions.add(
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_customize),
                            icon = R.drawable.tune_24px,
                            action = { sheetManager.showCustomizeSearchableModal(file) }
                        ))

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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            modifier = Modifier.sharedBounds(
                                rememberSharedContentState("label"),
                                this@AnimatedContent,
                            ),
                            text = file.labelOverride ?: file.label,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            file.getFileType(context),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    ShapedLauncherIcon(
                        size = 48.dp,
                        modifier = Modifier
                            .padding(8.dp)
                            .sharedElement(
                                rememberSharedContentState("icon"),
                                this@AnimatedContent,
                            ),
                        icon = { icon },
                        badge = { badge }
                    )
                }
            }
        }
    }
}

@Composable
fun FileItemGridPopup(
    file: File,
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

@Composable
private fun ColumnScope.FileMetadata(metadata: FileMetadata) {
    if (metadata.title != null) {
        Text(
            text = stringResource(R.string.file_meta_title, metadata.title!!),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (metadata.artist != null) {
        Text(
            text = stringResource(R.string.file_meta_artist, metadata.artist!!),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (metadata.album != null) {
        Text(
            text = stringResource(R.string.file_meta_album, metadata.album!!),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (metadata.duration != null) {
        Text(
            text = stringResource(R.string.file_meta_duration,
                DateUtils.formatElapsedTime(metadata.duration!!.inWholeSeconds)
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (metadata.year != null) {
        Text(
            text = stringResource(
                R.string.file_meta_year, android.icu.text.DateFormat.getInstanceForSkeleton(
                    LocalCalendarSystems.current.first(),
                    "yyyy",
                    ULocale.getDefault(),
                ).format(
                    Date(
                        metadata.year!!
                            .atDay(0)
                            .atTime(0, 0)
                            .atZone(ZoneId.systemDefault())
                            .toInstant().toEpochMilli()
                    )
                )
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (metadata.date != null) {
        Text(
            text = stringResource(
                R.string.file_meta_date, DateFormat.getInstanceForSkeleton(
                    LocalCalendarSystems.current.first(),
                    if (LocalTimeFormat.current == TimeFormat.TwelveHour) "yyyy-MM-dd, hh:mm a" else "yyyy-MM-dd, HH:mm",
                    ULocale.getDefault(),
                ).format(
                    Date(metadata.date!!.toInstant().toEpochMilli())
                )
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (metadata.dimensions != null) {
        Text(
            text = stringResource(
                R.string.file_meta_dimensions,
                "${metadata.dimensions!!.width} x ${metadata.dimensions!!.height}"
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (metadata.location != null) {
        Text(
            text = stringResource(R.string.file_meta_location, metadata.location!!),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (metadata.appName != null) {
        Text(
            text = stringResource(R.string.file_meta_app_name, metadata.appName!!),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (metadata.appPackageName != null) {
        Text(
            text = stringResource(R.string.file_meta_app_pkgname, metadata.appPackageName!!),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (metadata.appVersion != null) {
        Text(
            text = stringResource(R.string.file_meta_app_version, metadata.appVersion!!),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
