package de.mm20.launcher2.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.datastore.core.DataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.ClockStyle
import de.mm20.launcher2.preferences.dataStore
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.AnalogClock
import de.mm20.launcher2.ui.component.BinaryClock
import de.mm20.launcher2.ui.component.DigitalClock
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun SettingsClockScreen() {
    val dataStore = LocalContext.current.dataStore
    val scope = rememberCoroutineScope()
    val selectedClock by remember { dataStore.data.map { it.appearance.clockStyle } }.collectAsState(
        initial = ClockStyle.Digital
    )
    val time = System.currentTimeMillis()
    PreferenceScreen(title = stringResource(id = R.string.preference_clock_widget_style)) {
        item {
            ClockPreview(selected = selectedClock == ClockStyle.Digital, onClick = {
                scope.launch {
                    updateClock(dataStore, ClockStyle.Digital)
                }
            }) {
                DigitalClock(time)
            }
        }
        item {
            ClockPreview(selected = selectedClock == ClockStyle.Analog, onClick = {
                scope.launch {
                    updateClock(dataStore, ClockStyle.Analog)
                }
            }) {
                AnalogClock(time)
            }
        }
        item {
            ClockPreview(selected = selectedClock == ClockStyle.Binary, onClick = {
                scope.launch {
                    updateClock(dataStore, ClockStyle.Binary)
                }
            }) {
                BinaryClock(time)
            }
        }
    }
}

private suspend fun updateClock(dataStore: DataStore<Settings>, clockStyle: ClockStyle) {
    dataStore.updateData {
        it.toBuilder()
            .setAppearance(it.appearance.toBuilder().setClockStyle(clockStyle))
            .build()
    }
}

@Composable
private fun ClockPreview(
    selected: Boolean,
    onClick: () -> Unit,
    clock: @Composable () -> Unit
) {
    PreferenceCategory {
        Preference(
            title = "",
            icon = if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
            controls = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    clock()
                }
            },
            onClick = onClick
        )
    }
}