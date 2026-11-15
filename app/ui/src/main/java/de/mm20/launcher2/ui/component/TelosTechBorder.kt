package de.mm20.launcher2.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modifier for Morphe-style fine glowing borders with dark translucent glass container backgrounds.
 */
fun Modifier.telosTechCard(
    borderColor: Color = Color(0xFF00E676),
    shape: Shape = RoundedCornerShape(18.dp),
    borderWidth: Dp = 1.2.dp,
    alpha: Float = 0.85f,
    backgroundColor: Color = Color(0xFF0D0E15).copy(alpha = 0.92f)
): Modifier = this
    .clip(shape)
    .border(borderWidth, borderColor.copy(alpha = alpha), shape)
    .background(backgroundColor)

/**
 * Morphe-style Capsule/Pill Chip component for tabs, modes, and action buttons.
 */
@Composable
fun TelosTechPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    accentColor: Color = Color(0xFF00E676)
) {
    val container = TelosSurfaceTokens.techPill()
    val bg = if (isSelected) accentColor.copy(alpha = 0.16f).compositeOver(container) else container
    val borderCol = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val textCol = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .border(1.2.dp, borderCol, CircleShape)
            .clickable { onClick() },
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = textCol
                )
            }
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = textCol
            )
        }
    }
}

/**
 * Morphe-style Telemetry Card with title, metric value, and mini sparkline bars.
 */
@Composable
fun TelosTelemetryCard(
    title: String,
    value: String,
    bars: List<Float>, // Values 0.0f to 1.0f
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF00E676)
) {
    Column(
        modifier = modifier
            .telosTechCard(
                borderColor = accentColor,
                shape = RoundedCornerShape(16.dp),
                backgroundColor = TelosSurfaceTokens.cardOpaque(),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        // Mini Sparkline Bar Chart
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bars.take(16).forEach { barValue ->
                val clamped = barValue.coerceIn(0.1f, 1.0f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(clamped)
                        .clip(RoundedCornerShape(1.dp))
                        .background(accentColor.copy(alpha = 0.35f + clamped * 0.65f))
                )
            }
        }
    }
}
