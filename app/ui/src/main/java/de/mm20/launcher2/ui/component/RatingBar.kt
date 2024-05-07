package de.mm20.launcher2.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StarHalf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.round

/**
 * A star rating bar that displays a rating from 0 to 5 stars.
 * @param rating in the range of 0..1
 */
@Composable
fun RatingBar(
    rating: Float,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    starSize: Dp = 16.dp
) {
    val starRating = round(rating * 10f).toInt()
    val fullStars = starRating / 2
    val halfStar = starRating % 2 == 1
    val emptyStars = 5 - fullStars - if (halfStar) 1 else 0
    val iconModifier = Modifier.size(starSize)
    Row(
        modifier = modifier,
    ) {
        for (i in 0 until fullStars) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = tint,
                modifier = iconModifier
            )
        }
        if (halfStar) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.StarHalf,
                contentDescription = null,
                tint = tint,
                modifier = iconModifier
            )
        }
        for (i in 0 until emptyStars) {
            Icon(
                imageVector = Icons.Rounded.StarOutline,
                contentDescription = null,
                tint = tint,
                modifier = iconModifier
            )
        }
    }
}

@Preview
@Composable
fun RatingBarPreview() {
    RatingBar(0.67f)
}