package de.mm20.launcher2.ui.launcher

import android.app.Activity
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalOverScrollConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.rememberPagerState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.ktx.isAtLeastApiLevel
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

@OptIn(ExperimentalPagerApi::class, ExperimentalFoundationApi::class)
@Composable
fun PullDownScaffold(
    modifier: Modifier = Modifier,
    darkStatusBarIcons: Boolean = false,
    darkNavBarIcons: Boolean = false,
) {
    val viewModel: LauncherScaffoldVM = viewModel()
    val searchVM: SearchVM = viewModel()
    val context = LocalContext.current

    val density = LocalDensity.current

    val isSearchOpen by viewModel.isSearchOpen.observeAsState(false)
    val isWidgetEditMode by viewModel.isWidgetEditMode.observeAsState(false)

    val pagerState = rememberPagerState()
    val widgetsScrollState = rememberScrollState()
    val searchScrollState = rememberScrollState()

    val systemUiController = rememberSystemUiController()

    val colorSurface = MaterialTheme.colorScheme.surface
    LaunchedEffect(isWidgetEditMode, darkStatusBarIcons, colorSurface) {
        if (isWidgetEditMode) {
            systemUiController.setStatusBarColor(
                colorSurface
            )
        } else {
            systemUiController.setStatusBarColor(
                Color.Transparent,
                darkIcons = darkStatusBarIcons
            )
        }
    }

    LaunchedEffect(darkNavBarIcons) {
        systemUiController.setNavigationBarColor(
            Color.Transparent,
            darkIcons = darkNavBarIcons,
            navigationBarContrastEnforced = false
        )
    }

    val offsetY = remember { mutableStateOf(0f) }

    val maxOffset = with(density) { 64.dp.toPx() }
    val toggleSearchThreshold = with(density) { 48.dp.toPx() }

    val searchBarOffset = remember { mutableStateOf(0f) }

    val maxSearchBarOffset = with(density) { 128.dp.toPx() }

    val blurWallpaper by remember {
        derivedStateOf {
            isSearchOpen || offsetY.value > toggleSearchThreshold || widgetsScrollState.value > 0
        }
    }

    WallpaperBlur {
        blurWallpaper
    }


    val scope = rememberCoroutineScope()

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) searchScrollState.scrollTo(0)
        if (!isSearchOpen) searchVM.search("")
        //pagerState.animateScrollToPage(if (isSearchOpen) 1 else 0)
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
                val diff =
                    (if (isSearchOpen) searchScrollState.value else widgetsScrollState.value) - available.y
                val consumed = when {
                    (offsetY.value > 0 || source == NestedScrollSource.Drag && diff < 0) -> {
                        val consumed = -diff
                        offsetY.value = (offsetY.value + (consumed * 0.5f)).coerceIn(0f, maxOffset)
                        consumed
                    }
                    isSearchOpen && (offsetY.value < 0 || source == NestedScrollSource.Drag && diff > searchScrollState.maxValue) -> {
                        val consumed =
                            available.y - (searchScrollState.maxValue - searchScrollState.value)
                        offsetY.value = (offsetY.value + (consumed * 0.5f)).coerceIn(-maxOffset, 0f)
                        consumed
                    }
                    else -> {
                        0f
                    }
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


    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
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
                LocalOverScrollConfiguration provides null
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
                            .verticalScroll(searchScrollState)
                            .padding(8.dp)
                            .padding(top = 56.dp)
                            .padding(top = webSearchPadding)
                            .imePadding()
                    )
                    val editModePadding by animateDpAsState(if (isWidgetEditMode) 56.dp else 0.dp)
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
                            .padding(8.dp)
                            .padding(top = editModePadding),
                        clockHeight = { height },
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
                    isSearchOpen && searchScrollState.value == 0 -> SearchBarLevel.Active
                    isSearchOpen && searchScrollState.value > 0 -> SearchBarLevel.Raised
                    widgetsScrollState.value > 0 -> SearchBarLevel.Raised
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
                .padding(8.dp)
                .offset { IntOffset(0, searchBarOffset.value.toInt()) }
                .offset(y = editModeSearchBarOffset),
            focused = searchBarFocused,
            onFocusChange = {
                if (it) viewModel.openSearch()
                viewModel.setSearchbarFocus(it)
            }
        )

    }
}