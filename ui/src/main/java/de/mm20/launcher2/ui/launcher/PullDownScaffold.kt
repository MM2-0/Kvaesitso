package de.mm20.launcher2.ui.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ktx.animateTo
import de.mm20.launcher2.ui.launcher.helper.WallpaperBlur
import de.mm20.launcher2.ui.launcher.search.SearchBar
import de.mm20.launcher2.ui.launcher.search.SearchBarLevel
import de.mm20.launcher2.ui.launcher.search.SearchColumn
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.widgets.WidgetColumn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PullDownScaffold(
    modifier: Modifier = Modifier,
    darkStatusBarIcons: Boolean = false,
    darkNavBarIcons: Boolean = false,
) {
    val viewModel: LauncherScaffoldVM = viewModel()
    val searchVM: SearchVM = viewModel()

    val density = LocalDensity.current

    val isSearchOpen by viewModel.isSearchOpen.observeAsState(false)
    val isWidgetEditMode by viewModel.isWidgetEditMode.observeAsState(false)

    val widgetsScrollState = rememberScrollState()
    val searchState = rememberLazyListState()

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

    val fillClockHeight by viewModel.fillClockHeight.observeAsState(true)

    val showStatusBarScrim by remember {
        derivedStateOf {
            if (isSearchOpen) {
                !isSearchAtStart
            } else {
                widgetsScrollState.value > 0
            }
        }
    }
    val showNavBarScrim by remember {
        derivedStateOf {
            if (isSearchOpen) {
                !isSearchAtEnd
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

    val offsetY = remember { mutableStateOf(0f) }

    val maxOffset = with(density) { 64.dp.toPx() }
    val toggleSearchThreshold = with(density) { 48.dp.toPx() }

    val searchBarOffset = remember { mutableStateOf(0f) }

    val maxSearchBarOffset = with(density) { 128.dp.toPx() }

    val blurEnabled by viewModel.wallpaperBlur.observeAsState(false)

    val blurWallpaper by remember {
        derivedStateOf {
            blurEnabled && (isSearchOpen || offsetY.value > toggleSearchThreshold || widgetsScrollState.value > 0)
        }
    }

    WallpaperBlur {
        blurWallpaper
    }


    val scope = rememberCoroutineScope()

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) searchState.scrollToItem(0)
        if (!isSearchOpen) searchVM.search("")
        searchBarOffset.animateTo(0f)
    }

    LaunchedEffect(isWidgetEditMode) {
        if (!isWidgetEditMode) searchBarOffset.value = 0f
    }

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

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isWidgetEditMode) return Offset.Zero
                val canPullDown = if (isSearchOpen) {
                    isSearchAtStart
                } else {
                    isWidgetsAtStart
                }
                val canPullUp = isSearchOpen && isSearchAtEnd

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

                searchBarOffset.value =
                    (searchBarOffset.value + (available.y - consumed)).coerceIn(
                        -maxSearchBarOffset,
                        0f
                    )

                return Offset(0f, consumed)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY.value > toggleSearchThreshold || offsetY.value < -toggleSearchThreshold) {
                    viewModel.toggleSearch()
                }
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
            .nestedScroll(nestedScrollConnection)
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
                val offset by animateFloatAsState(if (isSearchOpen) 1f else 0f)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(height * 2)
                        .offset {
                            IntOffset(
                                0,
                                ((-0.5f + offset) * height.toPx()).roundToInt()
                            )
                        }
                ) {
                    val websearches by searchVM.websearchResults.observeAsState(emptyList())
                    val webSearchPadding by animateDpAsState(
                        if (websearches.isEmpty()) 0.dp else 48.dp
                    )
                    val windowInsets = WindowInsets.safeDrawing.asPaddingValues()
                    SearchColumn(
                        modifier = Modifier
                            .graphicsLayer {
                                transformOrigin = TransformOrigin.Center
                                scaleX = offset
                                scaleY = offset
                                alpha = offset
                            }
                            .fillMaxWidth()
                            .requiredHeight(height)
                            .padding(
                                start = windowInsets.calculateStartPadding(LocalLayoutDirection.current),
                                end = windowInsets.calculateStartPadding(LocalLayoutDirection.current),
                            ),
                        paddingValues = PaddingValues(
                            top = 60.dp + webSearchPadding + windowInsets.calculateTopPadding(),
                            bottom = 4.dp + windowInsets.calculateBottomPadding()
                        ),
                        state = searchState,

                    )
                    val clockPadding by animateDpAsState(
                        if (isWidgetsAtStart && fillClockHeight) insets.calculateBottomPadding() else 0.dp
                    )
                    val clockHeight by remember {
                        derivedStateOf {
                            if (fillClockHeight) {
                                height - (insets.calculateTopPadding() + insets.calculateBottomPadding() - clockPadding + 56.dp)
                            } else {
                                null
                            }
                        }
                    }
                    WidgetColumn(
                        modifier =
                        Modifier
                            .graphicsLayer {
                                transformOrigin = TransformOrigin.Center
                                scaleX = 1 - offset
                                scaleY = 1 - offset
                                alpha = 1 - offset
                            }
                            .fillMaxWidth()
                            .requiredHeight(height)
                            .verticalScroll(widgetsScrollState)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(8.dp)
                            .padding(top = 56.dp),
                        clockHeight = { clockHeight },
                        clockBottomPadding = { clockPadding },
                        editMode = isWidgetEditMode,
                        onEditModeChange = {
                            viewModel.setWidgetEditMode(it)
                        }
                    )
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
                    isSearchOpen && isSearchAtStart -> SearchBarLevel.Active
                    isSearchOpen && !isSearchAtStart -> SearchBarLevel.Raised
                    !isWidgetsAtStart -> SearchBarLevel.Raised
                    else -> SearchBarLevel.Resting
                }
            }
        }
        val searchBarFocused by viewModel.searchBarFocused.observeAsState(false)
        val editModeSearchBarOffset by animateDpAsState(
            if (isWidgetEditMode) -128.dp else 0.dp
        )

        SearchBar(
            level = { searchBarLevel },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp)
                .offset { IntOffset(0,
                    if (searchBarFocused) 0 else searchBarOffset.value.toInt()) }
                .offset {
                    IntOffset(
                        0,
                        with(density) {
                            editModeSearchBarOffset
                                .toPx()
                                .roundToInt()
                        })
                },
            focused = searchBarFocused,
            onFocusChange = {
                if (it) viewModel.openSearch()
                viewModel.setSearchbarFocus(it)
            }
        )

    }
}