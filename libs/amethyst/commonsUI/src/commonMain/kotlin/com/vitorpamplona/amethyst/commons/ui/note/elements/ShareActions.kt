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
package com.vitorpamplona.amethyst.commons.ui.note.elements

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.resources.quick_action_share_browser_link
import com.vitorpamplona.amethyst.commons.resources.share_as_image
import com.vitorpamplona.amethyst.commons.resources.share_as_image_url
import com.vitorpamplona.amethyst.commons.resources.share_as_qr
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.commons.ui.components.rememberImageSharer
import com.vitorpamplona.amethyst.commons.ui.components.rememberTextSharer
import com.vitorpamplona.amethyst.commons.ui.navigation.host.canOpen
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.externalLinkForNote
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * The note's Share options, in the [NoteActionGroup.SHARE] group: browser link, image file,
 * image URL, and the display-only QR code. The 3-dot menu folds them into a Share submenu (on a
 * phone it lists them, as it does Copy); [ShareActionRows] lays them out in the Share drawer of
 * the reaction row and the chat long-press sheet. Copy-to-clipboard options are not here.
 *
 * Callers only offer these for non-private notes: every option exposes the note publicly (a
 * shareable web link, or an image of it), which must never happen for a private gift-wrapped
 * rumor.
 */
@Composable
fun shareNoteActions(
    note: Note,
    nav: INav,
    onDismiss: () -> Unit,
): List<NoteAction> {
    val shareLinkStr = stringRes(Res.string.quick_action_share_browser_link)
    val shareStr = stringRes(Res.string.quick_action_share)
    val shareAsImageStr = stringRes(Res.string.share_as_image)
    val shareAsImageUrlStr = stringRes(Res.string.share_as_image_url)
    val shareAsQrStr = stringRes(Res.string.share_as_qr)
    val sharer = rememberTextSharer()
    val imageSharer = rememberImageSharer()
    // AddressableNotes are shared by their replaceable address; everything else
    // by event id. The two image routes resolve the note from this same id.
    val shareId = if (note is AddressableNote) note.address.toValue() else note.idHex
    // A front end whose nav host doesn't register the image routes leaves those options out, and
    // one with nowhere to send an image file (iOS for now) leaves out that one.
    val canShareImageFile = imageSharer.canShare && canOpen(Route.ShareNoteAsImageFile(shareId))
    val canShareImageUrl = canOpen(Route.ShareNoteAsImage(shareId))

    return buildList {
        add(
            NoteAction(MaterialSymbols.Share, shareLinkStr, group = NoteActionGroup.SHARE) {
                sharer.share(externalLinkForNote(note), shareLinkStr, shareStr)
                onDismiss()
            },
        )
        if (canShareImageFile) {
            add(
                NoteAction(MaterialSymbols.Image, shareAsImageStr, group = NoteActionGroup.SHARE) {
                    nav.nav(Route.ShareNoteAsImageFile(shareId))
                    onDismiss()
                },
            )
        }
        if (canShareImageUrl) {
            add(
                NoteAction(MaterialSymbols.Image, shareAsImageUrlStr, group = NoteActionGroup.SHARE) {
                    nav.nav(Route.ShareNoteAsImage(shareId))
                    onDismiss()
                },
            )
        }
        add(
            NoteAction(MaterialSymbols.QrCode2, shareAsQrStr, group = NoteActionGroup.SHARE) {
                nav.nav(Route.ShareNoteAsQr(shareId))
                onDismiss()
            },
        )
    }
}

/**
 * [shareNoteActions] as a dropdown from the reaction row's Share button. The menu already is the
 * Share submenu, so its items are listed rather than folded into one.
 */
@Composable
fun ShareDropdownMenu(
    note: Note,
    nav: INav,
    onDismiss: () -> Unit,
) {
    val actions = shareNoteActions(note, nav, onDismiss).map { it.copy(group = null) }
    NoteActionDropdownMenu(sections = listOf(actions), onDismiss = onDismiss)
}

/** [shareNoteActions] as rows, for the Share drawer of the chat long-press sheet ([ShareOptionsBottomSheet]). */
@Composable
fun ShareActionRows(
    note: Note,
    nav: INav,
    onDismiss: () -> Unit,
) {
    shareNoteActions(note, nav, onDismiss).forEach { action ->
        M3ActionRow(icon = action.symbol, text = action.label, onClick = action.onClick)
    }
}
