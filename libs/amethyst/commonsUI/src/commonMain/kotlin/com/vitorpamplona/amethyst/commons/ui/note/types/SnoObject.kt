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
import com.vitorpamplona.amethyst.commons.sno.ui.SnoObjectViewer
import com.vitorpamplona.amethyst.commons.ui.note.SnoObjectTurntableCard
import com.vitorpamplona.amethyst.commons.ui.note.SnoObjectUnreadableCard
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoObjectEvent
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPaletteRef
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoResult

private val VIEWER_HEIGHT = 360.dp

/**
 * Entry for a Simple Nostr Object (DECK-0003 kind 33331).
 *
 * The geometry travels in the event's content, so the object itself is drawn
 * from what the note already carries. The one thing that can be elsewhere is
 * its palette: §1.3a lets `colors` index a palette another event holds, and
 * [WithSnoPalette] fetches that one. Until it arrives the object is drawn
 * against the built-in, which is what §1.3b says an unresolved reference means.
 *
 * A payload that fails §1.9 is not drawn — the deck requires that — and the
 * rule it broke is shown instead of nothing.
 */
@Composable
fun RenderSnoObject(
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    val noteEvent = baseNote.event as? SnoObjectEvent ?: return
    // Parsed once against the built-in to learn which palette it wants, then
    // again with that palette once it is in hand. The second parse can fail
    // where the first did not — an index of 238 is fine against 256 entries and
    // out of range against a five-colour moment — and that is the object being
    // wrong rather than the palette.
    val first = remember(noteEvent) { noteEvent.sno() }

    WithSnoPalette(first.payloadOrNull()?.paletteRef ?: SnoPaletteRef.BuiltIn, accountViewModel) { palette ->
        val parsed = remember(noteEvent, palette) { if (palette == null) first else noteEvent.sno(palette) }

        when (parsed) {
            is SnoResult.Invalid -> SnoObjectUnreadableCard(parsed.rule)
            is SnoResult.Valid -> {
                var turning by remember(noteEvent) { mutableStateOf(false) }

                SnoObjectTurntableCard(
                    payload = parsed.payload,
                    eventId = noteEvent.id,
                    onOpen = { turning = true },
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
