package de.mm20.launcher2.ui.launcher

import android.app.WallpaperManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.VerticalPager
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.preferences.Settings
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
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun PullDownScaffold(
    modifier: Modifier = Modifier,
    darkStatusBarIcons: Boolean = false,
    darkNavBarIcons: Boolean = false,
    bottomSearchBar: Boolean = false,
    reverseSearchResults: Boolean = false,
    fixedSearchBar: Boolean = false,
) {
    val viewModel: LauncherScaffoldVM = viewModel()
    val searchVM: SearchVM = viewModel()

    val density = LocalDensity.current
    val context = LocalContext.current

    val actions by searchVM.searchActionResults

    val isSearchOpen by viewModel.isSearchOpen
    val isWidgetEditMode by viewModel.isWidgetEditMode

    val widgetsScrollState = rememberScrollState()
    val searchState = rememberLazyListState()

    val pagerState = rememberPagerState { 2 }

    val offsetY = remember { mutableStateOf(0f) }
    val maxOffset = with(density) { 64.dp.toPx() }
    val toggleSearchThreshold = with(density) { 48.dp.toPx() }

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

    val isOverThreshold by remember {
        derivedStateOf {
            offsetY.value.absoluteValue > toggleSearchThreshold
        }
    }

    val dragProgress by remember {
        derivedStateOf {
            (offsetY.value.absoluteValue / toggleSearchThreshold).coerceAtMost(1f)
        }
    }

    val systemUiController = rememberSystemUiController()

    val isWidgetsAtStart by remember {
        derivedStateOf {
            widgetsScrollState.value == 0
        }
    }

    val isWidgetsAtEnd by remember {
        derivedStateOf {
            widgetsScrollState.value >= widgetsScrollState.maxValue
        }
    }

    val fillClockHeight by viewModel.fillClockHeight.collectAsState()

    val showStatusBarScrim by remember {
        derivedStateOf {
            if (isSearchOpen) {
                !isSearchAtTop
            } else {
                widgetsScrollState.value > 0
            }
        }
    }
    val showNavBarScrim by remember {
        derivedStateOf {
            if (isSearchOpen) {
                !isSearchAtBottom
            } else {
                (widgetsScrollState.value > 0 || !fillClockHeight) && widgetsScrollState.value < widgetsScrollState.maxValue
            }
        }
    }

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

    val searchBarOffset = remember { mutableStateOf(0f) }

    val maxSearchBarOffset = with(density) { 128.dp.toPx() }

    val blurEnabled by viewModel.wallpaperBlur.collectAsState()
    val blurRadius by viewModel.wallpaperBlurRadius.collectAsState()

    val blurWallpaper by remember {
        derivedStateOf {
            blurEnabled && (isSearchOpen || isOverThreshold || widgetsScrollState.value > 0)
        }
    }

    WallpaperBlur {
        if (blurWallpaper) blurRadius else 0
    }


    val scope = rememberCoroutineScope()

    LaunchedEffect(isSearchOpen) {
        launch {
            searchBarOffset.animateTo(0f)
        }
        if (isSearchOpen) {
            searchState.scrollToItem(0)
            pagerState.animateScrollToPage(
                1,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        } else {
            searchVM.search("")
            if (viewModel.skipNextSearchAnimation) {
                pagerState.scrollToPage(0)
                viewModel.skipNextSearchAnimation = false
            } else {
                pagerState.animateScrollToPage(
                    0,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
            }
        }
    }

    LaunchedEffect(isWidgetEditMode) {
        if (!isWidgetEditMode) searchBarOffset.value = 0f
    }

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
    val hapticFeedback = LocalHapticFeedback.current
    val view = LocalView.current

    LaunchedEffect(isOverThreshold) {
        if (isOverThreshold) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } else if (offsetY.value != 0f) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isWidgetEditMode || source != NestedScrollSource.Drag) return Offset.Zero
                if (available.y.absoluteValue > available.x.absoluteValue * 2) {
                    viewModel.setSearchbarFocus(false)
                    searchVM.bestMatch.value = null
                }
                val canPullDown = if (isSearchOpen) {
                    isSearchAtTop
                } else {
                    isWidgetsAtStart
                }
                val canPullUp = isSearchOpen && isSearchAtBottom

                val consumed = when {
                    canPullUp && available.y < 0 || offsetY.value < 0 -> {
                        val consumed = available.y
                        offsetY.value = (offsetY.value + (consumed * 0.5f)).coerceIn(-maxOffset, 0f)
                        consumed
                    }

                    canPullDown && available.y > 0 || offsetY.value > 0 -> {
                        val consumed = available.y
                        offsetY.value = (offsetY.value + (consumed * 0.5f)).coerceIn(0f, maxOffset)
                        consumed
                    }

                    else -> 0f
                }

                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val deltaSearchBarOffset =
                    consumed.y * if (isSearchOpen && reverseSearchResults) 1 else -1
                searchBarOffset.value =
                    (searchBarOffset.value + deltaSearchBarOffset).coerceIn(0f, maxSearchBarOffset)
                return super.onPostScroll(consumed, available, source)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY.value > toggleSearchThreshold || offsetY.value < -toggleSearchThreshold) {
                    viewModel.toggleSearch()
                }
                if (!isWidgetEditMode) gestureManager.dispatchDragEnd()
                if (offsetY.value != 0f) {
                    offsetY.animateTo(0f)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (!isWidgetEditMode) gestureManager.dispatchDragEnd()
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        if (!isWidgetEditMode) gestureManager.dispatchDrag(Offset(dragAmount, 0f))
                    }
                )
            }
            .offset { IntOffset(0, offsetY.value.toInt()) },
        contentAlignment = Alignment.TopCenter
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
        ) {
            val height by remember {
                derivedStateOf { maxHeight }
            }
            CompositionLocalProvider(
                LocalOverscrollConfiguration provides null
            ) {
                VerticalPager(
                    modifier = Modifier.fillMaxSize(),
                    beyondBoundsPageCount = 1,
                    state = pagerState,
                    reverseLayout = true,
                    userScrollEnabled = false,
                    pageNestedScrollConnection = nestedScrollConnection,
                ) {
                    when (it) {

                        0 -> {
                            val clockPadding by animateDpAsState(
                                if (isWidgetsAtStart && fillClockHeight)
                                    insets.calculateBottomPadding() + if (bottomSearchBar) 64.dp else 0.dp
                                else 0.dp

                            )
                            val editModePadding by animateDpAsState(if (isWidgetEditMode && bottomSearchBar) 56.dp else 0.dp)

                            val clockHeight by remember {
                                derivedStateOf {
                                    if (fillClockHeight) {
                                        height - (insets.calculateTopPadding() + insets.calculateBottomPadding() - clockPadding + 56.dp)
                                    } else {
                                        null
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .graphicsLayer {
                                        val progress =
                                            pagerState.currentPage + pagerState.currentPageOffsetFraction
                                        transformOrigin = TransformOrigin.Center
                                        alpha = 1 - progress
                                        scaleX = 1f - (dragProgress * 0.05f)
                                        scaleY = 1f - (dragProgress * 0.05f)
                                    }
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
                                    .fillMaxSize()
                                    .verticalScroll(widgetsScrollState)
                                    .windowInsetsPadding(WindowInsets.safeDrawing)
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
                                    modifier = Modifier.fillMaxWidth(),
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
                            SearchColumn(
                                modifier = Modifier
                                    .graphicsLayer {
                                        val progress =
                                            pagerState.currentPage + pagerState.currentPageOffsetFraction
                                        transformOrigin = TransformOrigin.Center
                                        alpha = min(progress, 1f - dragProgress * 0.1f)
                                        scaleX = min(1f - (dragProgress * 0.05f), 1f - (1f - progress) * 0.1f)
                                        scaleY = min(1f - (dragProgress * 0.05f),1f - (1f - progress) * 0.1f)
                                    }
                                    .fillMaxSize()
                                    .padding(
                                        start = windowInsets.calculateStartPadding(
                                            LocalLayoutDirection.current
                                        ),
                                        end = windowInsets.calculateStartPadding(
                                            LocalLayoutDirection.current
                                        ),
                                    ),
                                paddingValues = PaddingValues(
                                    top = windowInsets.calculateTopPadding() + if (!bottomSearchBar) 60.dp + webSearchPadding else 4.dp,
                                    bottom = windowInsets.calculateBottomPadding() + if (bottomSearchBar) 60.dp + webSearchPadding else 4.dp
                                ),
                                state = searchState,
                                reverse = reverseSearchResults,
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
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                title = {
                    Text(stringResource(R.string.menu_edit_widgets))
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.setWidgetEditMode(false) }) {
                        Icon(imageVector = Icons.Rounded.Done, contentDescription = null)
                    }
                }
            )
        }

        val searchBarLevel by remember {
            derivedStateOf {
                when {
                    offsetY.value != 0f -> SearchBarLevel.Raised
                    !isSearchOpen && isWidgetsAtStart && (fillClockHeight || !bottomSearchBar) -> SearchBarLevel.Resting
                    isSearchOpen && isSearchAtTop && !bottomSearchBar -> SearchBarLevel.Active
                    isSearchOpen && isSearchAtBottom && bottomSearchBar -> SearchBarLevel.Active
                    else -> SearchBarLevel.Raised
                }
            }
        }
        val searchBarFocused by viewModel.searchBarFocused
        val editModeSearchBarOffset by animateDpAsState(
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
                        if (searchBarFocused || fixedSearchBar) 0 else searchBarOffset.value.toInt() * (if (bottomSearchBar) 1 else -1)
                    )
                }
                .offset {
                    IntOffset(
                        0,
                        with(density) {
                            editModeSearchBarOffset
                                .toPx()
                                .roundToInt()
                        })
                },
            level = { searchBarLevel },
            focused = searchBarFocused,
            onFocusChange = {
                if (it) viewModel.openSearch()
                viewModel.setSearchbarFocus(it)
            },
            actions = actions,
            highlightedAction = searchVM.bestMatch.value as? SearchAction,
            showHiddenItemsButton = isSearchOpen,
            value = { value },
            onValueChange = { searchVM.search(it) },
            darkColors = LocalPreferDarkContentOverWallpaper.current && searchBarColor == Settings.SearchBarSettings.SearchBarColors.Auto || searchBarColor == Settings.SearchBarSettings.SearchBarColors.Dark,
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