package de.mm20.launcher2.ui.launcher.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.launcher.search.SearchColumn
import de.mm20.launcher2.ui.launcher.search.SearchVM

internal class SearchComponent(
    private val reverse: Boolean = false,
    private val openKeyboard: Boolean = true,
) : ScaffoldComponent() {

    override val isAtTop: MutableState<Boolean?> = mutableStateOf(true)

    override val isAtBottom: MutableState<Boolean?> = mutableStateOf(true)

    override val reverseScrolling: Boolean = reverse

    override val hasIme: Boolean = true


    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        val searchVM = viewModel<SearchVM>()
        val lazyListState = rememberLazyListState()

        LaunchedEffect(isActive) {
            if (!isActive) {
                searchVM.reset()
                lazyListState.scrollToItem(0, 0)
            }
        }

        LaunchedEffect(searchVM.searchQuery.value, searchVM.filters.value) {
            lazyListState.requestScrollToItem(0, 0)
        }

        LaunchedEffect(lazyListState.canScrollForward, lazyListState.canScrollBackward) {
            isAtBottom.value =
                !lazyListState.canScrollForward && !reverse || !lazyListState.canScrollBackward && reverse
            isAtTop.value =
                !lazyListState.canScrollForward && reverse || !lazyListState.canScrollBackward && !reverse
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

        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {

            SearchColumn(
                modifier = Modifier.nestedScroll(scrollConnection).widthIn(max = 916.dp).fillMaxHeight(),
                paddingValues = insets,
                state = lazyListState,
                reverse = reverse,
                userScrollEnabled = !state.isDragged,
            )
        }
    }

    override suspend fun onDismiss(state: LauncherScaffoldState) {
        super.onDismiss(state)
    }

    override suspend fun onPreActivate(state: LauncherScaffoldState) {
        super.onPreActivate(state)
        if (openKeyboard) {
            state.isSearchBarFocused = true
        }
    }

    override suspend fun onPreDismiss(state: LauncherScaffoldState) {
        super.onPreDismiss(state)
        state.isSearchBarFocused = false
    }
}