package de.mm20.launcher2.ui.search

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.files.FilesViewModel

@Composable
fun fileResults(): LazyListScope.() -> Unit {
    val files by viewModel<FilesViewModel>().files.observeAsState(emptyList())
    return {
        files?.let { SearchableList(items = it) }
    }
}