/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.ui.navigation.topbars

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.clear
import com.vitorpamplona.amethyst.commons.resources.settings_search_placeholder
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * Top bar for the All Settings screen that hosts the settings filter directly in the app bar,
 * replacing the static "Settings" title. The back arrow is still shown only when there is
 * something to pop (i.e. the user arrived from a deep/profile link rather than the bottom nav,
 * which clears the stack). This keeps the shared [TopBarWithBackButton] untouched for every other
 * screen while giving Settings a search-as-app-bar layout.
 */
@Composable
fun SettingsSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    nav: INav,
) = SearchTopBar(query, onQueryChange, onClear, stringRes(Res.string.settings_search_placeholder), nav)

/**
 * A top bar that is a search field: the [CompactSearchField] pill in place of a title, with the
 * back arrow when there is something to pop. For screens whose list filters as you type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    nav: INav,
) {
    ShorterTopAppBar(
        expandedHeight = SettingsSearchTopBarHeight,
        title = {
            CompactSearchField(
                query = query,
                onQueryChange = onQueryChange,
                onClear = onClear,
                placeholder = placeholder,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 12.dp),
            )
        },
        navigationIcon = {
            // Suppress the back arrow when this is the bottom of the back stack (bottom-nav entry
            // clears it with popUpTo(route) { inclusive = true }); show it for deep-link entries.
            if (nav.canPop()) {
                IconButton(nav::popBack) {
                    ArrowBackIcon()
                }
            }
        },
    )
}

/**
 * A compact, pill-shaped search field sized to sit inside the app bar. It filters a list in
 * place rather than opening a results overlay, so it is a plain [BasicTextField] with a
 * custom decoration box (a full [androidx.compose.material3.TextField] enforces a ~56dp min height
 * that does not fit an app bar).
 */
@Composable
fun CompactSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface),
        cursorBrush = SolidColor(colorScheme.primary),
        decorationBox = { innerTextField ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(SettingsSearchFieldHeight)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceContainerHigh)
                        .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    symbol = MaterialSymbols.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            symbol = MaterialSymbols.Close,
                            contentDescription = stringRes(Res.string.clear),
                            modifier = Modifier.size(20.dp),
                            tint = LocalContentColor.current,
                        )
                    }
                }
            }
        },
    )
}

private val SettingsSearchTopBarHeight = 56.dp
private val SettingsSearchFieldHeight = 42.dp
