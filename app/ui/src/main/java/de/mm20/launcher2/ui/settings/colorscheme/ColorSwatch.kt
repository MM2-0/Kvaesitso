package de.mm20.launcher2.ui.settings.colorscheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import hct.Hct

@Composable
fun ColorSwatch(
    color: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val darkTheme = LocalDarkTheme.current
    val iconColor = Color(Hct.fromInt(color.toArgb()).let {
        val tone = if (darkTheme) {
            if (it.tone.toInt() > 40) 30f
            else 60f
        } else {
            if (it.tone.toInt() < 60) 80f
            else 40f
        }
        it.apply {
            this.setTone(tone.toDouble())
        }.toInt()
    })
    val borderColor = Color(Hct.fromInt(color.toArgb()).let {
        val tone = if (darkTheme) 30f else 80f
        it.apply {
            this.setTone(tone.toDouble())
        }.toInt()
    })
    Box(
        modifier = modifier
            .clip(CircleShape)
            .border(
                if (selected) 4.dp else 1.dp,
                borderColor,
                CircleShape
            )
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                painterResource(R.drawable.check_circle_24px),
                null,
                modifier = Modifier.size(32.dp),
                tint = iconColor,
            )
        }
    }
}