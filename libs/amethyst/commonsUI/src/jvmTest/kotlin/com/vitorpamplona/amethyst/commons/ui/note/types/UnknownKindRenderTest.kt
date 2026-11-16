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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.rendering.unknown.isUnknownKind
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocalNotePlatform
import com.vitorpamplona.amethyst.commons.ui.note.platform.NotePlatform
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders events of kinds the app has no renderer for, offscreen, into
 * `commonsUI/build/unknown-kinds/<case>-<theme>.png`: the old text path beside [RenderUnknownKind]
 * as NoteCompose draws it, full and quoted, so the card can be checked against the row's own first
 * line (expiration, location, PoW). It fails only if a case draws nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UnknownKindRenderTest {
    private val outDir = File("build/unknown-kinds").apply { mkdirs() }

    private val alice = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val bob = "7fa56f5d6962ab1e3cd424e758c3002b8665f7b0d8dcee9fe9e288d7751ac194"
    private val carol = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val quotedId = "a".repeat(64)

    /** LocalCache holds notes weakly: the fixtures are kept here so they outlive a collection. */
    private val held = ArrayList<Note>()

    private fun event(
        idSeed: Char,
        pubKey: String,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): Event = EventFactory.create(idSeed.toString().repeat(64), pubKey, 1_790_900_000, kind, tags, content, "0".repeat(128))

    private fun consume(event: Event): Note {
        val note = LocalCache.getOrCreateNote(event.id)
        held += note
        LocalCache.justConsume(event, null, wasVerified = true)
        assertTrue(note.event != null, "the cache refused ${event.kind}:${event.id}")
        return note
    }

    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @BeforeTest
    fun fill() {
        consume(event('b', alice, 0, emptyArray(), """{"name":"alice","display_name":"Alice","about":"bakes"}"""))
        consume(event('c', bob, 0, emptyArray(), """{"name":"bob","display_name":"Bob Builder"}"""))
        consume(event(quotedId[0], carol, 1, emptyArray(), "Has anyone tried the new sourdough starter from the market?"))
    }

    /** A recipe app's addressable kind: title, summary, picture, hashtags and custom tags; prose content. */
    private fun recipe() =
        event(
            '1',
            alice,
            35_123,
            arrayOf(
                arrayOf("d", "country-sourdough"),
                arrayOf("title", "Country Sourdough"),
                arrayOf("summary", "A three-day loaf with an open crumb and a dark crust."),
                arrayOf("image", "https://example.com/bread.jpg"),
                arrayOf("t", "baking"),
                arrayOf("t", "sourdough"),
                arrayOf("p", bob, "", "contributor"),
                arrayOf("servings", "2 loaves"),
                arrayOf("prep_time", "PT72H"),
                arrayOf("ingredient", "bread flour", "900g"),
                arrayOf("ingredient", "water", "700g"),
                arrayOf("ingredient", "salt", "20g"),
                arrayOf("published_at", "1790800000"),
                arrayOf("alt", "Recipe: Country Sourdough"),
            ),
            "Feed the starter the night before. Mix flour and water, rest an hour, then add the starter and salt. " +
                "Fold every 30 minutes for 3 hours, shape, and proof overnight in the fridge. Bake at 250°C in a Dutch oven.",
        )

    /** A machine record: JSON content, a `k`, a reference to a note and a person. */
    private fun jsonRecord() =
        event(
            '2',
            bob,
            30_999,
            arrayOf(
                arrayOf("d", "job-8812"),
                arrayOf("k", "1"),
                arrayOf("e", quotedId, "wss://relay.example.com", "root"),
                arrayOf("p", carol),
                arrayOf("status", "finished"),
                arrayOf("alt", "Translation job result"),
            ),
            """{"status":"success","language":"pt-BR","tokens":412,"model":"nllb-200","duration_ms":1840,"output":{"text":"Alguém já experimentou?"},"warnings":[]}""",
        )

    /** Ciphertext for someone, with only an `alt` and a `p` to go by. */
    private fun encrypted() =
        event(
            '3',
            carol,
            9_876,
            arrayOf(
                arrayOf("p", alice),
                arrayOf("alt", "Encrypted calendar invitation"),
                arrayOf("expiration", "1795000000"),
            ),
            "AhkAp3ftq1q1BHdaSYmW0RnXlpQSmrS7z8rBdGfKC5u7Aw0rYCIyp1C9Ef8xxhNa7hTmVmcqSZspoXAzN6UFUQ8nXPbpvk9AqW6/1KC1gO5S" +
                "5EpZRVDu/ArWT3lDTZLXqtFl4Zqy0wsFLnm4iOg5kD4X5Hc4FWdjYM1Qc8zR5I3iSpo3TEGvGMvqprFt5XJ6Zt3Vi8ebz1JZr5H9cv8A==",
        )

    /** Tags only, no content: a meetup announcement from an app the client doesn't know. */
    private fun tagsOnly() =
        event(
            '4',
            alice,
            37_515,
            arrayOf(
                arrayOf("d", "nostr-lisbon-meetup"),
                arrayOf("name", "Nostr Lisbon Meetup"),
                arrayOf("description", "Monthly meetup for builders and users. Bring a laptop."),
                arrayOf("start", "1791500000"),
                arrayOf("end", "1791510800"),
                arrayOf("location", "Praça do Comércio, Lisbon"),
                arrayOf("g", "eycs0p8ukc7v"),
                arrayOf("r", "https://lisbon.nostr.example/meetup"),
                arrayOf("relay", "wss://relay.lisbon.example"),
                arrayOf("p", bob, "", "host"),
                arrayOf("p", carol, "", "speaker"),
                arrayOf("capacity", "80"),
            ),
            "",
        )

    /** A hex blob with nothing to explain it but the kind number. */
    private fun opaque() =
        event(
            '5',
            bob,
            4_242,
            arrayOf(arrayOf("d", "state"), arrayOf("v", "3")),
            "9f2c4e8a1b7d3f6091c2e5a8b4d7f0a3c6e9b2d5f8a1c4e7b0d3f6a9c2e5b8d1f4a7c0e3b6d9f2a5c8e1b4d7f0a3c6e9b2",
        )

    /** A short post on an unknown kind that quotes another note. */
    private fun shortPost() =
        event(
            '6',
            carol,
            1_717,
            arrayOf(arrayOf("q", quotedId), arrayOf("t", "food"), arrayOf("g", "u4pruydqqvj"), arrayOf("nonce", "4", "20")),
            "Replying to my own question: yes, and it's great #food",
        )

    @Test fun recipeCase() = render("1-recipe", recipe(), 1500)

    @Test fun jsonRecordCase() = render("2-json-record", jsonRecord(), 1500)

    @Test fun encryptedCase() = render("3-encrypted", encrypted(), 900)

    @Test fun tagsOnlyCase() = render("4-tags-only", tagsOnly(), 1300)

    @Test fun opaqueCase() = render("5-opaque", opaque(), 800)

    @Test fun shortPostCase() = render("6-short-post", shortPost(), 1100)

    private fun render(
        name: String,
        event: Event,
        heightDp: Int,
    ) {
        assertTrue(isUnknownKind(event), "$name: kind ${event.kind} has a class in quartz, pick another")
        val note = consume(event)
        val nav = mockk<INav>(relaxed = true)
        val density = 2f
        for (dark in listOf(false, true)) {
            val scene =
                ImageComposeScene(width = (1640 * density).toInt(), height = (heightDp * density).toInt(), density = Density(density)) {
                    AmethystPreviewTheme(dark = dark) {
                        CompositionLocalProvider(LocalNotePlatform provides GeohashOnlyPlatform) {
                            Surface(color = MaterialTheme.colorScheme.background) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column(Modifier.weight(1f)) { Labeled("Before (text path)") { Today(note, nav) } }
                                    Column(Modifier.weight(1f)) {
                                        Labeled("After, in NoteCompose") {
                                            NoteCompose(baseNote = note, quotesLeft = 2, accountViewModel = mockAccountViewModel(), nav = nav)
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Labeled("After, quoted") {
                                            NoteCompose(
                                                baseNote = note,
                                                modifier = MaterialTheme.colorScheme.replyModifier,
                                                isQuotedNote = true,
                                                unPackReply = ReplyRenderType.NONE,
                                                makeItShort = true,
                                                quotesLeft = 0,
                                                accountViewModel = mockAccountViewModel(),
                                                nav = nav,
                                            )
                                        }
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
                File(outDir, "$name-${if (dark) "dark" else "light"}.png").writeBytes(png.bytes)

                val pixels = image.peekPixels() ?: error("no pixels")
                val distinct = HashSet<Int>()
                for (y in 0 until image.height step 7) for (x in 0 until image.width step 7) distinct += pixels.getColor(x, y)
                assertTrue(distinct.size > 20, "$name rendered almost nothing (${distinct.size} colours)")
            } finally {
                scene.close()
            }
        }
    }

    /** The note row's first line names a `g` geohash's city through the app's services; here it shows the geohash. */
    private object GeohashOnlyPlatform : NotePlatform {
        @Composable
        override fun LoadCityName(
            geohashStr: String,
            onLoading: (@Composable () -> Unit)?,
            content: @Composable (String) -> Unit,
        ) = content(geohashStr)
    }

    @Composable
    private fun Today(
        note: Note,
        nav: INav,
    ) {
        val background = remember { mutableStateOf(Color.Transparent) }
        val editState = remember { mutableStateOf<GenericLoadable<EditState>>(GenericLoadable.Empty()) }
        RenderTextEvent(note, false, true, 2, ReplyRenderType.NONE, background, editState, mockAccountViewModel(), nav)
    }

    @Composable
    private fun Labeled(
        label: String,
        content: @Composable () -> Unit,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(Modifier.fillMaxWidth().padding(vertical = 6.dp))
        content()
    }

    private companion object {
        const val SETTLE_FRAMES = 12
        const val FRAME_MILLIS = 80L
    }
}
