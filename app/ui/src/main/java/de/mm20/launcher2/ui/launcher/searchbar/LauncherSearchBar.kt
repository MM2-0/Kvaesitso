package de.mm20.launcher2.ui.launcher.searchbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.ui.component.SearchBar
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.search.filters.KeyboardFilterBar

@Composable
fun LauncherSearchBar(
    modifier: Modifier = Modifier,
    style: SearchBarStyle,
    level: () -> SearchBarLevel,
    value: () -> String,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    actions: List<SearchAction>,
    highlightedAction: SearchAction?,
    isSearchOpen: Boolean = false,
    darkColors: Boolean = false,
    bottomSearchBar: Boolean = false,
    searchBarOffset: () -> Int = { 0 },
    onKeyboardActionGo: (KeyboardActionScope.() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val searchVM: SearchVM = viewModel()

    LaunchedEffect(focused) {
        if (focused) focusRequester.requestFocus()
        else focusManager.clearFocus()
    }

    val filterBar by searchVM.filterBar.collectAsState(false)

    val _value = value()

    Box(modifier = modifier) {
        SearchBar(
            modifier = Modifier
                .align(if (bottomSearchBar) Alignment.BottomCenter else Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp)
                .offset { IntOffset(0, searchBarOffset()) },
            style = style, level = level(), value = _value, onValueChange = {
                searchVM.search(it)
            },
            reverse = bottomSearchBar,
            darkColors = darkColors,
            menu = {
                AnimatedVisibility(
                    isSearchOpen,
                    enter = scaleIn(tween(100)),
                    exit = scaleOut(tween(100))
                ) {
                    FilledIconButton(
                        onClick = {
                            searchVM.showFilters.value = !searchVM.showFilters.value
                        },
                        colors = if (searchVM.showFilters.value) IconButtonDefaults.filledTonalIconButtonColors()
                        else IconButtonDefaults.iconButtonColors()
                    ) {
                        Box {
                            Icon(imageVector = Icons.Rounded.FilterAlt, contentDescription = null)
                            androidx.compose.animation.AnimatedVisibility(
                                !searchVM.filters.value.allCategoriesEnabled,
                                enter = scaleIn(tween(100)),
                                exit = scaleOut(tween(100)),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(-3.dp, -3.dp)
                            ) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
                SearchBarMenu(searchBarValue = _value, onInputClear = {
                    searchVM.reset()
                })
            },
            actions = {
                SearchBarActions(
                    actions = actions,
                    reverse = bottomSearchBar,
                    highlightedAction = highlightedAction
                )
            },
            focusRequester = focusRequester,
            onFocus = { onFocusChange(true) },
            onUnfocus = { onFocusChange(false) },
            onKeyboardActionGo = onKeyboardActionGo
        )

        AnimatedVisibility (filterBar && isSearchOpen && !searchVM.showFilters.value
                // Use imeAnimationTarget instead of isImeVisible to animate the filter bar at the same time as the keyboard
                && WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            KeyboardFilterBar(
                filters = searchVM.filters.value,
                onFiltersChange = {
                    searchVM.setFilters(it)
                }
            )
        }
    }
}