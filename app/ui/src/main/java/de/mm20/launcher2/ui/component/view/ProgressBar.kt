package de.mm20.launcher2.ui.component.view

import android.widget.ProgressBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal fun ComposeProgressBar(
    view: ProgressBar,
    modifier: Modifier,
) {
    CircularProgressIndicator(
        color = view.progressTintList?.defaultColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}