package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

@Composable
fun SegmentClock(
    time: Long,
    compact: Boolean,
    showSeconds: Boolean,
    twentyFourHours: Boolean,
    useThemeColor: Boolean,
    darkColors: Boolean,
) {
    val parsed = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
    val hour = if (twentyFourHours) parsed.hour else (((parsed.hour + 11) % 12) + 1)
    val minute = parsed.minute
    val second = parsed.second

    var flick by remember { mutableStateOf(false) }

    LaunchedEffect(second) {
        flick = true
        delay(500)
        flick = false
    }

    val enabled = if (useThemeColor) {
        if (!darkColors) {
            if (LocalDarkTheme.current) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.primaryContainer
        } else {
            if (LocalDarkTheme.current) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.primary
        }
    } else {
        LocalContentColor.current
    }
    val disabled = LocalContentColor.current

    val allSegmentVectors = remember(compact, enabled, disabled) {
        val vectors = mutableListOf<ImageVector>()

        for (code in segmentBitsForDigits.indices) {
            vectors.add(getVectorDigitForNumber(compact, code, enabled, disabled))
        }

        vectors.toList()
    }

    val separator = remember(compact, enabled) {
        getVectorSeparator(compact, enabled)
    }

    Row(
        modifier = Modifier.padding(
            top = if (!compact) 16.dp else 0.dp,
            bottom = if (!compact) 16.dp else 0.dp,
            start = 0.dp, end = 0.dp
        ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(allSegmentVectors[hour / 10], null)
        Separator(compact)
        Image(allSegmentVectors[hour % 10], null)

        Separator(compact)
        Box(Modifier.alpha(if (flick) 1f else 0.05f)) { Image(separator, null) }
        Separator(compact)

        Image(allSegmentVectors[minute / 10], null)
        Separator(compact)
        Image(allSegmentVectors[minute % 10], null)

        if (!compact && showSeconds) {
            Separator(false)
            Box(Modifier.alpha(if (flick) 1f else 0.05f)) { Image(separator, null) }
            Separator(false)

            Image(allSegmentVectors[second / 10], null)
            Separator(false)
            Image(allSegmentVectors[second % 10], null)
        }
    }
}

@Composable
private fun Separator(compact: Boolean) {
    Box(Modifier.size(if (compact) 3.dp else 4.dp))
}

/*
   ┌─(A)─┐
  (F)   (B)  // Segments on byte: 0bGFEDBCA
   ├─(G)─┤   // (11 values counting one with all bits off at the end)
  (E)   (C)
   └─(D)─┘
 */
private val segmentBitsForDigits =
    arrayOf(0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f, 0x00)

private fun getVectorDigitForNumber(
    compact: Boolean,
    number: Int,
    enabled: Color,
    disabled: Color
): ImageVector {
    if (number < 0 || number > segmentBitsForDigits.size) {
        throw IllegalArgumentException()
    }

    val segment = segmentBitsForDigits[number]
    val solidEnabled = SolidColor(enabled)
    val solidDisabled = SolidColor(disabled)

    return ImageVector.Builder(
        defaultWidth = if (compact) 18.dp else 30.dp,
        defaultHeight = if (compact) 30.dp else 50.dp,
        viewportWidth = 15.874999f,
        viewportHeight = 26.458332f
    ).path(
        name = "A",
        fill = if ((segment and 0x01) == 0x01) solidEnabled else solidDisabled,
        fillAlpha = if ((segment and 0x01) == 0x01) 1.0f else 0.05f,
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(4.076372f, 3.6568797f)
        horizontalLineToRelative(7.802962f)
        lineTo(12.903025f, 0.0872854f)
        lineTo(3.470742f, 0.04026844f)
        curveTo(2.1788f, 0.0804f, 1.4853f, 0.7665f, 1.4853f, 0.7665f)
        close()
    }.path(
        name = "B",
        fill = if (((segment and 0x02) shr 1) == 0x01) solidEnabled else solidDisabled,
        fillAlpha = if (((segment and 0x02) shr 1) == 0x01) 1.0f else 0.05f,
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(13.50401f, 0.22460027f)
        lineToRelative(-1.096166f, 4.134793f)
        lineToRelative(-0.01958f, 7.2860675f)
        lineToRelative(2.388076f, 1.485313f)
        lineToRelative(1.076593f, -0.56201f)
        lineToRelative(0.05628f, -9.253104f)
        curveToRelative(0.0046f, -0.7586f, -0.9567f, -2.6294f, -2.4052f, -3.0911f)
        close()
    }.path(
        name = "C",
        fill = if (((segment and 0x04) shr 2) == 0x01) solidEnabled else solidDisabled,
        fillAlpha = if (((segment and 0x04) shr 2) == 0x01) 1.0f else 0.05f,
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(12.407844f, 15.358745f)
        lineToRelative(2.27063f, -1.505385f)
        lineToRelative(1.174464f, 0.56201f)
        lineToRelative(0.0049f, 8.658477f)
        curveToRelative(0.0015f, 2.733f, -2.9606f, 3.3846f, -2.9606f, 3.3846f)
        lineToRelative(-0.469785f, -3.637352f)
        close()
    }.path(
        name = "D",
        fill = if (((segment and 0x08) shr 3) == 0x01) solidEnabled else solidDisabled,
        fillAlpha = if (((segment and 0x08) shr 3) == 0x01) 1.0f else 0.05f,
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(4.088724f, 22.705027f)
        lineToRelative(-2.681692f, 3.010772f)
        curveToRelative(0.6655f, 0.7226f, 2.1287f, 0.7828f, 2.1287f, 0.7828f)
        horizontalLineToRelative(8.861135f)
        lineToRelative(-0.550436f, -3.793573f)
        close()
    }.path(
        name = "E",
        fill = if (((segment and 0x10) shr 4) == 0x01) solidEnabled else solidDisabled,
        fillAlpha = if (((segment and 0x10) shr 4) == 0x01) 1.0f else 0.05f,
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(3.247025f, 15.459104f)
        lineToRelative(-2.290205f, -1.686032f)
        lineToRelative(-0.880847f, 0.461652f)
        lineToRelative(-0.0367f, 8.711166f)
        reflectiveCurveToRelative(0.04649f, 1.324739f, 0.800103f, 2.248042f)
        lineToRelative(2.427225f, -2.461695f)
        close()
    }.path(
        name = "F",
        fill = if (((segment and 0x20) shr 5) == 0x01) solidEnabled else solidDisabled,
        fillAlpha = if (((segment and 0x20) shr 5) == 0x01) 1.0f else 0.05f,
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(1.035118f, 1.2482624f)
        reflectiveCurveToRelative(-0.928317f, 0.8023766f, -0.929784f, 2.1552107f)
        lineToRelative(-0.0098f, 9.024787f)
        lineToRelative(0.880848f, 0.521867f)
        lineToRelative(2.290204f, -1.465242f)
        verticalLineToRelative(-7.707575f)
        close()
    }.path(
        name = "G",
        fill = if (((segment and 0x40) shr 6) == 0x01) solidEnabled else solidDisabled,
        fillAlpha = if (((segment and 0x40) shr 6) == 0x01) 1.0f else 0.05f,
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(1.603085f, 13.391707f)
        curveToRelative(0.8352f, -0.5352f, 1.6703f, -1.0705f, 2.5055f, -1.6057f)
        horizontalLineToRelative(7.340399f)
        curveToRelative(0.9265f, 0.5486f, 1.853f, 1.0973f, 2.7796f, 1.6459f)
        curveToRelative(-0.8482f, 0.5553f, -1.6964f, 1.1106f, -2.5447f, 1.666f)
        horizontalLineTo(4.069459f)
        curveToRelative(-0.8221f, -0.5687f, -1.6442f, -1.1374f, -2.4664f, -1.7061f)
        close()
    }

        .build()
}

private fun getVectorSeparator(compact: Boolean, enabled: Color): ImageVector {

    return ImageVector.Builder(
        defaultWidth = if (compact) 3.6.dp else 6.dp,
        defaultHeight = if (compact) 30.dp else 50.dp,
        viewportWidth = 3.175f,
        viewportHeight = 26.458f
    ).apply {
        path(
            fill = SolidColor(enabled),
            fillAlpha = 1.0f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(3.175f, 18.5f)
            arcToRelative(
                1.587f,
                1.587f,
                0f,
                isMoreThanHalf = false,
                isPositiveArc = true,
                -1.587f,
                1.588f
            )
            arcToRelative(
                1.587f,
                1.587f,
                0f,
                isMoreThanHalf = false,
                isPositiveArc = true,
                -1.588f,
                -1.588f
            )
            arcToRelative(
                1.587f,
                1.587f,
                0f,
                isMoreThanHalf = false,
                isPositiveArc = true,
                1.588f,
                -1.587f
            )
            arcToRelative(
                1.587f,
                1.587f,
                0f,
                isMoreThanHalf = false,
                isPositiveArc = true,
                1.587f,
                1.587f
            )
            close()
            moveToRelative(0f, -9.634f)
            arcToRelative(
                1.587f,
                1.587f,
                0f,
                isMoreThanHalf = false,
                isPositiveArc = true,
                -1.587f,
                1.588f
            )
            arcToRelative(
                1.587f,
                1.587f,
                0f,
                isMoreThanHalf = false,
                isPositiveArc = true,
                -1.588f,
                -1.588f
            )
            arcToRelative(
                1.587f,
                1.587f,
                0f,
                isMoreThanHalf = false,
                isPositiveArc = true,
                1.588f,
                -1.587f
            )
            arcToRelative(
                1.587f,
                1.587f,
                0f,
                isMoreThanHalf = false,
                isPositiveArc = true,
                1.587f,
                1.587f
            )
            close()
        }
    }.build()
}