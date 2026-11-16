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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wotNudge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSurface
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders every web of trust nudge at phone width in both themes. A missing string throws.
 * Set WOT_RENDER_DIR to also write the PNGs for a human to look at.
 */
class WebOfTrustNudgeRenderTest {
    // 412 dp, a common phone width.
    private val width = 824
    private val height = 1800

    private val palette = listOf(0xFF7E57C2, 0xFF26A69A, 0xFFEF6C00, 0xFF5C6BC0, 0xFFD81B60, 0xFF43A047, 0xFF8D6E63, 0xFF0288D1)

    private fun face(
        initial: String,
        i: Int,
    ): NudgeFace =
        { size ->
            Box(Modifier.size(size).background(Color(palette[i % palette.size])), contentAlignment = Alignment.Center) {
                Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

    private val follows = listOf("A", "B", "C", "D", "E", "F").mapIndexed { i, s -> face(s, i) }
    private val strangers = listOf("x", "y", "z").mapIndexed { i, s -> face(s, i + 5) }
    private val me = face("V", 2)

    private val brainstorm = CircleProvider("brainstorm", "Brainstorm", face("B", 3))
    private val vertex = CircleProvider("vertex", "Vertex", face("V", 6))

    @Composable
    private fun rings(
        activation: CircleActivation,
        followCount: Int = 182,
        extendedCount: Int = 12_400,
        providers: List<CircleProvider> = listOf(brainstorm),
    ) = CircleRingsCard(
        me,
        follows,
        followCount = followCount,
        extendedCount = extendedCount,
        providers = providers,
        selected = brainstorm,
        onSelect = {},
        activation = activation,
        onActivate = {},
        onNotNow = {},
        onDismiss = {},
    )

    private fun nudge(
        activation: CircleActivation = CircleActivation.Offer,
        extendedCount: Int = 12_400,
        providers: List<CircleProvider> = listOf(brainstorm, vertex),
    ) = CircleNudge(
        me,
        follows,
        followCount = 182,
        extendedCount = extendedCount,
        providers = providers,
        selected = brainstorm,
        activation = activation,
        onSelect = {},
        onActivate = {},
        onNotNow = {},
        onDismiss = {},
    )

    private val failed = CircleActivation.Failed("Could not reach Brainstorm. Check your connection and try again.")

    private val scenarios: Map<String, @Composable () -> Unit> =
        linkedMapOf(
            "1-facepile" to { CircleFirstCard(follows, followCount = 182, onSetUp = {}, onNotNow = {}, onDismiss = {}) },
            "2-thread" to {
                ThreadPreviewCard(
                    friends =
                        listOf(
                            NudgePreviewReply(follows[0], "Alice", "You follow", "This is exactly what I was looking for, thanks for writing it up!"),
                            NudgePreviewReply(follows[3], "Dan", "Followed by Alice", "Same here. Bookmarking for the weekend."),
                        ),
                    otherFaces = strangers,
                    otherCount = 37,
                    onSetUp = {},
                    onNotNow = {},
                    onDismiss = {},
                )
            },
            "3-friends" to { FriendsUseItCard(follows, listOf("Alice", "Bob", "Carol", "Dan", "Eve", "Fiatjaf"), onSetUp = {}, onUseTheirs = {}, onDismiss = {}) },
            "3b-friends-one" to { FriendsUseItCard(follows.take(1), listOf("Alice"), onSetUp = {}, onUseTheirs = {}, onDismiss = {}) },
            "4-rings" to { rings(CircleActivation.Offer) },
            "4a-rings-loading" to { rings(CircleActivation.Offer, followCount = 3, extendedCount = 0) },
            "4b-rings-menu" to {
                // The scene does not draw popups: the open menu, drawn in place under the row.
                Column(Modifier.padding(horizontal = 28.dp)) {
                    CircleProviderLine(listOf(brainstorm, vertex), brainstorm, {}, enabled = true)
                    Box(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                        RoundedMenuSurface { CircleProviderMenu(listOf(brainstorm, vertex), brainstorm) {} }
                    }
                }
            },
            "4c-rings-working" to { rings(CircleActivation.Working("Signing in…")) },
            "4d-rings-failed" to { rings(CircleActivation.Failed("Could not reach Brainstorm. Check your connection and try again.")) },
            "4e-rings-activated" to { rings(CircleActivation.Activated) },
            "pa-hero" to { CircleHeroCard(nudge()) },
            "pa-hero-one-provider" to { CircleHeroCard(nudge(providers = listOf(brainstorm))) },
            "pa-hero-menu-one-provider" to {
                // The scene does not draw popups: the open menu, drawn in place under the pill.
                Column(Modifier.padding(horizontal = 28.dp)) {
                    CircleProviderLine(listOf(brainstorm), brainstorm, {}, enabled = true)
                    Box(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                        RoundedMenuSurface { CircleProviderMenu(listOf(brainstorm), brainstorm) {} }
                    }
                }
            },
            "pa-hero-loading" to { CircleHeroCard(nudge(extendedCount = 0)) },
            "pa-hero-working" to { CircleHeroCard(nudge(CircleActivation.Working("Signing in…"))) },
            "pa-hero-failed" to { CircleHeroCard(nudge(failed)) },
            "pa-hero-activated" to { CircleHeroCard(nudge(CircleActivation.Activated)) },
            "pb-banner" to { CircleBannerCard(nudge()) },
            "pb-banner-loading" to { CircleBannerCard(nudge(extendedCount = 0)) },
            "pb-banner-activated" to { CircleBannerCard(nudge(CircleActivation.Activated)) },
            "pc-spotlight" to { CircleSpotlightCard(nudge()) },
            "pc-spotlight-activated" to { CircleSpotlightCard(nudge(CircleActivation.Activated)) },
            "pd-sorted" to { CircleSortedCard(nudge()) },
            "pd-sorted-failed" to { CircleSortedCard(nudge(failed)) },
            "5-strips" to {
                Column {
                    CircleFirstStrip(CircleStripPlace.THREAD, onTurnOn = {}, onDismiss = {}, replyCount = 42)
                    CircleFirstStrip(CircleStripPlace.NOTIFICATIONS, onTurnOn = {}, onDismiss = {})
                    CircleFirstStrip(CircleStripPlace.MESSAGES, onTurnOn = {}, onDismiss = {})
                }
            },
            "6-ready" to { CircleReadyCard(follows, circleSize = 14_203, onOpenSettings = {}, onDismiss = {}) },
            "7-sheet" to {
                Surface(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Column {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.height(4.dp).fillMaxWidth(0.1f).background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)))
                        }
                        CircleSetupSheet("Brainstorm", CircleSetupOption.GUIDED, {}, isPrivate = false, onPrivateChange = {}, onContinue = {}, onCancel = {})
                    }
                }
            },
        )

    private fun render(
        name: String,
        dark: Boolean,
        content: @Composable () -> Unit,
        atMillis: Long = 600,
    ) {
        val scene =
            ImageComposeScene(width = width, height = height, density = Density(2f)) {
                AmethystPreviewTheme(dark = dark) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            content()
                        }
                    }
                }
            }
        try {
            // A frame into the animations, so a spinner shows as a spinner.
            // Step the clock in frames so animations and effects run on the way.
            var t = 0L
            while (t < atMillis) {
                scene.render(t * 1_000_000)
                t += 100
            }
            val png = scene.render(atMillis * 1_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
            val image = ImageIO.read(ByteArrayInputStream(png))
            val background = image.getRGB(1, image.height - 1)
            // Crop the empty background below the card.
            var bottom = image.height - 1
            while (bottom > 0 && (0 until image.width step 2).all { image.getRGB(it, bottom) == background }) bottom--
            assertTrue(bottom > 40, "$name drew nothing")
            assertTrue(bottom < image.height - 1, "$name did not fit in the scene")
            System.getenv("WOT_RENDER_DIR")?.let { dir ->
                ImageIO.write(image.getSubimage(0, 0, image.width, minOf(image.height, bottom + 32)), "png", File(dir, "wot-nudge-$name-${if (dark) "dark" else "light"}.png"))
            }
        } finally {
            scene.close()
        }
    }

    @Test
    fun activatedCardCountsDownThenCloses() {
        var closed = false
        val card: @Composable () -> Unit = { CircleHeroCard(nudge(CircleActivation.Activated).copyWith(onDismiss = { closed = true })) }
        render("pa-hero-activated-2s", dark = false, card, atMillis = 2_500)
        assertTrue(!closed, "closed before the countdown ran out")
        render("pa-hero-activated-4s", dark = true, card, atMillis = 4_500)
        assertTrue(!closed, "closed before the countdown ran out")
        render("pa-hero-activated-done", dark = false, card, atMillis = ACTIVATED_CLOSE_MS + 500L)
        assertTrue(closed, "did not close itself after ${ACTIVATED_CLOSE_MS}ms")
    }

    private fun CircleNudge.copyWith(onDismiss: () -> Unit) = CircleNudge(self, faces, followCount, extendedCount, providers, selected, activation, onSelect, onActivate, onNotNow, onDismiss)

    @Test
    fun everyNudgeRendersInBothThemes() {
        scenarios.forEach { (name, content) ->
            render(name, dark = false, content)
            render(name, dark = true, content)
        }
    }
}
