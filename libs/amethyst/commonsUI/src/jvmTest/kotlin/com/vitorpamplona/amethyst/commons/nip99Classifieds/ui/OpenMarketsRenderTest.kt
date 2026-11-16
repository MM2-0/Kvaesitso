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
package com.vitorpamplona.amethyst.commons.nip99Classifieds.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderClassifieds
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
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
 * Renders real Open Markets events (fetched from wss://conduit-congee.fly.dev and
 * wss://relay.plebeian.market on 2026-10-16; `resources/openMarkets/events.jsonl`) offscreen into
 * `commonsUI/build/open-markets/`:
 *
 * - Conduit's "Chicago V4V Summit" collection, whose products are all `visibility: hidden` and
 *   reached only through it, as a feed card and as a page, and its pickup option;
 * - Plebeian's "Feng Shui Objects" collection with two shipping options;
 * - a shipping option with ten countries, and a product review whose product is sold;
 * - a listing with a stock count, specs and a shipping reference that adds an extra cost, as its
 *   card and as the Open Markets part of its page, and a hidden listing's card ("Unlisted").
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenMarketsRenderTest {
    private val outDir = File("build/open-markets").apply { mkdirs() }

    /** LocalCache holds notes weakly: the fixtures are kept here so they outlive a collection. */
    private val held = ArrayList<Note>()

    private fun consume(event: Event) {
        val note = if (event is AddressableEvent) LocalCache.getOrCreateAddressableNote(event.address()) else LocalCache.getOrCreateNote(event.id)
        held += note
        // False for an event an earlier test already put there: what counts is that the note has it.
        LocalCache.justConsume(event, null, false)
        assertTrue(note.event != null, "the cache refused ${event.kind}:${event.id}")
    }

    /** The cards collect on `Dispatchers.Main`, which this module's tests do not have. */
    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @BeforeTest
    fun fill() {
        javaClass
            .getResource("/openMarkets/events.jsonl")!!
            .readText()
            .lines()
            .filter { it.isNotBlank() }
            .forEach { consume(Event.fromJson(it)) }
    }

    private val conduit = "ae953321ed511c0059b69a55b2fde8c0ceb994896261659eaee394ecb3c67a97"
    private val pival = "7165b7803e8edd74cd52d1864d1b8e0f6ecc8664dec5ab40e19944bab9bf1e6e"
    private val author = "cec4b2b2175f88d2eccbc82006455cfe1befb2f1f3fc22fd54c83e31db0e70ed"

    private val chicago = Address(30405, conduit, "chicago-v4v-summit-41ed40a4-market")
    private val fengShui = Address(30405, pival, "3aea62b5-132c-436d-a9b5-1a1e7135d1e3")
    private val pickup = Address(30406, conduit, "chicago-v4v-summit-41ed40a4-pickup")
    private val asiaPacific = Address(30406, author, "shipping_1792177458598_e95ep")
    private val review = Address(31555, "07041e3c0e886aa67462ee75cf9eba4e499c9696dfef65b5e0c6d6129548823b", "a:30402:f94f8648aacec9880ba667fb422f886d77fa67ccbe538645b1e21d5d580e214e:40b1defe72c862ccf4d4ca19f7a91dfedbf09d4dd924dc194fd838c02c21b42e")
    private val handbook = Address(30402, author, "product_1792176985739_616he")

    @Test
    fun collections() {
        render("collection-chicago-card", chicago, heightDp = 900) { note, avm, nav -> RenderProductCollection(note, false, avm, nav) }
        render("collection-chicago-page", chicago, heightDp = 1500) { note, avm, nav -> RenderProductCollection(note, false, avm, nav, isPage = true) }
        render("collection-feng-shui-page", fengShui, heightDp = 1300) { note, avm, nav -> RenderProductCollection(note, false, avm, nav, isPage = true) }
    }

    @Test
    fun shippingOptions() {
        render("shipping-asia-pacific", asiaPacific, heightDp = 360) { note, avm, nav -> RenderShippingOption(note, false, avm, nav) }
        render("shipping-event-pickup", pickup, heightDp = 360) { note, avm, nav -> RenderShippingOption(note, false, avm, nav) }
    }

    @Test
    fun productReview() {
        render("review", review, heightDp = 360) { note, avm, nav ->
            val background = remember { mutableStateOf(Color.Transparent) }
            RenderProductReview(note, false, true, 3, background, avm, nav)
        }
    }

    @Test
    fun listings() {
        render("listing-handbook-card", handbook, heightDp = 520) { note, avm, nav -> RenderClassifieds(note.event as ClassifiedsEvent, note, avm, nav) }
        render("listing-handbook-details", handbook, heightDp = 520) { note, avm, nav -> ClassifiedsOpenMarketsDetails(note.event as ClassifiedsEvent, avm, nav) }
        val hidden =
            LocalCache
                .getOrCreateAddressableNote(chicago)
                .event!!
                .tags
                .first { it[0] == "a" && it[1].startsWith("30402:") }[1]
        render("listing-unlisted-card", Address.parse(hidden)!!, heightDp = 520) { note, avm, nav -> RenderClassifieds(note.event as ClassifiedsEvent, note, avm, nav) }
    }

    private fun render(
        name: String,
        address: Address,
        heightDp: Int,
        content: @Composable (Note, AccountViewModel, INav) -> Unit,
    ) {
        val note = LocalCache.getOrCreateAddressableNote(address)
        assertTrue(note.event != null, "$name: the fixture is not in the cache")
        val nav = mockk<INav>(relaxed = true)
        val density = 2f
        val scene =
            ImageComposeScene(width = (900 * density).toInt(), height = (heightDp * density).toInt(), density = Density(density)) {
                ThemeComparisonRow {
                    Column(Modifier.padding(12.dp).fillMaxWidth()) {
                        content(note, mockAccountViewModel(), nav)
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
            File(outDir, "$name.png").writeBytes(png.bytes)

            val pixels = image.peekPixels() ?: error("no pixels")
            val distinct = HashSet<Int>()
            for (y in 0 until image.height step 7) for (x in 0 until image.width step 7) distinct += pixels.getColor(x, y)
            assertTrue(distinct.size > 20, "$name rendered almost nothing (${distinct.size} colours)")
        } finally {
            scene.close()
        }
    }

    private companion object {
        const val SETTLE_FRAMES = 12
        const val FRAME_MILLIS = 80L
    }
}
