package de.mm20.launcher2.ui.settings.search

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AppShortcut
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.icons.Wikipedia
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.SmallMessage
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.PreferenceWithSwitch
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.launcher.search.filters.SearchFilters
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun SearchSettingsScreen() {

    val viewModel: SearchSettingsScreenVM = viewModel()
    val context = LocalContext.current

    val navController = LocalNavController.current

    var showFilterEditor by remember { mutableStateOf(false) }

    val plugins by viewModel.plugins.collectAsStateWithLifecycle(null)
    val hasCalendarPlugins by remember { derivedStateOf { plugins?.any { it.plugin.type == PluginType.Calendar } } }
    val hasLocationPlugins by remember { derivedStateOf { plugins?.any { it.plugin.type == PluginType.LocationSearch } } }
    val hasContactPlugins by remember { derivedStateOf { plugins?.any { it.plugin.type == PluginType.ContactSearch } } }
    val isTasksAppInstalled by viewModel.isTasksAppInstalled.collectAsStateWithLifecycle()

    val hasAppShortcutsPermission by viewModel.hasAppShortcutPermission.collectAsStateWithLifecycle(
        null
    )
    val hasContactsPermission by viewModel.hasContactsPermission.collectAsStateWithLifecycle(null)
    val hasCalendarPermission by viewModel.hasCalendarPermission.collectAsStateWithLifecycle(null)
    val hasLocationPermission by viewModel.hasLocationPermission.collectAsStateWithLifecycle(null)

    val favorites by viewModel.favorites.collectAsStateWithLifecycle(null)
    val appShortcuts by viewModel.appShortcuts.collectAsStateWithLifecycle(null)
    val calendar by viewModel.calendarSearch.collectAsStateWithLifecycle(null)
    val places by viewModel.placesSearch.collectAsStateWithLifecycle(null)
    val contacts by viewModel.contacts.collectAsStateWithLifecycle(null)
    val calculator by viewModel.calculator.collectAsStateWithLifecycle(null)
    val unitConverter by viewModel.unitConverter.collectAsStateWithLifecycle(null)
    val wikipedia by viewModel.wikipedia.collectAsStateWithLifecycle(null)
    val websites by viewModel.websites.collectAsStateWithLifecycle(null)


    val hidePrivateProfile by viewModel.hidePrivateProfile.collectAsStateWithLifecycle(null)
    val autoFocus by viewModel.autoFocus.collectAsStateWithLifecycle(null)
    val launchOnEnter by viewModel.launchOnEnter.collectAsStateWithLifecycle(null)
    val reverseSearchResults by viewModel.reverseSearchResults.collectAsStateWithLifecycle(null)
    val filterBar by viewModel.filterBar.collectAsStateWithLifecycle(null)

    PreferenceScreen(title = stringResource(R.string.preference_screen_search)) {
        item {
            PreferenceCategory {
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

                if (hasContactPlugins != false) {
                    Preference(
                        title = stringResource(R.string.preference_search_contacts),
                        summary = stringResource(R.string.preference_search_contacts_summary),
                        icon = Icons.Rounded.Person,
                        onClick = {
                            navController?.navigate("settings/search/contacts")
                        },
                    )
                } else {
                    GuardedPreference(
                        locked = hasContactsPermission == false,
                        onUnlock = {
                            viewModel.requestContactsPermission(context as AppCompatActivity)
                        },
                        description = stringResource(R.string.missing_permission_contact_search_settings),
                    ) {
                        PreferenceWithSwitch(
                            title = stringResource(R.string.preference_search_contacts),
                            summary = stringResource(R.string.preference_search_contacts_summary),
                            icon = Icons.Rounded.Person,
                            switchValue = contacts == true && hasContactsPermission == true,
                            onSwitchChanged = {
                                viewModel.setContacts(it)
                            },
                            onClick = {
                                navController?.navigate("settings/search/contacts")
                            },
                            enabled = hasContactsPermission == true
                        )
                    }
                }

                if (hasCalendarPlugins != false || isTasksAppInstalled != false) {
                    Preference(
                        title = stringResource(R.string.preference_search_calendar),
                        summary = stringResource(R.string.preference_search_calendar_summary),
                        icon = Icons.Rounded.Today,
                        onClick = {
                            navController?.navigate("settings/search/calendar")
                        },
                    )
                } else {

                    GuardedPreference(
                        locked = hasCalendarPermission == false,
                        onUnlock = {
                            viewModel.requestCalendarPermission(context as AppCompatActivity)
                        },
                        description = stringResource(R.string.missing_permission_calendar_search_settings),
                    ) {
                        PreferenceWithSwitch(
                            title = stringResource(R.string.preference_search_calendar),
                            summary = stringResource(R.string.preference_search_calendar_summary),
                            switchValue = calendar == true,
                            onSwitchChanged = {
                                viewModel.setCalendarSearch(it)
                            },
                            icon = Icons.Rounded.Today,
                            enabled = hasCalendarPermission == true,
                            onClick = {
                                navController?.navigate("settings/search/calendar/local")
                            }
                        )
                    }
                }
                GuardedPreference(
                    locked = hasAppShortcutsPermission == false,
                    onUnlock = {
                        viewModel.requestAppShortcutsPermission(context as AppCompatActivity)
                    },
                    description = stringResource(
                        R.string.missing_permission_appshortcuts_search_settings,
                        stringResource(R.string.app_name),
                    ),
                ) {
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
                }

                SwitchPreference(
                    title = stringResource(R.string.preference_search_calculator),
                    summary = stringResource(R.string.preference_search_calculator_summary),
                    icon = Icons.Rounded.Calculate,
                    value = calculator == true,
                    onValueChanged = {
                        viewModel.setCalculator(it)
                    }
                )

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

                SwitchPreference(
                    title = stringResource(R.string.preference_search_websites),
                    summary = stringResource(R.string.preference_search_websites_summary),
                    icon = Icons.Rounded.Public,
                    value = websites == true,
                    onValueChanged = {
                        viewModel.setWebsites(it)
                    }
                )
                GuardedPreference(
                    locked = hasLocationPermission == false,
                    onUnlock = {
                        viewModel.requestLocationPermission(context as AppCompatActivity)
                    },
                    description = stringResource(R.string.missing_permission_location_search),
                ) {
                    if (hasLocationPlugins != false) {
                        Preference(
                            title = stringResource(R.string.preference_search_locations),
                            summary = stringResource(R.string.preference_search_locations_summary),
                            icon = Icons.Rounded.Place,
                            enabled = hasLocationPermission == true,
                            onClick = {
                                navController?.navigate("settings/search/locations")
                            }
                        )
                    } else {
                        PreferenceWithSwitch(
                            title = stringResource(R.string.preference_search_locations),
                            summary = stringResource(R.string.preference_search_locations_summary),
                            icon = Icons.Rounded.Place,
                            onClick = {
                                navController?.navigate("settings/search/locations")
                            },
                            switchValue = places == true,
                            onSwitchChanged = {
                                viewModel.setPlacesSearch(it)
                            },
                            enabled = hasLocationPermission == true,
                        )
                    }
                }

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
                SwitchPreference(
                    title = stringResource(R.string.preference_hide_private_profile),
                    iconPadding = true,
                    summary = stringResource(R.string.preference_hide_private_profile_summary),
                    value = hidePrivateProfile == true,
                    onValueChanged = {
                        viewModel.setHidePrivateProfile(it)
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
                    iconPadding = true,
                    summary = stringResource(R.string.preference_filter_bar_summary),
                    value = filterBar == true,
                    onValueChanged = {
                        viewModel.setFilterBar(it)
                    }
                )
                AnimatedVisibility(filterBar == true) {
                    Preference(
                        title = stringResource(R.string.preference_customize_filter_bar),
                        iconPadding = true,
                        summary = stringResource(R.string.preference_customize_filter_bar_summary),
                        onClick = {
                            navController?.navigate("settings/search/filterbar")
                        }
                    )
                }
            }
        }
        item {
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(R.string.preference_search_bar_auto_focus),
                    summary = stringResource(R.string.preference_search_bar_auto_focus_summary),
                    icon = Icons.Rounded.Keyboard,
                    value = autoFocus == true,
                    onValueChanged = {
                        viewModel.setAutoFocus(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_search_bar_launch_on_enter),
                    iconPadding = true,
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
                    icon = Icons.AutoMirrored.Rounded.Sort
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