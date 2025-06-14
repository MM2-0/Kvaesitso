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
import de.mm20.launcher2.preferences.SurfaceShape
import de.mm20.launcher2.preferences.ui.CardStyle
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference

@Composable
fun CardsSettingsScreen() {
    val viewModel: CardsSettingsScreenVM = viewModel()

    val cardStyle by viewModel.cardStyle.collectAsState(CardStyle())

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
                SliderPreference(
                    title = stringResource(R.string.preference_cards_stroke_width),
                    icon = Icons.Rounded.LineWeight,
                    value = cardStyle.borderWidth,
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