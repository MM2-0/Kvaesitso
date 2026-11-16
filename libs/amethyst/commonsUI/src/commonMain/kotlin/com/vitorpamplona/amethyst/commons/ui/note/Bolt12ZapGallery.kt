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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.vitorpamplona.amethyst.commons.model.Bolt12ZapEntry
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteZaps
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.CrossfadeToDisplayAmount
import com.vitorpamplona.amethyst.commons.ui.note.WatchUserMetadataAndFollowsAndRenderUserProfilePictureOrDefaultAuthor
import com.vitorpamplona.amethyst.commons.ui.note.ZappedIcon
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.StdStartPadding
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.utils.BigDecimal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

private fun onBolt12ZapEntryClick(
    entry: Bolt12ZapEntry,
    nav: INav,
) {
    entry.source.author?.let { nav.nav(routeFor(it)) }
}

/**
 * Reactions-row gallery of the payers who BOLT12-zapped this note. Mirrors the
 * onchain gallery but simpler: BOLT12 zaps are validated synchronously at consume
 * time, so there is no async re-verification and no pending state — every entry
 * here is already counted. `Note.addBolt12Zap` invalidates `flowSet.zaps`, so this
 * refreshes on arrival; memoizing on the `bolt12Zaps` map reference keeps a busy
 * lightning thread from churning it.
 */
@Composable
internal fun WatchBolt12ZapsAndRenderGallery(
    baseNote: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val zapsState by observeNoteZaps(baseNote, accountViewModel)
    val bolt12ZapsMap = zapsState?.note?.bolt12Zaps
    val entries =
        remember(bolt12ZapsMap) {
            bolt12ZapsMap?.values?.toImmutableList() ?: persistentListOf()
        }

    if (entries.isNotEmpty()) {
        RenderBolt12ZapGallery(entries, nav, accountViewModel)
    }
}

@Composable
private fun RenderBolt12ZapGallery(
    entries: ImmutableList<Bolt12ZapEntry>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Row(Modifier.fillMaxWidth()) {
        GalleryIconColumn { iconAlignment ->
            ZappedIcon(
                modifier = Modifier.size(Size25dp).align(iconAlignment),
            )
        }

        Bolt12ZapAuthorGallery(entries, nav, accountViewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bolt12ZapAuthorGallery(
    entries: ImmutableList<Bolt12ZapEntry>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Column(modifier = StdStartPadding) {
        FlowRow {
            entries.forEach { entry ->
                Bolt12ZapEntryRow(entry, nav, accountViewModel)
            }
        }
    }
}

@Composable
private fun Bolt12ZapEntryRow(
    entry: Bolt12ZapEntry,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    // Anonymous zaps carry no `P` payer tag — show the blank/unknown author (as the
    // standalone card does) instead of the throwaway ephemeral key's default avatar.
    val isAnonymous = (entry.source.event as? Bolt12ZapEvent)?.isAnonymous() == true
    val user = if (isAnonymous) null else entry.source.author

    // The amount is validated (checked against the proof's invoice_amount), so it
    // is safe to show for any sender. A not-yet-crypto-verified (compressed) proof
    // still dims its avatar so the viewer can tell it apart from a fully-verified one.
    val avatarAlpha = if (entry.cryptoVerified) 1f else 0.6f
    val amountText =
        remember(entry.amountMillisats) {
            val sats = entry.amountMillisats / 1000
            if (sats > 0L) showAmount(BigDecimal(sats)) else ""
        }

    Box(
        modifier = Size35Modifier.clickable { onBolt12ZapEntryClick(entry, nav) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(modifier = Modifier.alpha(avatarAlpha)) {
            WatchUserMetadataAndFollowsAndRenderUserProfilePictureOrDefaultAuthor(
                user,
                accountViewModel,
                nav,
            )
        }

        if (amountText.isNotEmpty()) {
            CrossfadeToDisplayAmount(amountText)
        }
    }
}
