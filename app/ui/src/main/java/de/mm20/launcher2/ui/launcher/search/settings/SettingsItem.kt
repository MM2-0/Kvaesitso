package de.mm20.launcher2.ui.launcher.search.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.PojoSettings
import de.mm20.launcher2.ui.R

@Composable
fun SettingsItem(
    data: PojoSettings,
    modifier: Modifier = Modifier)
{
    Row(
        modifier = modifier.padding(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_placeholder, data.label),
            style = MaterialTheme.typography.titleMedium
        )
    }
}