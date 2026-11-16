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

import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip92IMeta.imetasByUrl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CartLinksTest {
    private val front = "https://example.com/front.png"
    private val main = "https://example.com/main.png"
    private val mainX = "a".repeat(64)

    private fun imetas(vararg tags: Array<String>) = arrayOf(*tags).imetasByUrl()

    private fun imeta(
        url: String,
        vararg fields: String,
    ) = arrayOf("imeta", "url $url", *fields)

    private fun links(vararg tags: Array<String>) = CartLinks.forEntry(front, imetas(*tags))

    private fun hash(bytes: ByteArray) = sha256(bytes).toHexKey()

    @Test
    fun anOrdinaryNoteHasNoLinks() {
        assertNull(links(imeta(front, "m image/png", "x $mainX"), imeta(main, "m image/png", "x $mainX")))
        assertNull(CartLinks.forEntry(front, emptyMap()))
    }

    @Test
    fun aParsedNoteKeepsItsTagsOnlyWhenTheyNameACart() {
        val ordinary = imetas(imeta(front, "m image/png"))
        val game = imetas(imeta(front, "cart #game"))

        assertNull(CartLinks.tagsToKeep(ordinary))
        assertNull(CartLinks.tagsToKeep(emptyMap()))
        assertSame(game, CartLinks.tagsToKeep(game))
    }

    @Test
    fun anExtraCartIsReadWithItsNameUrlAndHash() {
        val links = assertNotNull(links(imeta(front, "cart #game"), imeta(main, "x $mainX", "cart #game_main-7")))

        assertEquals(listOf("#game"), links.ownNames)
        val extra = links.extras.single()
        assertEquals(listOf("#game_main-7"), extra.names)
        assertEquals(listOf(main), extra.urls)
        assertEquals(mainX, extra.sha256)
    }

    @Test
    fun theEntryNeedsNoNameOfItsOwn() {
        val links = assertNotNull(links(imeta(front), imeta(main, "x $mainX", "cart #main")))

        assertTrue(links.ownNames.isEmpty())
        assertEquals(1, links.extras.size)
    }

    @Test
    fun theEntryNeedsNoImeta() {
        assertEquals(1, links(imeta(main, "x $mainX", "cart #main"))?.extras?.size)
    }

    @Test
    fun aNameIsKeptExactlyAsWritten() {
        val name = "#Game_Main-7 with spaces"
        assertEquals(listOf(name), links(imeta(main, "x $mainX", "cart $name"))?.extras?.single()?.names)
    }

    @Test
    fun anExtraWithoutAHashIsIgnored() {
        assertNull(links(imeta(main, "cart #main")))
    }

    @Test
    fun anExtraWithoutANameIsNotACart() {
        assertTrue(links(imeta(front, "cart #game"), imeta(main, "x $mainX"))!!.extras.isEmpty())
    }

    @Test
    fun aCartFieldWithNoValueNamesNothing() {
        assertNull(links(imeta(main, "x $mainX", "cart")))
        assertNull(links(imeta(front, "cart")))
    }

    @Test
    fun aHashThatIsNotOneIsIgnored() {
        assertNull(links(imeta(main, "x abc", "cart #main")))
        assertNull(links(imeta(main, "x ${"a".repeat(63)}", "cart #main")))
        assertNull(links(imeta(main, "x ${"a".repeat(65)}", "cart #main")))
        assertNull(links(imeta(main, "x ${"g".repeat(64)}", "cart #main")))
    }

    @Test
    fun anUppercaseHashIsTheSameHash() {
        assertEquals(mainX, links(imeta(main, "x ${mainX.uppercase()}", "cart #main"))?.extras?.single()?.sha256)
    }

    @Test
    fun theCartFieldRepeatsForAGameThatUsesTwoSpellings() {
        val links = links(imeta(front, "cart #game", "cart #game-7"), imeta(main, "x $mainX", "cart #main", "cart main.p8"))

        assertEquals(listOf("#game", "#game-7"), links?.ownNames)
        assertEquals(listOf("#main", "main.p8"), links?.extras?.single()?.names)
    }

    @Test
    fun fallbacksComeAfterTheUrlInOrder() {
        val links = links(imeta(main, "x $mainX", "cart #main", "fallback https://a.example/1", "fallback https://b.example/2"))

        assertEquals(listOf(main, "https://a.example/1", "https://b.example/2"), links?.extras?.single()?.urls)
    }

    @Test
    fun aCartHasAtMostThreeUrls() {
        val fallbacks = Array(5) { "fallback https://a.example/$it" }
        val links = links(imeta(main, "x $mainX", "cart #main", *fallbacks))

        assertEquals(listOf(main, "https://a.example/0", "https://a.example/1"), links?.extras?.single()?.urls)
    }

    @Test
    fun aNoteHasAtMostSixteenExtraCarts() {
        val tags = Array(20) { imeta("https://example.com/$it.png", "x $mainX", "cart #$it") }
        val extras = links(*tags)!!.extras

        assertEquals(CartLinks.MAX_EXTRA_CARTS, extras.size)
        assertEquals(listOf("#15"), extras.last().names)
    }

    @Test
    fun ignoredEntriesDoNotCountTowardsTheLimit() {
        val bad = Array(20) { imeta("https://example.com/bad$it.png", "x nope", "cart #bad$it") }
        val good = imeta(main, "x $mainX", "cart #main")

        assertEquals(1, links(*bad, good)?.extras?.size)
    }

    @Test
    fun theEntryIsNotItsOwnExtra() {
        val links = links(imeta(front, "x $mainX", "cart #game"))

        assertEquals(listOf("#game"), links?.ownNames)
        assertTrue(links!!.extras.isEmpty())
    }

    @Test
    fun twoCartsInTheTextEachReachTheOther() {
        val otherX = "b".repeat(64)
        val tags = imetas(imeta(front, "x $otherX", "cart #a"), imeta(main, "x $mainX", "cart #b"))

        val first = CartLinks.forEntry(front, tags)
        val second = CartLinks.forEntry(main, tags)

        assertEquals(listOf("#a"), first?.ownNames)
        assertEquals(listOf("#b"), first?.extras?.single()?.names)
        assertEquals(listOf("#b"), second?.ownNames)
        assertEquals(listOf("#a"), second?.extras?.single()?.names)
        assertEquals(otherX, second?.extras?.single()?.sha256)
    }

    @Test
    fun theFirstCartToClaimANameKeepsIt() {
        val links =
            links(
                imeta(main, "x $mainX", "cart #shared", "cart #first"),
                imeta("https://example.com/other.png", "x ${"b".repeat(64)}", "cart #shared", "cart #second"),
            )

        assertEquals(listOf("#shared", "#first"), links?.extras?.get(0)?.names)
        assertEquals(listOf("#second"), links?.extras?.get(1)?.names)
    }

    @Test
    fun theEntrysOwnNamesBeatTheExtras() {
        val links = links(imeta(main, "x $mainX", "cart #game", "cart #main"), imeta(front, "cart #game"))

        assertEquals(listOf("#game"), links?.ownNames)
        assertEquals(listOf("#main"), links?.extras?.single()?.names)
    }

    @Test
    fun anExtraLeftWithNoNameIsDropped() {
        val links = links(imeta(front, "cart #game"), imeta(main, "x $mainX", "cart #game"))

        assertTrue(links!!.extras.isEmpty())
    }

    @Test
    fun anInvalidEntryCannotTakeAName() {
        val links = links(imeta(main, "x bad", "cart #main"), imeta("https://example.com/other.png", "x $mainX", "cart #main"))

        assertEquals(
            "https://example.com/other.png",
            links
                ?.extras
                ?.single()
                ?.urls
                ?.single(),
        )
    }

    @Test
    fun aDifferentSetOfCartsIsADifferentFingerprint() {
        val one = links(imeta(front, "cart #game"), imeta(main, "x $mainX", "cart #main"))!!
        val otherCart = links(imeta(front, "cart #game"), imeta(main, "x ${"b".repeat(64)}", "cart #main"))!!
        val otherName = links(imeta(front, "cart #game"), imeta(main, "x $mainX", "cart #other"))!!
        val otherOwnName = links(imeta(front, "cart #mine"), imeta(main, "x $mainX", "cart #main"))!!

        assertNotEquals(one.fingerprint(), otherCart.fingerprint())
        assertNotEquals(one.fingerprint(), otherName.fingerprint())
        assertNotEquals(one.fingerprint(), otherOwnName.fingerprint())
    }

    @Test
    fun anOwnNameCannotImitateTwoOwnNames() {
        val two = CartLinks(listOf("a", "b"), emptyList())
        val one = CartLinks(listOf("a\nb"), emptyList())

        assertNotEquals(two.fingerprint(), one.fingerprint())
    }

    @Test
    fun anOwnNameCannotImitateAnExtraCart() {
        val extra = ExtraCart(listOf("b"), listOf(main), mainX)
        val honest = CartLinks(listOf("a"), listOf(extra))
        val forged = CartLinks(listOf("a", "1:b $mainX"), emptyList())

        assertNotEquals(honest.fingerprint(), forged.fingerprint())
    }

    @Test
    fun theOrderOfItsOwnNamesDoesNotChangeTheFingerprint() {
        val second = imeta(front, "cart #b", "cart #a")
        val first = imeta(front, "cart #a", "cart #b")

        assertEquals(links(first)?.fingerprint(), links(second)?.fingerprint())
    }

    // Two carts uploaded with no `cart` field: the game's names can't say which file they mean, but
    // with two carts they don't have to.

    private val plain = arrayOf("m image/png", "dim 160x205")

    private fun plainCart(
        url: String,
        x: String,
    ) = imeta(url, "x $x", *plain)

    private val frontX = "b".repeat(64)
    private val thirdX = "c".repeat(64)

    private fun plainPair() = imetas(plainCart(front, frontX), plainCart(main, mainX))

    @Test
    fun twoPlainCartsAreEachOthersPartner() {
        val tags = plainPair()

        assertEquals(mainX, CartLinks.forEntry(front, tags)?.partner?.sha256)
        assertEquals(frontX, CartLinks.forEntry(main, tags)?.partner?.sha256)
        assertEquals(listOf(main), CartLinks.forEntry(front, tags)?.partner?.urls)
    }

    @Test
    fun aParsedNoteKeepsTheTagsOfExactlyTwoPlainCarts() {
        assertNotNull(CartLinks.tagsToKeep(plainPair()))
        assertNull(CartLinks.tagsToKeep(imetas(plainCart(front, frontX), imeta(main, "m image/png"))))
        assertNull(CartLinks.tagsToKeep(imetas(plainCart(front, frontX), plainCart(main, mainX), plainCart("https://example.com/third.png", thirdX))))
    }

    // Primal writes sizes as floats, and the Play button already reads them through DimensionTag.
    @Test
    fun aSizeWrittenAsFloatsIsStillACart() {
        val float = arrayOf("m image/png", "dim 160.0x205.0")
        val tags = imetas(imeta(front, "x $frontX", *float), imeta(main, "x $mainX", *float))

        assertEquals(mainX, CartLinks.forEntry(front, tags)?.partner?.sha256)
    }

    @Test
    fun aSizeThatIsNotOneIsNotACart() {
        val tags = imetas(plainCart(front, frontX), imeta(main, "x $mainX", "dim nonsense"))

        assertNull(CartLinks.tagsToKeep(tags))
    }

    @Test
    fun aHashWithACharOutsideLatinIsNotAHash() {
        val tags = imetas(plainCart(front, frontX), imeta(main, "x \u20ac${"a".repeat(63)}", *plain))

        assertNull(CartLinks.forEntry(front, tags))
    }

    @Test
    fun aPlainCartNeedsNoMimeType() {
        val tags = imetas(imeta(front, "x $frontX", "dim 160x205"), imeta(main, "x $mainX", "dim 160x205"))

        assertEquals(mainX, CartLinks.forEntry(front, tags)?.partner?.sha256)
    }

    // A tag's URLs become one entry each, so this reads as two carts with one hash and one more.
    @Test
    fun aCartListedAtTwoUrlsInOneTagTurnsThePairingOff() {
        val mirrored = arrayOf("imeta", "url $front", "url https://mirror.example/front.png", "x $frontX", *plain)

        assertNull(CartLinks.forEntry(front, imetas(mirrored, plainCart(main, mainX))))
    }

    @Test
    fun oneUrlInTwoTagsIsOneCart() {
        assertNull(CartLinks.forEntry(front, imetas(plainCart(front, frontX), plainCart(front, mainX))))
    }

    // A stranger posts a game's front cart beside a hostile image, through the real reading of the tags.
    @Test
    fun aPairNoteWithAnotherSecondCartIsAnotherGame() {
        val game = CartLinks.forEntry(front, plainPair())!!.saveKey(frontX)
        val hostile = CartLinks.forEntry(front, imetas(plainCart(front, frontX), plainCart(main, thirdX)))!!.saveKey(frontX)

        assertNotEquals(game, hostile)
        assertNotEquals(frontX, hostile)
    }

    @Test
    fun anUppercaseHashStillPairs() {
        val tags = imetas(plainCart(front, frontX), plainCart(main, mainX.uppercase()))

        assertEquals(mainX, CartLinks.forEntry(front, tags)?.partner?.sha256)
    }

    @Test
    fun aPlainCartNeedsItsShapeAndAHash() {
        assertNull(CartLinks.forEntry(front, imetas(plainCart(front, frontX), imeta(main, "x $mainX", "m image/png", "dim 100x100"))))
        assertNull(CartLinks.forEntry(front, imetas(plainCart(front, frontX), imeta(main, "x $mainX", "m image/jpeg", "dim 160x205"))))
        assertNull(CartLinks.forEntry(front, imetas(plainCart(front, frontX), imeta(main, *plain))))
        assertNull(CartLinks.forEntry(front, imetas(plainCart(front, frontX), imeta(main, "x nope", *plain))))
    }

    @Test
    fun threePlainCartsCannotBeToldApart() {
        val tags = imetas(plainCart(front, frontX), plainCart(main, mainX), plainCart("https://example.com/third.png", thirdX))

        assertNull(CartLinks.forEntry(front, tags))
    }

    @Test
    fun anImageBesideTwoPlainCartsIsNotPartOfThem() {
        assertNull(CartLinks.forEntry("https://example.com/photo.png", plainPair()))
    }

    @Test
    fun aPhotoInTheNoteDoesNotCountAsACart() {
        val tags = imetas(plainCart(front, frontX), plainCart(main, mainX), imeta("https://example.com/photo.jpg", "x $thirdX", "m image/jpeg", "dim 900x600"))

        assertEquals(mainX, CartLinks.forEntry(front, tags)?.partner?.sha256)
    }

    @Test
    fun oneFileFromTwoServersIsOneCart() {
        assertNull(CartLinks.forEntry(front, imetas(plainCart(front, mainX), plainCart(main, mainX))))
    }

    @Test
    fun aCartFieldAnywhereTurnsThePairingOff() {
        val links = links(plainCart(front, frontX), plainCart(main, mainX), imeta("https://example.com/third.png", "x $thirdX", "cart #third"))

        assertNull(links?.partner)
        assertEquals(listOf("#third"), links?.extras?.single()?.names)
    }

    @Test
    fun aDifferentPartnerIsADifferentFingerprint() {
        val one = CartLinks.forEntry(front, plainPair())!!
        val other = CartLinks.forEntry(front, imetas(plainCart(front, frontX), plainCart(main, thirdX)))!!

        assertNotEquals(one.fingerprint(), other.fingerprint())
    }

    private fun bytesPair() = imetas(plainCart(front, hash(entryBytes)), plainCart(main, hash(mainBytes)))

    private suspend fun frontCarts(fetched: ByteArray = mainBytes) = CartLinks.forEntry(front, bytesPair())!!.fetchCarts(entryBytes) { fetched }

    // Into Ruins: the jump asks from the front cart, and game over asks from the main cart.
    @Test
    fun aGameGoesToTheOtherCartAndComesBackForEveryRun() =
        runTest {
            val carts = frontCarts()

            repeat(3) {
                assertContentEquals(mainBytes, carts["#intoruins_main-7"])
                assertContentEquals(entryBytes, carts["#intoruins"])
            }
        }

    @Test
    fun aGameStartedOnItsSecondCartAlsoFindsTheOther() =
        runTest {
            val carts = CartLinks.forEntry(main, bytesPair())!!.fetchCarts(mainBytes) { entryBytes }

            assertContentEquals(entryBytes, carts["#intoruins"])
            assertContentEquals(mainBytes, carts["#intoruins_main-7"])
        }

    @Test
    fun aThirdNameHasNoCart() =
        runTest {
            val carts = frontCarts()
            carts["#one"]
            carts["#two"]

            assertNull(carts["#three"])
        }

    @Test
    fun aPartnerThatDoesNotMatchItsHashIsNotAvailable() =
        runTest {
            val carts = frontCarts(fetched = "hostile".encodeToByteArray())

            assertNull(carts["#intoruins_main-7"])
            assertNull(carts["#intoruins"])
        }

    // The download step: everything here runs with a fake fetch, so no test touches the network.

    private val entryBytes = "front".encodeToByteArray()
    private val mainBytes = "main".encodeToByteArray()

    private fun linksTo(
        bytes: ByteArray,
        vararg extra: String,
    ) = links(imeta(front, "cart #game"), imeta(main, "x ${hash(bytes)}", "cart #main", *extra))!!

    @Test
    fun theEntrysNamesAnswerWithItsBytes() =
        runTest {
            val carts = linksTo(mainBytes).fetchCarts(entryBytes) { mainBytes }

            assertContentEquals(entryBytes, carts["#game"])
        }

    @Test
    fun everyNameOfACartAnswersWithIt() =
        runTest {
            val carts = linksTo(mainBytes, "cart main.p8").fetchCarts(entryBytes) { mainBytes }

            assertContentEquals(mainBytes, carts["#main"])
            assertContentEquals(mainBytes, carts["main.p8"])
        }

    @Test
    fun aCartThatDoesNotMatchItsHashIsNotAvailable() =
        runTest {
            val carts = linksTo(mainBytes).fetchCarts(entryBytes) { "hostile".encodeToByteArray() }

            assertNull(carts["#main"])
            assertContentEquals(entryBytes, carts["#game"])
        }

    @Test
    fun aFailingCartDoesNotTakeAWorkingOneDown() =
        runTest {
            val otherBytes = "other".encodeToByteArray()
            val other = "https://example.com/other.png"
            val links =
                links(
                    imeta(main, "x ${hash(mainBytes)}", "cart #main"),
                    imeta(other, "x ${hash(otherBytes)}", "cart #other"),
                )!!

            val carts = links.fetchCarts(entryBytes) { url -> if (url == other) otherBytes else null }

            assertNull(carts["#main"])
            assertContentEquals(otherBytes, carts["#other"])
        }

    @Test
    fun aFallbackAnswersWhenTheUrlFails() =
        runTest {
            val fallback = "https://b.example/main.png"
            val asked = ArrayList<String>()

            val carts =
                linksTo(mainBytes, "fallback $fallback").fetchCarts(entryBytes) { url ->
                    asked.add(url)
                    if (url == fallback) mainBytes else null
                }

            assertContentEquals(mainBytes, carts["#main"])
            assertEquals(listOf(main, fallback), asked)
        }

    @Test
    fun aFallbackAnswersWhenTheUrlServesTheWrongFile() =
        runTest {
            val fallback = "https://b.example/main.png"

            val carts = linksTo(mainBytes, "fallback $fallback").fetchCarts(entryBytes) { url -> if (url == fallback) mainBytes else entryBytes }

            assertContentEquals(mainBytes, carts["#main"])
        }

    @Test
    fun noFallbackIsAskedOnceACartIsHere() =
        runTest {
            val asked = ArrayList<String>()

            linksTo(mainBytes, "fallback https://b.example/main.png").fetchCarts(entryBytes) { url ->
                asked.add(url)
                mainBytes
            }

            assertEquals(listOf(main), asked)
        }

    @Test
    fun aFetchThatThrowsIsACartNotAvailable() =
        runTest {
            val carts = linksTo(mainBytes).fetchCarts(entryBytes) { error("the network went away") }

            assertNull(carts["#main"])
            assertContentEquals(entryBytes, carts["#game"])
        }

    @Test
    fun aThrowingFetchStillTriesTheFallback() =
        runTest {
            val fallback = "https://b.example/main.png"

            val carts =
                linksTo(mainBytes, "fallback $fallback").fetchCarts(entryBytes) { url ->
                    if (url == fallback) mainBytes else error("no route to host")
                }

            assertContentEquals(mainBytes, carts["#main"])
        }

    @Test
    fun aFetchThatTimesOutOnItsOwnTriesTheFallback() =
        runTest {
            val fallback = "https://b.example/main.png"

            val carts =
                linksTo(mainBytes, "fallback $fallback").fetchCarts(entryBytes) { url ->
                    if (url == fallback) mainBytes else withTimeout(1) { awaitCancellation() }
                }

            assertContentEquals(mainBytes, carts["#main"])
        }

    @Test
    fun closingThePlayerStopsTheFetchInsteadOfTryingTheFallback() =
        runTest {
            val asked = ArrayList<String>()
            val fetching = CompletableDeferred<Unit>()
            var finished = false

            val load =
                launch {
                    linksTo(mainBytes, "fallback https://b.example/main.png").fetchCarts(entryBytes) { url ->
                        asked.add(url)
                        fetching.complete(Unit)
                        awaitCancellation()
                    }
                    finished = true
                }
            fetching.await()
            load.cancelAndJoin()
            runCurrent()

            assertEquals(listOf(main), asked)
            assertFalse(finished)
        }
}
