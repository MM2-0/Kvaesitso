package de.mm20.launcher2.ui.launcher.search.contacts

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
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
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.search.data.Contact
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.search.common.list.SearchResultList

@Composable
fun ColumnScope.ContactResults(reverse: Boolean = false) {
    val viewModel: SearchVM = viewModel()
    val context = LocalContext.current
    val contacts by viewModel.contactResults.observeAsState(emptyList())

    val isSearchEmpty by viewModel.isSearchEmpty.observeAsState(true)
    val missingPermission by viewModel.missingContactsPermission.collectAsState(false)
    AnimatedVisibility(contacts.isNotEmpty() || (!isSearchEmpty && missingPermission)) {
        LauncherCard(
            modifier = Modifier
                .padding(bottom = if (reverse) 0.dp else 8.dp, top = if (reverse) 8.dp else 0.dp)
                .fillMaxWidth()
        ) {
            Column {
                AnimatedVisibility(!isSearchEmpty && missingPermission) {
                    MissingPermissionBanner(
                        text = stringResource(R.string.missing_permission_contact_search),
                        onClick = { viewModel.requestContactsPermission(context as AppCompatActivity) },
                        modifier = Modifier.padding(16.dp),
                        secondaryAction = {
                            OutlinedButton(onClick = {
                                viewModel.disableContactsSearch()
                            }) {
                                Text(
                                    stringResource(R.string.turn_off),
                                )
                            }
                        }
                    )
                }
                SearchResultList(
                    items = contacts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    reverse = reverse,
                    highlightedItem = viewModel.getBestMatch() as? Contact
                )
            }
        }
    }
}