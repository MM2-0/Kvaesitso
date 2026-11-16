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
package com.vitorpamplona.amethyst.ui.note.elements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.FeatureSetType
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.note.FirstUserInfoRow
import com.vitorpamplona.amethyst.commons.ui.note.types.EditState
import com.vitorpamplona.amethyst.commons.ui.note.types.EmptyState
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.amethyst.service.location.CachedReversedGeoLocations
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private val AUTHOR = "a".repeat(64)
private val LONG_NAME_AUTHOR = "b".repeat(64)

private val AUTHOR_METADATA_ID = "e1".repeat(32)
private val LONG_NAME_METADATA_ID = "e2".repeat(32)

private val PLAIN_ID = "1".repeat(64)
private val RUMOR_ID = "3".repeat(64)
private val EDITED_ID = "4".repeat(64)
private val EDIT_VERSION_ID = "5".repeat(64)
private val DRAFT_ID = "6".repeat(64)
private val COMMUNITY_POST_ID = "7".repeat(64)

// 24 leading zero bits so strongPoWOrNull(min = 20) accepts the committed nonce.
private val POW_ID = "0".repeat(6) + "8".repeat(58)
private val KITCHEN_SINK_ID = "0".repeat(6) + "9".repeat(58)

private const val GEOHASH = "u1x1"
private val COMMUNITY_ADDRESS = "34550:" + "c".repeat(64) + ":amethyst-users"

/**
 * Design-system preview for the note-header first row, rendered by the REAL
 * `FirstUserInfoRow` over notes seeded into [LocalCache] — the same pattern
 * the ZapPollNote/Poll previews use. Rows go from bare to fully loaded so the
 * two marker tiers (`HeaderPill` and `QuietMark`, now in `commons/ui/note`)
 * can be reviewed together.
 *
 * Two markers cannot appear in a static preview because their visibility is
 * computed inside effects that previews never run: the followed-hashtag label
 * (needs the account follow-list flows) and the *confirmed* OTS pill (needs
 * attestation verification). The community label and the *pending* OTS pill
 * shown here exercise the same two visual styles through real code.
 */
@Preview(widthDp = 400)
@Composable
fun NoteHeaderFirstRowDensityPreview() {
    val accountViewModel = mockAccountViewModel()
    val nav = EmptyNav()

    // The jump-to-parent arrow only renders in complete UI mode.
    accountViewModel.settings.uiSettingsFlow.featureSet
        .tryEmit(FeatureSetType.COMPLETE)

    // Let DisplayLocation resolve the geohash synchronously from the cache.
    CachedReversedGeoLocations.locationNames.put(GEOHASH, "Belo Horizonte")

    val now = TimeUtils.now()
    val expiresAt = (now + 7200).toString()

    val plainEvent = TextNoteEvent(PLAIN_ID, AUTHOR, now - 300, emptyArray(), "GM", "x")

    // Drafts are addressable: consumption lands on the AddressableNote, so the
    // preview must fetch it by address rather than by event id.
    val draftEvent = DraftWrapEvent(DRAFT_ID, AUTHOR, now - 300, arrayOf(arrayOf("d", "preview-draft")), "", "x")

    val plain: Note = LocalCache.getOrCreateNote(PLAIN_ID)
    val rumorPinned: Note = LocalCache.getOrCreateNote(RUMOR_ID)
    val edited: Note = LocalCache.getOrCreateNote(EDITED_ID)
    val editVersion: Note = LocalCache.getOrCreateNote(EDIT_VERSION_ID)
    val draft: Note = LocalCache.getOrCreateAddressableNote(draftEvent.address())
    val communityPost: Note = LocalCache.getOrCreateNote(COMMUNITY_POST_ID)
    val geoPowExpiring: Note = LocalCache.getOrCreateNote(POW_ID)
    val kitchenSink: Note = LocalCache.getOrCreateNote(KITCHEN_SINK_ID)

    runBlocking {
        withContext(Dispatchers.IO) {
            LocalCache.justConsume(
                MetadataEvent(AUTHOR_METADATA_ID, AUTHOR, now, emptyArray(), """{"name":"Vitor"}""", "x"),
                null,
                true,
            )
            LocalCache.justConsume(
                MetadataEvent(LONG_NAME_METADATA_ID, LONG_NAME_AUTHOR, now, emptyArray(), """{"name":"A user with a very long display name"}""", "x"),
                null,
                true,
            )

            LocalCache.justConsume(plainEvent, null, true)
            LocalCache.justConsume(TextNoteEvent(EDIT_VERSION_ID, AUTHOR, now - 60, emptyArray(), "GM! (fixed typo)", "x"), null, true)
            // An empty sig is what marks a note as a private rumor.
            LocalCache.justConsume(TextNoteEvent(RUMOR_ID, AUTHOR, now - 300, emptyArray(), "just between us", ""), null, true)
            LocalCache.justConsume(TextNoteEvent(EDITED_ID, AUTHOR, now - 300, emptyArray(), "GM!", "x"), null, true)
            LocalCache.justConsume(draftEvent, null, true)
            LocalCache.justConsume(
                TextNoteEvent(COMMUNITY_POST_ID, AUTHOR, now - 300, arrayOf(arrayOf("a", COMMUNITY_ADDRESS)), "hello community", "x"),
                null,
                true,
            )
            LocalCache.justConsume(
                TextNoteEvent(
                    POW_ID,
                    AUTHOR,
                    now - 300,
                    arrayOf(
                        arrayOf("g", GEOHASH),
                        arrayOf("nonce", "776", "24"),
                        arrayOf("expiration", expiresAt),
                    ),
                    "mined and placed",
                    "x",
                ),
                null,
                true,
            )
            LocalCache.justConsume(
                TextNoteEvent(
                    KITCHEN_SINK_ID,
                    LONG_NAME_AUTHOR,
                    now - 300,
                    arrayOf(
                        arrayOf("e", PLAIN_ID),
                        arrayOf("a", COMMUNITY_ADDRESS),
                        arrayOf("g", GEOHASH),
                        arrayOf("nonce", "776", "24"),
                        arrayOf("expiration", expiresAt),
                    ),
                    "everything everywhere all at once",
                    // Empty sig: also a private rumor.
                    "",
                ),
                null,
                true,
            )
        }
    }

    // LoadOts renders the pending pill synchronously from this map; the
    // confirmed pill needs async verification a static preview can't run.
    accountViewModel.account.settings.pendingAttestations
        .update { it + (POW_ID to "stamp") + (KITCHEN_SINK_ID to "stamp") }

    val editedState =
        remember {
            mutableStateOf<GenericLoadable<EditState>>(
                GenericLoadable.Loaded(EditState().apply { updateModifications(listOf(editVersion)) }),
            )
        }

    ThemeComparisonColumn {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            // Bare minimum: username + time + options
            HeaderRowSample(plain, accountViewModel)

            // Quiet icon marks: private rumor + pinned
            HeaderRowSample(rumorPinned, accountViewModel, isPinned = true)

            // Quiet text mark, tappable: edited
            HeaderRowSample(edited, accountViewModel, editState = editedState)

            // Quiet text mark: draft
            HeaderRowSample(draft, accountViewModel)

            // Soft link: community label
            HeaderRowSample(communityPost, accountViewModel)

            // Pill cluster: location + PoW + pending OTS + expiration
            HeaderRowSample(geoPowExpiring, accountViewModel)

            // Kitchen sink under a long username: everything competes for space
            HeaderRowSample(kitchenSink, accountViewModel, isPinned = true, editState = editedState)
        }
    }
}

@Composable
private fun HeaderRowSample(
    note: Note,
    accountViewModel: AccountViewModel,
    isPinned: Boolean = false,
    editState: State<GenericLoadable<EditState>> = EmptyState,
) {
    FirstUserInfoRow(
        baseNote = note,
        showAuthorPicture = false,
        isPinned = isPinned,
        editState = editState,
        accountViewModel = accountViewModel,
        nav = EmptyNav(),
    )
}
