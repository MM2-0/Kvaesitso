package de.mm20.launcher2.ui.launcher.searchbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.SearchBar
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.launcher.search.SearchVM
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager

@Composable
fun LauncherSearchBar(
    modifier: Modifier = Modifier,
    style: SearchBarStyle,
    level: () -> SearchBarLevel,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    actions: List<SearchAction>,
    highlightedAction: SearchAction? = null,
    isSearchOpen: Boolean = false,
    darkColors: Boolean = false,
    bottomSearchBar: Boolean = false,
    searchBarOffset: () -> Int = { 0 },
    onKeyboardActionGo: (KeyboardActionScope.() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val searchVM: SearchVM = viewModel()
    val hiddenItemsButtonEnabled by searchVM.hiddenResultsButton.collectAsState(false)
    val hiddenItems = searchVM.hiddenResults

    val sheetManager = LocalBottomSheetManager.current

    LaunchedEffect(focused) {
        if (focused) focusRequester.requestFocus()
        else focusManager.clearFocus()
    }

    val value by searchVM.searchQuery

    Box(modifier = modifier) {
        SearchBar(
            modifier = Modifier
                .align(if (bottomSearchBar) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(8.dp)
                .offset { IntOffset(0, searchBarOffset()) },
            style = style, level = level(), value = value, onValueChange = {
                searchVM.search(it)
            },
            reverse = bottomSearchBar,
            darkColors = darkColors,
            menu = {
                AnimatedVisibility(
                    hiddenItemsButtonEnabled && isSearchOpen && hiddenItems.isNotEmpty(),
                    enter = scaleIn(tween(100)),
                    exit = scaleOut(tween(100))
                ) {
                    IconToggleButton(
                        checked = sheetManager.hiddenItemsSheetShown.value,
                        onCheckedChange = { if (it) sheetManager.showHiddenItemsSheet() },
                    ) {
                        Icon(
                            painter = painterResource(
                                if (sheetManager.hiddenItemsSheetShown.value) R.drawable.visibility_off_24px_filled
                                else R.drawable.visibility_off_24px
                            ),
                            contentDescription = null
                        )
                    }
                }
                AnimatedVisibility(
                    isSearchOpen,
                    enter = scaleIn(tween(100)),
                    exit = scaleOut(tween(100))
                ) {
                    IconToggleButton(
                        checked = searchVM.showFilters.value,
                        onCheckedChange = {
                            searchVM.showFilters.value = it
                        },
                    ) {
                        Box {
                            Icon(
                                painter = painterResource(
                                    if (searchVM.showFilters.value)R.drawable.filter_alt_24px_filled
                                    else R.drawable.filter_alt_24px
                                ),
                                contentDescription = stringResource(
                                    if (searchVM.showFilters.value) R.string.menu_hide_filters else R.string.menu_show_filters
                                )
                            )
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
                SearchBarMenu(searchBarValue = value, onInputClear = {
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
    }
}