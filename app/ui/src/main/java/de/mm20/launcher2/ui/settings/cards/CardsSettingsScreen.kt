package de.mm20.launcher2.ui.settings.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference

@Composable
fun CardsSettingsScreen() {
    val viewModel: CardsSettingsScreenVM = viewModel()
    PreferenceScreen(title = stringResource(R.string.preference_cards)) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondary)
            ) {
                LauncherCard(
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
        item {
            PreferenceCategory {
                val shape by viewModel.shape.collectAsState()
                ListPreference(
                    icon = Icons.Rounded.Rectangle,
                    title = stringResource(R.string.preference_cards_shape),
                    items = listOf(
                        stringResource(R.string.preference_cards_shape_rounded) to Settings.CardSettings.Shape.Rounded,
                        stringResource(R.string.preference_cards_shape_cut) to Settings.CardSettings.Shape.Cut,
                    ),
                    value = shape,
                    onValueChanged = {
                        if (it != null) viewModel.setShape(it)
                    })
                val radius by viewModel.radius.collectAsState()
                SliderPreference(
                    title = stringResource(R.string.preference_cards_corner_radius),
                    icon = Icons.Rounded.RoundedCorner,
                    value = radius,
                    min = 0,
                    max = 24,
                    step = 1,
                    onValueChanged = {
                        viewModel.setRadius(it)
                    }
                )
                val opacity by viewModel.opacity.collectAsState()
                SliderPreference(
                    title = stringResource(R.string.preference_cards_opacity),
                    icon = Icons.Rounded.Opacity,
                    value = opacity,
                    min = 0f,
                    max = 1f,
                    onValueChanged = {
                        viewModel.setOpacity(it)
                    }
                )
                val borderWidth by viewModel.borderWidth.collectAsState()
                SliderPreference(
                    title = stringResource(R.string.preference_cards_stroke_width),
                    icon = Icons.Rounded.LineWeight,
                    value = borderWidth,
                    min = 0,
                    max = 8,
                    step = 1,
                    onValueChanged = {
                        viewModel.setBorderWidth(it)
                    }
                )
            }
        }
    }
}