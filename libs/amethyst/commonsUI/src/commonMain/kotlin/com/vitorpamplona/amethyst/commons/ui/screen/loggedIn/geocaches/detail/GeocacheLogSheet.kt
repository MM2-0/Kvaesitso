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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_log_comment_title
import com.vitorpamplona.amethyst.commons.resources.geocache_log_placeholder
import com.vitorpamplona.amethyst.commons.resources.geocache_log_post
import com.vitorpamplona.amethyst.commons.resources.geocache_log_type_dnf
import com.vitorpamplona.amethyst.commons.resources.geocache_log_type_maintenance
import com.vitorpamplona.amethyst.commons.resources.geocache_log_type_note
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nipCCGeocaching.comment.GeocacheLogComment
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.launch

/**
 * The three non-found logs NIP-CC defines.
 *
 * They share one sheet because they differ by exactly one tag value: a kind 1111 comment rooted
 * on the listing, with `t` naming the type. Giving each its own route would cost three
 * back-stack entries and three screens to say "type a sentence".
 */
enum class GeocacheLogSheetType {
    DNF,
    NOTE,
    MAINTENANCE,
}

/**
 * Files a did-not-find, a note or a maintenance report against [listing].
 *
 * Deliberately a sheet and not a composer screen: there is one field, no media, and no
 * verification ceremony. "I found it" is the one that earns a screen of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeocacheLogSheet(
    type: GeocacheLogSheetType,
    listing: GeocacheListingEvent,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalSheetState()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var publishing by remember { mutableStateOf(false) }

    val title =
        when (type) {
            GeocacheLogSheetType.DNF -> stringRes(Res.string.geocache_log_type_dnf)
            GeocacheLogSheetType.NOTE -> stringRes(Res.string.geocache_log_type_note)
            GeocacheLogSheetType.MAINTENANCE -> stringRes(Res.string.geocache_log_type_maintenance)
        }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = stringRes(Res.string.geocache_log_comment_title) + " · " + title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(12.dp))

            SpellCheckedOutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringRes(Res.string.geocache_log_placeholder)) },
                minLines = 3,
            )

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    enabled = !publishing && message.isNotBlank(),
                    onClick = {
                        publishing = true
                        scope.launch {
                            // The cache's own preferred log relays travel in the bundle's hint, so a
                            // reader on a different relay set can still find the thread.
                            val bundle = EventHintBundle(listing, listing.logRelays().firstOrNull())
                            val template =
                                when (type) {
                                    GeocacheLogSheetType.DNF -> GeocacheLogComment.didNotFind(message.trim(), bundle)
                                    GeocacheLogSheetType.NOTE -> GeocacheLogComment.note(message.trim(), bundle)
                                    GeocacheLogSheetType.MAINTENANCE -> GeocacheLogComment.needsMaintenance(message.trim(), bundle)
                                }

                            runCatchingCancellable { accountViewModel.account.signAndComputeBroadcast(template) }
                            publishing = false
                            onDismiss()
                        }
                    },
                ) { Text(stringRes(Res.string.geocache_log_post)) }
            }

            Spacer(Modifier.navigationBarsPadding().height(20.dp))
        }
    }
}
