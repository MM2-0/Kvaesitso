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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.sno_shard_dataspace
import com.vitorpamplona.amethyst.commons.resources.sno_shard_ideaspace
import com.vitorpamplona.amethyst.commons.sno.ui.SnoObjectViewer
import com.vitorpamplona.amethyst.commons.ui.note.SnoObjectCard
import com.vitorpamplona.amethyst.commons.ui.note.SnoObjectUnreadableCard
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.cyberspace.CyberspaceCoordinate
import com.vitorpamplona.quartz.cyberspace.CyberspacePlane
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPaletteRef
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoResult
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoShardEvent
import org.jetbrains.compose.resources.stringResource

private val VIEWER_HEIGHT = 360.dp

/**
 * Entry for a shard: an object someone hid at a place (DECK-0003 §3.2, kind
 * 3330).
 *
 * The same payload and the same rules as a standalone object — only the
 * container differs — so this renders through the same card.
 *
 * This is the shard that arrived **on its own**: quoted in a note, or fetched
 * by id. One still inside its `kind 33330` bag is ciphertext until that
 * region's key is derived, and deriving it belongs to [RenderCyberspaceBag],
 * which sweeps the box the bag's hint names (§7.7) and renders what comes out
 * through this same card. So a sealed shard reaching here is one nobody has
 * opened, not one nobody can.
 */
@Composable
fun RenderSnoShard(
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    val noteEvent = baseNote.event as? SnoShardEvent ?: return

    // A shard whose geometry is still inside its bag is not a broken payload
    // and §7.6 says so outright, so there is nothing to report and nothing to
    // draw. Both 3330s reachable on a relay today are this.
    if (noteEvent.isSealed()) return

    val first = remember(noteEvent) { noteEvent.shard() }
    val place = placeOf(noteEvent)

    WithSnoPalette(first.payloadOrNull()?.paletteRef ?: SnoPaletteRef.BuiltIn, accountViewModel) { palette ->
        val parsed = remember(noteEvent, palette) { if (palette == null) first else noteEvent.shard(palette) }

        when (parsed) {
            is SnoResult.Invalid -> SnoObjectUnreadableCard(parsed.rule)
            is SnoResult.Valid -> {
                var turning by remember(noteEvent) { mutableStateOf(false) }

                SnoObjectCard(
                    payload = parsed.payload,
                    eventId = noteEvent.id,
                    onClick = { turning = true },
                    footnote = place,
                )

                if (turning) {
                    Dialog(onDismissRequest = { turning = false }) {
                        SnoObjectViewer(
                            payload = parsed.payload,
                            eventId = noteEvent.id,
                            contentDescription = parsed.payload.name.ifBlank { null },
                            modifier = Modifier.fillMaxWidth().height(VIEWER_HEIGHT),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Where this shard says it is, in the little a client with no world can say.
 *
 * §7.6 lets an item carry a `C` tag holding its exact coordinate, "which lets a
 * client render it at a point rather than somewhere in the region". Amethyst has
 * no region and no point to render it at, but the tag still carries one thing a
 * reader understands on its own: the plane, which says whether the shard was
 * hidden at a place on Earth or at one with no physical counterpart (§2.4). The
 * coordinate itself is abbreviated beside it — enough to tell two shards apart,
 * and the whole of it is in the event for a client that can go there. See
 * [CyberspaceCoordinate] for why the axes are not decoded.
 *
 * Null when there is no `C` tag, which §7.6 allows: such an item "is located no
 * more precisely than the region".
 */
@Composable
private fun placeOf(noteEvent: SnoShardEvent): String? {
    val coordinate = noteEvent.coordinate() ?: return null
    val plane = CyberspaceCoordinate.planeOf(coordinate) ?: return null
    val short = coordinate.take(SHORT_COORDINATE) + "\u2026" + coordinate.takeLast(SHORT_COORDINATE)
    return when (plane) {
        CyberspacePlane.DATASPACE -> stringResource(Res.string.sno_shard_dataspace, short)
        CyberspacePlane.IDEASPACE -> stringResource(Res.string.sno_shard_ideaspace, short)
    }
}

/** How much of a 32-byte coordinate to show at each end. */
private const val SHORT_COORDINATE = 6
