package de.mm20.launcher2.ui.settings.search

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.SearchResultOrder
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.SmallMessage
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.PreferenceWithSwitch
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.icons.Wikipedia
import de.mm20.launcher2.ui.launcher.search.filters.SearchFilters
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun SearchSettingsScreen() {

    val viewModel: SearchSettingsScreenVM = viewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val navController = LocalNavController.current

    val hasWorkProfile by viewModel.hasWorkProfile

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onResume(context)
        }
    }

    var showFilterEditor by remember {
        mutableStateOf(false)
    }


    PreferenceScreen(title = stringResource(R.string.preference_screen_search)) {
        item {
            PreferenceCategory {
                val favorites by viewModel.favorites.collectAsStateWithLifecycle(null)
                PreferenceWithSwitch(
                    title = stringResource(R.string.preference_search_favorites),
                    summary = stringResource(R.string.preference_search_favorites_summary),
                    icon = Icons.Rounded.Star,
                    switchValue = favorites == true,
                    onSwitchChanged = {
                        viewModel.setFavorites(it)
                    },
                    onClick = {
                        navController?.navigate("settings/favorites")
                    }
                )

                Preference(
                    title = stringResource(R.string.preference_search_files),
                    summary = stringResource(R.string.preference_search_files_summary),
                    icon = Icons.Rounded.Description,
                    onClick = {
                        navController?.navigate("settings/search/files")
                    }
                )

                val hasContactsPermission by viewModel.hasContactsPermission.collectAsStateWithLifecycle(
                    null
                )
                AnimatedVisibility(hasContactsPermission == false) {
                    MissingPermissionBanner(
                        text = stringResource(R.string.missing_permission_contact_search_settings),
                        onClick = {
                            viewModel.requestContactsPermission(context as AppCompatActivity)
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
                val contacts by viewModel.contacts.collectAsStateWithLifecycle(null)
                SwitchPreference(
                    title = stringResource(R.string.preference_search_contacts),
                    summary = stringResource(R.string.preference_search_contacts_summary),
                    icon = Icons.Rounded.Person,
                    value = contacts == true && hasContactsPermission == true,
                    onValueChanged = {
                        viewModel.setContacts(it)
                    },
                    enabled = hasContactsPermission == true
                )

                val hasCalendarPermission by viewModel.hasCalendarPermission.collectAsStateWithLifecycle(
                    null
                )
                AnimatedVisibility(hasCalendarPermission == false) {
                    MissingPermissionBanner(
                        text = stringResource(R.string.missing_permission_calendar_search_settings),
                        onClick = {
                            viewModel.requestCalendarPermission(context as AppCompatActivity)
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
                val calendar by viewModel.calendar.collectAsStateWithLifecycle(null)
                SwitchPreference(
                    title = stringResource(R.string.preference_search_calendar),
                    summary = stringResource(R.string.preference_search_calendar_summary),
                    icon = Icons.Rounded.Today,
                    value = calendar == true && hasCalendarPermission == true,
                    onValueChanged = {
                        viewModel.setCalendar(it)
                    },
                    enabled = hasCalendarPermission == true
                )

                val hasAppShortcutsPermission by viewModel.hasAppShortcutPermission.collectAsStateWithLifecycle(
                    null
                )
                AnimatedVisibility(hasAppShortcutsPermission == false) {
                    MissingPermissionBanner(
                        text = stringResource(
                            R.string.missing_permission_appshortcuts_search_settings,
                            stringResource(R.string.app_name)
                        ),
                        onClick = {
                            viewModel.requestAppShortcutsPermission(context as AppCompatActivity)
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
                val appShortcuts by viewModel.appShortcuts.collectAsStateWithLifecycle(null)
                SwitchPreference(
                    title = stringResource(R.string.preference_search_appshortcuts),
                    summary = stringResource(R.string.preference_search_appshortcuts_summary),
                    icon = Icons.Rounded.AppShortcut,
                    value = appShortcuts == true && hasAppShortcutsPermission == true,
                    onValueChanged = {
                        viewModel.setAppShortcuts(it)
                    },
                    enabled = hasAppShortcutsPermission == true
                )

                val settings by viewModel.settings.collectAsStateWithLifecycle(null)
                SwitchPreference(
                    title = stringResource(R.string.preference_search_settings),
                    summary = stringResource(R.string.preference_search_settings_summary),
                    icon = Icons.Rounded.Settings,
                    value = settings == true,
                    onValueChanged = {
                        viewModel.setSettings(it)
                    }
                )

                val calculator by viewModel.calculator.collectAsStateWithLifecycle(null)
                SwitchPreference(
                    title = stringResource(R.string.preference_search_calculator),
                    summary = stringResource(R.string.preference_search_calculator_summary),
                    icon = Icons.Rounded.Calculate,
                    value = calculator == true,
                    onValueChanged = {
                        viewModel.setCalculator(it)
                    }
                )

                val unitConverter by viewModel.unitConverter.collectAsStateWithLifecycle(null)
                PreferenceWithSwitch(
                    title = stringResource(R.string.preference_search_unitconverter),
                    summary = stringResource(R.string.preference_search_unitconverter_summary),
                    icon = Icons.Rounded.Loop,
                    switchValue = unitConverter == true,
                    onSwitchChanged = {
                        viewModel.setUnitConverter(it)
                    },
                    onClick = {
                        navController?.navigate("settings/search/unitconverter")
                    }
                )

                val wikipedia by viewModel.wikipedia.collectAsStateWithLifecycle(null)
                PreferenceWithSwitch(
                    title = stringResource(R.string.preference_search_wikipedia),
                    summary = stringResource(R.string.preference_search_wikipedia_summary),
                    icon = Icons.Rounded.Wikipedia,
                    switchValue = wikipedia == true,
                    onSwitchChanged = {
                        viewModel.setWikipedia(it)
                    },
                    onClick = {
                        navController?.navigate("settings/search/wikipedia")
                    }
                )

                val websites by viewModel.websites.collectAsStateWithLifecycle(null)
                SwitchPreference(
                    title = stringResource(R.string.preference_search_websites),
                    summary = stringResource(R.string.preference_search_websites_summary),
                    icon = Icons.Rounded.Public,
                    value = websites == true,
                    onValueChanged = {
                        viewModel.setWebsites(it)
                    }
                )

                val locations by viewModel.locations.collectAsStateWithLifecycle(null)
                PreferenceWithSwitch(
                    title = stringResource(R.string.preference_search_locations),
                    summary = stringResource(R.string.preference_search_locations_summary),
                    icon = Icons.Rounded.Place,
                    switchValue = locations == true,
                    onSwitchChanged = {
                        viewModel.setLocations(it)
                    },
                    onClick = {
                        navController?.navigate("settings/search/locations")
                    }
                )

                Preference(
                    title = stringResource(R.string.preference_screen_search_actions),
                    summary = stringResource(R.string.preference_search_search_actions_summary),
                    icon = Icons.Rounded.ArrowOutward,
                    onClick = {
                        navController?.navigate("settings/search/searchactions")
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.preference_hidden_items),
                    summary = stringResource(R.string.preference_hidden_items_summary),
                    icon = Icons.Rounded.VisibilityOff,
                    onClick = {
                        navController?.navigate("settings/search/hiddenitems")
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_screen_tags),
                    summary = stringResource(R.string.preference_screen_tags_summary),
                    icon = Icons.Rounded.Tag,
                    onClick = {
                        navController?.navigate("settings/search/tags")
                    }
                )
            }
        }
        item {
            val filterBar by viewModel.filterBar.collectAsStateWithLifecycle(null)
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.preference_default_filter),
                    summary = stringResource(R.string.preference_default_filter_summary),
                    icon = Icons.Rounded.FilterAlt,
                    onClick = {
                        showFilterEditor = true
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_filter_bar),
                    summary = stringResource(R.string.preference_filter_bar_summary),
                    value = filterBar == true,
                    onValueChanged = {
                        viewModel.setFilterBar(it)
                    }
                )
            }
        }
        if (hasWorkProfile) {
            item {
                PreferenceCategory {
                    val separateWorkProfile by viewModel.separateWorkProfile.collectAsStateWithLifecycle(
                        null
                    )
                    SwitchPreference(
                        title = stringResource(R.string.preference_search_bar_separate_work_profile),
                        summary = stringResource(R.string.preference_search_bar_separate_work_profile_summary),
                        icon = Icons.Rounded.Work,
                        value = separateWorkProfile == true,
                        onValueChanged = {
                            viewModel.setSeparateWorkProfile(it)
                        }
                    )
                }
            }
        }
        item {
            PreferenceCategory {
                val autoFocus by viewModel.autoFocus.collectAsStateWithLifecycle(null)
                SwitchPreference(
                    title = stringResource(R.string.preference_search_bar_auto_focus),
                    summary = stringResource(R.string.preference_search_bar_auto_focus_summary),
                    icon = Icons.Rounded.Keyboard,
                    value = autoFocus == true,
                    onValueChanged = {
                        viewModel.setAutoFocus(it)
                    }
                )
                val launchOnEnter by viewModel.launchOnEnter.collectAsStateWithLifecycle(null)
                SwitchPreference(
                    title = stringResource(R.string.preference_search_bar_launch_on_enter),
                    summary = stringResource(R.string.preference_search_bar_launch_on_enter_summary),
                    value = launchOnEnter == true,
                    onValueChanged = {
                        viewModel.setLaunchOnEnter(it)
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                val searchResultOrdering by viewModel.searchResultOrdering.collectAsStateWithLifecycle(
                    null
                )
                ListPreference(
                    title = stringResource(R.string.preference_search_result_ordering),
                    items = listOf(
                        stringResource(R.string.preference_search_result_ordering_alphabetic) to SearchResultOrder.Alphabetical,
                        stringResource(R.string.preference_search_result_ordering_weighted) to SearchResultOrder.Weighted
                    ),
                    value = searchResultOrdering,
                    onValueChanged = {
                        if (it != null) viewModel.setSearchResultOrdering(it)
                    },
                    icon = Icons.Rounded.Sort
                )

                val reverseSearchResults by viewModel.reverseSearchResults.collectAsStateWithLifecycle(
                    null
                )
                ListPreference(
                    title = stringResource(R.string.preference_layout_search_results),
                    items = listOf(
                        stringResource(R.string.search_results_order_top_down) to false,
                        stringResource(R.string.search_results_order_bottom_up) to true,
                    ),
                    value = reverseSearchResults,
                    onValueChanged = {
                        if (it != null) viewModel.setReverseSearchResults(it)
                    },
                )
            }
        }
    }

    if (showFilterEditor) {
        val filters by viewModel.searchFilters.collectAsStateWithLifecycle()
        BottomSheetDialog(onDismissRequest = { showFilterEditor = false }) {
            Column(
                modifier = Modifier.padding(it)
            ) {
                AnimatedVisibility(filters.allowNetwork) {
                    SmallMessage(
                        modifier = Modifier.padding(bottom = 16.dp),
                        icon = Icons.Rounded.Warning,
                        text = stringResource(R.string.filter_settings_network_warning)
                    )
                }
                SearchFilters(
                    filters = filters,
                    onFiltersChange = {
                        viewModel.setSearchFilters(it)
                    },
                    settings = true,
                )
            }
        }
    }
}