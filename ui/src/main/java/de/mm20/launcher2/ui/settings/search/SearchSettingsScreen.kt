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
                SwitchPreference(
                    title = stringResource(R.string.preference_search_favorites),
                    summary = stringResource(R.string.preference_search_favorites_summary),
                    icon = Icons.Rounded.Star,
                    value = favorites == true,
                    onValueChanged = {
                        viewModel.setFavorites(it)
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
                        text = stringResource(R.string.missing_permission_contact_search),
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
                        text = stringResource(R.string.missing_permission_calendar_search),
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
                SwitchPreference(
                    title = stringResource(R.string.preference_search_unitconverter),
                    summary = stringResource(R.string.preference_search_unitconverter_summary),
                    icon = Icons.Rounded.Loop,
                    value = unitConverter == true,
                    onValueChanged = {
                        viewModel.setUnitConverter(it)
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

                val webSearch by viewModel.webSearch.observeAsState()
                PreferenceWithSwitch(
                    title = stringResource(R.string.preference_search_websearch),
                    summary = stringResource(R.string.preference_search_websearch_summary),
                    icon = Icons.Rounded.TravelExplore,
                    switchValue = webSearch == true,
                    onSwitchChanged = {
                        viewModel.setWebSearch(it)
                    }
                )
            }
        }
    }
}