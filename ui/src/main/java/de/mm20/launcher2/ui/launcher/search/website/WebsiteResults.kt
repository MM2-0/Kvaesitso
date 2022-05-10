package de.mm20.launcher2.ui.launcher.search.website

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.launcher.search.SearchVM

@Composable
fun ColumnScope.WebsiteResults(reverse: Boolean = false) {
    val viewModel: SearchVM = viewModel()
    val website by viewModel.websiteResult.observeAsState(null)

    AnimatedVisibility(website != null) {
        LauncherCard(
            modifier = Modifier
                .padding(bottom = if (reverse) 0.dp else 8.dp, top = if (reverse) 8.dp else 0.dp)
        ) {
            website?.let { WebsiteItem(website = it) }
        }
    }

}