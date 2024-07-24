package de.mm20.launcher2.ui.launcher.search.filters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AppShortcut
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.SearchFilters
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.icons.Wikipedia

@Composable
fun SearchFilters(
    filters: SearchFilters,
    onFiltersChange: (SearchFilters) -> Unit,
    modifier: Modifier = Modifier,
    settings: Boolean = false
) {
    val allCategoriesEnabled = filters.allCategoriesEnabled
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
    ) {
        FilterChip(
            selected = filters.allowNetwork,
            onClick = {
                onFiltersChange(filters.copy(allowNetwork = !filters.allowNetwork))
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            },
            label = { Text(stringResource(R.string.search_filter_online)) }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        FlowRow {
            FilterChip(
                modifier = Modifier.padding(end = 16.dp),
                selected = filters.apps && (!allCategoriesEnabled || settings),
                onClick = {
                    if (settings) {
                        onFiltersChange(filters.copy(apps = !filters.apps))
                    } else {
                        onFiltersChange(filters.toggleApps())
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                },
                label = { Text(stringResource(R.string.search_filter_apps)) }
            )
            FilterChip(
                modifier = Modifier.padding(end = 16.dp),
                selected = filters.files && (!allCategoriesEnabled || settings),
                onClick = {
                    if (settings) {
                        onFiltersChange(filters.copy(files = !filters.files))
                    } else {
                        onFiltersChange(filters.toggleFiles())
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                },
                label = { Text(stringResource(R.string.preference_search_files)) }
            )
            FilterChip(
                modifier = Modifier.padding(end = 16.dp),
                selected = filters.contacts && (!allCategoriesEnabled || settings),
                onClick = {
                    if (settings) {
                        onFiltersChange(filters.copy(contacts = !filters.contacts))
                    } else {
                        onFiltersChange(filters.toggleContacts())
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                },
                label = { Text(stringResource(R.string.preference_search_contacts)) }
            )
            FilterChip(
                modifier = Modifier.padding(end = 16.dp),
                selected = filters.events && (!allCategoriesEnabled || settings),
                onClick = {
                    if (settings) {
                        onFiltersChange(filters.copy(events = !filters.events))
                    } else {
                        onFiltersChange(filters.toggleEvents())
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Today,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                },
                label = { Text(stringResource(R.string.preference_search_calendar)) }
            )
            FilterChip(
                modifier = Modifier.padding(end = 16.dp),
                selected = filters.shortcuts && (!allCategoriesEnabled || settings),
                onClick = {
                    if (settings) {
                        onFiltersChange(filters.copy(shortcuts = !filters.shortcuts))
                    } else {
                        onFiltersChange(filters.toggleShortcuts())
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.AppShortcut,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                },
                label = { Text(stringResource(R.string.preference_search_appshortcuts)) }
            )
            FilterChip(
                modifier = Modifier.padding(end = 16.dp),
                selected = filters.articles && (!allCategoriesEnabled || settings),
                onClick = {
                    if (settings) {
                        onFiltersChange(filters.copy(articles = !filters.articles))
                    } else {
                        onFiltersChange(filters.toggleArticles())
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Wikipedia,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                },
                label = { Text(stringResource(R.string.preference_search_wikipedia)) }
            )
            FilterChip(
                modifier = Modifier.padding(end = 16.dp),
                selected = filters.websites && (!allCategoriesEnabled || settings),
                onClick = {
                    if (settings) {
                        onFiltersChange(filters.copy(websites = !filters.websites))
                    } else {
                        onFiltersChange(filters.toggleWebsites())
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                },
                label = { Text(stringResource(R.string.preference_search_websites)) }
            )
            FilterChip(
                modifier = Modifier.padding(end = 16.dp),
                selected = filters.places && (!allCategoriesEnabled || settings),
                onClick = {
                    if (settings) {
                        onFiltersChange(filters.copy(places = !filters.places))
                    } else {
                        onFiltersChange(filters.togglePlaces())
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Place,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                },
                label = { Text(stringResource(R.string.preference_search_locations)) }
            )
            FilterChip(
                selected = filters.tools && (!allCategoriesEnabled || settings),
                onClick = {
                    if (settings) {
                        onFiltersChange(filters.copy(tools = !filters.tools))
                    } else {
                        onFiltersChange(filters.toggleTools())
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Handyman,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                },
                label = { Text(stringResource(R.string.search_filter_tools)) }
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        FilterChip(
            selected = filters.hiddenItems,
            onClick = {
                onFiltersChange(filters.copy(hiddenItems = !filters.hiddenItems))
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            },
            label = { Text(stringResource(R.string.preference_hidden_items)) }
        )
    }
}