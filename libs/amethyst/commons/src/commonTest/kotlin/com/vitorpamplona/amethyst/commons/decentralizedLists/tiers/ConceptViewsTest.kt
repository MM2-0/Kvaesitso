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
package com.vitorpamplona.amethyst.commons.decentralizedLists.tiers

import com.vitorpamplona.amethyst.commons.decentralizedLists.DListImageShape
import com.vitorpamplona.amethyst.commons.decentralizedLists.communityCoordinateOf
import com.vitorpamplona.amethyst.commons.decentralizedLists.dListHeaderView
import com.vitorpamplona.amethyst.commons.decentralizedLists.dListItemView
import com.vitorpamplona.amethyst.commons.util.languageDisplayName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tier three's readers (Brainstorm `components/items`, `lib/books.ts`, `lib/openingHours.ts`, `lib/placeLinks.ts`). */
class ConceptViewsTest {
    private val github =
        dListHeaderView(
            arrayOf(
                arrayOf("d", "github-accounts"),
                arrayOf("names", "GitHub Account", "GitHub Accounts"),
                arrayOf("required", "github-username"),
                arrayOf("optional", "description"),
                arrayOf("display", "title", "description"),
                arrayOf("display", "summary", "github-username"),
            ),
            "github-accounts",
        )

    @Test
    fun aViewIsKeyedByItsCommunityConceptAndAppliesOnlyWhenItsFieldsAreThere() {
        val item = arrayOf(arrayOf("github-username", "vcavallo"), arrayOf("description", "Vinney Cavallo"))
        val community = DListConceptView.GITHUB_ACCOUNT.community

        assertEquals(DListConceptView.GITHUB_ACCOUNT, conceptViewFor(community, item, github))
        assertNull(conceptViewFor("39998:${"0".repeat(64)}:github-accounts", item, github))
        // A copy that renames the handle leaves the item to tier two.
        val renamed = dListHeaderView(arrayOf(arrayOf("required", "handle")), null)
        assertNull(conceptViewFor(community, arrayOf(arrayOf("handle", "vcavallo")), renamed))
        // So does a value that is not a GitHub handle.
        assertNull(conceptViewFor(community, arrayOf(arrayOf("github-username", "../evil")), github))
    }

    @Test
    fun aGitHubAccountAmongManyLeadsWithItsHandleAndAvatar() {
        val item = arrayOf(arrayOf("github-username", "vcavallo"), arrayOf("description", "Vinney Cavallo"))

        val shown = DListConceptView.GITHUB_ACCOUNT.present(item, github, dListItemView(item, github))

        assertEquals("vcavallo", shown.title)
        assertEquals("Vinney Cavallo", shown.summary)
        assertEquals("https://github.com/vcavallo.png?size=160", shown.image)
    }

    private val food =
        dListHeaderView(
            arrayOf(
                arrayOf("required", "name"),
                arrayOf("required", "category"),
                arrayOf("optional", "cuisine"),
                arrayOf("optional", "address"),
                arrayOf("optional", "locality"),
                arrayOf("optional", "postal-code"),
                arrayOf("optional", "region"),
                arrayOf("optional", "lat"),
                arrayOf("optional", "lon"),
                arrayOf("optional", "phone"),
                arrayOf("optional", "opening-hours"),
                arrayOf("optional", "accepts-bitcoin"),
            ),
            null,
        )

    @Test
    fun aPlaceReadsThroughTheDefinition() {
        val item =
            arrayOf(
                arrayOf("name", "Il Sole"),
                arrayOf("category", "fast_food"),
                arrayOf("cuisine", "pizza"),
                arrayOf("address", "1 Main St Sarasota 34231-5608"),
                arrayOf("locality", "Sarasota"),
                arrayOf("region", "FL"),
                arrayOf("postal-code", "34231"),
                arrayOf("lat", "27.3"),
                arrayOf("lon", "-82.5"),
                arrayOf("phone", "+1 (941) 555-0100"),
                arrayOf("accepts-bitcoin", "lightning"),
                // Not declared: never read.
                arrayOf("website", "https://ilsole.example"),
            )

        val place = placeOf(item, food)!!

        assertEquals("Fast food · Pizza", place.kindWords())
        assertEquals(listOf("1 Main St", "Sarasota, FL 34231"), place.addressLines())
        assertEquals("tel:+19415550100", place.call)
        assertEquals(BitcoinPayment.LIGHTNING, bitcoinPaymentOf(place.bitcoin))
        assertNull(bitcoinPaymentOf("no"))
        assertEquals(BitcoinPayment.UNSPECIFIED, bitcoinPaymentOf("yes"))
        assertNull(place.website)
        assertTrue(place.directionsUrl()!!.contains("27.300000%2C-82.500000"))
        assertNull(placeOf(arrayOf(arrayOf("category", "cafe")), food))
    }

    @Test
    fun openingHoursInOpenStreetMapsSubset() {
        val week = parseOpeningHours("Mo-Fr 11:00-14:00,17:00-02:00; Sa 10:00-15:00; Su off")!!

        assertEquals(2, week.getValue(Weekday.MO).size)
        assertTrue(week.getValue(Weekday.SU).isEmpty())
        // Friday night's span runs into Saturday's small hours.
        assertEquals(OpeningStatus.Open(120), openingStatus(week, Weekday.SA, 60))
        assertEquals(OpeningStatus.Closed(600, Weekday.SA), openingStatus(week, Weekday.SA, 300))
        assertEquals(OpeningStatus.Closed(660, Weekday.MO), openingStatus(week, Weekday.SU, 600))
        assertEquals(OpeningStatus.Open(840), openingStatus(week, Weekday.TU, 700))
        assertTrue(parseOpeningHours("24/7")!!.values.all { it.single().close == 1440 })
        // The subset it can't read is no week at all.
        assertNull(parseOpeningHours("Mo-Fr sunrise-sunset"))
        assertNull(parseOpeningHours("Di-Sa 8:00-18:00"))
        assertEquals("17:00–02:00", spansWords(listOf(Span(1020, 1560))))
    }

    @Test
    fun aPlacesLocalClockIsEstimatedFromItsLongitude() {
        // 2026-10-15T12:00:00Z was a Thursday; 75 degrees west is UTC-5.
        val noonUtc = 1792065600L
        assertEquals(Weekday.TH to 720, placeLocalTime(0.0, noonUtc))
        assertEquals(Weekday.TH to 420, placeLocalTime(-75.0, noonUtc))
        assertEquals(Weekday.FR to 0, placeLocalTime(180.0, noonUtc))
    }

    @Test
    fun aBookFromItsOpenLibrarySeed() {
        val emptyHeader = dListHeaderView(arrayOf(arrayOf("d", "books"), arrayOf("names", "Book", "Books")), "books")
        val item =
            arrayOf(
                arrayOf("title", "Dune"),
                arrayOf("author", "Herbert, Frank (Author)"),
                arrayOf("isbn", "978-0-441-01359-3"),
                arrayOf("cover", "https://covers.openlibrary.org/b/id/1-L.jpg"),
                arrayOf("pages", "412"),
                arrayOf("lang", "eng"),
                arrayOf("subject", "Science fiction"),
                arrayOf("subject", "nyt:fiction=2017-11-12"),
                arrayOf("subject", "science fiction"),
                arrayOf("subject", "Arrakis (Imaginary place) -- Fiction"),
            )

        val book = bookOf(item)!!

        assertEquals("Frank Herbert", book.author)
        assertEquals("9780441013593", book.isbn)
        // In the reader's own language: whatever the platform calls English.
        assertEquals(languageDisplayName("en"), book.language)
        assertEquals(412, book.pages)
        assertEquals(listOf("Science fiction", "Arrakis (Imaginary place) -- Fiction"), book.subjects)
        assertEquals(listOf("Science fiction"), topSubjects(book.subjects, 4))
        assertEquals(DListConceptView.BOOK, conceptViewFor(DListConceptView.BOOK.community, item, emptyHeader))

        val shown = DListConceptView.BOOK.present(item, emptyHeader, dListItemView(item, emptyHeader))
        assertEquals("Dune", shown.title)
        assertEquals("Frank Herbert", shown.summary)
        assertEquals(DListImageShape.PORTRAIT, shown.imageShape)
    }

    @Test
    fun authorsAsOnTheCover() {
        assertEquals("Michael Duncan", authorName("Duncan, Michael (Podcaster)"))
        assertEquals("X. Kennedy & D. Gioia", authorName("Kennedy, X. & Gioia, D."))
        assertEquals("Ursula K. Le Guin", authorName("Ursula K. Le Guin"))
    }

    @Test
    fun shelvesFromTheRestOfTheList() {
        val dune = bookOf(arrayOf(arrayOf("title", "Dune"), arrayOf("author", "Frank Herbert"), arrayOf("subject", "Deserts"), arrayOf("subject", "Ecology")))!!
        val others =
            listOf(
                "messiah" to arrayOf(arrayOf("title", "Dune Messiah"), arrayOf("author", "Herbert, Frank"), arrayOf("subject", "Deserts")),
                "silent" to arrayOf(arrayOf("title", "Silent Spring"), arrayOf("author", "Rachel Carson"), arrayOf("subject", "Ecology"), arrayOf("subject", "deserts")),
                "other" to arrayOf(arrayOf("title", "Other"), arrayOf("author", "Someone")),
            )

        val parsed = others.map { (key, tags) -> key to bookOf(tags)!! }

        assertEquals(listOf("messiah"), booksByAuthor(dune, parsed))
        assertEquals(listOf("silent", "messiah"), similarBooks(dune, parsed))
        assertTrue(similarBooks(bookOf(arrayOf(arrayOf("title", "x")))!!, parsed).isEmpty())
    }

    /** As served by wss://dcosl.brainstorm.world (2026-10-15). */
    @Test
    fun aRealPlaceUnderTheCommunityHeader() {
        val community = DListConceptView.PLACE.community
        val header =
            dListHeaderView(
                arrayOf(
                    arrayOf("d", "food-and-drink-places"),
                    arrayOf("names", "Food and Drink Place", "Food and Drink Places"),
                    arrayOf("b", community, "pointer"),
                    arrayOf("required", "name"),
                    arrayOf("required", "category"),
                    arrayOf("recommended", "address"),
                    arrayOf("recommended", "locality"),
                    arrayOf("recommended", "cuisine"),
                    arrayOf("recommended", "osm-id"),
                    arrayOf("recommended", "g"),
                    arrayOf("optional", "country"),
                    arrayOf("optional", "region"),
                    arrayOf("optional", "postal-code"),
                    arrayOf("optional", "lat"),
                    arrayOf("optional", "lon"),
                    arrayOf("optional", "website"),
                    arrayOf("optional", "phone"),
                    arrayOf("optional", "opening-hours"),
                    arrayOf("optional", "accepts-bitcoin"),
                    arrayOf("optional", "btcmap-id"),
                    arrayOf("optional", "source"),
                    arrayOf("optional", "license"),
                    arrayOf("field-type", "website", "url"),
                    arrayOf("display", "title", "name"),
                    arrayOf("display", "summary", "address"),
                    arrayOf("display", "link", "website"),
                ),
                "food-and-drink-places",
            )
        val item =
            arrayOf(
                arrayOf("d", "osm-way-996743150"),
                arrayOf("z", community),
                arrayOf("name", "Steak 'n Shake"),
                arrayOf("category", "fast_food"),
                arrayOf("address", "6019 Illinois Road Fort Wayne 46804"),
                arrayOf("locality", "Fort Wayne"),
                arrayOf("region", "IN"),
                arrayOf("postal-code", "46804"),
                arrayOf("country", "US"),
                arrayOf("cuisine", "burger"),
                arrayOf("lat", "41.07390385"),
                arrayOf("lon", "-85.21903259999999"),
                arrayOf("website", "https://www.steaknshake.com/locations/in-ft-wayne-illinois-road/"),
                arrayOf("phone", "+1 260 432 1958"),
                arrayOf("opening-hours", "Mo-Su 10:00-22:00"),
                arrayOf("accepts-bitcoin", "lightning"),
                arrayOf("source", "btcmap"),
                arrayOf("g", "dp73sv32u"),
                arrayOf("g", "dp73sv"),
            )

        // The community header points at itself: it is its own community concept.
        val resolvedCommunity = communityCoordinateOf(community, header)
        assertEquals(community, resolvedCommunity)
        assertEquals(DListConceptView.PLACE, conceptViewFor(resolvedCommunity, item, header))

        val place = placeOf(item, header)!!
        assertEquals("Fast food · Burger", place.kindWords())
        assertEquals(listOf("6019 Illinois Road", "Fort Wayne, IN 46804"), place.addressLines())
        assertEquals("tel:+12604321958", place.call)
        assertTrue(parseOpeningHours(place.hours)!!.values.all { it == listOf(Span(600, 1320)) })
    }

    @Test
    fun aWrittenNoIsNoBitcoin() {
        listOf("false", "None", "0").forEach { assertNull(bitcoinPaymentOf(it)) }
    }

    /** OpenStreetMap habits beyond Brainstorm's subset: holidays, rules without days, `,` between rules. */
    @Test
    fun commonOsmHoursAreRead() {
        val holidays = parseOpeningHours("Mo-Fr 09:00-17:00; PH off")!!
        assertEquals(listOf(Span(540, 1020)), holidays[Weekday.MO])
        assertEquals(emptyList(), holidays[Weekday.SA])

        assertTrue(parseOpeningHours("10:00-22:00")!!.values.all { it == listOf(Span(600, 1320)) })

        val joined = parseOpeningHours("Mo-Fr 09:00-17:00, Sa 10:00-14:00")!!
        assertEquals(listOf(Span(540, 1020)), joined[Weekday.FR])
        assertEquals(listOf(Span(600, 840)), joined[Weekday.SA])
        assertEquals(emptyList(), joined[Weekday.SU])

        val sundays = parseOpeningHours("Mo-Sa 09:00-17:00; Su,PH off")!!
        assertEquals(emptyList(), sundays[Weekday.SU])
        // Day lists with commas still read as days, not as a second rule.
        assertEquals(listOf(Span(600, 720)), parseOpeningHours("Mo,We 10:00-12:00")!![Weekday.WE])
    }

    /** Open around the clock never reads "until 00:00"; a span that runs into the next day's carries on. */
    @Test
    fun openUntilFollowsSpansThatJoinAtMidnight() {
        assertEquals(OpeningStatus.Open(null), openingStatus(parseOpeningHours("24/7")!!, Weekday.TU, 600))
        assertEquals(OpeningStatus.Open(null), openingStatus(parseOpeningHours("Mo-Su 00:00-24:00")!!, Weekday.TU, 600))
        val late = parseOpeningHours("Fr 18:00-24:00; Sa 00:00-02:00")!!
        assertEquals(OpeningStatus.Open(1440 + 120), openingStatus(late, Weekday.FR, 1200))
    }
}
