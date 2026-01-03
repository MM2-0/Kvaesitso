package de.mm20.launcher2.ui.component

import android.icu.text.DecimalFormat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
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
    starSize: Dp = 16.dp,
    ratingCount: Int? = null,
) {
    val starRating = round(rating * 10f).toInt()
    val fullStars = starRating / 2
    val halfStar = starRating % 2 == 1
    val emptyStars = 5 - fullStars - if (halfStar) 1 else 0
    val iconModifier = Modifier.size(starSize)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until fullStars) {
            Icon(
                painter = painterResource(R.drawable.star_20px_filled),
                contentDescription = null,
                tint = tint,
                modifier = iconModifier
            )
        }
        if (halfStar) {
            Icon(
                painter = painterResource(R.drawable.star_half_20px),
                contentDescription = null,
                tint = tint,
                modifier = iconModifier
            )
        }
        for (i in 0 until emptyStars) {
            Icon(
                painter = painterResource(R.drawable.star_20px),
                contentDescription = null,
                tint = tint,
                modifier = iconModifier
            )
        }
        Text(
            modifier = Modifier.padding(start = 4.dp),
            text = DecimalFormat("#.0").format(starRating / 2) +
                    if (ratingCount == null) "" else " ($ratingCount)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview
@Composable
fun RatingBarPreview() {
    RatingBar(0.68f, ratingCount = 263)
}