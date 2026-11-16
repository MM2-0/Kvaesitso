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
package com.vitorpamplona.amethyst.commons.decentralizedLists

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Headers and items as served by wss://search.brainstorm.world (2026-10). */
class DListPresentationTest {
    private val foodHeader =
        arrayOf(
            arrayOf("d", "food-and-drink-places"),
            arrayOf("names", "Food and Drink Place", "Food and Drink Places"),
            arrayOf("description", "Restaurants, cafes, bars, bakeries and food shops, anywhere in the world."),
            arrayOf("required", "name", "Name of the business"),
            arrayOf("required", "category", "Kind of place"),
            arrayOf("recommended", "address", "Street address, one line"),
            arrayOf("recommended", "cuisine", "Primary cuisine"),
            arrayOf("recommended", "g", "Geohash of the location"),
            arrayOf("optional", "website", "Website"),
            arrayOf("optional", "phone", "Phone number, international format"),
            arrayOf("optional", "opening-hours", "Opening hours, in OpenStreetMap syntax"),
            arrayOf("field-type", "website", "url"),
            arrayOf("display", "title", "name"),
            arrayOf("display", "summary", "address"),
            arrayOf("display", "image", "image"),
            arrayOf("display", "link", "website"),
        )

    private val choconat =
        arrayOf(
            arrayOf("d", "osm-node-10600960420"),
            arrayOf("z", "39998:b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450:food-and-drink-places"),
            arrayOf("name", "Choconat"),
            arrayOf("category", "pastry"),
            arrayOf("address", "R1-L7 Guará 71020-218"),
            arrayOf("website", "http://instagram.com/choconat.confeitariadf"),
            arrayOf("phone", "+55 61995218884"),
            arrayOf("opening-hours", "Di-Sa 8:00-18:00"),
            arrayOf("btcmap-id", "2819"),
            arrayOf("g", "6vjtx"),
            arrayOf("t", "pastry"),
            arrayOf("alt", "Food and drink place: Choconat"),
        )

    @Test
    fun headerNamesAndFields() {
        val header = dListHeaderView(foodHeader, "food-and-drink-places")

        assertEquals("Food and Drink Place", header.singular)
        assertEquals("Food and Drink Places", header.plural)
        assertEquals(listOf("name", "category", "address", "cuisine", "g", "website", "phone", "opening-hours"), header.fields.map { it.name })
        // `image` is not a declared field here, so its display hint is dropped.
        assertNull(header.hint("image"))
        assertEquals("website", header.hint("link"))
        assertNull(header.image)
    }

    @Test
    fun headerWithoutNamesFallsBackToItsDTag() {
        val withdrawn = arrayOf(arrayOf("d", "github-accounts"), arrayOf("b", "b-tag-deferred"))

        val header = dListHeaderView(withdrawn, "github-accounts")

        assertEquals("github-accounts", header.singular)
        assertEquals("github-accounts", header.plural)
        assertTrue(header.unaffiliated)
        assertTrue(header.fields.isEmpty())
    }

    @Test
    fun itemReadsThroughTheHeadersHints() {
        val item = dListItemView(choconat, dListHeaderView(foodHeader, null))

        assertEquals("Choconat", item.title)
        assertEquals("R1-L7 Guará 71020-218", item.summary)
        assertEquals("http://instagram.com/choconat.confeitariadf", item.link?.href)
        assertEquals("Website", item.link?.label)
        assertEquals("instagram.com", item.link?.host)
        // No `fact` hints: nothing is a fact. The declared fields nothing shows wait in the fold, in
        // header order (title, summary and link are not repeated), then the undeclared tags apart;
        // protocol tags and alt are never fields.
        assertTrue(item.facts.isEmpty())
        assertEquals(listOf("category", "g", "phone", "opening-hours"), item.moreFields.map { it.field })
        assertEquals(listOf("btcmap-id"), item.undeclared.map { it.field })
        assertTrue(item.defined)
        assertTrue(item.missing.isEmpty())
    }

    @Test
    fun repeatedFieldsCountTheirExtraValues() {
        val item =
            choconat +
                arrayOf(
                    arrayOf("phone", "+55 61 0000-0000"),
                    arrayOf("phone", " "),
                    arrayOf("btcmap-id", "2820"),
                    arrayOf("btcmap-id", "2821"),
                )

        val view = dListItemView(item, dListHeaderView(foodHeader, null))
        val facts = (view.moreFields + view.undeclared).associateBy { it.field }

        // Blank values are not values; declared and undeclared fields both count their repeats.
        assertEquals("+55 61995218884", facts.getValue("phone").value)
        assertEquals(1, facts.getValue("phone").extra)
        assertEquals("2819", facts.getValue("btcmap-id").value)
        assertEquals(2, facts.getValue("btcmap-id").extra)
    }

    @Test
    fun missingRequiredFieldsAreReported() {
        val item = dListItemView(arrayOf(arrayOf("name", "El Sapo")), dListHeaderView(foodHeader, null))

        assertEquals(listOf("category"), item.missing)
    }

    @Test
    fun withoutHintsTheFirstRequiredFieldIsTheTitle() {
        val github =
            arrayOf(
                arrayOf("names", "GitHub Account", "GitHub Accounts"),
                arrayOf("required", "github-username"),
                arrayOf("optional", "description"),
            )
        val item = arrayOf(arrayOf("github-username", "beep-boop"), arrayOf("description", "Some evil bot"))

        val view = dListItemView(item, dListHeaderView(github, null))

        assertEquals("beep-boop", view.title)
        assertEquals("Some evil bot", view.summary)
        assertTrue(view.facts.isEmpty())
        assertTrue(view.moreFields.isEmpty())
    }

    @Test
    fun withoutAHeaderTheItemsOwnTagsSpeak() {
        val duty =
            arrayOf(
                arrayOf("d", "duty-menu"),
                arrayOf("z", "39998:15f7dafc4624b1e6b00ab7f863de1a53b71967528070ec7d1837c7a40c1c7270:duty-menu"),
                arrayOf("duty", "30382", "wss://tapestry.brainstorm.world/relay"),
                arrayOf("duty", "30392", "wss://tapestry.brainstorm.world/relay"),
            )

        val view = dListItemView(duty, null)

        // Tier one: no definition, so the tags are shown as they are.
        assertTrue(!view.defined)
        assertNull(view.title)
        assertTrue(view.facts.isEmpty())
        assertEquals("duty", view.undeclared.single().field)
        assertEquals(1, view.undeclared.single().extra)
    }

    @Test
    fun aTagElementIsNamedByItsContent() {
        val podcaster =
            arrayOf(
                arrayOf("d", "podcaster"),
                arrayOf("z", "39998:82b75e474dda005e912bcbb910391c60c2b89cc7faf5d3c30b7c59a324973833:tag"),
            )
        val content = "{\"tag\":{\"slug\":\"podcaster\",\"name\":\"Podcaster\",\"description\":\"Someone who hosts podcasts.\"}}"
        val tagHeader = dListHeaderView(arrayOf(arrayOf("d", "tag"), arrayOf("names", "tag", "tags")), "tag")

        val view = dListItemView(podcaster, content, tagHeader)

        assertEquals("Podcaster", view.title)
        assertEquals("Someone who hosts podcasts.", view.summary)
        // Content that is not a tag element changes nothing.
        assertNull(dListItemView(podcaster, "not json", tagHeader).title)
    }

    @Test
    fun aTelFieldCallsItsDigitsAndALeadingPlus() {
        assertEquals("tel:+15403910134", telHref("+1-540-391-0134"))
        assertEquals("tel:02079460958", telHref("(020) 7946.0958"))
        assertEquals("tel:911", telHref(" 911 "))
        assertEquals("tel:+5561995218884", telHref("+55 61995218884"))
    }

    @Test
    fun aTelValueWithAnythingElseInItStaysText() {
        assertNull(telHref("call us"))
        assertNull(telHref("555-1234 ext 2"))
        assertNull(telHref("1+2"))
        assertNull(telHref("+"))
        assertNull(telHref(""))
        assertNull(telHref(null))
    }

    @Test
    fun anEmailFieldMailsOneAddressAndNothingElse() {
        assertEquals("mailto:hello@ilsolefood.com", emailHref(" hello@ilsolefood.com "))
        assertNull(emailHref("hello at ilsolefood.com"))
        assertNull(emailHref("a@b"))
        assertNull(emailHref("a@b.com?bcc=c@d.com"))
        assertNull(emailHref("mailto:a@b.com"))
        assertNull(emailHref("a@b.com, c@d.com"))
        assertNull(emailHref(null))
    }

    @Test
    fun aFieldsTypeDecidesWhatItsValueOpens() {
        assertEquals("tel:+13464932858", fieldHref("tel", "+1 346 493 2858"))
        assertEquals("mailto:hello@ilsolefood.com", fieldHref("email", "hello@ilsolefood.com"))
        assertEquals("https://btcmap.org", fieldHref("url", "https://btcmap.org"))
        // `url` links only http(s).
        assertNull(fieldHref("url", "javascript:alert(1)"))
        assertNull(fieldHref("url", "tel:+15403910134"))
        // A value's look never types it: text, no type, or a type no reader knows stays text.
        assertNull(fieldHref("text", "+1-540-391-0134"))
        assertNull(fieldHref("text", "hello@ilsolefood.com"))
        assertNull(fieldHref("text", "https://btcmap.org"))
        assertNull(fieldHref(null, "https://btcmap.org"))
        assertNull(fieldHref("toString", "+1-540-391-0134"))
    }

    @Test
    fun itemFactsLinkOnlyWhereTheHeaderTypedTheField() {
        val header =
            dListHeaderView(
                foodHeader +
                    arrayOf(
                        arrayOf("optional", "email", "Contact address"),
                        arrayOf("optional", "menu", "Menu"),
                        arrayOf("field-type", "phone", "tel"),
                        arrayOf("field-type", "email", "email"),
                    ),
                null,
            )
        val item =
            choconat +
                arrayOf(
                    arrayOf("email", "hello@choconat.com.br"),
                    arrayOf("menu", "https://choconat.example/menu"),
                    arrayOf("instagram", "https://instagram.com/choconat"),
                )

        val view = dListItemView(item, header)
        val facts = (view.moreFields + view.undeclared).associateBy { it.field }

        assertEquals("tel:+5561995218884", facts.getValue("phone").href)
        assertEquals("+55 61995218884", facts.getValue("phone").value)
        assertEquals("mailto:hello@choconat.com.br", facts.getValue("email").href)
        // An untyped field and a tag the header never declared are text, whatever they look like.
        assertNull(facts.getValue("menu").href)
        assertNull(facts.getValue("instagram").href)
        assertNull(facts.getValue("opening-hours").href)
    }

    @Test
    fun withoutAHeaderNoValueIsGuessedALink() {
        val item = arrayOf(arrayOf("name", "Choconat"), arrayOf("website", "http://instagram.com/choconat.confeitariadf"), arrayOf("phone", "+55 61995218884"))

        val facts = dListItemView(item, null).undeclared

        assertEquals(listOf("website", "phone"), facts.map { it.field })
        assertTrue(facts.all { it.href == null })
    }

    @Test
    fun factHintsAreTheOnlyFactsAndEachFieldShowsOnce() {
        val header =
            dListHeaderView(
                foodHeader +
                    arrayOf(
                        arrayOf("display", "fact", "phone"),
                        arrayOf("display", "fact", "opening-hours", "Hours"),
                        // A field a role already shows is not a fact as well.
                        arrayOf("display", "fact", "name"),
                        arrayOf("field-type", "phone", "tel"),
                    ),
                null,
            )

        val view = dListItemView(choconat, header)

        assertEquals(listOf("phone", "opening-hours"), view.facts.map { it.field })
        assertEquals(listOf(null, "Hours"), view.facts.map { it.label })
        assertEquals("tel:+5561995218884", view.facts.first().href)
        assertEquals(listOf("category", "g"), view.moreFields.map { it.field })
        assertTrue("phone" in view.usedFields && "name" in view.usedFields)
    }

    @Test
    fun mediaPlaysByItsExtensionAndLocationTakesTheMostPreciseGeohash() {
        val header =
            dListHeaderView(
                arrayOf(
                    arrayOf("names", "Song", "Songs"),
                    arrayOf("optional", "title"),
                    arrayOf("optional", "url"),
                    arrayOf("optional", "g"),
                    arrayOf("display", "title", "title"),
                    arrayOf("display", "media", "url"),
                    arrayOf("display", "location", "g"),
                ),
                null,
            )
        val song = arrayOf(arrayOf("title", "Tune"), arrayOf("url", "https://cdn.example/a/tune.MP3?x=1"), arrayOf("g", "6vjtx"), arrayOf("g", "6vjtxq2"), arrayOf("g", "6v"))

        val view = dListItemView(song, header)

        assertEquals(DListMediaKind.AUDIO, view.media?.kind)
        assertEquals("6vjtxq2", view.location?.geohash)
        assertTrue(view.location!!.isSpot)
        assertNull(dListItemView(arrayOf(arrayOf("title", "Page"), arrayOf("url", "https://example.com/page.html")), header).media)
        assertEquals(DListMediaKind.VIDEO, mediaKindOfUrl("https://x.example/v.m3u8"))
    }

    @Test
    fun aCopyNamesItsCommunityConceptByItsB() {
        val community = "39998:b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450:github-accounts"
        val copy = dListHeaderView(arrayOf(arrayOf("d", "github-accounts"), arrayOf("b", community, "pointer"), arrayOf("required", "github-username")), null)
        val canonical = dListHeaderView(arrayOf(arrayOf("d", "url-templates"), arrayOf("b", URL_TEMPLATES_CONCEPT, "pointer")), null)

        assertEquals(community, communityCoordinateOf("39998:${"a".repeat(64)}:github-accounts", copy))
        // A concept that points at itself is its own community.
        assertEquals(URL_TEMPLATES_CONCEPT, communityCoordinateOf(URL_TEMPLATES_CONCEPT, canonical))
        assertTrue(copy.pointsAt(community))
    }

    @Test
    fun wordsAndUrls() {
        assertEquals("Opening hours", fieldWords("opening-hours"))
        assertEquals("https://btcmap.org", httpUrl(" https://btcmap.org "))
        assertNull(httpUrl("btcmap.org"))
        assertNull(httpUrl("https://"))
    }

    /** Kotlin writes `-5.0E-4` for a small double: a map URL can't read it. */
    @Test
    fun coordinatesInLinksNeverUseScientificNotation() {
        assertEquals("-0.000500", degreesText(-0.0005))
        assertEquals("51.470000", degreesText(51.47))
        assertEquals("0.000000", degreesText(0.0))
        assertTrue(DListLocation("gcpuv", 51.47, -0.0005).osmUrl().endsWith("#map=13/51.470000/-0.000500"))
    }

    /** The host shown beside a link is the one it opens: nothing that browsers would read as another. */
    @Test
    fun urlsThatHideTheirHostAreNoLinks() {
        assertNull(httpUrl("https://evil.com\\@github.com"))
        assertNull(httpUrl("https://a@github.com@evil.com"))
        assertNull(httpUrl("https://github.com@evil.com/x"))
        assertNull(httpUrl("https://git hub.com"))
        assertNull(httpUrl("https://"))
        assertNull(httpUrl("https://github.com:x/"))
        assertEquals("https://github.com:8080/a?b#c", httpUrl("https://github.com:8080/a?b#c"))
        assertEquals("github.com", httpHost("https://www.github.com:8080/a"))
        assertEquals("mapy.cz", httpHost("https://mapy.cz?x=1"))
    }

    @Test
    fun oneEmailAddressOnly() {
        assertEquals("mailto:a@b.co", emailHref("a@b.co"))
        assertNull(emailHref("a,b@c.co"))
        assertNull(emailHref("a@b.co;c@d.co"))
    }
}
