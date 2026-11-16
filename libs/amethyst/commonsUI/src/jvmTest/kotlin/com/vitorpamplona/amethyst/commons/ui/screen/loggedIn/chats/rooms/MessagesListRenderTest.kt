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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.public_chat
import com.vitorpamplona.amethyst.commons.ui.theme.ChatRowAvatarSize
import com.vitorpamplona.amethyst.commons.ui.theme.ChatRowPictureModifier
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders a Messages list offscreen (no device, no display) through `ImageComposeScene`, in the dark
 * and light themes side by side, and writes it to `commonsUI/build/messages-list/messages.png`.
 *
 * The rows go through the real [ChannelName] / `ChatHeaderLayout` / title / label / preview-cleanup
 * code; only the pictures are stand-ins (flat discs), because a real one needs the network and an
 * account. It exists so the list's visual weight can be looked at while it is tuned, and it fails if
 * the list throws or draws nothing.
 */
class MessagesListRenderTest {
    private val outDir = File("build/messages-list").apply { mkdirs() }

    @Test
    fun messages() {
        val density = 2f
        val widthDp = 840
        val heightDp = 760
        val scene =
            ImageComposeScene(
                width = (widthDp * density).toInt(),
                height = (heightDp * density).toInt(),
                density = Density(density),
            ) { ThemeComparisonRow { SampleMessagesList() } }
        try {
            var image = scene.render(0)
            repeat(SETTLE_FRAMES) { frame ->
                Thread.sleep(FRAME_MILLIS)
                image = scene.render((frame + 1) * FRAME_MILLIS * 1_000_000L)
            }
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("could not encode")
            File(outDir, "messages.png").writeBytes(png.bytes)

            val pixels = image.peekPixels() ?: error("no pixels")
            val distinct = HashSet<Int>()
            for (y in 0 until image.height step 7) for (x in 0 until image.width step 7) distinct += pixels.getColor(x, y)
            assertTrue(distinct.size > 20, "the list rendered almost nothing (${distinct.size} colours)")
        } finally {
            scene.close()
        }
    }

    private companion object {
        const val SETTLE_FRAMES = 12
        const val FRAME_MILLIS = 60L
    }
}

private val Fabrica = Color(0xFF6D8B74)
private val Sunset = Color(0xFFD9825B)
private val Ocean = Color(0xFF4F86C6)
private val Forest = Color(0xFF55745A)
private val Buzz = Color(0xFFE6195E)
private val Soapbox = Color(0xFFB400E0)
private val Amethyst = Color(0xFFF2F2F2)
private val Robot = Color(0xFF3E6E8E)
private val Bender = Color(0xFF3C8C73)

@Composable
private fun Disc(color: Color) {
    Box(ChatRowPictureModifier.background(color))
}

/** Stand-in for [com.vitorpamplona.amethyst.commons.ui.note.ChatRoomFaces]: same geometry, flat faces. */
@Composable
private fun TwoFaces(
    back: Color,
    front: Color,
) {
    val face = ChatRowAvatarSize * 0.68f
    Box(Modifier.size(ChatRowAvatarSize)) {
        Box(Modifier.align(Alignment.TopStart).size(face).background(back, CircleShape))
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(face + 4.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape),
        ) {
            Box(Modifier.size(face).background(front, CircleShape))
        }
    }
}

@Composable
private fun DmTitle(
    name: String,
    modifier: Modifier,
    pinned: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (pinned) {
            Icon(
                symbol = MaterialSymbols.PushPin,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
            )
        }
    }
}

@Composable
private fun SampleMessagesList() {
    val now = TimeUtils.now()
    val nevent = NEvent.create("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", null, 1, relay = null)

    Column {
        ChannelName(
            channelPicture = { TwoFaces(Fabrica, Sunset) },
            channelTitle = { DmTitle("NosFabrica", it, pinned = true) },
            channelLastTime = now - 57 * 60,
            channelLastContent = "You: Test2",
            hasNewMessages = false,
            onClick = {},
        )
        ChannelName(
            channelPicture = { TwoFaces(Ocean, Forest) },
            channelTitle = { DmTitle("Minced, greenart7c3, Dee, KotlinG", it, pinned = true) },
            channelLastTime = now - 2 * 24 * 3600,
            channelLastContent = "You: testing group message from armada",
            hasNewMessages = false,
            onClick = {},
        )
        ChannelName(
            channelPicture = { Disc(Buzz) },
            channelTitle = { ChannelTitleWithLabelInfo("buzz-fork", MaterialSymbols.Dns, "buzz.relay.tools", it) },
            channelLastTime = now - 2 * 60,
            channelLastContent = "buzz-watcher: [github block/buzz@main] `a3870e1` **fix** the relay reconnect loop",
            hasNewMessages = true,
            onClick = {},
        )
        ChannelName(
            channelPicture = { Disc(Robot) },
            channelTitle = { DmTitle("Test - npub18aj6sjrr…jsmpnxpv", it) },
            channelLastTime = now - 22 * 60,
            channelLastContent = "You: https://nostr.download/ee078c63eab4f77eb2c1a9.png",
            hasNewMessages = false,
            onClick = {},
        )
        ChannelName(
            channelPicture = { Disc(Amethyst) },
            channelTitle = { ChannelTitleWithLabelInfo("Amethyst Users", MaterialSymbols.Public, Res.string.public_chat, it) },
            channelLastTime = now - 3600,
            channelLastContent = "OK3E: nostr:$nevent",
            hasNewMessages = false,
            onClick = {},
        )
        ChannelName(
            channelPicture = { Disc(Soapbox) },
            channelTitle = { ChannelTitleWithLabelInfo("armada", MaterialSymbols.Group, "Soapbox Community", it) },
            channelLastTime = now - 3600,
            channelLastContent = "Dino Dini: i found also some kind of way to post gifs from the app",
            hasNewMessages = false,
            onClick = {},
        )
        ChannelName(
            channelPicture = { Disc(Soapbox) },
            channelTitle = { ChannelTitleWithLabelInfo("general", MaterialSymbols.Group, "Soapbox Community", it) },
            channelLastTime = now - 8 * 3600,
            channelLastContent = "nycta: !meme",
            hasNewMessages = true,
            onClick = {},
        )
        ChannelName(
            channelPicture = { Disc(Bender) },
            channelTitle = { ChannelTitleWithLabelInfo("Nostr", MaterialSymbols.Public, Res.string.public_chat, it) },
            channelLastTime = now - 12 * 3600,
            channelLastContent = "J Smith: In my surroundings, there is one elderly neighbour who still posts",
            hasNewMessages = false,
            onClick = {},
        )
        ChannelName(
            channelIdHex = "9q8yy",
            channelPicture = null,
            channelTitle = { ChannelTitleWithLabelInfo("#9q8yy", MaterialSymbols.LocationOn, "San Francisco", it) },
            channelLastTime = now - 14 * 3600,
            channelLastContent = "anon: anyone at the meetup tonight?",
            hasNewMessages = false,
            loadProfilePicture = false,
            loadRobohash = false,
            autoPlayGif = false,
            onClick = {},
            fallbackSymbol = MaterialSymbols.LocationOn,
        )
    }
}
