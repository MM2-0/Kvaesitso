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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPalette
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPaletteEventReader
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPaletteRef
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote

/**
 * The 256 colours an object's palette indices name, fetched when the object
 * names them in another event (DECK-0003 §1.3a).
 *
 * `colors` is an index into a palette and the payload says which one: absent
 * means the built-in, a list means the object carries its own, and an `nevent`
 * or `naddr` means somebody else's event holds it. Until that event is in hand,
 * an object that names one is drawn against the built-in — §1.3b is explicit
 * that a reference which has not resolved "counts as a failed fetch, which
 * means the built-in", never a refusal to draw. So this hands back null first
 * and the palette after, and the object appears immediately and repaints once.
 *
 * **Pinned to the event named, and never to a newer one.** §1.3a: "A reader
 * MUST NOT follow the `e` chain forward to a newer version, and MUST render the
 * object with the event the object names." That is why an `nevent` is loaded by
 * its own id. An `naddr` is accepted too, for a palette somebody publishes as an
 * addressable event of their own, and there the address *is* what was named.
 *
 * Fetching matters more than it sounds. Every palette on the network today is a
 * `kind 3367` colour moment of three to six colours, so an object built against
 * one and drawn against the built-in 256 is not slightly off — it is a
 * different object, in colours its author never chose. It can also stop being
 * drawable: an index of 238 is fine against 256 entries and out of range
 * against five, and §1.3 makes that a defect in the payload rather than
 * something to clamp. Both are the format working as written.
 */
@Composable
fun WithSnoPalette(
    ref: SnoPaletteRef,
    accountViewModel: AccountViewModel,
    content: @Composable (SnoPalette?) -> Unit,
) {
    if (ref !is SnoPaletteRef.Event) {
        // Inline and built-in are already resolved inside the payload; there is
        // nothing to wait for and nothing to fetch.
        content(null)
        return
    }

    when (val entity = remember(ref.bech32) { Nip19Parser.uriToRoute(ref.bech32)?.entity }) {
        is NEvent -> LoadPaletteNote(entity.hex, accountViewModel, content)
        is NNote -> LoadPaletteNote(entity.hex, accountViewModel, content)
        is NAddress ->
            LoadAddressableNote(entity.address()) { note ->
                content(remember(note?.event) { note?.event?.let { SnoPaletteEventReader.read(it) } })
            }
        // A reference the parser accepted as well-formed but that names nothing
        // this client can look up. The built-in, as an unresolved one always is.
        else -> content(null)
    }
}

@Composable
private fun LoadPaletteNote(
    hex: String,
    accountViewModel: AccountViewModel,
    content: @Composable (SnoPalette?) -> Unit,
) {
    LoadNote(hex) { note ->
        // The kind is deliberately not checked: §1.3b says this format "does
        // not define a palette kind and does not want one", and a reader that
        // accepts the shape reads whatever convention wins. An event that is
        // not one reads as null, which is the built-in.
        content(remember(note?.event) { note?.event?.let { SnoPaletteEventReader.read(it) } })
    }
}
