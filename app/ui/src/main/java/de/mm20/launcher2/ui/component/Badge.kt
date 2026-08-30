package de.mm20.launcher2.ui.component

import android.icu.text.NumberFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeIcon
import kotlin.math.roundToInt

@Composable
fun Badge(
    badge: Badge,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    Surface(
        tonalElevation = 1.dp,
        modifier = modifier.size(size),
        color = MaterialTheme.colorScheme.secondary,
        shape = CircleShape
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {

            badge.progress?.let {
                val progress by animateFloatAsState(it)
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(0.8f),
                    progress = { progress },
                    strokeWidth = size / 16,
                    color = MaterialTheme.colorScheme.onSecondary
                )
            }
            val badgeIcon = badge.icon

            val number = badge.number
            if (badgeIcon is BadgeIcon.Vector) {
                Icon(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(size / 8),
                    painter = painterResource(badgeIcon.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary,
                )
            } else if (badgeIcon is BadgeIcon.Drawable) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(size / 16)
                ) {
                    badgeIcon.drawable.setBounds(
                        0,
                        0,
                        this.size.width.roundToInt(),
                        this.size.height.roundToInt()
                    )
                    drawIntoCanvas {
                        badgeIcon.drawable.draw(it.nativeCanvas)
                    }
                }
            } else if (number != null && number > 0 && number < 100) {
                Text(
                    NumberFormat.getInstance(Locale.current.platformLocale).format(number),
                    color = MaterialTheme.colorScheme.onSecondary,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = with(LocalDensity.current) {
                            size.toSp() * 0.6f
                        }
                    ),
                )
            }
        }
    }
}