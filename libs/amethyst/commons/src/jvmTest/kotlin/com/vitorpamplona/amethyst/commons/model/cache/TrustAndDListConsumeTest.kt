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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.quartz.experimental.decentralizedLists.header.AddressableListHeaderEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.ListHeaderEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.AddressableListItemEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.ListItemEvent
import com.vitorpamplona.quartz.experimental.trustedLists.users.UserTrustedListEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip85TrustedAssertions.externalIds.ExternalIdAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The trusted-assertion, Trusted List and Decentralized List kinds must reach the cache, or a link
 * to one never gets past "Event is loading". Events as served by public relays (2026-10).
 */
class TrustAndDListConsumeTest {
    private fun consumeAddressable(json: String): Event {
        val event = Event.fromJson(json)
        val cache = EventCache()
        // Not pre-verified, as relay events arrive.
        assertTrue(cache.justConsume(event, null, false))
        assertSame(event, cache.getAddressableNoteIfExists((event as AddressableEvent).address())?.event)
        return event
    }

    @Test
    fun userAssertion() {
        assertIs<UserAssertionEvent>(consumeAddressable(USER_ASSERTION))
    }

    @Test
    fun externalIdAssertion() {
        assertIs<ExternalIdAssertionEvent>(consumeAddressable(EXTERNAL_ID_ASSERTION))
    }

    @Test
    fun trustedPeopleList() {
        assertIs<UserTrustedListEvent>(consumeAddressable(TRUSTED_LIST))
    }

    @Test
    fun dListHeader() {
        assertIs<AddressableListHeaderEvent>(consumeAddressable(DLIST_HEADER))
    }

    @Test
    fun dListItem() {
        assertIs<AddressableListItemEvent>(consumeAddressable(DLIST_ITEM))
    }

    @Test
    fun otherAppsKind9998StaysOut() {
        // contentport's license events share kind 9998 but declare no list.
        val event = Event.fromJson(OTHER_APP_9998)
        assertIs<ListHeaderEvent>(event)
        val cache = EventCache()
        assertFalse(cache.justConsume(event, null, true))
        assertNull(cache.getNoteIfExists(event.id)?.event)
    }

    private fun kind9999(vararg tags: Array<String>) =
        ListItemEvent(
            id = "ab".repeat(32),
            pubKey = "cd".repeat(32),
            createdAt = 1_791_000_000,
            tags = arrayOf(*tags),
            content = "",
            sig = "ef".repeat(64),
        )

    @Test
    fun kind9999ThatDeclaresAListIsKept() {
        // The spec's "nonstandard" way to declare a list: an item with `names` and no parent. The
        // card draws it as a header, so the cache must keep it even though it names no list.
        val event = kind9999(arrayOf("names", "Recipe", "Recipes"), arrayOf("required", "title"))
        val cache = EventCache()
        assertTrue(cache.justConsume(event, null, true))
        assertSame(event, cache.getNoteIfExists(event.id)?.event)
    }

    @Test
    fun kind9999ThatNamesAListIsKept() {
        val event = kind9999(arrayOf("z", "39998:${"12".repeat(32)}:recipes"), arrayOf("title", "Pancakes"))
        val cache = EventCache()
        assertTrue(cache.justConsume(event, null, true))
    }

    @Test
    fun otherAppsKind9999StaysOut() {
        val event = kind9999(arrayOf("t", "something-else"))
        val cache = EventCache()
        assertFalse(cache.justConsume(event, null, true))
        assertNull(cache.getNoteIfExists(event.id)?.event)
    }

    companion object {
        private const val USER_ASSERTION = """{"content":"","created_at":1791894144,"id":"301c9152bf0264a525c749338484221d453a41175d3ad73a226b2392514135d6","kind":30382,"pubkey":"2e7afe3f9af8c3dc90d6e8acaf5d7aa926a54f0c22f1c555707309c059fe831b","sig":"915ffb73fbfd46df6ed186363de7e4d9af38d12f8899db5fe37f0e76d1197284f3645a855b7a5cd80da3197022b0777b8986d658b31078b350f5be2411cc07c8","tags":[["d","4e635907dc983a8493ae3ddacc5d6bf1c8168d91a7f3605793a9e688149f0c35"],["rank","40"],["livello","avviato"],["xp","272"],["affari","2"],["r","https://barattolo.store"],["client","Barattolo","31990:2e7afe3f9af8c3dc90d6e8acaf5d7aa926a54f0c22f1c555707309c059fe831b:barattolo","wss://relay.damus.io"]]}"""
        private const val EXTERNAL_ID_ASSERTION = """{"content":"","created_at":1791725332,"id":"93a22afd7aca5e0d533949569ba2b78fad282e180d137fd7d9300adf123a5bf8","kind":30385,"pubkey":"999aaf53a738ce189647cc8f1458082270ca019246c3ccbee6f16d6af071f34a","sig":"f9640a862ed5bd3ae36366338ed699c949c499fd85cd71ba91c219e937328964a9ded5c5be88a18732ea1bafc4dfc118e75727ebb1f381b997959e230b643dc2","tags":[["d","wss://yabu.me"],["status","evaluated"],["algorithm","v0.3.0"],["algorithm_url","https://github.com/Letdown2491/trustedrelays/blob/main/ALGORITHM.md"],["score","84"],["reliability","73"],["quality","91"],["accessibility","92"],["confidence","low"],["observations","10"],["observation_period","5d"],["first_seen","1791724609"],["operator","b707d6be7fd9cc9e1aee83e81c3994156cfcf74ded5b09111930fdeeeb5a0c20"],["operator_verified","nip11"],["operator_confidence","70"],["operator_trust","38"],["policy","open"],["policy_confidence","30"]]}"""
        private const val TRUSTED_LIST = """{"id":"918acc6d856c066c55c9e92d2312419f92b19150c12e94d38c2279246778ac08","pubkey":"919ba08af7786892093b8264332d817379662a0ba0ba1f5c791ed7b62a7ee2ff","created_at":1791859004,"kind":30392,"tags":[["d","tl-pin-b83a28b7-b83a28b7-author"],["title","Author"],["metric","pinned-tag-membership"],["observer","b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450"],["source-tag","47f17726f7cf377b1a299e5ed8e5002424bd8afe596491d1fcafd8093ff2723f","b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450","author"],["cutoff","2"],["min-rank","2"],["p","b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450"]],"content":"{\"members\":[{\"pubkey\":\"b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450\",\"endorsements\":3,\"disputes\":0}]}","sig":"720dfed4e2b3b08f0676fef7de0d1f67bca8b33f5103e9f4997147d89bcaf3ccfb24905d1c29d20c05c844ce4fd5eff5f95f5a2d3c01b6bb2a128a74576bcf7b"}"""
        private const val DLIST_HEADER = """{"id":"21e5b848861b888f7c8e9aeb6f59df434573c3211b218377ef13362dacca8648","pubkey":"b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450","created_at":1791221370,"kind":39998,"tags":[["d","food-and-drink-places"],["names","Food and Drink Place","Food and Drink Places"],["description","Restaurants, cafes, bars, bakeries and food shops, anywhere in the world. Seeded from BTC Map. Data © OpenStreetMap contributors, available under the Open Database License (ODbL 1.0)."],["b","39998:b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450:food-and-drink-places","pointer"],["required","name","Name of the business"],["required","category","Kind of place, as an OpenStreetMap amenity or shop value: restaurant, cafe, bakery, …"],["recommended","address","Street address, one line"],["recommended","locality","City or town"],["recommended","cuisine","Primary cuisine"],["recommended","osm-id","OpenStreetMap id, e.g. node:10011069455"],["recommended","g","Geohash of the location"],["optional","description","What it is, in a sentence or two"],["optional","country","ISO 3166-1 two-letter code"],["optional","region","State or province"],["optional","postal-code","Postcode"],["optional","lat","Latitude, decimal degrees"],["optional","lon","Longitude, decimal degrees"],["optional","website","Website"],["optional","image","A photo"],["optional","phone","Phone number, international format"],["optional","opening-hours","Opening hours, in OpenStreetMap syntax"],["optional","accepts-bitcoin","lightning, onchain, both, or yes when the method is unknown"],["optional","btcmap-id","BTC Map place id"],["optional","source","Where this record came from"],["optional","license","Licence of the record's data, as an SPDX identifier"],["optional","price-range","1 (cheap) to 4 (expensive)"],["optional","menu","Link to the menu"],["optional","reservations","Link for reservations"],["field-type","website","url"],["field-type","image","url"],["field-type","menu","url"],["field-type","reservations","url"],["display","title","name"],["display","summary","address"],["display","image","image"],["display","link","website"],["license","ODbL-1.0"],["r","https://www.openstreetmap.org/copyright"],["r","https://opendatacommons.org/licenses/odbl/1-0/"],["r","https://btcmap.org"]],"content":"","sig":"e1029437a8c25889b3836363be62b95e6f95e13dd3fdc66bfc142d0e08fd93885accb8a1869ec5318a291fc41a451f08d18f70e0d28ddc696d54ec5582b631de"}"""
        private const val DLIST_ITEM = """{"content":"","created_at":1791863669,"id":"94bac4ccd0a4707dfa44125fc9ba30cf571cce39aea7d97c7a5951f323fe340c","kind":39999,"pubkey":"919ba08af7786892093b8264332d817379662a0ba0ba1f5c791ed7b62a7ee2ff","sig":"7d39386177467bcf50fffa19512e63b5cfc3f58db6b115e54c4894f918aa117f99b953c00f40a3f582c95f074a0253e097637ecebce1a8943ccd819757a4e884","tags":[["d","duty-menu"],["z","39998:15f7dafc4624b1e6b00ab7f863de1a53b71967528070ec7d1837c7a40c1c7270:duty-menu"],["p","919ba08af7786892093b8264332d817379662a0ba0ba1f5c791ed7b62a7ee2ff"],["duty","30382","wss://tapestry.brainstorm.world/relay"],["duty","30392","wss://tapestry.brainstorm.world/relay"],["duty","30393","wss://tapestry.brainstorm.world/relay"],["duty","30394","wss://tapestry.brainstorm.world/relay"],["duty","39998","wss://tapestry.brainstorm.world/relay"],["duty","39999","wss://tapestry.brainstorm.world/relay"]]}"""
        private const val OTHER_APP_9998 = """{"id":"a61db20e013a8e4c861dab9da1763beff80a936210383327c4854ccadc46d220","pubkey":"22b55780785d437e3357b4e4c5ad10c8c4c96ed9616bf70a57591948433ec2e1","created_at":1791193419,"kind":9998,"tags":[["e","36e43ac2b2343f8731a8810c2a71a8c7abb3a721caf22e641f608aaf6d1b7357"],["p","a7568c6ce30b8329b7bf1296f7beaa0dfd5010bf77c2b39fa946c15bb4248878"],["t","contentport-license-v1"]],"content":"{\"schema\":\"contentport.license.v1\",\"offer_event_id\":\"36e43ac2b2343f8731a8810c2a71a8c7abb3a721caf22e641f608aaf6d1b7357\",\"payment_hash\":\"80f8d3454b02efb517f7f2c16cabdc91205014b0af03734b01a30b458ed56dd2\",\"amount_sats\":5000,\"starts_at\":1791193419,\"ends_at\":1793785419,\"evidence_type\":\"contentport-settlement-attestation\"}","sig":"49557332e2802cdc0b21de37d57c0c7340835a6d10ed9eda0bf66291b2a89edd9f3eaded17c5b8a013b6791fad864bb541a09e246775d5781cd7f17bef67bc25"}"""
    }
}
