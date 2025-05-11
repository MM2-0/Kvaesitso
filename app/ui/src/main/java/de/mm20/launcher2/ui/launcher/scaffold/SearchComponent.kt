package de.mm20.launcher2.ui.launcher.scaffold

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.launcher.search.SearchColumn
import de.mm20.launcher2.ui.launcher.search.SearchVM

internal object SearchComponent : ScaffoldComponent {

    private val lazyListState = LazyListState()

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        val searchVM = viewModel<SearchVM>()

        val isActive by remember {
            derivedStateOf {
                state.currentComponent == this && state.currentProgress > 0.5f
            }
        }

        LaunchedEffect(isActive) {
            if (!isActive) searchVM.reset()
            state.isSearchBarFocused = isActive
        }

        val scrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    state.isSearchBarFocused = false
                    state.onComponentScroll(
                        -consumed.y,
                        lazyListState.canScrollForward,
                        lazyListState.canScrollBackward
                    )
                    return super.onPostScroll(consumed, available, source)
                }
            }
        }

        Column(
            modifier = modifier.nestedScroll(scrollConnection),
        ) {
            SearchColumn(
                paddingValues = insets,
                state = lazyListState
            )
        }
    }

    override suspend fun onMount(state: LauncherScaffoldState) {
        super.onMount(state)
    }

    override suspend fun onUnmount(state: LauncherScaffoldState) {
        super.onUnmount(state)
        lazyListState.scrollToItem(0, 0)
    }
}