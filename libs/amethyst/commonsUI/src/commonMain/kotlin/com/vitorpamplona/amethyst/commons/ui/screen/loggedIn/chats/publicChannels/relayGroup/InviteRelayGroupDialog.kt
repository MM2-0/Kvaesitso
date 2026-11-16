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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.copy
import com.vitorpamplona.amethyst.commons.resources.relay_group_invite_code_label
import com.vitorpamplona.amethyst.commons.resources.relay_group_invite_description
import com.vitorpamplona.amethyst.commons.resources.relay_group_invite_preparing
import com.vitorpamplona.amethyst.commons.resources.relay_group_invite_title
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.launch

/**
 * Mint a NIP-29 invite (kind 9009) for the group as soon as the dialog opens and
 * show the code to copy and share. Requires admin/moderator rights on the relay.
 */
@Composable
fun InviteRelayGroupDialog(
    channel: RelayGroupChannel,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val code by remember { mutableStateOf(RandomInstance.bytes(6).toHexKey()) }

    // Observe the relay-signed metadata so the naddr / closed-state fill in live: the
    // dialog is often opened before the kind-39000 has arrived, and reading a snapshot
    // there would leave the Copy button permanently disabled once it does load.
    val channelState by channel
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val liveChannel = channelState.channel as? RelayGroupChannel ?: channel

    val isClosed = liveChannel.isClosed()

    // A shareable, cross-client coordinate for the group (opens the chat in any NIP-29
    // client). Null until the relay-signed metadata has loaded. For a closed (invite-only)
    // group we append the one-time code as the spec's `?invite=<code>` suffix so the whole
    // link is a single tap that auto-joins — no separate code to paste.
    val nAddr =
        liveChannel.toNAddr()?.let { base ->
            val uri = "nostr:$base"
            if (isClosed) "$uri?invite=$code" else uri
        }

    // A join code is only meaningful for closed (invite-only) groups; open groups
    // join directly from the shared naddr. So mint the kind-9009 invite only when
    // the group is actually closed, rather than on every dialog open.
    LaunchedEffect(code, isClosed) {
        if (isClosed) accountViewModel.createRelayGroupInvite(channel, code)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.relay_group_invite_title)) },
        text = {
            Column {
                Text(stringRes(Res.string.relay_group_invite_description))

                if (nAddr != null) {
                    Text(
                        text = nAddr,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                    )
                } else {
                    // Metadata (the group's pubkey, needed for the naddr) hasn't loaded yet.
                    Text(
                        text = stringRes(Res.string.relay_group_invite_preparing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Closed groups additionally need a one-time code to join.
                if (isClosed) {
                    Text(stringRes(Res.string.relay_group_invite_code_label))
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            // Copy the single shareable link. For a closed group the code is already
            // embedded as `?invite=…`, so one tap joins. Nothing to copy until the
            // group's metadata (and thus the naddr) has loaded, so disable until then.
            TextButton(
                enabled = nAddr != null,
                onClick = {
                    nAddr?.let { link -> scope.launch { clipboard.setText(link) } }
                    onDismiss()
                },
            ) {
                Text(stringRes(Res.string.copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}
