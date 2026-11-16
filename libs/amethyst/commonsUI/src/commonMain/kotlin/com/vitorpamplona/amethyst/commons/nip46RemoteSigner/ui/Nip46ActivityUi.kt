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
package com.vitorpamplona.amethyst.commons.nip46RemoteSigner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ActivityEntry
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_act_connected
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_act_decrypted
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_act_encrypted
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_act_listed_relays
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_act_other
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_act_ping
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_act_shared_pubkey
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_act_signed_kind
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_activity_denied
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.commons.ui.stringRes

private val LiveGreen = Color(0xFF3DDC84)

/** A card listing the most recent [entries] a NIP-46 signer serviced (newest first). */
@Composable
fun Nip46ActivityCard(
    entries: List<Nip46ActivityEntry>,
    max: Int = 8,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            entries.take(max).forEach { Nip46ActivityRow(it) }
        }
    }
}

@Composable
private fun Nip46ActivityRow(entry: Nip46ActivityEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (entry.ok) LiveGreen else MaterialTheme.colorScheme.error),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                describeNip46Activity(entry),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.clientPubKey.take(12) + "…",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TimeAgo(entry.atSeconds)
    }
}

/** A friendly, localized one-liner for a serviced request (e.g. "Signed an event (kind 1)"). */
@Composable
fun describeNip46Activity(entry: Nip46ActivityEntry): String {
    val base =
        when (entry.method) {
            "sign_event" -> stringRes(Res.string.nip46_signer_act_signed_kind, entry.kind ?: 0)
            "nip04_encrypt", "nip44_encrypt" -> stringRes(Res.string.nip46_signer_act_encrypted)
            "nip04_decrypt", "nip44_decrypt" -> stringRes(Res.string.nip46_signer_act_decrypted)
            "get_public_key" -> stringRes(Res.string.nip46_signer_act_shared_pubkey)
            "connect" -> stringRes(Res.string.nip46_signer_act_connected)
            "ping" -> stringRes(Res.string.nip46_signer_act_ping)
            "get_relays" -> stringRes(Res.string.nip46_signer_act_listed_relays)
            else -> stringRes(Res.string.nip46_signer_act_other, entry.method)
        }
    return if (entry.ok) base else "$base · ${stringRes(Res.string.nip46_signer_activity_denied)}"
}
