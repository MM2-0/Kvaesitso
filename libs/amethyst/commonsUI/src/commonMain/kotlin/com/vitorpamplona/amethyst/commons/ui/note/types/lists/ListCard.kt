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
package com.vitorpamplona.amethyst.commons.ui.note.types.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip51_list_has_private_members
import com.vitorpamplona.amethyst.commons.ui.components.ShowMoreButton
import com.vitorpamplona.amethyst.commons.ui.note.getGradient
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.Log
import org.jetbrains.compose.resources.StringResource
import kotlin.coroutines.cancellation.CancellationException

/** How many members a list card shows before it asks to be expanded. Matches DisplayPeopleList. */
private const val COLLAPSED_MEMBERS = 3

/**
 * How many more members each "Show more" adds once a card is expanded. A Trusted List or a mute
 * list can hold thousands, and a feed row that composed all of them at once would stall the feed.
 */
private const val MEMBERS_PAGE = 50

/**
 * The shared shape of every NIP-51 list card: a name, an optional description, the members, and —
 * when the viewer is not the list's author — an honest line about the ones they cannot see.
 *
 * Each list kind supplies its own title, its own member type and its own row, because a mute list
 * of pubkeys and a bookmark list of notes are not the same thing and should not pretend to be.
 * What they share is this frame.
 */
@Composable
fun <T> ListCard(
    title: String,
    description: String?,
    items: List<T>,
    /** True when the event carries private members this viewer has no key for. */
    hasUnreadablePrivateItems: Boolean,
    backgroundColor: MutableState<Color>,
    /** Extra lines under the description that only some list kinds carry, e.g. an app set's platforms. */
    subtitle: (@Composable () -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(5.dp),
        textAlign = TextAlign.Center,
    )

    description?.takeIf { it.isNotBlank() }?.let {
        Text(
            text = it,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    subtitle?.invoke()

    // Only when there is nothing else to show. A card with visible members already reads as a list,
    // and most lists keep something private, so saying so on every one of them is noise. The line
    // exists for the case that would otherwise be a title and blank space — an all-private list,
    // which is what divine.video publishes — so that it reads as closed rather than as broken.
    if (hasUnreadablePrivateItems && items.isEmpty()) {
        Text(
            text = stringRes(Res.string.nip51_list_has_private_members),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 2.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    PagedMemberColumn(items, backgroundColor, Modifier.fillMaxWidth().padding(top = 5.dp), itemContent = itemContent)
}

/**
 * A card's members, a few at a time: [collapsed] of them under a fading "Show more" (a plain one
 * under them when there is no [backgroundColor] to fade into), then pages of [MEMBERS_PAGE] with a
 * "Show more" under the last one, so a list of thousands only ever composes what the reader asked
 * to see.
 */
@Composable
fun <T> PagedMemberColumn(
    items: List<T>,
    backgroundColor: MutableState<Color>?,
    modifier: Modifier = Modifier,
    collapsed: Int = COLLAPSED_MEMBERS,
    itemContent: @Composable (T) -> Unit,
) {
    var shownCount by rememberSaveable { mutableIntStateOf(collapsed) }
    val shown = if (items.size <= shownCount) items else items.subList(0, shownCount)
    val hasMore = items.size > shownCount

    Box {
        Column(modifier = modifier) {
            shown.forEach { itemContent(it) }

            if (hasMore && (shownCount > collapsed || backgroundColor == null)) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    ShowMoreButton { shownCount = if (shownCount <= collapsed) MEMBERS_PAGE else shownCount + MEMBERS_PAGE }
                }
            }
        }

        if (hasMore && shownCount <= collapsed && backgroundColor != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(getGradient(backgroundColor)),
            ) {
                ShowMoreButton { shownCount = MEMBERS_PAGE }
            }
        }
    }
}

/**
 * Decrypts a list's private members, and keeps "could not read them" apart from "there are none".
 *
 * `null` means we never got the plaintext — the viewer is not the author, or the decrypt failed.
 * An empty list means we read the content and it holds nothing, which is an empty list and must
 * not be labelled private: its author can see straight into it.
 */
@Composable
fun <T> loadPrivateItems(
    noteEvent: Event,
    accountViewModel: AccountViewModel,
    load: suspend (NostrSigner) -> List<T>?,
): State<List<T>?> =
    produceState<List<T>?>(initialValue = null, noteEvent) {
        if (noteEvent.pubKey != accountViewModel.account.signer.pubKey) return@produceState
        value =
            try {
                load(accountViewModel.account.signer)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("NIP51ListCard", "Cannot decrypt private members of ${noteEvent.id}", e)
                null
            }
    }

/**
 * True when this event is withholding members from the viewer.
 *
 * Both halves matter: an empty `content` is a list with no private members at all, and a non-null
 * [privateItems] means we already read them, so neither is hidden.
 */
fun Event.hidesPrivateMembers(privateItems: List<*>?): Boolean = content.isNotEmpty() && privateItems == null

/** A list's own name if it has one, else the `d` tag it is addressed by, else the kind's name. */
@Composable
fun listTitle(
    explicitTitle: String?,
    dTag: String?,
    fallback: StringResource,
): String =
    explicitTitle?.takeIf { it.isNotBlank() }
        ?: dTag?.takeIf { it.isNotBlank() }
        ?: stringRes(fallback)

/** Remembers the concatenation of a list's public and (once decrypted) private members. */
@Composable
fun <T> rememberAllMembers(
    public: List<T>,
    private: List<T>?,
): List<T> = remember(public, private) { if (private == null) public else public + private }
