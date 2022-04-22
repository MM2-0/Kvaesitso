package de.mm20.launcher2.ui.launcher

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.VerticalPager
import com.google.accompanist.pager.calculateCurrentOffsetForPage
import com.google.accompanist.pager.rememberPagerState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ktx.toDp
import de.mm20.launcher2.ui.launcher.search.SearchBar
import de.mm20.launcher2.ui.launcher.search.SearchBarLevel
import de.mm20.launcher2.ui.launcher.search.SearchColumn
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.widgets.WidgetColumn
import de.mm20.launcher2.ui.modifier.scale
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.ranges.coerceIn

@OptIn(ExperimentalPagerApi::class)
@Composable
fun PullDownScaffold(
    modifier: Modifier = Modifier,
    darkStatusBarIcons: Boolean = false,
    darkNavBarIcons: Boolean = false,
) {
    val viewModel: LauncherScaffoldVM = viewModel()
    val searchVM: SearchVM = viewModel()
    val context = LocalContext.current

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

    val dp = LocalDensity.current.density

    val offsetY = remember { Animatable(0.dp, Dp.VectorConverter) }
    var searchBarOffset by remember { mutableStateOf(0.dp) }

    val blurWallpaper = isSearchOpen || offsetY.value > 48.dp || widgetsScrollState.value > 0

    LaunchedEffect(blurWallpaper) {
        if (!isAtLeastApiLevel(31)) return@LaunchedEffect
        (context as Activity).window.attributes = context.window.attributes.also {
            if (blurWallpaper) {
                it.blurBehindRadius = (32 * dp).toInt()
                it.flags = it.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            } else {
                it.blurBehindRadius = 0
                it.flags = it.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            }
        }
    }


    val nestedScrollDispatcher = remember { NestedScrollDispatcher() }
    val scope = rememberCoroutineScope()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val consumed = when {
                    isSearchOpen && (offsetY.value > 0.dp || source == NestedScrollSource.Drag && searchScrollState.value - available.y < 0) -> {
                        val consumed = available.y - searchScrollState.value
                        scope.launch {
                            offsetY.snapTo((offsetY.value + (consumed * 0.5f / dp).dp).coerceIn(0.dp..64.dp))
                        }
                        consumed
                    }
                    isSearchOpen && (offsetY.value < 0.dp || source == NestedScrollSource.Drag && searchScrollState.value - available.y > searchScrollState.maxValue) -> {
                        val consumed =
                            available.y - (searchScrollState.maxValue - searchScrollState.value)
                        scope.launch {
                            offsetY.snapTo((offsetY.value + (consumed * 0.5f / dp).dp).coerceIn(-64.dp..0.dp))
                        }
                        consumed
                    }
                    !isSearchOpen && (offsetY.value > 0.dp || source == NestedScrollSource.Drag && widgetsScrollState.value - available.y < 0) -> {
                        val consumed = available.y - widgetsScrollState.value
                        scope.launch {
                            offsetY.snapTo((offsetY.value + (consumed * 0.5f / dp).dp).coerceIn(0.dp..64.dp))
                        }
                        consumed
                    }
                    else -> {
                        0f
                    }
                }

                searchBarOffset =
                    ((searchBarOffset) + ((available.y - consumed) / dp).dp).coerceIn(-128.dp, 0.dp)

                return Offset(0f, consumed)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY.value > 48.dp || offsetY.value < -48.dp) {
                    viewModel.toggleSearch()
                }
                if (offsetY.value != 0.dp) {
                    offsetY.animateTo(0.dp)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(isSearchOpen) {
        searchScrollState.scrollTo(0)
        if (!isSearchOpen) searchVM.search("")
        searchBarOffset = 0.dp
        pagerState.animateScrollToPage(if (isSearchOpen) 1 else 0)
    }

    LaunchedEffect(isWidgetEditMode) {
        if (!isWidgetEditMode) searchBarOffset = 0.dp
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

    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged {
                size = it
            }
            .offset(y = offsetY.value),
        contentAlignment = Alignment.TopCenter
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier
                .nestedScroll(nestedScrollConnection, nestedScrollDispatcher),
            count = 2,
            reverseLayout = true,
            userScrollEnabled = false,
        ) {
            if (it == 1) {
                val websearches by searchVM.websearchResults.observeAsState(emptyList())
                val webSearchPadding by animateDpAsState(
                    if (websearches.isEmpty()) 0.dp else 48.dp
                )
                val offset = calculateCurrentOffsetForPage(1).absoluteValue
                SearchColumn(
                    modifier = Modifier
                        .alpha(1 - offset)
                        .scale(1 - offset, TransformOrigin.Center)
                        .fillMaxSize()
                        .verticalScroll(searchScrollState)
                        .padding(8.dp)
                        .padding(top = 56.dp)
                        .padding(top = webSearchPadding)
                )
            }

            if (it == 0) {
                val offset = calculateCurrentOffsetForPage(0).absoluteValue
                val editModePadding by animateDpAsState(if (isWidgetEditMode) 56.dp else 0.dp)
                WidgetColumn(
                    modifier =
                    Modifier
                        .alpha(1 - offset)
                        .scale(1 - offset, TransformOrigin.Center)
                        .fillMaxSize()
                        .verticalScroll(widgetsScrollState)
                        .padding(8.dp)
                        .padding(top = editModePadding),
                    clockHeight = size.height.toDp(),
                    editMode = isWidgetEditMode,
                    onEditModeChange = {
                        viewModel.setWidgetEditMode(it)
                    }
                )
            }
        }

        val searchBarLevel = when {
            offsetY.value != 0.dp -> SearchBarLevel.Raised
            isSearchOpen && searchScrollState.value == 0 -> SearchBarLevel.Active
            isSearchOpen && searchScrollState.value > 0 -> SearchBarLevel.Raised
            widgetsScrollState.value > 0 -> SearchBarLevel.Raised
            else -> SearchBarLevel.Resting
        }
        val searchBarFocused by viewModel.searchBarFocused.observeAsState(false)


        val editModeSearchBarOffset by animateDpAsState(
            if (isWidgetEditMode) -128.dp else 0.dp
        )
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
        SearchBar(
            level = searchBarLevel,
            modifier = Modifier.offset(y = searchBarOffset + editModeSearchBarOffset),
            focused = searchBarFocused,
            onFocusChange = {
                if (it) viewModel.openSearch()
                viewModel.setSearchbarFocus(it)
            }
        )

    }
}