package de.mm20.launcher2.ui.search

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ApplicationItem(
    modifier: Modifier = Modifier,
    app: Application,
    representation: Representation,
    initialRepresentation: Representation,
    onRepresentationChange: ((Representation) -> Unit)
) {

    val padding by animateDpAsState(
        if (representation == Representation.Grid) 0.dp else 16.dp
    )
    val iconSize by animateDpAsState(
        if (representation == Representation.Grid) 52.dp else 84.dp
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
        ) {

            Column(
                modifier = Modifier
                    .weight(1f, true)
            ) {
                AnimatedVisibility(
                    representation == Representation.Full,
                    enter = expandIn() + fadeIn(),
                    exit = shrinkOut() + fadeOut(),
                ) {
                    Column {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.h1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        app.version?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.body1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = app.`package`,
                            style = MaterialTheme.typography.body1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                    item = app,
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
            val storeDetails = app.getStoreDetails(LocalContext.current)
            val rightActions = listOf(
                favoritesToolbarAction(app),
                DefaultToolbarAction(
                    stringResource(id = R.string.menu_app_info),
                    Icons.Rounded.Info
                ) { },
                DefaultToolbarAction(
                    stringResource(id = R.string.menu_uninstall),
                    Icons.Rounded.Delete
                ) { },
                if (storeDetails == null) {
                    DefaultToolbarAction(
                        stringResource(id = R.string.menu_share),
                        Icons.Rounded.Share,
                        {}
                    )
                } else {
                    SubmenuToolbarAction(
                        stringResource(id = R.string.menu_share),
                        Icons.Rounded.Share,
                        listOf(
                            DefaultToolbarAction(
                                stringResource(
                                    id = R.string.share_menu_store_link,
                                    storeDetails.label
                                ),
                                Icons.Rounded.Share,
                                {}
                            ),
                            DefaultToolbarAction(
                                stringResource(id = R.string.share_menu_apk_file),
                                Icons.Rounded.Share,
                                {}
                            )
                        )
                    )
                },
                hideToolbarAction(app),
            )
            Toolbar(
                modifier = Modifier.fillMaxWidth(),
                leftActions = leftActions,
                rightActions = rightActions
            )
        }
        AnimatedVisibility(representation == Representation.Grid) {
            GridItemLabel(app)
        }
    }
}