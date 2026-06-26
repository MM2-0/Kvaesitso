package de.mm20.launcher2.ui.component.preferences

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import de.mm20.launcher2.ui.ktx.animateShapeAsState

@Composable
fun MainSwitchPreference(
    title: String,
    value: Boolean,
    onValueChanged: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val shape by animateShapeAsState(
        if (value) MaterialTheme.shapes.small else MaterialTheme.shapes.large,
        MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val backgroundColor by animateColorAsState(
        if (value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val contentColor by animateColorAsState(
        if (value) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    Box(
        modifier = Modifier
            .clip(shape)
            .background(backgroundColor, shape)
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor
        ) {

            SwitchPreference(
                title = title,
                value = value,
                onValueChanged = onValueChanged,
                enabled = enabled,
                containerColor = Color.Transparent,
            )
        }
    }
}