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
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.launcher.LauncherScaffoldVM
import de.mm20.launcher2.ui.launcher.helper.WallpaperBlur
import de.mm20.launcher2.ui.launcher.search.SearchColumn
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.searchbar.LauncherSearchBar
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Composable
fun AssistantScaffold(
    modifier: Modifier = Modifier,
    darkStatusBarIcons: Boolean = false,
    darkNavBarIcons: Boolean = false,
    bottomSearchBar: Boolean = false,
    reverseSearchResults: Boolean = false,
    fixedSearchBar: Boolean = false,
) {
    val viewModel: LauncherScaffoldVM = viewModel()

    var searchBarFocused by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(null) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            searchBarFocused = false
            if (!viewModel.autoFocusSearch.first()) return@repeatOnLifecycle
            delay(100)
            searchBarFocused = true
            keyboardController?.show()
        }
    }

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

    val searchBarLevel by remember {
        derivedStateOf {
            when {
                reverseSearchResults == bottomSearchBar && isSearchAtStart -> SearchBarLevel.Active
                reverseSearchResults != bottomSearchBar && isSearchAtEnd -> SearchBarLevel.Active
                else -> SearchBarLevel.Raised
            }
        }
    }

    val systemUiController = rememberSystemUiController()
    val showStatusBarScrim by remember {
        derivedStateOf {
            if (reverseSearchResults) {
                !isSearchAtEnd
            } else {
                !isSearchAtStart
            }
        }
    }
    val showNavBarScrim by remember {
        derivedStateOf {
            if (reverseSearchResults) {
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

    WallpaperBlur {
        true
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
                val y = available.y * if (reverseSearchResults) -1f else 1f
                searchBarOffset = (searchBarOffset + y).coerceIn(-maxSearchBarOffset, 0f)
                return super.onPreScroll(available, source)
            }
        }
    }

    val searchVM: SearchVM = viewModel()
    val actions by searchVM.searchActionResults.observeAsState(emptyList())
    val webSearchPadding by animateDpAsState(
        if (actions.isEmpty()) 0.dp else 48.dp
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
                top = (if (bottomSearchBar) 0.dp else 56.dp + webSearchPadding) + 4.dp + windowInsets.calculateTopPadding(),
                bottom = (if (bottomSearchBar) 56.dp + webSearchPadding else 0.dp) + 4.dp + windowInsets.calculateBottomPadding()
            ),
            reverse = reverseSearchResults,
            state = searchState
        )

        val value by searchVM.searchQuery.observeAsState("")

        val searchBarColor by viewModel.searchBarColor.observeAsState(Settings.SearchBarSettings.SearchBarColors.Auto)
        val searchBarStyle by viewModel.searchBarStyle.observeAsState(Settings.SearchBarSettings.SearchBarStyle.Transparent)

        LauncherSearchBar(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(if (bottomSearchBar) Alignment.BottomCenter else Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp)
                .offset {
                    if (searchBarFocused || fixedSearchBar) IntOffset.Zero
                    else IntOffset(
                        0,
                        searchBarOffset.toInt() * if (bottomSearchBar) -1 else 1
                    )
                },
            level = { searchBarLevel },
            focused = searchBarFocused,
            onFocusChange = {
                if (it) viewModel.openSearch()
                viewModel.setSearchbarFocus(it)
            },
            actions = actions,
            showHiddenItemsButton = true,
            value = { value },
            onValueChange = { searchVM.search(it) },
            darkColors = LocalPreferDarkContentOverWallpaper.current && searchBarColor == Settings.SearchBarSettings.SearchBarColors.Auto || searchBarColor == Settings.SearchBarSettings.SearchBarColors.Dark,
            style = searchBarStyle,
            reverse = bottomSearchBar
        )
    }
}