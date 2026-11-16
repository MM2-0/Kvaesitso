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
import com.vitorpamplona.amethyst.commons.ui.note.SnoAvatarCard
import com.vitorpamplona.amethyst.commons.ui.note.SnoAvatarDefaultCard
import com.vitorpamplona.amethyst.commons.ui.note.SnoAvatarUnpaidCard
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoAvatarEvent
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPaletteRef
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload

private val VIEWER_HEIGHT = 360.dp

/**
 * Entry for a cyberspace avatar (kind 11333).
 *
 * §8.10 gates the drawing rather than the parsing: an avatar that has not paid
 * for its reach and its detail, or whose content cannot be read, MUST NOT be
 * drawn.
 *
 * Empty content is the default avatar: it owes no work, and it is what every
 * identity is until it adopts a shape. Drawing nothing for it is correct and
 * unhelpful — the note disappears out of the feed, which reads as a fault — so
 * it gets the wireframe icosahedron the reference puts in its place.
 */
@Composable
fun RenderSnoAvatar(
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    val noteEvent = baseNote.event as? SnoAvatarEvent ?: return
    if (noteEvent.isDefaultAvatar()) {
        SnoAvatarDefaultCard(noteEvent.id)
        return
    }

    // One parse: pricing an avatar reads its payload, and payment() hands that
    // back, so nothing here parses the same content twice on the way to a frame.
    // The price cannot change with the palette — §8.10 charges for reach and
    // detail and never for colour — but whether the payload reads at all can,
    // so the verdict is taken again with whichever palette arrives.
    val first = remember(noteEvent) { noteEvent.payment() }

    WithSnoPalette(first.payload?.paletteRef ?: SnoPaletteRef.BuiltIn, accountViewModel) { palette ->
        val payment = remember(noteEvent, palette) { if (palette == null) first else noteEvent.payment(palette) }
        val shape: SnoPayload? = if (payment.ok) payment.payload else null

        if (shape == null) {
            SnoAvatarUnpaidCard()
        } else {
            var turning by remember(noteEvent) { mutableStateOf(false) }

            SnoAvatarCard(
                payload = shape,
                eventId = noteEvent.id,
                name = noteEvent.nameTag(),
                onClick = { turning = true },
            )

            if (turning) {
                Dialog(onDismissRequest = { turning = false }) {
                    SnoObjectViewer(
                        payload = shape,
                        eventId = noteEvent.id,
                        contentDescription = noteEvent.nameTag() ?: shape.name.ifBlank { null },
                        modifier = Modifier.fillMaxWidth().height(VIEWER_HEIGHT),
                    )
                }
            }
        }
    }
}
