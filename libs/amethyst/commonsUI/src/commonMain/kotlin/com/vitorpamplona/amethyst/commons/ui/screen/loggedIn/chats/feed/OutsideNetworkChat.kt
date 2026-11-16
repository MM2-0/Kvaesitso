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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chat_outside_network_messages
import com.vitorpamplona.amethyst.commons.resources.chat_outside_network_show
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.wot.network.TrustVerdicts
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Who a public chat vouches for regardless of the Web of Trust: a stream's host and speakers, a
 * relay group's members, a channel's creator. Their messages never collapse. Build it from the
 * room's observed state, so a change (a new speaker, a new member) builds a new one.
 */
@Immutable
class ChatRoomVouches(
    private val authors: Set<HexKey>,
) {
    fun vouchesFor(author: HexKey): Boolean = author in authors

    companion object {
        val NOBODY = ChatRoomVouches(emptySet())
    }
}

/**
 * Consecutive messages from outside the network, collapsed into one row. [head] is the run's
 * oldest message, where the row is drawn (the top of the run on screen); [members] are all of
 * them, newest first.
 */
@Immutable
class OutsideNetworkRun(
    val head: Note,
    val members: List<Note>,
)

/** Which messages of [notes] (newest first) collapse, by id, and the run each belongs to. */
@Immutable
class OutsideNetworkRuns(
    val byId: Map<String, OutsideNetworkRun>,
) {
    companion object {
        val NONE = OutsideNetworkRuns(emptyMap())
    }
}

/**
 * Groups the messages from outside the network into runs, skipping the ones the user already
 * revealed and the people the room [vouches] for. Nothing collapses while no network is active,
 * when [vouches] is null, or for the user's own messages and the people they follow (see
 * [TrustVerdicts]).
 */
@Composable
fun rememberOutsideNetworkRuns(
    notes: List<Note>,
    revealed: Set<String>,
    vouches: ChatRoomVouches?,
    accountViewModel: AccountViewModel,
): OutsideNetworkRuns {
    if (vouches == null) return OutsideNetworkRuns.NONE
    val verdicts by accountViewModel.account.trustVerdicts.collectAsStateWithLifecycle()
    return remember(notes, revealed, verdicts, vouches) {
        if (!verdicts.isActive) {
            OutsideNetworkRuns.NONE
        } else {
            // One verdict per author: a long chat repeats the same few senders.
            val byAuthor = HashMap<String, Boolean>()
            outsideNetworkRuns(notes, revealed) { author -> byAuthor.getOrPut(author) { !vouches.vouchesFor(author) && verdicts.isOutside(author) } }
        }
    }
}

/** [notes] is newest first, as the chat feed holds it. */
fun outsideNetworkRuns(
    notes: List<Note>,
    revealed: Set<String>,
    isOutside: (authorHex: String) -> Boolean,
): OutsideNetworkRuns {
    val byId = HashMap<String, OutsideNetworkRun>()
    var run = ArrayList<Note>()

    fun close() {
        if (run.isEmpty()) return
        val members = run
        val group = OutsideNetworkRun(head = members.last(), members = members)
        members.forEach { byId[it.idHex] = group }
        run = ArrayList()
    }

    for (note in notes) {
        val author = note.author?.pubkeyHex
        if (author != null && note.idHex !in revealed && isOutside(author)) {
            run.add(note)
        } else {
            close()
        }
    }
    close()
    return if (byId.isEmpty()) OutsideNetworkRuns.NONE else OutsideNetworkRuns(byId)
}

/**
 * Where a run of messages from outside the network sits: a quiet pill that says something is
 * there and how many, with no names, pictures, text or times. Tapping it shows the messages.
 */
@Composable
fun OutsideNetworkChatRow(
    count: Int,
    onReveal: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onReveal)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Shield,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.placeholderText,
            )
            Text(
                pluralStringRes(Res.plurals.chat_outside_network_messages, count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.placeholderText,
            )
            Text(
                stringRes(Res.string.chat_outside_network_show),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
