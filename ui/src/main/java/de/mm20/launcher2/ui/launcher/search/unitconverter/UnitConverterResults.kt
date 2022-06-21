package de.mm20.launcher2.ui.launcher.search.unitconverter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.launcher.search.SearchVM

@Composable
fun ColumnScope.UnitConverterResults(reverse: Boolean = false) {
    val viewModel: SearchVM = viewModel()
    val unitConverter by viewModel.unitConverterResult.observeAsState(null)

    AnimatedVisibility(unitConverter != null) {
        LauncherCard(
            modifier = Modifier
                .padding(bottom = if (reverse) 0.dp else 8.dp, top = if (reverse) 8.dp else 0.dp)
        ) {
            unitConverter?.let { UnitConverterItem(unitConverter = it) }
        }
    }

}