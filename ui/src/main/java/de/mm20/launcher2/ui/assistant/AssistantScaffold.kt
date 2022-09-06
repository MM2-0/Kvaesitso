package de.mm20.launcher2.ui.assistant

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.launcher.LauncherScaffoldVM
import de.mm20.launcher2.ui.launcher.search.SearchBar
import de.mm20.launcher2.ui.launcher.search.SearchBarLevel
import de.mm20.launcher2.ui.launcher.search.SearchColumn
import de.mm20.launcher2.ui.launcher.search.SearchVM
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Composable
fun AssistantScaffold(
    modifier: Modifier = Modifier,
    darkStatusBarIcons: Boolean = false,
    darkNavBarIcons: Boolean = false,
) {
    val viewModel: LauncherScaffoldVM = viewModel()

    val bottomSearchBar by remember {
        viewModel.dataStore.data.map { it.appearance.layout != Settings.AppearanceSettings.Layout.PullDown }
    }.collectAsState(null)

    val reverseResults by remember {
        viewModel.dataStore.data.map { it.appearance.layout != Settings.AppearanceSettings.Layout.PullDown }
    }.collectAsState(null)

    val searchBarFocused by viewModel.searchBarFocused.observeAsState(false)

    val searchState = rememberLazyListState()

    val isSearchAtStart by remember {
        derivedStateOf {
            searchState.firstVisibleItemIndex == 0 && searchState.firstVisibleItemScrollOffset == 0
        }
    }

    val isSearchAtEnd by remember {
        derivedStateOf {
            val lastItem =
                searchState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            lastItem.offset + lastItem.size <= searchState.layoutInfo.viewportEndOffset - searchState.layoutInfo.afterContentPadding
        }
    }

    if (reverseResults == null || bottomSearchBar == null) return


    val searchBarLevel by remember {
        derivedStateOf {
            when {
                reverseResults == bottomSearchBar && isSearchAtStart -> SearchBarLevel.Active
                reverseResults != bottomSearchBar && isSearchAtEnd -> SearchBarLevel.Active
                else -> SearchBarLevel.Raised
            }
        }
    }

    val systemUiController = rememberSystemUiController()
    val showStatusBarScrim by remember {
        derivedStateOf {
            if (reverseResults == true) {
                !isSearchAtEnd
            } else {
                !isSearchAtStart
            }
        }
    }
    val showNavBarScrim by remember {
        derivedStateOf {
            if (reverseResults == true) {
                !isSearchAtStart
            } else {
                !isSearchAtEnd
            }
        }
    }


    val colorSurface = MaterialTheme.colorScheme.surface
    LaunchedEffect(darkStatusBarIcons, colorSurface, showStatusBarScrim) {
        if (showStatusBarScrim) {
            systemUiController.setStatusBarColor(
                colorSurface.copy(0.7f),
            )
        } else {
            systemUiController.setStatusBarColor(
                Color.Transparent,
                darkIcons = darkStatusBarIcons
            )
        }
    }

    LaunchedEffect(darkNavBarIcons, showNavBarScrim) {
        if (showNavBarScrim) {
            systemUiController.setNavigationBarColor(
                colorSurface.copy(0.7f),
            )
        } else {
            systemUiController.setNavigationBarColor(
                Color.Transparent,
                darkIcons = darkNavBarIcons,
                navigationBarContrastEnforced = false
            )
        }
    }

    val density = LocalDensity.current
    val maxSearchBarOffset = with(density) { 128.dp.toPx() }
    var searchBarOffset by remember {
        mutableStateOf(0f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val y = available.y * if (reverseResults == true) -1f else 1f
                searchBarOffset = (searchBarOffset + y).coerceIn(-maxSearchBarOffset, 0f)
                return super.onPreScroll(available, source)
            }
        }
    }

    val searchVM: SearchVM = viewModel()
    val websearches by searchVM.websearchResults.observeAsState(emptyList())
    val webSearchPadding by animateDpAsState(
        if (websearches.isEmpty()) 0.dp else 48.dp
    )
    val windowInsets = WindowInsets.safeDrawing.asPaddingValues()
    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        SearchColumn(
            modifier = Modifier.fillMaxSize(),
            paddingValues = PaddingValues(
                top = (if (bottomSearchBar == true) 0.dp else 56.dp + webSearchPadding) + 4.dp + windowInsets.calculateTopPadding(),
                bottom = (if (bottomSearchBar == true) 56.dp + webSearchPadding else 0.dp) + 4.dp + windowInsets.calculateBottomPadding()
            ),
            reverse = reverseResults == true,
            state = searchState
        )

        SearchBar(
            level = { searchBarLevel },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(if (bottomSearchBar == true) Alignment.BottomCenter else Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp)
                .offset {
                    if (searchBarFocused) IntOffset.Zero
                    else IntOffset(
                        0,
                        searchBarOffset.toInt() * if (bottomSearchBar == true) -1 else 1
                    )
                },
            focused = searchBarFocused,
            onFocusChange = {
                viewModel.setSearchbarFocus(it)
            },
            reverse = bottomSearchBar == true
        )
    }
}