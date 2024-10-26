package de.mm20.launcher2.ui.component.view

import android.widget.ProgressBar
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal fun ComposeProgressBar(
    view: ProgressBar,
    modifier: Modifier,
) {
    CircularProgressIndicator(
        color = view.progressTintList?.defaultColor?.let { Color(it) } ?: MaterialTheme.colors.primary,
        modifier = modifier,
    )
}