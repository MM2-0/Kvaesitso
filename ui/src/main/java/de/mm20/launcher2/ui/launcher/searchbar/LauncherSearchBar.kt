package de.mm20.launcher2.ui.launcher.searchbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.ui.component.SearchBar
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.launcher.modals.HiddenItemsSheet
import de.mm20.launcher2.ui.launcher.search.SearchVM

@Composable
fun LauncherSearchBar(
    modifier: Modifier = Modifier,
    style: Settings.SearchBarSettings.SearchBarStyle,
    level: () -> SearchBarLevel,
    value: () -> String,
    onValueChange: (String) -> Unit,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    actions: List<SearchAction>,
    showHiddenItemsButton: Boolean = false,
    reverse: Boolean = false,
    darkColors: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val searchVM: SearchVM = viewModel()

    var showHiddenItemsSheet by remember { mutableStateOf(false) }

    val hiddenItems by searchVM.hiddenResults.observeAsState(emptyList())


    LaunchedEffect(focused) {
        if (focused) focusRequester.requestFocus()
        else focusManager.clearFocus()
    }

    val _value = value()

    SearchBar(
        modifier = modifier,
        style = style, level = level(), value = _value, onValueChange = onValueChange,
        reverse = reverse,
        darkColors = darkColors,
        menu = {
            AnimatedVisibility(
                showHiddenItemsButton && hiddenItems.isNotEmpty(),
                enter = scaleIn(tween(100)),
                exit = scaleOut(tween(100))
            ) {
                FilledIconButton(
                    onClick = { showHiddenItemsSheet = !showHiddenItemsSheet },
                    colors = if (showHiddenItemsSheet) IconButtonDefaults.filledTonalIconButtonColors() else IconButtonDefaults.iconButtonColors()
                ) {
                    Icon(imageVector = Icons.Rounded.VisibilityOff, contentDescription = null)
                }
            }
            SearchBarMenu(searchBarValue = _value, onSearchBarValueChange = onValueChange)
        },
        actions = {
            SearchBarActions(actions = actions, reverse = reverse)
        },
        focusRequester = focusRequester,
        onFocus = { onFocusChange(true) },
        onUnfocus = { onFocusChange(false) },
    )

    if (showHiddenItemsSheet) {
        HiddenItemsSheet(hiddenItems, onDismiss = { showHiddenItemsSheet = false })
    }
}