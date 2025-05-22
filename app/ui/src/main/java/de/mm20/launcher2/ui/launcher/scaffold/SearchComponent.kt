package de.mm20.launcher2.ui.launcher.scaffold

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.launcher.search.SearchColumn
import de.mm20.launcher2.ui.launcher.search.SearchVM

internal class SearchComponent(
    private val reverse: Boolean = false,
) : ScaffoldComponent() {

    private val lazyListState = LazyListState()

    override var isAtTop: State<Boolean?> = derivedStateOf {
        !lazyListState.canScrollForward && reverse || !lazyListState.canScrollBackward && !reverse
    }

    override var isAtBottom: State<Boolean?> = derivedStateOf {
        !lazyListState.canScrollForward && !reverse || !lazyListState.canScrollBackward && reverse
    }

    override val hasIme: Boolean = true


    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        val searchVM = viewModel<SearchVM>()

        LaunchedEffect(isActive) {
            if (!isActive) {
                searchVM.reset()
            }
        }

        val scrollConnection = remember(state) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    searchVM.bestMatch.value = null
                    state.isSearchBarFocused = false
                    state.onComponentScroll(
                        if (reverse) consumed.y else -consumed.y,
                    )
                    return super.onPostScroll(consumed, available, source)
                }
            }
        }

        SearchColumn(
            modifier.nestedScroll(scrollConnection),
            paddingValues = insets,
            state = lazyListState,
            reverse = reverse,
            userScrollEnabled = !state.isDragged,
        )
    }

    override suspend fun onDismiss(state: LauncherScaffoldState) {
        super.onDismiss(state)
        lazyListState.scrollToItem(0, 0)
    }

    override suspend fun onPreActivate(state: LauncherScaffoldState) {
        super.onPreActivate(state)
        state.isSearchBarFocused = true
    }

    override suspend fun onPreDismiss(state: LauncherScaffoldState) {
        super.onPreDismiss(state)
        state.isSearchBarFocused = false
    }
}