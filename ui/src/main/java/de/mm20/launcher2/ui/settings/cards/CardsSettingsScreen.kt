package de.mm20.launcher2.ui.settings.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BorderStyle
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference

@Composable
fun CardsSettingsScreen() {
    val viewModel: CardsSettingsScreenVM = viewModel()
    PreferenceScreen(title = stringResource(R.string.preference_cards)) {
        item {
            Box(modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondary)) {
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
                val opacity by viewModel.opacity.observeAsState(0f)
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
                val radius by viewModel.radius.observeAsState(0)
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
                val borderWidth by viewModel.borderWidth.observeAsState(0)
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