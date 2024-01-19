package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import android.content.Context
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

interface PartProvider {

    fun getRanking(context: Context): Flow<Int>

    fun setTime(time: Long) {}

    @Composable
    fun Component(compactLayout: Boolean)
}