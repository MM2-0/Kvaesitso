package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import androidx.compose.runtime.Composable
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockWidgetLayout
import kotlinx.coroutines.flow.Flow

interface PartProvider {

    fun getRanking(): Flow<Int>

    @Composable
    fun Component(layout: ClockWidgetLayout)
}