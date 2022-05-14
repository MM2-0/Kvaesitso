package de.mm20.launcher2.ui.launcher

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.SearchBar
import de.mm20.launcher2.ui.launcher.search.SearchBarLevel
import de.mm20.launcher2.ui.launcher.search.SearchColumn
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.widgets.WidgetColumn

@OptIn(ExperimentalPagerApi::class)
@Composable
fun PagerScaffold(
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

    val isWidgetsScrollZero by remember {
        derivedStateOf {
            widgetsScrollState.value == 0
        }
    }

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

    val blurWallpaper by remember {
        derivedStateOf {
            isSearchOpen || pagerState.currentPage == 0 && pagerState.currentPageOffset > 0.5f ||
                    pagerState.currentPage == 1 && pagerState.currentPageOffset <= 0.5f ||
                    !isWidgetsScrollZero
        }
    }

    val density = LocalDensity.current


    LaunchedEffect(blurWallpaper) {
        if (!isAtLeastApiLevel(31)) return@LaunchedEffect
        (context as Activity).window.attributes = context.window.attributes.also {
            if (blurWallpaper) {
                it.blurBehindRadius = with(density) { 32.dp.toPx().toInt() }
                it.flags = it.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            } else {
                it.blurBehindRadius = 0
                it.flags = it.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            }
        }
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

    BackHandler {
        when {
            isSearchOpen -> {
                viewModel.closeSearch()
                searchVM.search("")
            }
            isWidgetEditMode -> {
                viewModel.setWidgetEditMode(false)
            }
        }
    }


    Box(
        modifier = modifier
    ) {

        HorizontalPager(
            count = 2,
            state = pagerState,
            modifier = Modifier
                .fillMaxSize(),
            userScrollEnabled = !isWidgetEditMode
        ) {
            if (it == 1) {
                val websearches by searchVM.websearchResults.observeAsState(emptyList())
                val webSearchPadding by animateDpAsState(
                    if (websearches.isEmpty()) 0.dp else 48.dp
                )
                SearchColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(searchScrollState, reverseScrolling = true)
                        .imePadding()
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 64.dp)
                        .padding(bottom = webSearchPadding),
                    reverse = true,
                )
            }

            if (it == 0) {
                val editModePadding by animateDpAsState(if (isWidgetEditMode) 56.dp else 0.dp)

                val clockPadding by animateDpAsState(
                    if (isWidgetsScrollZero) 64.dp else 0.dp
                )
                var size by remember { mutableStateOf(IntSize.Zero) }

                val clockHeight by remember {
                    derivedStateOf {
                        with(density) { size.height.toDp() } - (64.dp - clockPadding)
                    }
                }

                WidgetColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged {
                            size = it
                        }
                        .verticalScroll(widgetsScrollState)
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 64.dp)
                        .padding(top = editModePadding),
                    clockHeight = { clockHeight },
                    clockBottomPadding = { clockPadding },
                    editMode = isWidgetEditMode,
                    onEditModeChange = {
                        viewModel.setWidgetEditMode(it)
                    }
                )
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
                },
            )
        }

        val searchBarLevel by remember {
            derivedStateOf {
                when {
                    pagerState.isScrollInProgress -> SearchBarLevel.Raised
                    !isSearchOpen && isWidgetsScrollZero -> SearchBarLevel.Resting
                    isSearchOpen && searchScrollState.value == 0 -> SearchBarLevel.Active
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
                .imePadding()
                .offset(y = widgetEditModeOffset),
            level = searchBarLevel, focused = focusSearchBar, onFocusChange = {
                if (it) viewModel.openSearch()
                viewModel.setSearchbarFocus(it)
            },
            reverse = true
        )
    }

}