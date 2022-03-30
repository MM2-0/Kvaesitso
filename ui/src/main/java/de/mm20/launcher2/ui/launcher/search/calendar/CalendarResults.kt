package de.mm20.launcher2.ui.launcher.search.calendar

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.search.common.list.SearchResultList

@Composable
fun ColumnScope.CalendarResults() {
    val viewModel: SearchVM = viewModel()
    val calendarEvents by viewModel.calendarResults.observeAsState(emptyList())
    val context = LocalContext.current

    val isSearchEmpty by viewModel.isSearchEmpty.observeAsState(true)
    val missingPermission by viewModel.missingCalendarPermission.collectAsState(false)
    AnimatedVisibility(calendarEvents.isNotEmpty() || (!isSearchEmpty && missingPermission)) {
        LauncherCard(
            modifier = Modifier
                .padding(bottom = 8.dp)
                .fillMaxWidth()
        ) {
            Column {
                AnimatedVisibility(!isSearchEmpty && missingPermission) {
                    MissingPermissionBanner(
                        text = stringResource(R.string.missing_permission_calendar_search),
                        onClick = { viewModel.requestCalendarPermission(context as AppCompatActivity) },
                        modifier = Modifier.padding(16.dp),
                        secondaryAction = {
                            TextButton(onClick = {
                                viewModel.disableCalendarSearch()
                            }) {
                                Text(
                                    stringResource(R.string.turn_off),
                                )
                            }
                        }
                    )
                }

                SearchResultList(
                    items = calendarEvents,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }
        }
    }
}
