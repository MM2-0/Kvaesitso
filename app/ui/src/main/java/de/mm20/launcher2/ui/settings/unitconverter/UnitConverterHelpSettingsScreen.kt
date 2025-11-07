package de.mm20.launcher2.ui.settings.unitconverter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.launcher.search.unitconverter.getDimensionIcon
import de.mm20.launcher2.unitconverter.Dimension

@Composable
fun UnitConverterHelpSettingsScreen() {
    val viewModel: UnitConverterSettingsScreenVM = viewModel()

    val availableConverters by viewModel.availableConverters.collectAsState(emptyList())
    val availableUnits by viewModel.availableUnits.collectAsState(emptyList())

    PreferenceScreen(
        title = stringResource(R.string.preference_search_unitconverter),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        helpUrl = "https://kvaesitso.mm20.de/docs/user-guide/search/unit-converter"
    ) {
        for (i in availableConverters.indices) {
            stickyHeader {
                DimensionHeader(availableConverters[i].dimension)
            }
            items(availableUnits.getOrNull(i)?.size ?: 0) {
                val unit = availableUnits[i].getOrNull(it) ?: return@items
                Preference(
                    title = unit.formatName(LocalContext.current, 1.0),
                    controls = {
                        Box(
                            modifier = Modifier.padding(horizontal = 4.dp).widthIn(min = 36.dp).height(36.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.extraSmall),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(unit.symbol, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DimensionHeader(dimension: Dimension) {
    Row(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer).fillMaxWidth()
            .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(getDimensionIcon(dimension)),
            null,
            modifier = Modifier.padding(end = 8.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Text(
            stringResource(dimension.resource),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

