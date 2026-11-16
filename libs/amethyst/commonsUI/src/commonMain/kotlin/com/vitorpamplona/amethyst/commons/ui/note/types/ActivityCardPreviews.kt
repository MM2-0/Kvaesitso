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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderNutzap
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderOnchainZap
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderReaction
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderZapReceiptCard
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent

/**
 * Design-time fixtures for the activity cards (reaction / lightning zap /
 * cashu nutzap / onchain zap). The notes are built by hand and never consumed
 * into [LocalCache], except for the users behind the avatars.
 */
private object ActivityCardPreviewData {
    val senderHex = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    val authorHex = "ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b"
    const val SIG = "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
    const val TIME = 1_700_000_000L

    val targetId = "a".repeat(64)

    fun targetNote(): Note =
        Note(targetId).apply {
            event =
                TextNoteEvent(
                    targetId,
                    authorHex,
                    TIME,
                    emptyArray(),
                    "Just finished the new release — zaps, nests, and a faster feed. What should we build next?",
                    SIG,
                )
            author = LocalCache.getOrCreateUser(authorHex)
            replyTo = emptyList()
        }

    fun reactionNote(reaction: String): Note {
        val id = "b".repeat(64)
        return Note(id).apply {
            event =
                ReactionEvent(
                    id,
                    senderHex,
                    TIME,
                    arrayOf(arrayOf("e", targetId), arrayOf("p", authorHex)),
                    reaction,
                    SIG,
                )
            author = LocalCache.getOrCreateUser(senderHex)
            replyTo = listOf(targetNote())
        }
    }

    fun nutzapNote(): Note {
        val id = "c".repeat(64)
        return Note(id).apply {
            event =
                NutzapEvent(
                    id,
                    senderHex,
                    TIME,
                    arrayOf(
                        arrayOf("proof", """{"amount":2100,"id":"00ad268c4d1f5826","secret":"s","C":"c"}"""),
                        arrayOf("u", "https://mint.example.com"),
                        arrayOf("e", targetId),
                        arrayOf("p", authorHex),
                    ),
                    "Have some cashu, great post!",
                    SIG,
                )
            author = LocalCache.getOrCreateUser(senderHex)
            replyTo = listOf(targetNote())
        }
    }

    fun zapReceiptNote(): Note {
        val id = "d".repeat(64)
        // Receipts are signed by the lightning provider; the preview skips the
        // embedded request and feeds the decrypted card to RenderZapReceiptCard directly.
        return Note(id).apply {
            event = null
            author = null
            replyTo = listOf(targetNote())
        }
    }

    fun onchainZapNote(): Note {
        val id = "e".repeat(64)
        return Note(id).apply {
            event =
                OnchainZapEvent(
                    id,
                    senderHex,
                    TIME,
                    arrayOf(
                        arrayOf("i", "bitcoin:tx:" + "ab".repeat(32)),
                        arrayOf("amount", "150000"),
                        arrayOf("e", targetId),
                        arrayOf("p", authorHex),
                    ),
                    "Welcome to the timechain!",
                    SIG,
                )
            author = LocalCache.getOrCreateUser(senderHex)
            replyTo = listOf(targetNote())
        }
    }
}

@Preview
@Composable
fun LikeActivityCardPreview() {
    val accountViewModel = mockAccountViewModel()

    ThemeComparisonColumn {
        RenderReaction(
            note = ActivityCardPreviewData.reactionNote("+"),
            quotesLeft = 1,
            backgroundColor = remember { mutableStateOf(Color.Transparent) },
            accountViewModel = accountViewModel,
            nav = EmptyNav(),
        )
    }
}

@Preview
@Composable
fun EmojiReactionActivityCardPreview() {
    val accountViewModel = mockAccountViewModel()

    ThemeComparisonColumn {
        RenderReaction(
            note = ActivityCardPreviewData.reactionNote("🤙"),
            quotesLeft = 1,
            backgroundColor = remember { mutableStateOf(Color.Transparent) },
            accountViewModel = accountViewModel,
            nav = EmptyNav(),
        )
    }
}

@Preview
@Composable
fun ZapReceiptActivityCardPreview() {
    val accountViewModel = mockAccountViewModel()

    ThemeComparisonColumn {
        RenderZapReceiptCard(
            note = ActivityCardPreviewData.zapReceiptNote(),
            card =
                ZapAmountCommentNotification(
                    user = LocalCache.getOrCreateUser(ActivityCardPreviewData.senderHex),
                    comment = "Great post! ⚡",
                    amount = "100k",
                ),
            recipientKey = ActivityCardPreviewData.authorHex,
            quotesLeft = 1,
            backgroundColor = remember { mutableStateOf(Color.Transparent) },
            accountViewModel = accountViewModel,
            nav = EmptyNav(),
        )
    }
}

@Preview
@Composable
fun NutzapActivityCardPreview() {
    val accountViewModel = mockAccountViewModel()

    ThemeComparisonColumn {
        RenderNutzap(
            note = ActivityCardPreviewData.nutzapNote(),
            quotesLeft = 1,
            backgroundColor = remember { mutableStateOf(Color.Transparent) },
            accountViewModel = accountViewModel,
            nav = EmptyNav(),
        )
    }
}

@Preview
@Composable
fun OnchainZapActivityCardPreview() {
    val accountViewModel = mockAccountViewModel()

    ThemeComparisonColumn {
        RenderOnchainZap(
            note = ActivityCardPreviewData.onchainZapNote(),
            quotesLeft = 1,
            backgroundColor = remember { mutableStateOf(Color.Transparent) },
            accountViewModel = accountViewModel,
            nav = EmptyNav(),
        )
    }
}
