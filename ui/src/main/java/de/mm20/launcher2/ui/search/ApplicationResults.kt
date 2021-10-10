package de.mm20.launcher2.ui.search

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.applications.AppViewModel
import org.koin.androidx.compose.getViewModel

@Composable
fun applicationResults(): LazyListScope.(listState: LazyListState) -> Unit {
    val viewModel: AppViewModel = getViewModel()
    val apps by viewModel.applications.observeAsState(emptyList())
    return {
        SearchableGrid(items = apps, listState = it)
    }
}