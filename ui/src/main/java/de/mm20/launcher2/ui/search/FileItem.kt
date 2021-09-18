package de.mm20.launcher2.ui.search

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.File
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.favoritesToolbarAction
import de.mm20.launcher2.ui.component.hideToolbarAction

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun FileItem(
    modifier: Modifier = Modifier,
    file: File,
    representation: Representation,
    initialRepresentation: Representation,
    onRepresentationChange: ((Representation) -> Unit)
) {

    val iconSize = 52.dp

    val padding by animateDpAsState(
        if (representation == Representation.Grid) 0.dp else 16.dp
    )


    Column(
        modifier = Modifier
            .combinedClickable(
                enabled = representation == Representation.List,
                onClick = {},
                onLongClick = {
                    onRepresentationChange(Representation.Full)
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, true)
                    .padding(end = 8.dp)
            ) {
                AnimatedVisibility(
                    representation != Representation.Grid
                ) {
                    Column {
                        Text(
                            text = file.label,
                            style = MaterialTheme.typography.h2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                AnimatedVisibility(
                    representation == Representation.List
                ) {

                    Text(
                        text = file.getFileType(LocalContext.current),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AnimatedVisibility(representation == Representation.Full) {
                    Column {
                        Text(
                            text = "${stringResource(R.string.file_meta_type)}: ${file.mimeType}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (file.path.isNotBlank()) {
                            Text(
                                text = "${stringResource(R.string.file_meta_path)}: ${file.path}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!file.isDirectory) {
                            Text(
                                text = "${stringResource(R.string.file_meta_size)}: ${
                                    Formatter.formatShortFileSize(
                                        LocalContext.current, file.size
                                    )
                                }",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        for ((k, v) in file.metaData) {
                            Text(
                                text = "${stringResource(k)}: ${v}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            val width by animateDpAsState(
                if (representation == Representation.Grid) LocalGridColumnWidth.current else iconSize,
                spring(Spring.StiffnessHigh)
            )
            Column(
                modifier = Modifier
                    .widthIn(max = width)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ShapedLauncherIcon(
                    item = file,
                    size = iconSize,
                    onLongClick = {
                        onRepresentationChange(Representation.Full)
                    }
                )
            }
        }
        AnimatedVisibility(representation == Representation.Full) {
            val leftActions = listOf(
                DefaultToolbarAction(
                    stringResource(id = R.string.menu_back),
                    Icons.Rounded.ArrowBack
                ) { onRepresentationChange(initialRepresentation) }
            )
            val rightActions = listOf(
                favoritesToolbarAction(file),
                DefaultToolbarAction(
                    stringResource(id = R.string.menu_delete),
                    Icons.Rounded.Delete
                ) { },
                hideToolbarAction(file),
                DefaultToolbarAction(
                    stringResource(id = R.string.menu_share),
                    Icons.Rounded.Share
                ) {}
            )
            Toolbar(
                modifier = Modifier.fillMaxWidth(),
                leftActions = leftActions,
                rightActions = rightActions
            )
        }
        AnimatedVisibility(representation == Representation.Grid) {
            GridItemLabel(file)
        }
    }
}
