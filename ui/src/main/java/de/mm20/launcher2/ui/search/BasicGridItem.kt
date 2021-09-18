package de.mm20.launcher2.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.ShapedLauncherIcon

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun BasicGridItem(
    modifier: Modifier,
    item: Searchable,
    iconSize: Dp,
    showLabel: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {

        ShapedLauncherIcon(
            item = item,
            size = iconSize,
            onClick = onClick,
            onLongClick = onLongClick
        )
        AnimatedVisibility(
            showLabel
        ) {
            GridItemLabel(
                item
            )
        }
    }
}