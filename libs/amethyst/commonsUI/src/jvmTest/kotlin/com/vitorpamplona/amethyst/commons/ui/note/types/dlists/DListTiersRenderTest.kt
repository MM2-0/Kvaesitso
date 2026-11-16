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
package com.vitorpamplona.amethyst.commons.ui.note.types.dlists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skiko.MainUIDispatcher
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders real Decentralized List items (fetched from wss://dcosl.brainstorm.world on 2026-10-15;
 * `resources/dlists/events.jsonl`) offscreen through [RenderDListItem], in each tier, into
 * `commonsUI/build/dlist-tiers/`:
 *
 * - tier three on the item's page: a GitHub account, a food and drink place, a book;
 * - tier two in a feed: the same items as cards, read the custom view's way among many;
 * - tier one: an item whose header nobody can read.
 *
 * The reader (the preview account) holds their own copy of GitHub Accounts, Brainstorm's, which
 * carries the display hints and the GitHub profile URL template, so the GitHub page has its buttons.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DListTiersRenderTest {
    private val outDir = File("build/dlist-tiers").apply { mkdirs() }

    /**
     * The preview account. Its pubkey is not the one its private key derives, so nothing can sign as
     * it: the reader's own events are built here and handed to the cache as already verified.
     */
    private val reader = "989c3734c46abac7ce3ce229971581a5a6ee39cdd6aa7261a55823fa7f8c4799"

    private fun readerEvent(
        idSeed: Char,
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
    ): Event = EventFactory.create<Event>(idSeed.toString().repeat(64), reader, createdAt, kind, tags, "", "0".repeat(128))

    private val githubCommunity = "39998:b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450:github-accounts"

    /** LocalCache holds notes weakly: the fixtures are kept here so they outlive a collection. */
    private val held = ArrayList<Note>()

    private fun consume(
        event: Event,
        wasVerified: Boolean = false,
    ) {
        val note = if (event is AddressableEvent) LocalCache.getOrCreateAddressableNote(event.address()) else LocalCache.getOrCreateNote(event.id)
        held += note
        // False for an event an earlier test already put there: what counts is that the note has it.
        LocalCache.justConsume(event, null, wasVerified)
        assertTrue(note.event != null, "the cache refused ${event.kind}:${event.id}")
    }

    /**
     * The cards collect with `collectAsStateWithLifecycle`, which runs on `Dispatchers.Main`, and
     * this module's tests have none: without one, every collection throws in the background and
     * kotlinx-coroutines-test pins the exceptions on the next `runTest` in the same JVM.
     */
    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @BeforeTest
    fun fill() {
        javaClass
            .getResource("/dlists/events.jsonl")!!
            .readText()
            .lines()
            .filter { it.isNotBlank() }
            .forEach { consume(Event.fromJson(it)) }

        // Brainstorm's copy of GitHub Accounts, as the reader's own.
        val copy =
            readerEvent(
                'c',
                1790900700,
                39998,
                arrayOf(
                    arrayOf("d", "github-accounts"),
                    arrayOf("names", "GitHub Account", "GitHub Accounts"),
                    arrayOf("description", "A list of github handles/accounts"),
                    arrayOf("required", "github-username"),
                    arrayOf("field-type", "github-username", "text"),
                    arrayOf("optional", "description"),
                    arrayOf("display", "title", "description"),
                    arrayOf("display", "summary", "github-username"),
                    arrayOf("image", "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png"),
                    arrayOf("link", "f19f39daf75388ff0f19cc37dde5ae1b90124b0d60e35659160c48dc690deca4", "wss://dcosl.brainstorm.world", "username", "github-username"),
                    arrayOf("b", githubCommunity, "pointer"),
                ),
            )
        consume(copy, wasVerified = true)

        // An item filed under a header no relay has: tier one.
        val orphan =
            readerEvent(
                'd',
                1790900800,
                39999,
                arrayOf(
                    arrayOf("d", "duty-menu"),
                    arrayOf("z", "39998:15f7dafc4624b1e6b00ab7f863de1a53b71967528070ec7d1837c7a40c1c7270:duty-menu"),
                    arrayOf("duty", "30382", "wss://tapestry.brainstorm.world/relay"),
                    arrayOf("duty", "30392", "wss://tapestry.brainstorm.world/relay"),
                    arrayOf("schedule", "daily"),
                ),
            )
        consume(orphan, wasVerified = true)
    }

    private val github = Address(39999, "2efaa715bbb46dd5be6b7da8d7700266d11674b913b8178addb5c2e63d987331", "vitorpamplona-ni5x31")
    private val place = Address(39999, "4bded2172075221ead393a0baec9c530238ec192c2a9cdbbc7754ba8c3357b64", "osm-way-996743150")
    private val book = Address(39999, "6aca05b812da97601151776d13de04ae71afc9d86da1408f0e72cffef72ece4b", "ol-ol19722168w")
    private val orphan = Address(39999, "989c3734c46abac7ce3ce229971581a5a6ee39cdd6aa7261a55823fa7f8c4799", "duty-menu")

    @Test
    fun tierThreePages() {
        render("3-github-page", github, isPage = true, heightDp = 360)
        render("3-place-page", place, isPage = true, heightDp = 900)
        render("3-book-page", book, isPage = true, heightDp = 520)
    }

    @Test
    fun tierTwoCards() {
        render("2-github-card", github, isPage = false, heightDp = 300)
        render("2-place-card", place, isPage = false, heightDp = 420)
        render("2-book-card", book, isPage = false, heightDp = 360)
    }

    @Test
    fun tierOneRaw() {
        render("1-unknown-header", orphan, isPage = false, heightDp = 300)
    }

    private fun render(
        name: String,
        address: Address,
        isPage: Boolean,
        heightDp: Int,
    ) {
        val note = LocalCache.getOrCreateAddressableNote(address)
        assertTrue(note.event != null, "$name: the fixture is not in the cache")
        val nav = mockk<INav>(relaxed = true)
        val density = 2f
        val scene =
            onUiThread {
                ImageComposeScene(
                    width = (900 * density).toInt(),
                    height = (heightDp * density).toInt(),
                    density = Density(density),
                    coroutineContext = MainUIDispatcher,
                ) {
                    ThemeComparisonRow {
                        val background = remember { mutableStateOf(Color.Transparent) }
                        Column(Modifier.padding(12.dp).fillMaxWidth()) {
                            RenderDListItem(note, 3, background, mockAccountViewModel(), nav, isPage = isPage)
                        }
                    }
                }
            }
        try {
            var image = onUiThread { scene.render(0) }
            repeat(SETTLE_FRAMES) { frame ->
                Thread.sleep(FRAME_MILLIS)
                image = onUiThread { scene.render((frame + 1) * FRAME_MILLIS * 1_000_000L) }
            }
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("could not encode")
            File(outDir, "$name.png").writeBytes(png.bytes)

            val pixels = image.peekPixels() ?: error("no pixels")
            val distinct = HashSet<Int>()
            for (y in 0 until image.height step 7) for (x in 0 until image.width step 7) distinct += pixels.getColor(x, y)
            assertTrue(distinct.size > 20, "$name rendered almost nothing (${distinct.size} colours)")
        } finally {
            onUiThread { scene.close() }
        }
    }

    /**
     * Runs [block] on the AWT event thread. Compose desktop assumes that thread owns every scene:
     * RectManager's throttled layout callbacks are posted there (`postDelayed` on skiko's
     * [MainUIDispatcher]) and reorder its rect list. Driving the scene from the test thread let
     * such a callback run in the middle of `render` or `close`, and `close` then failed with
     * "LayoutNode … not found in RectList". Frames still sleep on the test thread, so the callbacks
     * run between frames, never during one.
     */
    private fun <T> onUiThread(block: () -> T): T = runBlocking(MainUIDispatcher) { block() }

    private companion object {
        const val SETTLE_FRAMES = 12
        const val FRAME_MILLIS = 80L
    }
}
