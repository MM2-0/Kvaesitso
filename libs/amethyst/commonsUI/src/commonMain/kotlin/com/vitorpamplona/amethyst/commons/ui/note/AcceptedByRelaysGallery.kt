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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accepted_by_relays
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.RenderRelay
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.MediumRelayIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.NotificationIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdStartPadding
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample

/**
 * Observes the relays the note was seen on, throttled and de-duplicated for the
 * "accepted by relays" gallery line. Exposed as State so the caller can both gate
 * the gallery's visibility on it and feed it to [RenderAcceptedByRelaysGallery]
 * from a single subscription.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun observeNoteRelays(baseNote: Note): State<ImmutableList<NormalizedRelayUrl>> {
    // Cold wrapper: `flow()` must be re-resolved on every collection start. A memory
    // trim destroys the NoteFlowSet while the lifecycle is stopped; a stateFlow
    // captured in remember would then be orphaned and never see another relay update.
    // Sampled at 500ms because relay arrivals churn while a note is actively fanning out.
    val flow =
        remember(baseNote) {
            flow { emitAll(baseNote.flow().relays.stateFlow) }
                .sample(500)
                .map { it.note.relays.toImmutableList() }
                .distinctUntilChanged()
        }

    val initial = remember(baseNote) { baseNote.relays.toImmutableList() }
    return flow.collectAsStateWithLifecycle(initial)
}

/**
 * Lightweight "does this note have any relays" observer for the reaction row's
 * expand-button gate, which runs for every note in the feed. Unlike
 * [observeNoteRelays] it maps to a Boolean (no per-emission list allocation) and
 * needs no throttling, since the emptiness flips at most a couple of times.
 */
@Composable
internal fun observeNoteHasRelays(baseNote: Note): State<Boolean> {
    val flow =
        remember(baseNote) {
            flow { emitAll(baseNote.flow().relays.stateFlow) }
                .map { it.note.relays.isNotEmpty() }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(baseNote.relays.isNotEmpty())
}

/**
 * The "accepted by relays" line of the reaction gallery: the favicon of every relay
 * the note was seen on. This is the same relay-pill set that Complete UI Mode used to
 * paint below the author avatar; it now lives in the expanded reaction gallery so it
 * is available to everyone, not just Complete mode.
 */
@Composable
internal fun RenderAcceptedByRelaysGallery(
    relays: ImmutableList<NormalizedRelayUrl>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Row(Modifier.fillMaxWidth()) {
        // Indented, NotificationIconModifier (55dp wide, 5dp end padding) matches the
        // category-icon column of the zap/like/nutzap gallery rows so the Dns icon lines up
        // with them; full width centres it in their shared 35dp slot.
        GalleryIconColumn(NotificationIconModifier) { iconAlignment ->
            Icon(
                symbol = MaterialSymbols.Dns,
                contentDescription = stringRes(id = Res.string.accepted_by_relays),
                modifier = Modifier.size(Size20dp).align(iconAlignment),
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }

        RelayGallery(relays, nav, accountViewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelayGallery(
    relays: ImmutableList<NormalizedRelayUrl>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Column(modifier = StdStartPadding) {
        FlowRow {
            relays.forEach { relay ->
                // Match the 35dp author pictures of the sibling zap/boost/reaction lines.
                RenderRelay(
                    relay = relay,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    boxSize = Size35dp,
                    iconModifier = MediumRelayIconModifier,
                )
            }
        }
    }
}
