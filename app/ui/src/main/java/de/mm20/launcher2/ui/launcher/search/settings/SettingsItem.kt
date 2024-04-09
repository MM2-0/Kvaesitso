package de.mm20.launcher2.ui.launcher.search.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.search.data.PojoSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel

@Composable
fun SettingsItem(
    data: PojoSettings,
    modifier: Modifier = Modifier)
{
    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${data.key}")
    val icon by viewModel.icon.collectAsStateWithLifecycle()

    Row(
        modifier = modifier.padding(8.dp)
    ) {
        Column {
            ShapedLauncherIcon(
                size = 48.dp,
                icon = { icon },
            )
        }
        Box(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            Text(
                text = stringResource(R.string.settings_placeholder, data.label),
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis
            )
        }

    }
}