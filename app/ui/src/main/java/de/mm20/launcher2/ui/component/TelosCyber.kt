package de.mm20.launcher2.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared Cyber-Tech accent palette for Telos virtual apps. Neon borders + opaque Material
 * containers (never transparent surface as icon/text). Surface role names live in
 * [TelosSurfaceTokens] (mirrors ui-kmp Helix kit).
 */
object TelosCyber {
    val NeonGreen = Color(0xFF00E676)
    val NeonCyan = Color(0xFF00F0FF)
    val NeonMagenta = Color(0xFFFF007F)
    val NeonOrange = Color(0xFFFF9100)

    fun accentFor(index: Int): Color =
        when (index.mod(4)) {
            0 -> NeonCyan
            1 -> NeonGreen
            2 -> NeonMagenta
            else -> NeonOrange
        }
}

/** Opaque tonal tech card: neon border over [TelosSurfaceTokens.cardOpaque] (glass-safe). */
@Composable
fun Modifier.telosTechSurface(
    borderColor: Color = TelosCyber.NeonCyan,
    shape: Shape = RoundedCornerShape(18.dp),
    borderWidth: Dp = 1.2.dp,
): Modifier =
    telosTechCard(
        borderColor = borderColor,
        shape = shape,
        borderWidth = borderWidth,
        alpha = 0.92f,
        backgroundColor = TelosSurfaceTokens.cardOpaque(),
    )

@Composable
fun TelosCyberAppBarTitle(
    title: String,
    status: String? = null,
    statusColor: Color = TelosCyber.NeonCyan,
    modifier: Modifier = Modifier,
) {
    TelosCyberHeading(modifier) {
        Column {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!status.isNullOrBlank()) {
                TelosDotMatrixText(
                    text = status.uppercase(),
                    fontSize = 9.sp,
                    color = statusColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** A shared title rail; no extra root backing or fixed text height. */
@Composable
fun TelosCyberHeading(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(3.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f, fill = false)) { content() }
    }
}

@Composable
fun TelosCyberSectionLabel(
    text: String,
    accent: Color = TelosCyber.NeonCyan,
    modifier: Modifier = Modifier,
) {
    TelosDotMatrixText(
        text = text.uppercase(),
        modifier = modifier.padding(vertical = 4.dp),
        color = accent,
        fontSize = 11.sp,
    )
}

@Composable
fun TelosCyberHubTile(
    title: String,
    value: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = TelosCyber.NeonCyan,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 108.dp)
                .telosTechSurface(borderColor = accent, shape = RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = accent,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TelosCyberStatChip(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = TelosCyber.NeonMagenta,
) {
    Column(
        modifier
            .telosTechSurface(borderColor = accent, shape = RoundedCornerShape(14.dp))
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = accent)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun TelosCyberMetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = TelosCyber.NeonGreen,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TelosDotMatrixText(label.uppercase(), fontSize = 10.sp, color = accent)
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
