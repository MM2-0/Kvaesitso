package de.mm20.launcher2.ui.launcher.search.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.SearchFilters
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.icons.Wikipedia
import de.mm20.launcher2.ui.overlays.Overlay

@Composable
fun KeyboardFilterBar(filters: SearchFilters, onFiltersChange: (SearchFilters) -> Unit) {
    Overlay {
        val allCategoriesEnabled = filters.allCategoriesEnabled
        Box(modifier = Modifier
            .fillMaxSize()
            .imePadding(), contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
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
                    VerticalDivider(
                        modifier = Modifier
                            .height(36.dp)
                            .padding(horizontal = 8.dp)
                    )
                    FilterChip(
                        modifier = Modifier.padding(end = 8.dp),
                        selected = filters.apps && !allCategoriesEnabled,
                        onClick = {
                            onFiltersChange(filters.toggleApps())
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
                        modifier = Modifier.padding(end = 8.dp),
                        selected = filters.files && !allCategoriesEnabled,
                        onClick = {
                            onFiltersChange(filters.toggleFiles())
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
                        modifier = Modifier.padding(end = 8.dp),
                        selected = filters.contacts && !allCategoriesEnabled,
                        onClick = {
                            onFiltersChange(filters.toggleContacts())
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
                        modifier = Modifier.padding(end = 8.dp),
                        selected = filters.events && !allCategoriesEnabled,
                        onClick = {
                            onFiltersChange(filters.toggleEvents())
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
                        modifier = Modifier.padding(end = 8.dp),
                        selected = filters.shortcuts && !allCategoriesEnabled,
                        onClick = {
                            onFiltersChange(filters.toggleShortcuts())
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
                        modifier = Modifier.padding(end = 8.dp),
                        selected = filters.articles && !allCategoriesEnabled,
                        onClick = {
                            onFiltersChange(filters.toggleArticles())
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
                        modifier = Modifier.padding(end = 8.dp),
                        selected = filters.websites && !allCategoriesEnabled,
                        onClick = {
                            onFiltersChange(filters.toggleWebsites())
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
                        modifier = Modifier.padding(end = 8.dp),
                        selected = filters.places && !allCategoriesEnabled,
                        onClick = {
                            onFiltersChange(filters.togglePlaces())
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
                        selected = filters.tools && !allCategoriesEnabled,
                        onClick = {
                            onFiltersChange(filters.toggleTools())
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
                    VerticalDivider(
                        modifier = Modifier
                            .height(36.dp)
                            .padding(horizontal = 8.dp)
                    )
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
                HorizontalDivider()
            }
        }
    }
}