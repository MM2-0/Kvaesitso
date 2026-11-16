package de.mm20.launcher2.ui.settings.art

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.i18n.R as I18nR


/** Stateless header so scrolling never starts an ART scan or an optimization. */
@Composable
internal fun ArtOptimizerAppSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    activeFilter: ArtAppFilter,
    onFilterChange: (ArtAppFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(I18nR.string.art_optimizer_apps_category),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        de.mm20.launcher2.ui.component.TelosCommandHud(
            searchQuery = query,
            onSearchQueryChange = onQueryChange,
            searchPlaceholder = stringResource(I18nR.string.art_optimizer_search_apps_hint),
            actions = ArtAppFilter.entries.map { filter ->
                val label = when (filter) {
                    ArtAppFilter.ALL -> I18nR.string.art_optimizer_filter_all
                    ArtAppFilter.NEEDS_OPTIMIZATION -> I18nR.string.art_optimizer_filter_needed
                    ArtAppFilter.UP_TO_DATE -> I18nR.string.art_optimizer_filter_optimized
                    ArtAppFilter.DEGRADED -> I18nR.string.art_optimizer_filter_degraded
                    ArtAppFilter.FROZEN -> I18nR.string.art_optimizer_filter_frozen
                }
                de.mm20.launcher2.ui.component.TelosHudAction(
                    id = filter.name,
                    icon = when (filter) {
                        ArtAppFilter.ALL -> de.mm20.launcher2.base.R.drawable.apps_24px
                        ArtAppFilter.NEEDS_OPTIMIZATION -> de.mm20.launcher2.base.R.drawable.warning_24px
                        ArtAppFilter.UP_TO_DATE -> de.mm20.launcher2.base.R.drawable.check_circle_24px
                        ArtAppFilter.DEGRADED -> de.mm20.launcher2.base.R.drawable.error_24px
                        ArtAppFilter.FROZEN -> de.mm20.launcher2.base.R.drawable.visibility_off_24px
                    },
                    label = stringResource(label),
                    selected = activeFilter == filter,
                    onClick = { onFilterChange(filter) },
                )
            },
        )
    }
}
