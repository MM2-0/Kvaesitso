package de.mm20.launcher2.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.insets.systemBarsPadding
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.ui.component.NavBarEffects
import de.mm20.launcher2.ui.component.SearchBar
import de.mm20.launcher2.ui.component.SearchColumn
import de.mm20.launcher2.ui.component.WidgetColumn
import de.mm20.launcher2.ui.locals.LocalWindowSize
import de.mm20.launcher2.ui.toPixels
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalAnimationApi::class,
    ExperimentalPagerApi::class,
    InternalCoroutinesApi::class
)
@Composable
fun LauncherMainScreen() {

    val systemUiController = rememberSystemUiController()

    val pagerState = rememberPagerState()
    val searchColumnState = rememberLazyListState()
    val widgetColumnState = rememberScrollState()

    val isLightTheme = MaterialTheme.colors.isLight

    val windowHeight = LocalWindowSize.current.height

    LaunchedEffect(pagerState) {
        val offsetFlow = snapshotFlow { pagerState.currentPageOffset + pagerState.currentPage }
        val scrollFlow = snapshotFlow { widgetColumnState.value }

        offsetFlow.combine(scrollFlow) { pageOffset, scrollValue ->
            pageOffset > 0.5f || scrollValue > windowHeight / 2
        }.collect { proposeDarkIcons ->
            if (proposeDarkIcons) {
                systemUiController.setSystemBarsColor(
                    color = Color.Transparent,
                    isNavigationBarContrastEnforced = false,
                    darkIcons = isLightTheme
                )
            } else {
                systemUiController.setStatusBarColor(
                    color = Color.Transparent,
                    darkIcons = false //TODO Add preference to control that
                )
                systemUiController.setNavigationBarColor(
                    color = Color.Transparent,
                    navigationBarContrastEnforced = false,
                    darkIcons = false //TODO Add preference to control that
                )
            }
        }
    }

    var searchBarOffset by remember { mutableStateOf(0f) }

    var lastWidgetScrollPosition by remember { mutableStateOf(0) }

    searchBarOffset = run {
        val lastScrollPos = lastWidgetScrollPosition
        val scrollPos = widgetColumnState.value
        lastWidgetScrollPosition = scrollPos
        (searchBarOffset - (lastScrollPos - scrollPos) / 100.dp.toPixels()).coerceIn(0f, 1f)
    }

    val scope = rememberCoroutineScope()


    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        BackHandler {
            scope.launch {
                if (pagerState.currentPage > 0) {
                    pagerState.animateScrollToPage(0)
                } else if (widgetColumnState.value > 0) {
                    widgetColumnState.animateScrollTo(0)
                }
            }
        }
        NavBarEffects(
            modifier = Modifier.fillMaxSize()
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            count = 2
        ) { page ->
            when (page) {
                0 -> WidgetColumn(
                    modifier = Modifier.fillMaxSize(),
                    scrollState = widgetColumnState
                )
                1 -> SearchColumn(
                    modifier = Modifier.fillMaxSize(),
                    listState = searchColumnState
                )
            }
        }
        val scope = rememberCoroutineScope()
        SearchBar(
            modifier = Modifier
                .systemBarsPadding()
                .padding(8.dp),
            pagerState = pagerState,
            widgetColumnState = widgetColumnState,
            offScreen = searchBarOffset,
            onFocus = {
                scope.launch {
                    pagerState.animateScrollToPage(1)
                }
            }
        )
    }

}

enum class Page {
    Home, Search
}

enum class SwipeState {
    Initial,
    Swiping
}