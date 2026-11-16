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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NutzapEntry
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteZaps
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nutzap
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.CrossfadeToDisplayAmount
import com.vitorpamplona.amethyst.commons.ui.note.WatchUserMetadataAndFollowsAndRenderUserProfilePictureOrDefaultAuthor
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.StdStartPadding
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.BigDecimal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

private fun onNutzapEntryClick(
    entry: NutzapEntry,
    nav: INav,
) {
    entry.source.author?.let { nav.nav(routeFor(it)) }
}

@Composable
internal fun WatchNutzapsAndRenderGallery(
    baseNote: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    // Same flow the lightning + onchain galleries subscribe to. The flow
    // fires on every kind of zap mutation on this note, so memoize on the
    // nutzap map reference (immutable; allocated fresh per mutation by
    // Note.addNutzap) to keep recomposition off the hot lightning path.
    val zapsState by observeNoteZaps(baseNote, accountViewModel)
    val nutzapsMap = zapsState?.note?.nutzaps
    val entries =
        remember(nutzapsMap) {
            nutzapsMap?.values?.toImmutableList() ?: persistentListOf()
        }

    if (entries.isNotEmpty()) {
        RenderNutzapGalleryRow(entries, nav, accountViewModel)
    }
}

@Composable
private fun RenderNutzapGalleryRow(
    entries: ImmutableList<NutzapEntry>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Row(Modifier.fillMaxWidth()) {
        GalleryIconColumn { iconAlignment ->
            Icon(
                imageVector = CustomHashTagIcons.Cashu,
                contentDescription = stringRes(Res.string.nutzap),
                modifier = Modifier.size(Size20dp).align(iconAlignment),
                // Tint the monochrome cashu outline brand orange so it reads as
                // a value transfer, matching the lightning ZappedIcon in the
                // sibling zap gallery.
                tint = BitcoinOrange,
            )
        }

        NutzapAuthorGallery(entries, nav, accountViewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NutzapAuthorGallery(
    entries: ImmutableList<NutzapEntry>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Column(modifier = StdStartPadding) {
        FlowRow {
            entries.forEach { entry ->
                NutzapEntryAvatar(entry, nav, accountViewModel)
            }
        }
    }
}

@Composable
private fun NutzapEntryAvatar(
    entry: NutzapEntry,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val user = entry.source.author
    val amountText =
        remember(entry.claimedSats) {
            if (entry.claimedSats > 0L) showAmount(BigDecimal(entry.claimedSats)) else ""
        }

    Box(
        modifier = Size35Modifier.clickable { onNutzapEntryClick(entry, nav) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        WatchUserMetadataAndFollowsAndRenderUserProfilePictureOrDefaultAuthor(
            user,
            accountViewModel,
            nav,
        )

        if (amountText.isNotEmpty()) {
            CrossfadeToDisplayAmount(amountText)
        }
    }
}
