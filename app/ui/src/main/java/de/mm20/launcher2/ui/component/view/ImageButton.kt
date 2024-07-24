package de.mm20.launcher2.ui.component.view

import android.graphics.BlendModeColorFilter
import android.graphics.ColorMatrixColorFilter
import android.widget.ImageButton
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@Composable
internal fun ComposeImageButton(
    view: ImageButton,
    modifier: Modifier,
) {
    AsyncImage(
        modifier = modifier.alpha(view.imageAlpha / 255f),
        model = view.drawable,
        contentDescription = view.contentDescription?.toString(),
        contentScale = when (view.scaleType) {
            ImageView.ScaleType.CENTER -> ContentScale.None
            ImageView.ScaleType.FIT_XY -> ContentScale.FillBounds
            ImageView.ScaleType.FIT_START,
            ImageView.ScaleType.FIT_CENTER,
            ImageView.ScaleType.FIT_END -> ContentScale.Fit

            ImageView.ScaleType.CENTER_CROP -> ContentScale.Crop
            ImageView.ScaleType.CENTER_INSIDE -> ContentScale.Inside
            else -> ContentScale.None
        },
        alignment = when (view.scaleType) {
            ImageView.ScaleType.FIT_XY,
            ImageView.ScaleType.CENTER,
            ImageView.ScaleType.FIT_CENTER,
            ImageView.ScaleType.CENTER_CROP,
            ImageView.ScaleType.CENTER_INSIDE -> Alignment.Center

            ImageView.ScaleType.FIT_START -> Alignment.TopStart
            ImageView.ScaleType.FIT_END -> Alignment.BottomEnd
            else -> Alignment.Center
        },
    )
}
