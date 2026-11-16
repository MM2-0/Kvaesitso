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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_add_people_empty
import com.vitorpamplona.amethyst.commons.resources.buzz_add_people_hint
import com.vitorpamplona.amethyst.commons.resources.buzz_import_added
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.relay_group_add_member
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import androidx.compose.runtime.LaunchedEffect as ComposeLaunchedEffect

/**
 * A reusable "add a person" dialog for Buzz: the app's ordinary user search — the same
 * [UserSuggestionState] engine the @-mention typeahead uses — over the local cache, the relays
 * (NIP-50) and NIP-05 identifiers, plus a pasted npub/nprofile.
 *
 * It used to search only [com.vitorpamplona.amethyst.commons.model.cache.LocalCache], so anyone the device had
 * never seen simply had no result and the only way through was to paste a raw hex key — which is
 * what the field's own hint told you to do. Searching the relays is what makes finding a person by
 * name work at all here.
 *
 * Context-agnostic — the caller supplies [isAlreadyIn] (membership predicate) and [onAdd] (the
 * actual add, e.g. a channel kind-9000 put-user or a community kind-9030 admin-add). Used by both
 * the channel members screen and the Buzz community view. Members already present render an "Added"
 * hint instead of the add affordance and do nothing when tapped.
 */
@Composable
fun BuzzAddPeopleDialog(
    title: String,
    accountViewModel: AccountViewModel,
    isAlreadyIn: (HexKey) -> Boolean,
    onAdd: (HexKey) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val userSuggestions =
        remember(accountViewModel) {
            UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
        }
    val focusRequester = remember { FocusRequester() }

    ComposeLaunchedEffect(query) { userSuggestions.processCurrentWord(query) }
    ComposeLaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            symbol = MaterialSymbols.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    label = { Text(stringRes(Res.string.buzz_add_people_hint)) },
                )

                // The typeahead needs a couple of characters before a relay search is worth firing;
                // below that the list would flash every match in the cache.
                if (query.length > 2) {
                    ShowUserSuggestionList(
                        userSuggestions = userSuggestions,
                        onSelect = { user ->
                            if (!isAlreadyIn(user.pubkeyHex)) {
                                onAdd(user.pubkeyHex)
                                onDismiss()
                            }
                        },
                        accountViewModel = accountViewModel,
                        modifier = SuggestionListDefaultHeightChat,
                        // The dialog already supplies the surface and the spacing: drop the
                        // dropdown's opaque rows, per-row dividers and top gap, which are there for
                        // floating over a composer.
                        itemColors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        showDividers = false,
                        contentPadding = PaddingValues(0.dp),
                        onEmpty = {
                            Text(
                                text = stringRes(Res.string.buzz_add_people_empty),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = { user ->
                            if (isAlreadyIn(user.pubkeyHex)) {
                                Text(
                                    text = stringRes(Res.string.buzz_import_added),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Icon(
                                    symbol = MaterialSymbols.PersonAdd,
                                    contentDescription = stringRes(Res.string.relay_group_add_member),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}
