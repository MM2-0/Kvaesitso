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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderProfileCard
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

// LocalCache is a process-wide object shared by every preview the renderer runs, and
// consuming a kind-0 is a no-op when the id already exists or the createdAt isn't newer.
// These keys must not collide with any other preview's (e.g. NoteHeaderMarkersPreview's
// "a"*64 / "e1"*32) or whichever renders first wins and this one shows that profile.
private val FULL_AUTHOR = "c0".repeat(32)
private val MINIMAL_AUTHOR = "c1".repeat(32)
private val BOT_AUTHOR = "c2".repeat(32)

private val FULL_ID = "d0".repeat(32)
private val MINIMAL_ID = "d1".repeat(32)
private val BOT_ID = "d2".repeat(32)

/**
 * Design-system preview for the kind-0 feed card, rendered by the REAL
 * [RenderProfileCard] over notes seeded into [LocalCache] — same pattern as
 * `NoteHeaderFirstRowDensityPreview`.
 *
 * Remote images never load in a preview, so the banner falls back to the
 * bundled default and the avatar to the robohash. What this preview is for is
 * the layout: banner-to-body fade, avatar overhang, and how the name block and
 * the chip row behave from a full profile down to one with nothing but a name.
 */
@Preview(widthDp = 400, heightDp = 1200)
@Composable
fun ProfileCardPreview() {
    val accountViewModel = mockAccountViewModel()

    val now = TimeUtils.now()

    val full: Note = LocalCache.getOrCreateAddressableNote(MetadataEvent.createAddress(FULL_AUTHOR))
    val minimal: Note = LocalCache.getOrCreateAddressableNote(MetadataEvent.createAddress(MINIMAL_AUTHOR))
    val bot: Note = LocalCache.getOrCreateAddressableNote(MetadataEvent.createAddress(BOT_AUTHOR))

    runBlocking {
        withContext(Dispatchers.IO) {
            LocalCache.justConsume(
                MetadataEvent(
                    FULL_ID,
                    FULL_AUTHOR,
                    now,
                    emptyArray(),
                    """
                    {
                      "display_name": "Vitor Pamplona",
                      "name": "vitor",
                      "pronouns": "he/him",
                      "about": "Building Amethyst. Nostr, open protocols and a lot of Kotlin. Ask me about relays, NIPs, or why kind 0 deserves a nicer card than a wall of JSON.",
                      "website": "https://vitorpamplona.com/",
                      "nip05": "_@vitorpamplona.com",
                      "lud16": "vitor@zeuspay.com"
                    }
                    """.trimIndent(),
                    "x",
                ),
                null,
                true,
            )

            // Nothing but a name: the card must not collapse into empty rows.
            LocalCache.justConsume(
                MetadataEvent(MINIMAL_ID, MINIMAL_AUTHOR, now, emptyArray(), """{"name":"someone"}""", "x"),
                null,
                true,
            )

            LocalCache.justConsume(
                MetadataEvent(
                    BOT_ID,
                    BOT_AUTHOR,
                    now,
                    emptyArray(),
                    """{"display_name":"Relay Watcher","name":"relaywatch","bot":true,"about":"Posts relay uptime every hour."}""",
                    "x",
                ),
                null,
                true,
            )
        }
    }

    ThemeComparisonColumn {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            ProfileCardSample(full, accountViewModel)
            ProfileCardSample(minimal, accountViewModel)
            ProfileCardSample(bot, accountViewModel)
        }
    }
}

@Composable
private fun ProfileCardSample(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val defaultBackground = MaterialTheme.colorScheme.background
    val background = remember { mutableStateOf(defaultBackground) }

    RenderProfileCard(
        baseNote = note,
        backgroundColor = background,
        accountViewModel = accountViewModel,
        nav = EmptyNav(),
    )
}
