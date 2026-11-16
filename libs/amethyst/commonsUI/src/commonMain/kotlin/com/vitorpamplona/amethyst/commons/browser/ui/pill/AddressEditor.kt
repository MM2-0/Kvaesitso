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
package com.vitorpamplona.amethyst.commons.browser.ui.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_pill_address_hint
import com.vitorpamplona.amethyst.commons.resources.browser_pill_clear
import com.vitorpamplona.amethyst.commons.resources.browser_pill_fill_in
import com.vitorpamplona.amethyst.commons.resources.browser_pill_go
import com.vitorpamplona.amethyst.commons.resources.browser_pill_paste_go
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * The address as a first-class input, opened from the origin field. A 56dp pill field with the URL
 * pre-selected, a leading search/security icon, and trailing clear + a filled Go; below it Paste and go
 * (when the clipboard holds a URL) and the omnibox suggestions for what's typed ([suggestionsFor]), each
 * with a ↖ that fills its address in without leaving. [onCancel] folds it back into the origin field.
 */
@Composable
fun AddressEditor(
    initialUrl: String,
    security: BrowserChrome.Security,
    suggestionsFor: (String) -> List<AddressSuggestion>,
    clipboardUrl: String?,
    onGo: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
    onPasteAndGo: (() -> Unit)? = null,
) {
    var field by remember { mutableStateOf(TextFieldValue(initialUrl, TextRange(0, initialUrl.length))) }
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Ranked against what the user typed; the untouched page URL counts as nothing typed yet.
    val typed = if (field.text == initialUrl) "" else field.text
    val suggestions = remember(typed) { suggestionsFor(typed) }

    fun go(text: String = field.text) {
        text.trim().takeIf { it.isNotEmpty() }?.let(onGo)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Icon(MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = null)
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The field shows what it will do: the page's badge while it still holds the page's URL,
                // then a globe or a search glyph for what Go will do with what they typed.
                if (field.text == initialUrl) {
                    SecurityIcon(security, size = 20.dp)
                } else {
                    Icon(
                        if (OmniboxInput.isAddress(field.text)) MaterialSymbols.Language else MaterialSymbols.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (field.text.isEmpty()) {
                        Text(stringRes(Res.string.browser_pill_address_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                            ),
                        keyboardActions = KeyboardActions(onGo = { go() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (field.text.isNotEmpty()) {
                    IconButton(onClick = { field = TextFieldValue("") }) {
                        Icon(MaterialSymbols.Cancel, contentDescription = stringRes(Res.string.browser_pill_clear), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FilledIconButton(onClick = { go() }, enabled = field.text.isNotBlank(), modifier = Modifier.size(44.dp)) {
                    Icon(MaterialSymbols.AutoMirrored.ArrowForward, contentDescription = stringRes(Res.string.browser_pill_go), modifier = Modifier.size(22.dp))
                }
            }
        }

        // [clipboardUrl] when the host may read the clipboard up front; otherwise [onPasteAndGo] reads it only
        // on tap (Android announces every clipboard read, so the offer can't peek at the contents).
        if ((clipboardUrl != null && clipboardUrl != field.text) || onPasteAndGo != null) {
            val pasteLabel = stringRes(Res.string.browser_pill_paste_go)
            AssistChip(
                onClick = { if (clipboardUrl != null) go(clipboardUrl) else onPasteAndGo?.invoke() },
                label = { Text(if (clipboardUrl != null) pasteLabel + "  ·  " + BrowserChrome.displayHost(clipboardUrl) else pasteLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(MaterialSymbols.ContentPasteGo, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                modifier = Modifier.padding(start = 48.dp),
            )
        }

        if (suggestions.isNotEmpty()) {
            Column(Modifier.padding(start = 4.dp)) {
                suggestions.take(MAX_SUGGESTIONS).forEach { suggestion ->
                    SuggestionRow(
                        suggestion = suggestion,
                        onOpen = { go(suggestion.url) },
                        onFill = { field = TextFieldValue(suggestion.url, TextRange(suggestion.url.length)) },
                    )
                }
            }
        }
    }
}

private const val MAX_SUGGESTIONS = 5

@Composable
private fun SuggestionRow(
    suggestion: AddressSuggestion,
    onOpen: () -> Unit,
    onFill: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            SiteMonogram(suggestion.title.ifBlank { BrowserChrome.displayHost(suggestion.url) }, size = 32.dp)
            if (suggestion.isFavorite) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MaterialSymbols.Star, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.primary, filled = true)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(suggestion.title.ifBlank { BrowserChrome.displayHost(suggestion.url) }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(suggestion.url.removePrefix("https://"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onFill) {
            Icon(MaterialSymbols.NorthWest, contentDescription = stringRes(Res.string.browser_pill_fill_in), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
