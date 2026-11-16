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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.devtools.Nip19Field
import com.vitorpamplona.amethyst.commons.devtools.Nip19Inspection
import com.vitorpamplona.amethyst.commons.devtools.Nip19Inspector
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.uri.uriToRoute
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.devtools_field_address
import com.vitorpamplona.amethyst.commons.resources.devtools_field_author
import com.vitorpamplona.amethyst.commons.resources.devtools_field_event_id
import com.vitorpamplona.amethyst.commons.resources.devtools_field_event_json
import com.vitorpamplona.amethyst.commons.resources.devtools_field_identifier
import com.vitorpamplona.amethyst.commons.resources.devtools_field_kind
import com.vitorpamplona.amethyst.commons.resources.devtools_field_private_key
import com.vitorpamplona.amethyst.commons.resources.devtools_field_public_key
import com.vitorpamplona.amethyst.commons.resources.devtools_field_relay
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_usage_title
import com.vitorpamplona.amethyst.commons.resources.devtools_nip19_decoded
import com.vitorpamplona.amethyst.commons.resources.devtools_nip19_encodings
import com.vitorpamplona.amethyst.commons.resources.devtools_nip19_hex_ambiguous
import com.vitorpamplona.amethyst.commons.resources.devtools_nip19_input_label
import com.vitorpamplona.amethyst.commons.resources.devtools_nip19_invalid
import com.vitorpamplona.amethyst.commons.resources.devtools_nip19_secret_warning
import com.vitorpamplona.amethyst.commons.resources.devtools_nip19_title
import com.vitorpamplona.amethyst.commons.resources.devtools_open_in_app
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import org.jetbrains.compose.resources.StringResource

/**
 * nak web / nostr-tools.com's NIP-19 converter: paste any code or a 64-char hex and see every
 * part it carries, every other way to write it, and a way to open it in the app.
 */
@Composable
fun Nip19ToolScreen(
    initialInput: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var input by rememberSaveable { mutableStateOf(initialInput ?: "") }
    val inspection = remember(input) { Nip19Inspector.inspect(input) }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.devtools_nip19_title), nav) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
        ) {
            DevToolsInput(input, { input = it }, stringRes(Res.string.devtools_nip19_input_label))

            when (inspection) {
                Nip19Inspection.Empty -> {}

                Nip19Inspection.Invalid ->
                    DevToolsCheckRow(MaterialSymbols.ErrorOutline, stringRes(Res.string.devtools_nip19_invalid), MaterialTheme.colorScheme.error)

                is Nip19Inspection.RawHex -> {
                    Text(
                        stringRes(Res.string.devtools_nip19_hex_ambiguous),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DevToolsSectionTitle(stringRes(Res.string.devtools_nip19_encodings))
                    inspection.encodings.forEach { DevToolsValueRow(it.prefix, it.value) }
                }

                is Nip19Inspection.Decoded -> {
                    if (inspection.isSecret) {
                        DevToolsCheckRow(MaterialSymbols.Warning, stringRes(Res.string.devtools_nip19_secret_warning), MaterialTheme.colorScheme.error)
                    }
                    DevToolsSectionTitle(stringRes(Res.string.devtools_nip19_decoded, inspection.prefix))
                    inspection.rows.forEach { row ->
                        if (row.field == Nip19Field.EVENT_JSON) {
                            Text(stringRes(row.field.label()), style = MaterialTheme.typography.labelMedium)
                            DevToolsCodeBlock(row.value)
                        } else {
                            val label =
                                if (row.field == Nip19Field.KIND) {
                                    stringRes(Res.string.devtools_field_kind) + " · " + kindName(row.value.toIntOrNull() ?: -1)
                                } else {
                                    stringRes(row.field.label())
                                }
                            DevToolsValueRow(label, row.value)
                        }
                    }

                    if (inspection.encodings.isNotEmpty()) {
                        DevToolsSectionTitle(stringRes(Res.string.devtools_nip19_encodings))
                        inspection.encodings.forEach { DevToolsValueRow(it.prefix, it.value) }
                    }

                    val kind =
                        inspection.rows
                            .firstOrNull { it.field == Nip19Field.KIND }
                            ?.value
                            ?.toIntOrNull()
                    // nrelay has no screen of its own; an nsec must never be routed anywhere.
                    if (!inspection.isSecret && inspection.prefix != "nrelay") {
                        // Resolved on tap: uriToRoute creates cache entries, which every keystroke must not.
                        OutlinedButton(
                            onClick = { uriToRoute(input.trim(), accountViewModel.account)?.let(nav::nav) },
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text(stringRes(Res.string.devtools_open_in_app))
                        }
                    }
                    if (kind != null) {
                        OutlinedButton(onClick = { nav.nav(Route.DevToolsKindUsage(kind)) }) {
                            Text(stringRes(Res.string.devtools_kind_usage_title, kind))
                        }
                    }
                }
            }
        }
    }
}

private fun Nip19Field.label(): StringResource =
    when (this) {
        Nip19Field.PUBLIC_KEY -> Res.string.devtools_field_public_key
        Nip19Field.PRIVATE_KEY -> Res.string.devtools_field_private_key
        Nip19Field.EVENT_ID -> Res.string.devtools_field_event_id
        Nip19Field.AUTHOR -> Res.string.devtools_field_author
        Nip19Field.KIND -> Res.string.devtools_field_kind
        Nip19Field.IDENTIFIER -> Res.string.devtools_field_identifier
        Nip19Field.ADDRESS -> Res.string.devtools_field_address
        Nip19Field.RELAY -> Res.string.devtools_field_relay
        Nip19Field.EVENT_JSON -> Res.string.devtools_field_event_json
    }
