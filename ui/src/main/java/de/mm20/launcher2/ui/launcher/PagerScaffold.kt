package de.mm20.launcher2.ui.launcher

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.material3.*
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.helper.WallpaperBlur
import de.mm20.launcher2.ui.launcher.search.SearchBar
import de.mm20.launcher2.ui.launcher.search.SearchBarLevel
import de.mm20.launcher2.ui.launcher.search.SearchColumn
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.widgets.WidgetColumn
import de.mm20.launcher2.ui.launcher.widgets.clock.ClockWidget
import de.mm20.launcher2.ui.utils.rememberNotificationShadeController
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PagerScaffold(
    modifier: Modifier = Modifier,
    darkStatusBarIcons: Boolean = false,
    darkNavBarIcons: Boolean = false,
    reverse: Boolean = false,
) {
    val viewModel: LauncherScaffoldVM = viewModel()
    val searchVM: SearchVM = viewModel()

    val isSearchOpen by viewModel.isSearchOpen.observeAsState(false)
    val isWidgetEditMode by viewModel.isWidgetEditMode.observeAsState(false)

    val widgetsScrollState = rememberScrollState()
    val searchState = rememberLazyListState()
    val swipeableState = rememberSwipeableState(if (isSearchOpen) Page.Search else Page.Widgets)

    val isSearchAtStart by remember {
        derivedStateOf {
            searchState.firstVisibleItemIndex == 0 && searchState.firstVisibleItemScrollOffset == 0
        }
    }

    val isSearchAtEnd by remember {
        derivedStateOf {
            val lastItem = searchState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            lastItem.offset + lastItem.size <= searchState.layoutInfo.viewportEndOffset - searchState.layoutInfo.afterContentPadding
        }
    }

    val showStatusBarScrim by remember {
        derivedStateOf {
            if (isSearchOpen) {
                !isSearchAtEnd
            } else {
                widgetsScrollState.value > 0
            }
        }
    }

    val fillClockHeight by viewModel.fillClockHeight.observeAsState(true)

    val showNavBarScrim by remember {
        derivedStateOf {
            if (isSearchOpen) {
                !isSearchAtStart
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

    val blurEnabled by viewModel.wallpaperBlur.observeAsState(false)

    val blurWallpaper by remember {
        derivedStateOf {
            blurEnabled && (
                    isSearchOpen || swipeableState.progress.to == Page.Widgets && swipeableState.progress.fraction <= 0.5f ||
                            swipeableState.progress.to == Page.Search && swipeableState.progress.fraction > 0.5f ||
                            !isWidgetsScrollZero)
        }
    }

    WallpaperBlur {
        blurWallpaper
    }

    val currentPage = swipeableState.currentValue
    LaunchedEffect(currentPage) {
        if (currentPage == Page.Search) viewModel.openSearch()
        else viewModel.closeSearch()
    }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) swipeableState.animateTo(Page.Search)
        else {
            swipeableState.animateTo(Page.Widgets)
            searchVM.search("")
        }
    }

    val scope = rememberCoroutineScope()
    BackHandler {
        when {
            isSearchOpen -> {
                viewModel.closeSearch()
                searchVM.search("")
            }
            isWidgetEditMode -> {
                viewModel.setWidgetEditMode(false)
            }
            widgetsScrollState.value != 0 -> {
                scope.launch {
                    widgetsScrollState.animateScrollTo(0)
                }
            }
        }
    }

    val notificationDragThreshold = with(LocalDensity.current) { 200.dp.toPx() }
    val notificationShadeController = rememberNotificationShadeController()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            private var pullDownTotalY: Float? = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isWidgetsScrollZero) return Offset.Zero
                val diff = -available.y
                var totalY = pullDownTotalY ?: return available
                if (diff >= 0) return super.onPreScroll(available, source)

                totalY += diff

                if (totalY < -notificationDragThreshold) {
                    notificationShadeController.expandNotifications()
                    pullDownTotalY = null
                    return available
                }
                pullDownTotalY = totalY

                return super.onPreScroll(available, source)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullDownTotalY == null) {
                    pullDownTotalY = 0f
                    return available
                }
                return super.onPreFling(available)
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


            val widthPx = width.toPixels()

            val originalLayoutDirection = LocalLayoutDirection.current

            CompositionLocalProvider(
                LocalOverscrollConfiguration provides null,
                LocalLayoutDirection provides if (reverse) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {

                Row(
                    modifier = Modifier
                        .requiredWidth(width * 2)
                        .fillMaxHeight()
                        .swipeable(
                            swipeableState,
                            orientation = Orientation.Horizontal,
                            anchors = mapOf(
                                -widthPx / 2f to Page.Search,
                                widthPx / 2f to Page.Widgets,
                            ),
                            thresholds = { _, _ ->
                                FractionalThreshold(0.5f)
                            },
                            enabled = !isWidgetEditMode,
                            reverseDirection = reverse,
                        )
                        .offset {
                            IntOffset(swipeableState.offset.value.roundToInt(), 0)
                        },
                ) {

                    CompositionLocalProvider(
                        LocalLayoutDirection provides originalLayoutDirection
                    ) {


                        val editModePadding by animateDpAsState(if (isWidgetEditMode) 56.dp else 0.dp)

                        val clockPadding by animateDpAsState(
                            if (isWidgetsScrollZero && fillClockHeight) 64.dp + insets.calculateBottomPadding() else 0.dp
                        )

                        val clockHeight by remember {
                            derivedStateOf {
                                if (fillClockHeight){
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
                                .nestedScroll(nestedScrollConnection)
                                .verticalScroll(widgetsScrollState)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(horizontal = 8.dp)
                                .padding(top = 8.dp, bottom = 64.dp)
                                .padding(top = editModePadding)
                        ) {

                            AnimatedVisibility(!isWidgetEditMode) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(clockHeight?.let { Modifier.height(it) } ?: Modifier)
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


                        val websearches by searchVM.websearchResults.observeAsState(emptyList())
                        val webSearchPadding by animateDpAsState(
                            if (websearches.isEmpty()) 0.dp else 48.dp
                        )
                        val windowInsets = WindowInsets.safeDrawing.asPaddingValues()
                        SearchColumn(
                            modifier = Modifier
                                .requiredWidth(width)
                                .fillMaxHeight()
                                .padding(
                                    start = windowInsets.calculateStartPadding(LocalLayoutDirection.current),
                                    end = windowInsets.calculateStartPadding(LocalLayoutDirection.current),
                                ),
                            reverse = true,
                            state = searchState,
                            paddingValues = PaddingValues(
                                top = 4.dp + windowInsets.calculateTopPadding(),
                                bottom = 60.dp + webSearchPadding + windowInsets.calculateBottomPadding()
                            )
                        )
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
                    swipeableState.direction != 0f -> SearchBarLevel.Raised
                    !isSearchOpen && isWidgetsScrollZero && fillClockHeight -> SearchBarLevel.Resting
                    isSearchOpen && isSearchAtStart -> SearchBarLevel.Active
                    else -> SearchBarLevel.Raised
                }
            }
        }

        val focusSearchBar by viewModel.searchBarFocused.observeAsState(false)

        val widgetEditModeOffset by animateDpAsState(
            if (isWidgetEditMode) 128.dp else 0.dp
        )

        SearchBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .offset(y = widgetEditModeOffset),
            level = { searchBarLevel }, focused = focusSearchBar, onFocusChange = {
                if (it) viewModel.openSearch()
                viewModel.setSearchbarFocus(it)
            },
            reverse = true
        )
    }
}

private enum class Page {
    Widgets,
    Search
}