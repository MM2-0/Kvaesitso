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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.amethyst.commons.frontpage.Fixtures.ALICE
import com.vitorpamplona.amethyst.commons.frontpage.Fixtures.BOB
import com.vitorpamplona.amethyst.commons.frontpage.Fixtures.corpus
import com.vitorpamplona.amethyst.commons.frontpage.Fixtures.event
import com.vitorpamplona.amethyst.commons.frontpage.Fixtures.id
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Kitchen desk, and the tag facts the digest prints for calendar, classifieds and highlights. */
class KitchenAndTagFactsTest {
    private val kitchen = DESKS.first { it.key == "kitchen" }
    private val articles = DESKS.first { it.key == "articles" }

    private fun recipe(
        n: Int,
        vararg hashtags: String,
        d: String = "recipe-$n",
        content: String = "## Ingredients\n- 2 eggs\n\n\n## Steps\n1. Whisk.",
    ) = event(n, kind = 30023, content = content, tags = arrayOf(arrayOf("d", d), arrayOf("title", "Recipe $n")) + hashtags.map { arrayOf("t", it) })

    @Test
    fun theKitchenReadsBothTagsOverThirtyDays() {
        assertEquals(listOf(30023), kitchen.kinds)
        assertEquals(setOf("zapcooking", "nostrcooking"), kitchen.tags?.get("t")?.toSet())
        assertEquals(30L * 24 * 60 * 60, kitchen.lookbackSeconds)
        // Every other desk reads the fixed 24 hours.
        assertTrue(DESKS.filter { it.key != "kitchen" }.all { it.lookbackSeconds == FRONT_PAGE_WINDOW_SECONDS })
        val filter = deskFilter(kitchen.kinds, 1_000, 2_000, kitchen.limit, Fixtures.OBSERVER, DEFAULT_TRUST_FLOOR, kitchen.tags)
        assertEquals(kitchen.tags, filter.tags)
        assertTrue(filter.search!!.startsWith("observer:"))
    }

    @Test
    fun theKitchenLeavesOutTheMirrorAndLongReadsLeaveOutRecipes() {
        val community = recipe(1, "zapcooking")
        val older = recipe(2, "NostrCooking")
        val mirror = recipe(3, "zapcooking", "newsarchive", "nytcooking")
        val essay = recipe(4, "bitcoin")
        assertEquals(listOf(community, older), kitchen.belongs(Fixtures.OBSERVER, listOf(community, older, mirror, essay)))
        assertEquals(listOf(essay), articles.belongs(Fixtures.OBSERVER, listOf(community, older, mirror, essay)))
    }

    @Test
    fun aRecipeThatRanLatelyIsNotOfferedAgain() {
        val first = recipe(1, "zapcooking")
        val second = recipe(2, "zapcooking")
        assertEquals(first, pickRecipe(listOf(first, second), emptySet()))
        // An edit of the same recipe (a new id, the same address) has still run.
        val edited = recipe(9, "zapcooking", d = "recipe-1")
        assertEquals(second, pickRecipe(listOf(edited, second), setOf(recipeAddress(first))))
        assertNull(pickRecipe(listOf(first), setOf(recipeAddress(first))))
        val editions =
            listOf(
                SavedEdition("2026-11-12", "A", 0, "m", recipe = recipeAddress(first)),
                SavedEdition("2026-11-13", "B", 0, "m", recipe = recipeAddress(second)),
            )
        // Today's own reprint doesn't count against today's recipe.
        assertEquals(setOf(recipeAddress(first)), recentRecipes(editions, today = "2026-11-13"))
    }

    @Test
    fun theDigestGivesTheRecipeWholeWithItsCookAndPicture() {
        val dish =
            event(
                1,
                kind = 30023,
                content = "Grandma's bread.\n\n## Ingredients\n- 500 g flour\n- 10 g salt\n\n## Steps\n1. Knead.\n2. Bake at 220 °C.",
                tags = arrayOf(arrayOf("d", "bread"), arrayOf("title", "Grandma's bread"), arrayOf("t", "zapcooking"), arrayOf("image", "https://cdn.example/bread.jpg")),
            )
        val c = corpus(mapOf("kitchen" to listOf(dish)))
        assertEquals("https://cdn.example/bread.jpg", c.art.single().url)
        val text = digest(c)
        assertTrue(text.contains("## Kitchen (1)"))
        assertTrue(text.contains("over the last 30 days, not 24 hours"))
        assertTrue(text.contains("title: Grandma's bread"))
        assertTrue(text.contains("Alice Moreira"))
        assertTrue(text.contains("picture: ${c.art.single().id}"))
        assertTrue(text.contains("    - 500 g flour\n    - 10 g salt"))
        assertTrue(text.contains("2. Bake at 220 °C."))
        // The page that cites it ran it.
        // Cited the way the page cites a recipe now: by its naddr (a recipe is addressable).
        assertEquals("30023:$ALICE:bread", citedRecipe("<a href=\"${PaperLinkSet("https://brainstorm.world/e/", "https://brainstorm.world/p/").citation(dish)}\">Source</a>", c))
        assertEquals("30023:$ALICE:bread", citedRecipe("<a href=\"${PaperLinkSet.JUMBLE.citation(dish)}\">Source</a>", c))
        // And by its nevent, the way older pages did.
        assertEquals("30023:$ALICE:bread", citedRecipe("<a href=\"https://jumble.social/notes/${toNevent(dish.id)}\">Source</a>", c))
        assertNull(citedRecipe("<p>no recipe</p>", c))
        // The first picture in the text, when there is no image tag.
        assertEquals("https://cdn.example/a.png", firstImageIn("Look ![the loaf](https://cdn.example/a.png) and https://cdn.example/b.jpg"))
    }

    @Test
    fun calendarListingsSayWhenAndWhere() {
        val timed =
            event(
                1,
                kind = 31923,
                tags = arrayOf(arrayOf("d", "meetup"), arrayOf("start", "1796493600"), arrayOf("end", "1796500800"), arrayOf("location", "Café Nostr, Lisbon")),
            )
        val facts = tagFacts("calendar", timed, emptyMap())
        assertEquals("WHEN: Saturday, December 5, 2026, 18:00 to 20:00 (UTC)", facts[0])
        assertEquals("LOCATION: Café Nostr, Lisbon", facts[1])
        val allDay = event(2, kind = 31922, tags = arrayOf(arrayOf("d", "fest"), arrayOf("start", "2026-12-05"), arrayOf("end", "2026-12-07")))
        assertEquals(listOf("WHEN: Saturday, December 5, 2026 to Monday, December 7, 2026 (all day)"), tagFacts("calendar", allDay, emptyMap()))
        assertEquals(listOf("WHEN: no date given"), tagFacts("calendar", event(3, kind = 31923, tags = arrayOf(arrayOf("d", "x"))), emptyMap()))
    }

    @Test
    fun classifiedsSayPriceStatusAndCondition() {
        val listing = event(1, kind = 30402, tags = arrayOf(arrayOf("d", "mug"), arrayOf("price", "11.99", "USD"), arrayOf("status", "sold"), arrayOf("condition", "Like new")))
        assertEquals(listOf("PRICE: 11.99 USD", "STATUS: sold", "CONDITION: Like new"), tagFacts("classifieds", listing, emptyMap()))
        val rent = event(2, kind = 30402, tags = arrayOf(arrayOf("price", "900", "EUR", "month")))
        assertEquals(listOf("PRICE: 900 EUR per month"), tagFacts("classifieds", rent, emptyMap()))
    }

    @Test
    fun highlightsSayWhoWroteTheExcerptAndWhere() {
        val marked =
            event(
                1,
                kind = 9802,
                content = "Information wants to be free.",
                tags = arrayOf(arrayOf("p", BOB, "", "author"), arrayOf("r", "https://example.com/essay", "source"), arrayOf("context", "He wrote that information wants to be free.")),
            )
        val facts = tagFacts("highlights", marked, mapOf(BOB to Profile("Bob Ferreira", null)))
        assertEquals(
            listOf(
                "EXCERPT: Information wants to be free.",
                "AUTHOR: Bob Ferreira",
                "SOURCE: https://example.com/essay",
                "CONTEXT: He wrote that information wants to be free.",
            ),
            facts,
        )
        val c = corpus(mapOf("highlights" to listOf(marked)), profiles = mapOf(ALICE to Profile("Alice Moreira", null), BOB to Profile("Bob Ferreira", null)))
        val text = digest(c)
        assertTrue(text.contains("  EXCERPT: Information wants to be free."))
        assertTrue(text.contains("  AUTHOR: Bob Ferreira"))
        // The excerpt isn't printed a second time as the post's text.
        assertEquals(1, Regex("Information wants to be free\\.").findAll(text.substringBefore("CONTEXT")).count())
        assertEquals(listOf(BOB), highlightAuthorKeys(marked))
        assertFalse(highlightAuthorKeys(event(5)).isNotEmpty())
        assertEquals(id(1), marked.id)
    }
}
