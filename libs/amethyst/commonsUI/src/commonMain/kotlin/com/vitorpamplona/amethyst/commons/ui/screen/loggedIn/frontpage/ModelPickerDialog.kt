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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.frontpage.AgentModel
import com.vitorpamplona.amethyst.commons.frontpage.ModelChoice
import com.vitorpamplona.amethyst.commons.frontpage.ModelChoices
import com.vitorpamplona.amethyst.commons.frontpage.ModelVendorGroup
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.front_page_model_dialog_title
import com.vitorpamplona.amethyst.commons.resources.front_page_model_none_found
import com.vitorpamplona.amethyst.commons.resources.front_page_model_other_vendor
import com.vitorpamplona.amethyst.commons.resources.front_page_model_search
import com.vitorpamplona.amethyst.commons.resources.front_page_model_use_typed
import com.vitorpamplona.amethyst.commons.resources.routstr_edition_sats
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSectionLabel
import com.vitorpamplona.amethyst.commons.ui.components.M3LazyActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.PillTextField
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.util.formatGrouped

/**
 * The model choice in the app's grouped-dialog style (the feed selector's): models under their
 * vendor with readable names and the id beneath, the current one checked, a search field once the
 * list is long, and a row to use a typed id the list doesn't hold.
 *
 * OpenRouter lists hundreds: the names and vendors are worked out once per list
 * ([ModelChoices.catalog]), a keystroke only filters, and the list is one lazy column (a sticky
 * header per vendor, a row per model). It is a radio group: the search has the focus when it
 * opens, Enter picks the typed id or the one match, and it opens scrolled to the current model.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModelPickerDialog(
    ids: List<String>,
    byId: Map<String, AgentModel>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    /** One edition with a model in sats, for a Routstr node that prices in sats; null shows no price. */
    editionSats: ((AgentModel?) -> Long?)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val searchable = ids.size >= ModelChoices.SEARCH_FROM
    val catalog = remember(ids, byId) { ModelChoices.catalog(ids, byId) }
    val groups = remember(catalog, query) { catalog.group(query) }
    val typed = remember(ids, query) { ModelChoices.typedId(query, ids) }
    val other = stringRes(Res.string.front_page_model_other_vendor)
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }

    fun pick(id: String) {
        onSelect(id)
        onDismiss()
    }

    // Opens on the current model; a new search starts at the top.
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (!opened) {
            opened = true
            // One row up: the vendor's sticky header would cover the current model otherwise.
            indexOf(selected, groups, typed)?.let { listState.scrollToItem((it - 1).coerceAtLeast(0)) }
            if (searchable) focus.requestFocus()
        } else {
            listState.scrollToItem(0)
        }
    }

    M3LazyActionDialog(
        title = stringRes(Res.string.front_page_model_dialog_title),
        onDismiss = onDismiss,
        listState = listState,
        listModifier = Modifier.selectableGroup(),
        header =
            if (searchable) {
                {
                    PillTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringRes(Res.string.front_page_model_search),
                        leading = MaterialSymbols.Search,
                        imeAction = ImeAction.Done,
                        keyboardActions =
                            KeyboardActions(onDone = {
                                // Enter: the id typed, or the one model the search left.
                                val only = groups.singleOrNull()?.models?.singleOrNull()
                                (typed ?: only?.id)?.let(::pick)
                            }),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).focusRequester(focus),
                    )
                }
            } else {
                null
            },
    ) {
        if (typed != null) {
            item(key = "typed") {
                ModelRow(
                    name = stringRes(Res.string.front_page_model_use_typed, typed),
                    id = null,
                    selected = typed == selected,
                    icon = true,
                    onClick = { pick(typed) },
                )
            }
        }
        if (groups.isEmpty() && typed == null && query.isNotBlank()) {
            item(key = "none") {
                Text(
                    stringRes(Res.string.front_page_model_none_found, query.trim()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
        groups.forEach { group ->
            stickyHeader(key = "vendor:${group.label}") {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    M3ActionSectionLabel(group.label.ifEmpty { other })
                }
            }
            items(group.models, key = { "model:${it.id}" }) { choice ->
                val sats = editionSats?.invoke(byId[choice.id])
                ChoiceRow(choice, choice.id == selected, sats?.let { satsPerEdition(it) }) { pick(choice.id) }
            }
        }
    }
}

/** Where [selected] sits in the list as laid out: the typed row, then each vendor's header and models. */
private fun indexOf(
    selected: String?,
    groups: List<ModelVendorGroup>,
    typed: String?,
): Int? {
    if (selected == null) return null
    var index = if (typed != null) 1 else 0
    if (typed == selected) return 0
    for (group in groups) {
        index++
        val at = group.models.indexOfFirst { it.id == selected }
        if (at >= 0) return index + at
        index += group.models.size
    }
    return null
}

@Composable
private fun ChoiceRow(
    choice: ModelChoice,
    selected: Boolean,
    price: String?,
    onClick: () -> Unit,
) = ModelRow(choice.name, choice.id.takeIf { it != choice.name }, selected, icon = false, price = price, onClick = onClick)

@Composable
private fun satsPerEdition(sats: Long): String = pluralStringRes(Res.plurals.routstr_edition_sats, sats.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), formatGrouped(sats))

@Composable
private fun ModelRow(
    name: String,
    id: String?,
    selected: Boolean,
    icon: Boolean,
    price: String? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 28.dp, vertical = 8.dp),
    ) {
        if (icon) {
            Icon(MaterialSymbols.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Size20Modifier)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            id?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        price?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(MaterialSymbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}
