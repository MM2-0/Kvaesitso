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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.toNostrUriOrFallback
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_archived
import com.vitorpamplona.amethyst.commons.resources.geocache_found_it
import com.vitorpamplona.amethyst.commons.resources.geocache_ftf_won_by
import com.vitorpamplona.amethyst.commons.resources.geocache_invalid_proof
import com.vitorpamplona.amethyst.commons.resources.geocache_log_type_dnf
import com.vitorpamplona.amethyst.commons.resources.geocache_log_type_maintenance
import com.vitorpamplona.amethyst.commons.resources.geocache_log_type_note
import com.vitorpamplona.amethyst.commons.resources.geocache_verified_find
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.ui.components.rememberTextSharer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.GeocacheChip
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.rememberGeocachePalette
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nipCCGeocaching.comment.declaredGeocacheLogType
import com.vitorpamplona.quartz.nipCCGeocaching.comment.tags.GeocacheLogType
import com.vitorpamplona.quartz.nipCCGeocaching.firstToFind.FirstToFindResolver
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationValidator

/**
 * Every log filed under a cache, split the way the screen needs it.
 *
 * [winner] is the pubkey holding the exclusive first-to-find claim, which is the owner's `F` tag
 * when they have published one and the earliest *verified* log otherwise — never simply the
 * earliest log, because an unverified "I was first" is somebody's word.
 */
@Immutable
data class GeocacheLogs(
    val all: List<Note>,
    val finds: List<GeocacheFoundLogEvent>,
    val winner: String?,
)

/**
 * The cache's logs, recomputed whenever the note's reply list changes.
 *
 * Takes the already-observed [noteState] rather than observing again. The detail screen needs
 * this in three places — the body, the action bar and the owner menu — and each `observeNote`
 * opens its own relay subscription, so observing per caller would open three REQs for one note
 * and recompute the first-to-find resolution three times on every arriving log.
 *
 * `Note.replies` is a plain `var` with no snapshot behind it, so it cannot drive a recomposition
 * on its own. The observation the caller already holds does: a log arriving is filed under this
 * note by `LocalCache.computeReplyTo` and bumps the note's own state.
 */
@Composable
fun rememberGeocacheLogs(noteState: NoteState): GeocacheLogs {
    val listing = noteState.note.event as? GeocacheListingEvent

    return remember(noteState, listing) {
        val replies = noteState.note.replies
        val sorted = replies.sortedByDescending { it.createdAt() ?: 0L }
        val finds = replies.mapNotNull { it.event as? GeocacheFoundLogEvent }

        GeocacheLogs(
            all = sorted,
            finds = finds,
            winner = listing?.let { FirstToFindResolver.winnerPubKey(it, finds) },
        )
    }
}

/** Who hid it, with a tap through to their profile. */
@Composable
fun GeocacheOwnerRow(
    listing: GeocacheListingEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadUser(listing.pubKey) { user ->
        if (user != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClickableUserPicture(user, Size25dp, accountViewModel)
                UsernameDisplay(user, accountViewModel = accountViewModel)
            }
        }
    }
}

/** The first-to-find status line, naming the holder rather than printing a hex key at them. */
@Composable
fun GeocacheWinnerStrip(
    winner: String,
    accountViewModel: AccountViewModel,
) {
    LoadUser(winner) { user ->
        // LoadUser resolves the User object, not its metadata, so reading the display name off it
        // left the winner as a hex stub whenever their kind 0 arrived after this composed.
        // observeUserName asks the relays for the profile and recomposes when it lands. The name
        // stays an argument to the format string rather than a separate composable so translators
        // keep control of the word order.
        val name = user?.let { observeUserName(it, accountViewModel).value }

        Text(
            text = stringRes(Res.string.geocache_ftf_won_by, name?.ifBlank { null } ?: winner.take(8)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Shares the cache as `nostr:naddr…`, which is how it opens in another NIP-CC client. */
@Composable
fun GeocacheShareAction(address: Address) {
    val sharer = rememberTextSharer()
    val shareStr = stringRes(Res.string.quick_action_share)
    IconButton(onClick = {
        sharer.share(address.toNostrUriOrFallback(null, null), null, shareStr)
    }) {
        Icon(
            symbol = MaterialSymbols.AutoMirrored.Send,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * One row of the cache's history: a kind 7516 find, or a kind 1111 comment carrying a
 * `dnf`/`note`/`maintenance` type.
 *
 * A find's proof badge is computed, never assumed — the same rule the feed card follows. The
 * validation runs here rather than in a background state because the thread is short and already
 * behind a scroll; a cache with hundreds of verified logs would want the [produceState] treatment
 * the feed card uses.
 */
@Composable
fun GeocacheLogRow(
    log: Note,
    listing: GeocacheListingEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = log.event ?: return
    val author = log.author?.pubkeyHex ?: event.pubKey
    val palette = rememberGeocachePalette()

    // A proven find gets a rule down its left edge. In a thread of a dozen logs this is what
    // separates "someone says they found it" from "someone demonstrably stood there", without
    // making the reader parse a badge on every row.
    val proven =
        event is GeocacheFoundLogEvent &&
            event.hasVerificationAttached() &&
            GeocacheVerificationValidator.isValid(event, listing)

    Column(
        Modifier
            .fillMaxWidth()
            .clickable { nav.nav(Route.Note(log.idHex)) }
            .then(
                if (proven) {
                    Modifier
                        .drawBehind {
                            drawRect(
                                color = palette.proven,
                                size = Size(RULE_WIDTH_PX, size.height),
                            )
                        }.padding(start = 10.dp)
                } else {
                    Modifier
                },
            ).padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LoadUser(author) { user ->
                if (user != null) {
                    ClickableUserPicture(user, Size25dp, accountViewModel)
                    UsernameDisplay(user, accountViewModel = accountViewModel)
                }
            }

            Spacer(Modifier.weight(1f))

            when (event) {
                is GeocacheFoundLogEvent -> GeocacheFindBadge(event, listing)
                is CommentEvent -> GeocacheCommentBadge(event)
            }
        }

        if (event.content.isNotBlank()) {
            Text(
                text = event.content.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun GeocacheFindBadge(
    log: GeocacheFoundLogEvent,
    listing: GeocacheListingEvent,
) {
    val palette = rememberGeocachePalette()
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    when {
        !log.hasVerificationAttached() -> GeocacheChip(stringRes(Res.string.geocache_found_it), muted)
        GeocacheVerificationValidator.isValid(log, listing) ->
            GeocacheChip("🔐  " + stringRes(Res.string.geocache_verified_find), palette.wash(palette.proven), strong = true)
        else -> GeocacheChip("⚠️  " + stringRes(Res.string.geocache_invalid_proof), muted, dim = true)
    }
}

@Composable
private fun GeocacheCommentBadge(comment: CommentEvent) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    val label =
        when (comment.tags.declaredGeocacheLogType()) {
            GeocacheLogType.DNF -> stringRes(Res.string.geocache_log_type_dnf)
            GeocacheLogType.NOTE -> stringRes(Res.string.geocache_log_type_note)
            GeocacheLogType.MAINTENANCE -> stringRes(Res.string.geocache_log_type_maintenance)
            // The owner's own retirement note. The listing's `archived` type is what actually
            // takes the cache out of play, and the screen already says so at the top, so
            // repeating it on the comment row would be noise.
            GeocacheLogType.ARCHIVED -> stringRes(Res.string.geocache_archived)
            null -> return
        }

    GeocacheChip(label, muted, dim = true)
}

/** Width of the "verified" rule, in pixels at the density the thread draws at. */
private const val RULE_WIDTH_PX = 7f
