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
package com.vitorpamplona.amethyst.commons.cartridges

import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CartSaveIsolationTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store() = AppPreferenceStores(rootFilesDir = { folder.root.absolutePath.toPath() }).cartSaveStore()

    private fun values(n: Int) = IntArray(CartSaveStore.VALUES) { n }

    // A joke cart that names a popular game's save id must not wipe that game's high scores.
    @Test
    fun aCartCannotReadOrOverwriteAnotherCartsSave() =
        runBlocking {
            val store = store()
            val game = store.forCart { "aa".repeat(32) }
            val joke = store.forCart { "bb".repeat(32) }
            game.save(mapOf("celeste_1" to values(42)))

            assertNull(joke.loadAll()["celeste_1"])
            joke.save(mapOf("celeste_1" to values(0)))

            assertEquals(42, game.loadAll().getValue("celeste_1")[0])
        }

    @Test
    fun theSameFilePostedTwiceSharesItsSaves() =
        runBlocking {
            val store = store()
            store.forCart { "AA".repeat(32) }.save(mapOf("s" to values(7)))

            assertEquals(7, store.forCart { "aa".repeat(32) }.loadAll().getValue("s")[0])
        }

    private val entry = "ee".repeat(32)

    private fun extra(
        name: String,
        sha256: String,
    ) = ExtraCart(listOf(name), listOf("https://example.com/$name.png"), sha256)

    private fun linksTo(vararg extras: ExtraCart) = CartLinks(listOf("#game"), extras.toList())

    // A stranger's note reuses a popular game's front cart and lists a hostile "second cart" under
    // the name that game loads.
    @Test
    fun aHostileSecondCartCannotReachTheSavesOfTheGameItsFrontCartBelongsTo() =
        runBlocking {
            val store = store()
            val game = linksTo(extra("#main", "aa".repeat(32)))
            val hostile = linksTo(extra("#main", "bb".repeat(32)))
            store.forCart { game.saveKey(entry) }.save(mapOf("s" to values(42)))

            assertNull(store.forCart { hostile.saveKey(entry) }.loadAll()["s"])
            store.forCart { hostile.saveKey(entry) }.save(mapOf("s" to values(0)))

            assertEquals(42, store.forCart { game.saveKey(entry) }.loadAll().getValue("s")[0])
        }

    @Test
    fun theSameFrontCartWithDifferentExtrasIsADifferentGame() {
        val one = linksTo(extra("#main", "aa".repeat(32)))

        assertNotEquals(one.saveKey(entry), linksTo(extra("#other", "aa".repeat(32))).saveKey(entry))
        assertNotEquals(one.saveKey(entry), one.saveKey("ff".repeat(32)))
    }

    @Test
    fun theSameExtrasInAnotherOrderAreTheSameGame() {
        val a = extra("#a", "aa".repeat(32))
        val b = extra("#b", "bb".repeat(32))

        assertEquals(linksTo(a, b).saveKey(entry), linksTo(b, a).saveKey(entry))
    }

    @Test
    fun aSingleCartKeepsItsKeySoExistingSavesStayPut() {
        assertEquals(entry, CartLinks(listOf("#game"), emptyList()).saveKey(entry))
    }

    private fun partnerOf(sha256: String) = CartLinks(emptyList(), emptyList(), ExtraCart(emptyList(), listOf("https://example.com/other.png"), sha256))

    @Test
    fun twoPlainCartsShareOneKeyWhicheverWasStarted() {
        val other = "aa".repeat(32)

        assertEquals(partnerOf(other).saveKey(entry), partnerOf(entry).saveKey(other))
    }

    // The same attack with no names: a popular front cart posted beside a hostile image.
    @Test
    fun aHostilePartnerCannotReachTheSavesOfTheGameItsFrontCartBelongsTo() {
        val game = partnerOf("aa".repeat(32)).saveKey(entry)
        val hostile = partnerOf("bb".repeat(32)).saveKey(entry)

        assertNotEquals(game, hostile)
        assertNotEquals(entry, hostile)
    }

    // Names are a stranger's text: one with a line break must not read as two carts.
    @Test
    fun aNameCannotImitateTwoCarts() {
        val a = "aa".repeat(32)
        val b = "bb".repeat(32)
        val honest = linksTo(extra("#a", a), extra("#b", b))
        val forged = linksTo(extra("#a $a\n#b", b))

        assertNotEquals(honest.saveKey(entry), forged.saveKey(entry))
    }

    @Test
    fun nothingIsReadOrWrittenBeforeTheCartIsKnown() =
        runBlocking {
            val store = store()
            val unknown = store.forCart { null }
            unknown.save(mapOf("s" to values(1)))

            assertTrue(unknown.loadAll().isEmpty())
            assertTrue(store.forCart { "aa".repeat(32) }.loadAll().isEmpty())
        }

    @Test
    fun theStoreStopsGrowingAtItsCap() =
        runBlocking {
            val store = store()
            for (cart in 0 until 20) {
                store.save("cart$cart", (0 until 60).associate { "id$it" to values(it) })
            }
            val total = (0 until 20).sumOf { store.loadAll("cart$it").size }

            assertEquals(CartSaveStore.MAX_STORED_SAVES, total)
            // An id already kept is still written past the cap.
            store.save("cart0", mapOf("id0" to values(99)))
            assertEquals(99, store.loadAll("cart0").getValue("id0")[0])
        }

    @Test
    fun onlyTheMostRecentPlayersStayAlive() {
        val released = mutableListOf<String>()
        val live = LivePlayers<String>(2) { released += it }

        live.touch("a")
        live.touch("b")
        live.touch("a")
        live.touch("c")

        assertEquals(listOf("b"), released)
        assertEquals(2, live.size)
    }
}
