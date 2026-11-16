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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.chats.ui.ChatBubbleLayout
import com.vitorpamplona.amethyst.commons.chats.ui.ChatDivisor
import com.vitorpamplona.amethyst.commons.chats.ui.ChatGroupPosition
import com.vitorpamplona.amethyst.commons.chats.ui.chatDayKey
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.ChatLayoutType
import com.vitorpamplona.amethyst.commons.ui.note.chatDayLabelWith
import com.vitorpamplona.amethyst.commons.ui.note.chatTimestampWith
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeOfDayFormatter
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalBlankLineScale
import com.vitorpamplona.amethyst.commons.ui.screen.DisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import com.vitorpamplona.amethyst.commons.ui.theme.Font12SP
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.quartz.utils.TimeUtils
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A busy group chat (five people over four days) rendered offscreen in each [ChatLayoutType], at
 * the newest message and scrolled back through history, to see how each layout tells the reader
 * which day a message is from: the day pill (bubbles), the docked day bar (compact), the day in
 * every stamp (open). Writes `commonsUI/build/chat-screen/people-<layout>[-light].png`.
 *
 * The bubbles, dividers, day indicator and reaction chips are the real composables; the footer
 * mirrors `ChatMessageFooter`'s rules (time of day under a pinned day, the day in the stamp
 * otherwise) and the text mirrors `RichTextViewer`'s blank lines.
 */
class ChatPeopleRenderTest {
    private val outDir = File("build/chat-screen").apply { mkdirs() }

    @Test
    fun bubbles() = render(ChatLayoutType.BUBBLES, dark = true)

    @Test
    fun bubblesLight() = render(ChatLayoutType.BUBBLES, dark = false)

    @Test
    fun compact() = render(ChatLayoutType.COMPACT, dark = true)

    @Test
    fun compactLight() = render(ChatLayoutType.COMPACT, dark = false)

    @Test
    fun open() = render(ChatLayoutType.OPEN, dark = true)

    private fun render(
        layout: ChatLayoutType,
        dark: Boolean,
    ) {
        val density = 2f
        val widthDp = PANEL_WIDTH * SCROLL_POSITIONS.size
        val heightDp = PANEL_HEIGHT
        val scene =
            ImageComposeScene(
                width = (widthDp * density).toInt(),
                height = (heightDp * density).toInt(),
                density = Density(density),
            ) {
                AmethystPreviewTheme(dark = dark) {
                    Row {
                        SCROLL_POSITIONS.forEach { firstIndex ->
                            Box(Modifier.width(PANEL_WIDTH.dp).fillMaxHeight()) {
                                Surface(color = MaterialTheme.colorScheme.background) {
                                    PeopleChat(layout, firstIndex)
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
            File(outDir, "people-${layout.name.lowercase()}${if (dark) "" else "-light"}.png").writeBytes(png.bytes)

            val pixels = image.peekPixels() ?: error("no pixels")
            val distinct = HashSet<Int>()
            for (y in 0 until image.height step 7) for (x in 0 until image.width step 7) distinct += pixels.getColor(x, y)
            assertTrue(distinct.size > 20, "the chat rendered almost nothing (${distinct.size} colours)")
        } finally {
            scene.close()
        }
    }

    private companion object {
        const val SETTLE_FRAMES = 14
        const val FRAME_MILLIS = 60L
        const val PANEL_WIDTH = 380
        const val PANEL_HEIGHT = 720

        // Where each panel is scrolled to: the newest message, then further back through history.
        val SCROLL_POSITIONS = listOf(0, 4, 6, 9)
    }
}

private class Person(
    val hex: String,
    val name: String,
    val face: Color,
)

private val ME = Person("7e2c55aa10b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d", "Me", Color(0xFF7E57C2))
private val ALICE = Person("a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f9", "Alice Moreau", Color(0xFF5A8F6E))
private val BOB = Person("3f9e2d1c0b0a99887766554433221100ffeeddccbbaa99887766554433221100", "bob", Color(0xFFD9825B))
private val CAROL = Person("c0ffee00112233445566778899aabbccddeeff00112233445566778899aabbcc", "Carol (release captain)", Color(0xFF4F86C6))
private val DAVE = Person("0badc0de99887766554433221100ffeeddccbbaa99887766554433221100aabb", "dave", Color(0xFF9C6B98))

private class Message(
    val author: Person,
    val text: String,
    val daysAgo: Long,
    val hour: Int,
    val minute: Int,
    val quote: Pair<Person, String>? = null,
    val reaction: String? = null,
) {
    val createdAt: Long =
        LocalDate
            .now()
            .minusDays(daysAgo)
            .atTime(hour, minute)
            .atZone(ZoneId.systemDefault())
            .toEpochSecond()
}

// Oldest first, as people wrote them; the feed shows them newest at the bottom.
private val CONVERSATION =
    listOf(
        Message(CAROL, "Kicking off the 2.4 release thread. Freeze is Friday.", 5, 9, 2),
        Message(ALICE, "I still have the relay picker fix open", 5, 9, 10),
        Message(ALICE, "should land today", 5, 9, 11),
        Message(BOB, "Is the onboarding redesign in or out?", 5, 10, 40),
        Message(CAROL, "Out. It slips to 2.5.", 5, 10, 42, reaction = "👍"),
        Message(ME, "I can take the translations sweep", 3, 14, 5),
        Message(DAVE, "Crash reports are down 40% since the last beta", 3, 16, 30, reaction = "🔥"),
        Message(DAVE, "the image cache change did most of it", 3, 16, 31),
        Message(ALICE, "Relay picker fix merged.\n\nIt also fixes the duplicate relay rows people reported.", 1, 11, 15),
        Message(BOB, "Nice", 1, 11, 20),
        Message(CAROL, "Release notes draft is up, please read before tomorrow", 1, 18, 2),
        Message(ME, "Reading it now", 1, 18, 30, quote = CAROL to "Release notes draft is up, please read before tomorrow"),
        Message(ME, "The zaps section needs the new screenshot", 1, 18, 31),
        Message(CAROL, "Tagging the release candidate in an hour", 0, 9, 5),
        Message(DAVE, "CI is green on main", 0, 9, 40),
        Message(ALICE, "Ship it", 0, 9, 41, reaction = "🚀"),
        Message(ME, "Translations are in. 31 locales complete.", 0, 10, 12),
        Message(CAROL, "RC1 is out. Thanks everyone!", 0, 10, 55, reaction = "🎉"),
    ).asReversed()

private fun groupPositionOf(index: Int): ChatGroupPosition {
    val message = CONVERSATION[index]

    fun joins(other: Message?) = other != null && other.author == message.author && chatDayKey(other.createdAt) == chatDayKey(message.createdAt)
    val above = joins(CONVERSATION.getOrNull(index + 1))
    val below = joins(CONVERSATION.getOrNull(index - 1))
    return when {
        above && below -> ChatGroupPosition.MIDDLE
        above -> ChatGroupPosition.BOTTOM
        below -> ChatGroupPosition.TOP
        else -> ChatGroupPosition.SINGLE
    }
}

private fun opensDay(index: Int): Boolean = index == CONVERSATION.lastIndex || chatDayKey(CONVERSATION[index + 1].createdAt) != chatDayKey(CONVERSATION[index].createdAt)

@Composable
private fun PeopleChat(
    layout: ChatLayoutType,
    firstIndex: Int,
) {
    val pinsDay = layout != ChatLayoutType.OPEN
    CompositionLocalProvider(
        LocalDisplaySettings provides DisplaySettings(chatLayout = layout),
        LocalChatDayShownByFeed provides pinsDay,
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Release team · 5 members",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = firstIndex)
                LazyColumn(state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize()) {
                    items(CONVERSATION.size) { index ->
                        Column {
                            if (opensDay(index)) {
                                ChatDivisor(chatDayLabelWith(CONVERSATION[index].createdAt, TimeUtils.nowMillis(), "Today", "Yesterday"))
                            }
                            PersonMessage(index, layout, pinsDay)
                        }
                    }
                }
                if (pinsDay) {
                    ChatFeedDayIndicator(listState, CONVERSATION.size, docked = layout == ChatLayoutType.COMPACT) { CONVERSATION.getOrNull(it)?.createdAt }
                }
            }
        }
    }
}

@Composable
private fun PersonMessage(
    index: Int,
    layout: ChatLayoutType,
    pinsDay: Boolean,
) {
    val message = CONVERSATION[index]
    val mine = message.author == ME
    val position = groupPositionOf(index)
    val isLight = MaterialTheme.colorScheme.isLight

    // ChatMessageFooter's rules: the last of a run carries the time; in OPEN each of someone
    // else's messages does. Under a pinned day it is the time of day; otherwise it names the day.
    val showFooter = position.isLastOfGroup || (layout == ChatLayoutType.OPEN && !mine)
    val timeOfDay = rememberTimeOfDayFormatter()
    val stamp =
        if (pinsDay) {
            timeOfDay(message.createdAt * 1000)
        } else {
            chatTimestampWith(message.createdAt, TimeUtils.nowMillis(), timeOfDay, "Yesterday")
        }

    ChatBubbleLayout(
        isLoggedInUser = mine,
        isDraft = false,
        innerQuote = false,
        drawAuthorInfo = !mine && position.isFirstOfGroup,
        groupPosition = position,
        onClick = { false },
        onAuthorClick = {},
        actionMenu = {},
        reactionsRow = message.reaction?.let { emoji -> { ChatChipFlowRow { ReactionChipView(ReactionChip(emoji, 2, false), {}, {}) } } },
        footerRow = if (showFooter) ({ StampRow(stamp, mine) }) else null,
        drawAuthorLine = { AuthorName(message.author, isLight) },
        authorAvatar = if (mine) null else ({ size -> Face(message.author.face, size) }),
    ) { bgColor ->
        Column {
            message.quote?.let { (who, what) -> QuotedMessage(who, what, bgColor) }
            Paragraphs(message.text)
        }
    }
}

@Composable
private fun AuthorName(
    person: Person,
    isLight: Boolean,
) {
    Text(
        person.name,
        color = authorNameColorFor(person.hex, isLight),
        fontWeight = ChatAuthorNameWeight,
        fontSize = ChatAuthorNameSize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Blank lines as RichTextViewer draws them: a line tall, scaled by [LocalBlankLineScale]. */
@Composable
private fun Paragraphs(text: String) {
    val blankLine = (16 * 1.3f * LocalBlankLineScale.current).dp
    Column(verticalArrangement = Arrangement.spacedBy(blankLine)) {
        text.split("\n\n").forEach { Text(it) }
    }
}

@Composable
private fun QuotedMessage(
    person: Person,
    text: String,
    parentBackgroundColor: MutableState<Color>,
) {
    val isLight = MaterialTheme.colorScheme.isLight
    ChatBubbleLayout(
        isLoggedInUser = false,
        isDraft = false,
        innerQuote = true,
        drawAuthorInfo = true,
        parentBackgroundColor = parentBackgroundColor,
        onClick = { true },
        onAuthorClick = {},
        actionMenu = {},
        drawAuthorLine = { AuthorName(person, isLight) },
    ) { Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
}

@Composable
private fun StampRow(
    stamp: String,
    mine: Boolean,
) {
    Row {
        Text(stamp, color = MaterialTheme.colorScheme.placeholderText, fontSize = Font12SP)
        if (mine) {
            Spacer(Modifier.width(4.dp))
            Icon(MaterialSymbols.DoneAll, contentDescription = null, tint = MaterialTheme.colorScheme.placeholderText, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun Face(
    color: Color,
    size: Dp,
) {
    Box(Modifier.size(size).background(color, CircleShape))
}
