package de.mm20.launcher2.ui.launcher.search.contacts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.search.common.list.SearchResultList

@Composable
fun ColumnScope.ContactResults() {
    val viewModel: SearchVM = viewModel()
    val contacts by viewModel.contactResults.observeAsState(emptyList())

    AnimatedVisibility(contacts.isNotEmpty()) {
        LauncherCard(
            modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
        ) {
            SearchResultList(items = contacts)
        }
    }
}