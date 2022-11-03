package de.mm20.launcher2.ui.launcher.searchbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.ui.component.SearchBar
import de.mm20.launcher2.ui.component.SearchBarLevel

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
    reverse: Boolean = false,
    darkColors: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }


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
            SearchBarMenu(searchBarValue = _value, onSearchBarValueChange = onValueChange)
        },
        actions = {
            SearchBarActions(actions = actions, reverse = reverse)
        },
        focusRequester = focusRequester,
        onFocus = { onFocusChange(true) },
        onUnfocus = { onFocusChange(false) },
    )
}