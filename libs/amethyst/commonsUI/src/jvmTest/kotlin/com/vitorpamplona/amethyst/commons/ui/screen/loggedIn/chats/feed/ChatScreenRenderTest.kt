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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.chats.ui.ChatBubbleLayout
import com.vitorpamplona.amethyst.commons.chats.ui.ChatDivisor
import com.vitorpamplona.amethyst.commons.chats.ui.ChatGroupPosition
import com.vitorpamplona.amethyst.commons.chats.ui.ChatSystemCaption
import com.vitorpamplona.amethyst.commons.chats.ui.ThinSendButton
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.ChatLayoutType
import com.vitorpamplona.amethyst.commons.ui.screen.DisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Font12SP
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders a group chat offscreen (no device, no display) through `ImageComposeScene`, in the dark and
 * light themes side by side, once per [ChatLayoutType], and writes them to
 * `commonsUI/build/chat-screen/chat.png` (bubbles), `chat-compact.png` and `chat-open.png`.
 *
 * Bubbles, dividers, the rename caption, reaction chips and the send button are the real shared
 * composables. The header row, the bubble footer and the faces are stand-ins with the same geometry
 * and styles, since the real ones need a Note, an account and the network. It exists so the chat's
 * visual weight can be looked at while it is tuned, and it fails if the screen draws nothing.
 */
class ChatScreenRenderTest {
    private val outDir = File("build/chat-screen").apply { mkdirs() }

    @Test
    fun chat() = render(ChatLayoutType.BUBBLES, "chat.png")

    @Test
    fun chatCompact() = render(ChatLayoutType.COMPACT, "chat-compact.png")

    @Test
    fun chatOpen() = render(ChatLayoutType.OPEN, "chat-open.png")

    private fun render(
        layout: ChatLayoutType,
        fileName: String,
    ) {
        val density = 2f
        val widthDp = 840
        val heightDp = 1040
        val scene =
            ImageComposeScene(
                width = (widthDp * density).toInt(),
                height = (heightDp * density).toInt(),
                density = Density(density),
            ) {
                CompositionLocalProvider(LocalDisplaySettings provides DisplaySettings(chatLayout = layout)) {
                    ThemeComparisonRow { SampleGroupChat() }
                }
            }
        try {
            var image = scene.render(0)
            repeat(SETTLE_FRAMES) { frame ->
                Thread.sleep(FRAME_MILLIS)
                image = scene.render((frame + 1) * FRAME_MILLIS * 1_000_000L)
            }
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("could not encode")
            File(outDir, fileName).writeBytes(png.bytes)

            val pixels = image.peekPixels() ?: error("no pixels")
            val distinct = HashSet<Int>()
            for (y in 0 until image.height step 7) for (x in 0 until image.width step 7) distinct += pixels.getColor(x, y)
            assertTrue(distinct.size > 20, "the chat rendered almost nothing (${distinct.size} colours)")
        } finally {
            scene.close()
        }
    }

    private companion object {
        const val SETTLE_FRAMES = 12
        const val FRAME_MILLIS = 60L
    }
}

private const val OUTPOST = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f9"
private const val ME = "7e2c55aa10b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d"
private const val DAVID = "3f9e2d1c0b0a99887766554433221100ffeeddccbbaa99887766554433221100"

@Composable
private fun Face(
    color: Color,
    size: Int = 28,
) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

/** Same geometry as `ChatRoomFaces` (two overlapping faces with a background ring). */
@Composable
private fun HeaderFaces() {
    val face = 34.dp * 0.68f
    Box(Modifier.size(34.dp)) {
        Box(Modifier.align(Alignment.TopStart).size(face).background(Color(0xFF8C6E5A), CircleShape))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.align(Alignment.BottomEnd).size(face + 4.dp).background(MaterialTheme.colorScheme.background, CircleShape),
        ) {
            Box(Modifier.size(face).background(Color(0xFF4F86C6), CircleShape))
        }
    }
}

@Composable
private fun Header() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.placeholderText)
        Spacer(Modifier.width(12.dp))
        HeaderFaces()
        Column(Modifier.padding(start = 10.dp)) {
            Text("NosFabrica Product", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text("5 members", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.placeholderText)
        }
    }
}

/** Same type and color as `ChatTimeAgo` + the delivered tick of `ChatTimeWithDelivery`. */
@Composable
private fun Footer(
    time: String,
    mine: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(time, color = MaterialTheme.colorScheme.placeholderText, fontSize = Font12SP)
        if (mine) {
            Spacer(Modifier.width(4.dp))
            Icon(MaterialSymbols.DoneAll, contentDescription = null, tint = MaterialTheme.colorScheme.placeholderText, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun Bubble(
    text: String,
    mine: Boolean,
    position: ChatGroupPosition = ChatGroupPosition.SINGLE,
    time: String? = null,
    author: Pair<String, String>? = null,
    faceColor: Color = Color.Gray,
    fire: Boolean = false,
    quote: Pair<Pair<String, String>?, String>? = null,
) {
    val isLight = MaterialTheme.colorScheme.isLight
    ChatBubbleLayout(
        isLoggedInUser = mine,
        isDraft = false,
        innerQuote = false,
        drawAuthorInfo = author != null && position.isFirstOfGroup,
        groupPosition = position,
        onClick = { false },
        onAuthorClick = {},
        actionMenu = {},
        reactionsRow =
            if (fire) {
                { ChatChipFlowRow { ReactionChipView(ReactionChip("🔥", 1, false), {}, {}) } }
            } else {
                null
            },
        footerRow = time?.let { { Footer(it, mine) } },
        // Like DrawAuthorInfo, it can name the user too: the compact layout names every line.
        drawAuthorLine = {
            val (hex, name) = author ?: (ME to "Me")
            Text(name, color = authorNameColorFor(hex, isLight), fontWeight = ChatAuthorNameWeight, fontSize = ChatAuthorNameSize, maxLines = 1)
        },
        authorAvatar = if (author != null) ({ size -> Face(faceColor, size.value.toInt()) }) else null,
    ) { bgColor ->
        Column {
            quote?.let { (quoted, quotedText) -> Quote(quoted, quotedText, bgColor) }
            Text(text)
        }
    }
}

/** A quoted reply, the way `RenderReply` nests one message inside another. */
@Composable
private fun Quote(
    author: Pair<String, String>?,
    text: String,
    parentBackgroundColor: MutableState<Color>,
) {
    val isLight = MaterialTheme.colorScheme.isLight
    ChatBubbleLayout(
        isLoggedInUser = author == null,
        isDraft = false,
        innerQuote = true,
        drawAuthorInfo = author != null,
        parentBackgroundColor = parentBackgroundColor,
        onClick = { true },
        onAuthorClick = {},
        actionMenu = {},
        drawAuthorLine = {
            val (hex, name) = author ?: (ME to "Me")
            Text(name, color = authorNameColorFor(hex, isLight), fontWeight = ChatAuthorNameWeight, fontSize = ChatAuthorNameSize, maxLines = 1)
        },
    ) { Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
}

@Composable
private fun Composer(typed: String?) {
    Surface(
        shape = EditFieldBorder,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 4.dp)) {
            Icon(MaterialSymbols.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.placeholderText)
            Spacer(Modifier.width(12.dp))
            Text(
                typed ?: "Message",
                color = if (typed != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.placeholderText,
                modifier = Modifier.weight(1f),
            )
            ThinSendButton(isActive = typed != null, modifier = Modifier) {}
        }
    }
}

@Composable
private fun SampleGroupChat() {
    Column {
        Header()
        ChatDivisor("Today")
        ChatSystemCaption("You renamed the chat to “NosFabrica Product”")
        Bubble("Renamed the chat to “NosFabrica Product”", mine = true, position = ChatGroupPosition.TOP)
        Bubble("Ok, this is working way better than I expected", mine = true, position = ChatGroupPosition.BOTTOM, time = "8:38", fire = true)
        Bubble("A cool goal of this is to delete messages from unknown users automatically", mine = true, time = "8:49")
        Bubble(
            "to prevent random users or bots out of network from sliding in DMs?",
            mine = false,
            time = "8:55",
            author = OUTPOST to "Relay Outpost",
            faceColor = Color(0xFF5A5A5A),
        )
        Bubble(
            "Delete the past scammers",
            mine = true,
            time = "8:59",
            fire = true,
            quote = (OUTPOST to "Relay Outpost") to "to prevent random users or bots out of network from sliding in DMs?",
        )
        Bubble(
            "Big fat red alert next to the accounts that need it.",
            mine = false,
            position = ChatGroupPosition.TOP,
            author = DAVID to "david",
            faceColor = Color(0xFFD9825B),
        )
        Bubble(
            "Unlike every other nostr app that give you no useful warnings at all.\n\nAnd a second paragraph, so blank lines inside a message can be told from the gap between two.",
            mine = false,
            position = ChatGroupPosition.BOTTOM,
            time = "9:08",
            author = DAVID to "david",
            faceColor = Color(0xFFD9825B),
        )
        Bubble("But also, send a message to the relays that store DMs to delete those Giftwraps.", mine = true, time = "9:09")
        Spacer(Modifier.size(8.dp))
        Composer(typed = null)
        Composer(typed = "Sounds good")
    }
}
