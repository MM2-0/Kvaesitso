package de.mm20.launcher2.ui.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.preferences.Settings.SearchBarSettings.SearchBarColors
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.gestures.LocalGestureDetector
import de.mm20.launcher2.ui.ktx.animateTo
import de.mm20.launcher2.ui.launcher.gestures.LauncherGestureHandler
import de.mm20.launcher2.ui.launcher.helper.WallpaperBlur
import de.mm20.launcher2.ui.launcher.search.SearchColumn
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.searchbar.LauncherSearchBar
import de.mm20.launcher2.ui.launcher.widgets.WidgetColumn
import de.mm20.launcher2.ui.launcher.widgets.clock.ClockWidget
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun PagerScaffold(
    modifier: Modifier = Modifier,
    darkStatusBarIcons: Boolean = false,
    darkNavBarIcons: Boolean = false,
    reverse: Boolean = false,
    bottomSearchBar: Boolean = true,
    reverseSearchResults: Boolean = true,
    fixedSearchBar: Boolean = false,
) {
    val viewModel: LauncherScaffoldVM = viewModel()
    val searchVM: SearchVM = viewModel()

    val context = LocalContext.current

    val isSearchOpen by viewModel.isSearchOpen
    val isWidgetEditMode by viewModel.isWidgetEditMode

    val actions by searchVM.searchActionResults

    val widgetsScrollState = rememberScrollState()
    val searchState = rememberLazyListState()

    val pagerState = rememberPagerState { 2 }

    val isSearchAtBottom by remember {
        derivedStateOf {
            if (reverseSearchResults) {
                searchState.firstVisibleItemIndex == 0 && searchState.firstVisibleItemScrollOffset == 0
            } else {
                val lastItem =
                    searchState.layoutInfo.visibleItemsInfo.lastOrNull()
                        ?: return@derivedStateOf true
                lastItem.offset + lastItem.size <= searchState.layoutInfo.viewportEndOffset - searchState.layoutInfo.afterContentPadding
            }
        }
    }

    val isSearchAtTop by remember {
        derivedStateOf {
            if (reverseSearchResults) {
                val lastItem =
                    searchState.layoutInfo.visibleItemsInfo.lastOrNull()
                        ?: return@derivedStateOf true
                lastItem.offset + lastItem.size <= searchState.layoutInfo.viewportEndOffset - searchState.layoutInfo.afterContentPadding
            } else {
                searchState.firstVisibleItemIndex == 0 && searchState.firstVisibleItemScrollOffset == 0
            }
        }
    }

    val showStatusBarScrim by remember {
        derivedStateOf {
            if (isSearchOpen) {
                !isSearchAtTop
            } else {
                widgetsScrollState.value > 0
            }
        }
    }

    val fillClockHeight by viewModel.fillClockHeight.collectAsState()

    val showNavBarScrim by remember {
        derivedStateOf {
            if (isSearchOpen) {
                !isSearchAtBottom
            } else {
                (widgetsScrollState.value > 0 || !fillClockHeight) && widgetsScrollState.value < widgetsScrollState.maxValue
            }
        }
    }

    val isWidgetsScrollZero by remember {
        derivedStateOf {
            widgetsScrollState.value == 0
        }
    }

    val systemUiController = rememberSystemUiController()

    val colorSurface = MaterialTheme.colorScheme.surface
    LaunchedEffect(isWidgetEditMode, darkStatusBarIcons, colorSurface, showStatusBarScrim) {
        if (isWidgetEditMode) {
            systemUiController.setStatusBarColor(
                colorSurface
            )
        } else if (showStatusBarScrim) {
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

    val blurEnabled by viewModel.wallpaperBlur.collectAsState()

    val blurWallpaper by remember {
        derivedStateOf {
            blurEnabled && (isSearchOpen || !isWidgetsScrollZero)
        }
    }

    WallpaperBlur {
        blurWallpaper
    }

    val currentPage = pagerState.currentPage
    LaunchedEffect(currentPage) {
        if (currentPage == 1) viewModel.openSearch()
        else viewModel.closeSearch()
    }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) pagerState.animateScrollToPage(1)
        else {
            pagerState.animateScrollToPage(0)
            searchVM.search("")
        }
    }

    val searchBarOffset = remember { mutableStateOf(0f) }

    val scope = rememberCoroutineScope()

    val handleBackOrHomeEvent = {
        when {
            isSearchOpen -> {
                viewModel.closeSearch()
                searchVM.search("")
                true
            }

            isWidgetEditMode -> {
                viewModel.setWidgetEditMode(false)
                true
            }

            widgetsScrollState.value != 0 -> {
                scope.launch {
                    widgetsScrollState.animateScrollTo(0)
                }
                scope.launch {
                    searchBarOffset.animateTo(0f)
                }
                true
            }

            else -> false
        }
    }

    BackHandler {
        handleBackOrHomeEvent()
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    val gestureManager = LocalGestureDetector.current

    val density = LocalDensity.current
    val maxSearchBarOffset = with(density) { 128.dp.toPx() }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val drag = gestureManager.currentDrag
                if (drag != null && (drag.y > 0 || (reverse && drag.x < 0 || !reverse && drag.x > 0))) {
                    gestureManager.dispatchDrag(available)
                    return available
                }
                return super.onPreScroll(available, source)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.Drag && !isWidgetEditMode) gestureManager.dispatchDrag(
                    available
                )
                val deltaSearchBarOffset =
                    consumed.y * if (isSearchOpen && reverseSearchResults) 1 else -1
                searchBarOffset.value =
                    (searchBarOffset.value + deltaSearchBarOffset).coerceIn(0f, maxSearchBarOffset)
                return super.onPostScroll(consumed, available, source)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val drag = gestureManager.currentDrag
                if (drag != null && (drag.y > 0 || (reverse && drag.x < 0 || !reverse && drag.x > 0))) {
                    gestureManager.dispatchDragEnd()
                    return available
                }
                gestureManager.dispatchDragEnd()
                return super.onPreFling(available)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        searchBarOffset.animateTo(0f)
    }

    val searchNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y.absoluteValue > available.x.absoluteValue * 2) {
                    viewModel.setSearchbarFocus(false)
                    searchVM.bestMatch.value = null
                }
                return super.onPreScroll(available, source)
            }
        }
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Box(
        modifier = modifier
    ) {

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val height by remember {
                derivedStateOf { maxHeight }
            }
            val width by remember {
                derivedStateOf { maxWidth }
            }

            CompositionLocalProvider(
                LocalOverscrollConfiguration provides null,
            ) {

                HorizontalPager(
                    modifier = Modifier.fillMaxSize(),
                    beyondBoundsPageCount = 1,
                    reverseLayout = reverse,
                    state = pagerState,
                    userScrollEnabled = !isWidgetEditMode,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        lowVelocityAnimationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
                    // FIXME: Workaround https://issuetracker.google.com/issues/276738324
                    pageNestedScrollConnection = nestedScrollConnection
                ) {
                    when (it) {
                        0 -> {
                            val editModePadding by animateDpAsState(if (isWidgetEditMode && bottomSearchBar) 56.dp else 0.dp)

                            val clockPadding by animateDpAsState(
                                if (isWidgetsScrollZero && fillClockHeight)
                                    insets.calculateBottomPadding() + if (bottomSearchBar) 64.dp else 0.dp
                                else 0.dp
                            )

                            val clockHeight by remember {
                                derivedStateOf {
                                    if (fillClockHeight) {
                                        height - (64.dp + insets.calculateTopPadding() + insets.calculateBottomPadding() - clockPadding)
                                    } else {
                                        null
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .requiredWidth(width)
                                    .fillMaxHeight()
                                    .pointerInput(gestureManager.shouldDetectDoubleTaps) {
                                        detectTapGestures(
                                            onDoubleTap = if (gestureManager.shouldDetectDoubleTaps) {
                                                {
                                                    if (!isWidgetEditMode) gestureManager.dispatchDoubleTap(
                                                        it
                                                    )
                                                }
                                            } else null,
                                            onLongPress = {
                                                if (!isWidgetEditMode) gestureManager.dispatchLongPress(
                                                    it
                                                )
                                            },
                                            onTap = {
                                                if (!isWidgetEditMode) gestureManager.dispatchTap(it)
                                            },
                                        )
                                    }
                                    .verticalScroll(widgetsScrollState)
                                    .windowInsetsPadding(WindowInsets.safeDrawing)
                                    .graphicsLayer {
                                        val pagerProgress =
                                            pagerState.currentPage + pagerState.currentPageOffsetFraction
                                        alpha = 1f - pagerProgress
                                    }
                                    .padding(8.dp)
                                    .padding(
                                        top = if (bottomSearchBar) 0.dp else 56.dp,
                                        bottom = if (bottomSearchBar) 56.dp else 0.dp,
                                    )
                                    .padding(top = editModePadding)
                            ) {

                                AnimatedVisibility(!isWidgetEditMode) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(clockHeight?.let { Modifier.height(it) }
                                                ?: Modifier)
                                            .padding(bottom = clockPadding),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        ClockWidget(
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                WidgetColumn(
                                    editMode = isWidgetEditMode,
                                    onEditModeChange = {
                                        viewModel.setWidgetEditMode(it)
                                    }
                                )
                            }
                        }

                        1 -> {
                            val webSearchPadding by animateDpAsState(
                                if (actions.isEmpty()) 0.dp else 48.dp
                            )
                            val windowInsets = WindowInsets.safeDrawing.asPaddingValues()
                            val paddingValues = if (bottomSearchBar) {
                                PaddingValues(
                                    top = 4.dp + windowInsets.calculateTopPadding(),
                                    bottom = 60.dp + webSearchPadding + windowInsets.calculateBottomPadding()
                                )
                            } else {
                                PaddingValues(
                                    bottom = 4.dp + windowInsets.calculateBottomPadding(),
                                    top = 60.dp + webSearchPadding + windowInsets.calculateTopPadding()
                                )
                            }
                            SearchColumn(
                                modifier = Modifier
                                    .requiredWidth(width)
                                    .fillMaxHeight()
                                    .graphicsLayer {
                                        val pagerProgress =
                                            pagerState.currentPage + pagerState.currentPageOffsetFraction
                                        alpha = pagerProgress
                                    }
                                    .nestedScroll(searchNestedScrollConnection)
                                    .padding(
                                        start = windowInsets.calculateStartPadding(
                                            LocalLayoutDirection.current
                                        ),
                                        end = windowInsets.calculateStartPadding(
                                            LocalLayoutDirection.current
                                        ),
                                    ),
                                reverse = reverseSearchResults,
                                state = searchState,
                                paddingValues = paddingValues,
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(visible = isWidgetEditMode,
            enter = slideIn { IntOffset(0, -it.height) },
            exit = slideOut { IntOffset(0, -it.height) }
        ) {
            CenterAlignedTopAppBar(
                modifier = Modifier.systemBarsPadding(),
                title = {
                    Text(stringResource(R.string.menu_edit_widgets))
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.setWidgetEditMode(false) }) {
                        Icon(imageVector = Icons.Rounded.Done, contentDescription = null)
                    }
                },
            )
        }

        val searchBarLevel by remember {
            derivedStateOf {
                when {
                    pagerState.currentPageOffsetFraction != 0f -> SearchBarLevel.Raised
                    !isSearchOpen && isWidgetsScrollZero && (fillClockHeight || !bottomSearchBar) -> SearchBarLevel.Resting
                    isSearchOpen && isSearchAtTop && !bottomSearchBar -> SearchBarLevel.Active
                    isSearchOpen && isSearchAtBottom && bottomSearchBar -> SearchBarLevel.Active
                    else -> SearchBarLevel.Raised
                }
            }
        }

        val focusSearchBar by viewModel.searchBarFocused

        val widgetEditModeOffset by animateDpAsState(
            (if (isWidgetEditMode) 128.dp else 0.dp) * (if (bottomSearchBar) 1 else -1)
        )

        val value by searchVM.searchQuery

        val searchBarColor by viewModel.searchBarColor.collectAsState()
        val searchBarStyle by viewModel.searchBarStyle.collectAsState()

        val launchOnEnter by searchVM.launchOnEnter.collectAsState(false)

        LauncherSearchBar(
            modifier = Modifier
                .align(if (bottomSearchBar) Alignment.BottomCenter else Alignment.TopCenter)
                .fillMaxWidth()
                .wrapContentHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp)
                .offset {
                    IntOffset(
                        0,
                        if (focusSearchBar || fixedSearchBar) 0 else searchBarOffset.value.toInt() * if (bottomSearchBar) 1 else -1
                    )
                }
                .offset {
                    IntOffset(
                        0,
                        with(density) {
                            widgetEditModeOffset
                                .toPx()
                                .roundToInt()
                        })
                },
            level = { searchBarLevel },
            focused = focusSearchBar,
            onFocusChange = {
                if (it) viewModel.openSearch()
                viewModel.setSearchbarFocus(it)
            },
            actions = actions,
            highlightedAction = searchVM.bestMatch.value as? SearchAction,
            showHiddenItemsButton = isSearchOpen,
            value = { value },
            onValueChange = { searchVM.search(it) },
            darkColors = LocalPreferDarkContentOverWallpaper.current && searchBarColor == SearchBarColors.Auto || searchBarColor == SearchBarColors.Dark,
            style = searchBarStyle,
            reverse = bottomSearchBar,
            onKeyboardActionGo = if (launchOnEnter) {
                { searchVM.launchBestMatchOrAction(context) }
            } else null
        )
    }
    LauncherGestureHandler(
        onHomeButtonPress = handleBackOrHomeEvent,
    )
}

private enum class Page {
    Widgets,
    Search
}