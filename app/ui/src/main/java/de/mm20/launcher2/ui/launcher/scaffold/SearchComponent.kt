package de.mm20.launcher2.ui.launcher.scaffold

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.mm20.launcher2.ui.launcher.search.SearchColumn

internal object SearchComponent : ScaffoldComponent {

    val lazyListState = LazyListState()

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        Column(
            modifier = modifier,
        ) {
            SearchColumn(
                paddingValues = insets,
                state = lazyListState
            )
        }
    }

    override suspend fun onUnmount() {
        super.onUnmount()
        lazyListState.scrollToItem(0)
    }
}