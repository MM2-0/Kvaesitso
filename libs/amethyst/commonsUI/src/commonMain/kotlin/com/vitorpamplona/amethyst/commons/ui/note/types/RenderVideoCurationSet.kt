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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.video_curation_set_private_items
import com.vitorpamplona.amethyst.commons.resources.video_curation_set_video_count
import com.vitorpamplona.amethyst.commons.ui.components.DisplayBlurHash
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.BookmarkIdTag
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import com.vitorpamplona.quartz.nip51Lists.videoCurationSet.VideoCurationSetEvent
import com.vitorpamplona.quartz.nip71Video.VideoEvent
import com.vitorpamplona.quartz.utils.Log

/**
 * A NIP-51 kind-30005 video list: its name, what it is for, and a strip of its videos.
 *
 * Members may be public tags or NIP-51 private ones inside the encrypted content — divine.video
 * puts every member there, so for anyone but the list's owner the strip is legitimately empty and
 * the card says so rather than looking broken.
 */
@Composable
fun RenderVideoCurationSet(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? VideoCurationSetEvent ?: return

    val publicItems = remember(noteEvent) { noteEvent.publicItems() }

    // Decryption needs the signer and only succeeds for the list's owner, so it runs off the
    // composition and simply yields nothing for everyone else.
    //
    // Null means "we did not read the content": not the owner, or the decrypt failed. An empty
    // list means we read it and it holds nothing. The two have to stay apart, because a list whose
    // encrypted content decrypts to `[]` is an empty list, not a private one — divine.video
    // publishes exactly that for a list a user created and never added to.
    val privateItems by
        produceState<List<BookmarkIdTag>?>(initialValue = null, noteEvent) {
            if (noteEvent.pubKey != accountViewModel.account.signer.pubKey) return@produceState
            value =
                try {
                    noteEvent.privateItems(accountViewModel.account.signer)
                } catch (e: Exception) {
                    if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                    Log.w("RenderVideoCurationSet", "Cannot decrypt private items of ${noteEvent.id}", e)
                    null
                }
        }

    val items = remember(publicItems, privateItems) { publicItems + (privateItems ?: emptyList()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (noteEvent.title() ?: noteEvent.dTag()).takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        noteEvent.description()?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // An empty strip has two very different causes. Encrypted content we could not read means
        // the members may be there and private; content we did read, or no content at all, means
        // the list is simply empty. Calling that "Private list" would be a lie — to its own author
        // most of all, who just decrypted it and found nothing in it.
        val membersAreHidden = items.isEmpty() && noteEvent.content.isNotBlank() && privateItems == null

        Text(
            text =
                if (membersAreHidden) {
                    stringRes(Res.string.video_curation_set_private_items)
                } else {
                    pluralStringRes(Res.plurals.video_curation_set_video_count, items.size, items.size)
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (items.isNotEmpty() && quotesLeft > 0) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.take(MAX_POSTERS).forEach { item ->
                    key(item.toTagIdOnly().joinToString(":")) {
                        VideoListMemberPoster(item, accountViewModel, nav)
                    }
                }
            }
        }
    }
}

/** One video in the strip: its poster, or its blurhash until that loads. */
@Composable
private fun VideoListMemberPoster(
    item: BookmarkIdTag,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (item) {
        is AddressBookmark ->
            LoadAddressableNote(item.address) { note ->
                note?.let { VideoPoster(it, accountViewModel) { nav.nav(Route.Note(addressTag(item.address))) } }
            }

        is EventBookmark ->
            LoadNote(item.eventId) { note ->
                note?.let { VideoPoster(it, accountViewModel) { nav.nav(Route.Note(item.eventId)) } }
            }
    }
}

@Composable
private fun VideoPoster(
    note: Note,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val videoEvent = note.event as? VideoEvent ?: return
    // The poster, not the video: a strip of simultaneously-playing players is unusable, and every
    // NIP-71 imeta that has a thumbnail carries it as `image`.
    val imeta = remember(videoEvent) { videoEvent.imetaTags().firstOrNull { it.image.isNotEmpty() } ?: videoEvent.imetaTags().firstOrNull() }
    val poster = imeta?.image?.firstOrNull()

    val tileModifier = Modifier.width(POSTER_WIDTH).height(POSTER_HEIGHT).clickable(onClick = onClick)

    if (poster == null) {
        imeta?.blurhash?.let { DisplayBlurHash(it, videoEvent.title(), ContentScale.Crop, tileModifier) }
        return
    }

    MyAsyncImage(
        imageUrl = poster,
        contentDescription = videoEvent.title(),
        contentScale = ContentScale.Crop,
        mainImageModifier = tileModifier,
        loadedImageModifier = MaterialTheme.colorScheme.imageModifier,
        accountViewModel = accountViewModel,
        onLoadingBackground = { imeta.blurhash?.let { DisplayBlurHash(it, videoEvent.title(), ContentScale.Crop, tileModifier) } },
        onError = null,
    )
}

private fun addressTag(address: Address) = Address.assemble(address.kind, address.pubKeyHex, address.dTag)

// Enough to show what kind of list this is without turning a feed card into a gallery.
private const val MAX_POSTERS = 8
private val POSTER_WIDTH = 96.dp
private val POSTER_HEIGHT = 128.dp
