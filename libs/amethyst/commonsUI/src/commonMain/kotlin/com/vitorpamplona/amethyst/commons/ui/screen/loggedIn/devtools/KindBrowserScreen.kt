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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_custom
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_lookup_title
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_nip
import com.vitorpamplona.amethyst.commons.resources.devtools_kinds_count
import com.vitorpamplona.amethyst.commons.resources.devtools_kinds_search
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.kinds.KindName
import com.vitorpamplona.quartz.kinds.KindNames

/**
 * Kind Lookup's entry point: every kind Quartz knows ([KindNames], the same registry `amy kind`
 * reads), searchable by number, name or NIP. Tapping one opens [KindLookupScreen] to crawl relays
 * for it; typing a number Quartz does not know still offers the crawl, which is the question
 * kinds.amethyst.social exists to answer.
 */
@Composable
fun KindBrowserScreen(nav: INav) {
    var query by rememberSaveable { mutableStateOf("") }
    val all =
        remember {
            KindNames.names.entries
                .sortedBy { it.key }
                .map { it.key to it.value }
        }
    val matches = remember(query) { filterKinds(all, query) }
    val typedKind = query.trim().toIntOrNull()?.takeIf { it in 0..65535 }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.devtools_kind_lookup_title), nav) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier.padding(padding),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringRes(Res.string.devtools_kinds_search)) },
                    leadingIcon = { Icon(MaterialSymbols.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    pluralStringRes(Res.plurals.devtools_kinds_count, matches.size, matches.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            if (typedKind != null && KindNames.infoFor(typedKind) == null) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { nav.nav(Route.DevToolsKindUsage(typedKind)) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(MaterialSymbols.TravelExplore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringRes(Res.string.devtools_kind_custom, typedKind),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    HorizontalDivider()
                }
            }

            items(matches, key = { it.first }) { (kind, info) ->
                KindRow(kind, info) { nav.nav(Route.DevToolsKindUsage(kind)) }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun KindRow(
    kind: Int,
    info: KindName,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            kind.toString(),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(72.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(kindName(kind), style = MaterialTheme.typography.bodyMedium)
        }
        info.nip?.let { nip ->
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    stringRes(Res.string.devtools_kind_nip, nip),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Matches a kind number prefix, a NIP number (`57`, `nip-57`) or any part of the English name. */
private fun filterKinds(
    all: List<Pair<Int, KindName>>,
    query: String,
): List<Pair<Int, KindName>> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return all
    val nip = q.removePrefix("nip-").removePrefix("nip").trim()
    return all.filter { (kind, info) ->
        kind.toString().startsWith(q) ||
            info.name.lowercase().contains(q) ||
            info.nip?.lowercase() == nip
    }
}
