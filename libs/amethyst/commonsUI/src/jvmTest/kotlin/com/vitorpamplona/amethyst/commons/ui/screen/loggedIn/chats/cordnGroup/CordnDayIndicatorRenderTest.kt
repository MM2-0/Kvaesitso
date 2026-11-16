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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.ChatLayoutType
import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupChatroom
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.screen.DisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatFeedDayIndicator
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.LocalChatDayShownByFeed
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.cordn.spec02Envelopes.CordnMessageKinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.skia.EncodedImageFormat
import org.junit.After
import org.junit.Before
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A cordn room over four days, rendered offscreen in each [ChatLayoutType] at the newest message
 * and scrolled back, through the real [CordnGroupChatroom], [DaySeparator], [CordnMessageRow] (and
 * so cordn's own footer) and the shared [ChatFeedDayIndicator], laid out as CordnGroupChatScreen's
 * list lays them out. Writes `commonsUI/build/chat-screen/cordn-<layout>.png`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CordnDayIndicatorRenderTest {
    private val outDir = File("build/chat-screen").apply { mkdirs() }

    // CordnMessageRow collects flows with collectAsStateWithLifecycle, which needs a Main
    // dispatcher. A plain JVM test has none; without one the first collection throws on the
    // scene's coroutines and stops recomposition after the first frame, so nothing reacts to
    // the list's layout (the day indicator included) and the render shows a half-built room.
    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun bubbles() = render(ChatLayoutType.BUBBLES)

    @Test
    fun compact() = render(ChatLayoutType.COMPACT)

    @Test
    fun open() = render(ChatLayoutType.OPEN)

    private fun render(layout: ChatLayoutType) {
        val density = 2f
        val scene =
            ImageComposeScene(
                width = (PANEL_WIDTH * SCROLL_POSITIONS.size * density).toInt(),
                height = (PANEL_HEIGHT * density).toInt(),
                density = Density(density),
            ) {
                AmethystPreviewTheme(dark = true) {
                    Row {
                        SCROLL_POSITIONS.forEach { firstIndex ->
                            Box(Modifier.width(PANEL_WIDTH.dp).fillMaxHeight()) {
                                Surface(color = MaterialTheme.colorScheme.background) {
                                    CordnRoom(layout, firstIndex)
                                }
                            }
                        }
                    }
                }
            }
        try {
            var image = scene.render(0)
            repeat(SETTLE_FRAMES) { frame ->
                Thread.sleep(FRAME_MILLIS)
                image = scene.render((frame + 1) * FRAME_MILLIS * 1_000_000L)
            }
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("could not encode")
            File(outDir, "cordn-${layout.name.lowercase()}.png").writeBytes(png.bytes)

            val pixels = image.peekPixels() ?: error("no pixels")
            val distinct = HashSet<Int>()
            for (y in 0 until image.height step 7) for (x in 0 until image.width step 7) distinct += pixels.getColor(x, y)
            assertTrue(distinct.size > 20, "the room rendered almost nothing (${distinct.size} colours)")
        } finally {
            scene.close()
        }
    }

    private companion object {
        const val SETTLE_FRAMES = 14
        const val FRAME_MILLIS = 60L
        const val PANEL_WIDTH = 380
        const val PANEL_HEIGHT = 720
        val SCROLL_POSITIONS = listOf(0, 4, 7, 10)
    }
}

// mockAccountViewModel's key, so these messages are "mine".
private const val ME = "989c3734c46abac7ce3ce229971581a5a6ee39cdd6aa7261a55823fa7f8c4799"
private const val ALICE = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f9"
private const val BOB = "3f9e2d1c0b0a99887766554433221100ffeeddccbbaa99887766554433221100"
private const val CAROL = "c0ffee00112233445566778899aabbccddeeff00112233445566778899aabbcc"
private val COORDINATOR = "c".repeat(64)

private fun at(
    daysAgo: Long,
    hour: Int,
    minute: Int,
): Long =
    LocalDate
        .now()
        .minusDays(daysAgo)
        .atTime(hour, minute)
        .atZone(ZoneId.systemDefault())
        .toEpochSecond()

// Oldest first, as the coordinator delivered them.
private val DELIVERED =
    listOf(
        Triple(CAROL, "Kicking off the 2.4 release thread. Freeze is Friday.", at(5, 9, 2)),
        Triple(ALICE, "I still have the relay picker fix open", at(5, 9, 10)),
        Triple(ALICE, "should land today", at(5, 9, 11)),
        Triple(BOB, "Is the onboarding redesign in or out?", at(5, 10, 40)),
        Triple(ME, "I can take the translations sweep", at(3, 14, 5)),
        Triple(BOB, "Crash reports are down 40% since the last beta", at(3, 16, 30)),
        Triple(ALICE, "Relay picker fix merged.\n\nIt also fixes the duplicate relay rows.", at(1, 11, 15)),
        Triple(CAROL, "Release notes draft is up, please read before tomorrow", at(1, 18, 2)),
        Triple(ME, "Reading it now", at(1, 18, 30)),
        Triple(CAROL, "Tagging the release candidate in an hour", at(0, 9, 5)),
        Triple(BOB, "CI is green on main", at(0, 9, 40)),
        Triple(ME, "Translations are in. 31 locales complete.", at(0, 10, 12)),
        Triple(CAROL, "RC1 is out. Thanks everyone!", at(0, 10, 55)),
        Triple(ALICE, "🎉", at(0, 10, 56)),
    ).mapIndexed { index, (author, text, createdAt) ->
        CordnDeliveredMessage(
            CordnEnvelope(
                id = index.toString().padStart(64, '0'),
                pubKey = author,
                createdAt = createdAt,
                kind = CordnMessageKinds.TEXT,
                tags = emptyArray(),
                content = text,
            ),
            cursor = index + 1L,
        )
    }

@Composable
private fun CordnRoom(
    layout: ChatLayoutType,
    firstIndex: Int,
) {
    val accountViewModel = mockAccountViewModel()
    val room = remember { CordnGroupChatroom("gid-render", COORDINATOR).also { it.addAll(DELIVERED) } }
    val messages by room.messages.collectAsState()
    val annotations by room.annotations.collectAsState()
    val rows = remember(messages) { messages.asReversed() }
    val today = rememberToday()
    val pinsDay = layout != ChatLayoutType.OPEN

    CompositionLocalProvider(
        LocalDisplaySettings provides DisplaySettings(chatLayout = layout),
        LocalChatDayShownByFeed provides pinsDay,
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Release team (cordn)",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            )
            // As CordnGroupChatScreen: the Box carries the weight and the side padding.
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = firstIndex)
                LazyColumn(state = listState, reverseLayout = true, contentPadding = FeedPadding, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(rows, key = { _, it -> it.envelope.id }) { index, message ->
                        val older = rows.getOrNull(index + 1)
                        val newer = rows.getOrNull(index - 1)
                        Column {
                            if (!message.sameDayAs(older)) {
                                DaySeparator(message.envelope.createdAt, today)
                            }
                            CordnMessageRow(
                                message = message,
                                room = room,
                                annotations = annotations,
                                me = ME,
                                groupPosition = cordnGroupPositionFor(newer, message, older),
                                text = annotations.contentOf(message.envelope.id),
                                isEdited = false,
                                shouldHighlight = false,
                                accountViewModel = accountViewModel,
                                nav = EmptyNav(),
                                onHighlightFinished = {},
                                onScrollToMessage = {},
                                onReply = {},
                                onEdit = {},
                                onDelete = {},
                                onTogglePin = {},
                                onReact = {},
                            )
                        }
                    }
                }
                if (pinsDay) {
                    ChatFeedDayIndicator(listState, rows.size, docked = layout == ChatLayoutType.COMPACT) { rows.getOrNull(it)?.envelope?.createdAt }
                }
            }
        }
    }
}
