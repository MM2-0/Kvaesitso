package de.mm20.launcher2.ui.settings.search

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.preferences.*
import de.mm20.launcher2.ui.icons.Wikipedia
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun SearchSettingsScreen() {

    val viewModel: SearchSettingsScreenVM = viewModel()
    val context = LocalContext.current

    val navController = LocalNavController.current

    PreferenceScreen(title = stringResource(R.string.preference_screen_search)) {
        item {
            PreferenceCategory {
                val favorites by viewModel.favorites.observeAsState()
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

                val hasContactsPermission by viewModel.hasContactsPermission.observeAsState()
                AnimatedVisibility(hasContactsPermission == false) {
                    MissingPermissionBanner(
                        text = stringResource(R.string.missing_permission_contact_search_settings),
                        onClick = {
                            viewModel.requestContactsPermission(context as AppCompatActivity)
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
                val contacts by viewModel.contacts.observeAsState()
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

                val hasCalendarPermission by viewModel.hasCalendarPermission.observeAsState()
                AnimatedVisibility(hasCalendarPermission == false) {
                    MissingPermissionBanner(
                        text = stringResource(R.string.missing_permission_calendar_search_settings),
                        onClick = {
                            viewModel.requestCalendarPermission(context as AppCompatActivity)
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
                val calendar by viewModel.calendar.observeAsState()
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

                val hasAppShortcutsPermission by viewModel.hasAppShortcutPermission.observeAsState()
                AnimatedVisibility(hasAppShortcutsPermission == false) {
                    MissingPermissionBanner(
                        text = stringResource(R.string.missing_permission_appshortcuts_search_settings, stringResource(R.string.app_name)),
                        onClick = {
                            viewModel.requestAppShortcutsPermission(context as AppCompatActivity)
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
                val appShortcuts by viewModel.appShortcuts.observeAsState()
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

                val calculator by viewModel.calculator.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_search_calculator),
                    summary = stringResource(R.string.preference_search_calculator_summary),
                    icon = Icons.Rounded.Calculate,
                    value = calculator == true,
                    onValueChanged = {
                        viewModel.setCalculator(it)
                    }
                )

                val unitConverter by viewModel.unitConverter.observeAsState()
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

                val wikipedia by viewModel.wikipedia.observeAsState()
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

                val websites by viewModel.websites.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_search_websites),
                    summary = stringResource(R.string.preference_search_websites_summary),
                    icon = Icons.Rounded.Language,
                    value = websites == true,
                    onValueChanged = {
                        viewModel.setWebsites(it)
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
            val autoFocus by viewModel.autoFocus.observeAsState()
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(R.string.preference_search_bar_auto_focus),
                    summary = stringResource(R.string.preference_search_bar_auto_focus_summary),
                    value = autoFocus == true,
                    onValueChanged = {
                    viewModel.setAutoFocus(it)
                })
                Preference(
                    title = stringResource(R.string.preference_hidden_items),
                    summary = stringResource(R.string.preference_hidden_items_summary),
                    onClick = {
                        navController?.navigate("settings/search/hiddenitems")
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_screen_tags),
                    summary = stringResource(R.string.preference_screen_tags_summary),
                    onClick = {
                        navController?.navigate("settings/search/tags")
                    }
                )
            }
        }
    }
}