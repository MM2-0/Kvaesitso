package de.mm20.launcher2.ui.launcher.search.appshortcuts

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
fun ColumnScope.AppShortcutResults() {
    val viewModel: SearchVM = viewModel()
    val shortcuts by viewModel.appShortcutResults.observeAsState(emptyList())
    val context = LocalContext.current

    val isSearchEmpty by viewModel.isSearchEmpty.observeAsState(true)
    val missingPermission by viewModel.missingAppShortcutPermission.collectAsState(false)

    AnimatedVisibility(shortcuts.isNotEmpty() || (!isSearchEmpty && missingPermission)) {
        LauncherCard(
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Column {
                AnimatedVisibility(!isSearchEmpty && missingPermission) {
                    MissingPermissionBanner(
                        text = stringResource(R.string.missing_permission_appshortcuts_search, stringResource(R.string.app_name)),
                        onClick = { viewModel.requestAppShortcutPermission(context as AppCompatActivity) },
                        modifier = Modifier.padding(16.dp),
                        secondaryAction = {
                            TextButton(onClick = {
                                viewModel.disableAppShortcutSearch()
                            }) {
                                Text(
                                    stringResource(R.string.turn_off),
                                )
                            }
                        }
                    )
                }
            }
            SearchResultList(
                items = shortcuts,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )
        }
    }

}