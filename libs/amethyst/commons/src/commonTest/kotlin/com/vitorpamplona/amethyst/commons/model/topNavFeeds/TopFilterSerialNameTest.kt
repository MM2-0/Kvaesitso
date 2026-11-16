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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins every [TopFilter] subclass to the serial name it had before the class
 * moved from `com.vitorpamplona.amethyst.model` to commons. These names are
 * the polymorphic `type` discriminator [JsonMapper] writes into per-account
 * preferences (LocalPreferences' DEFAULT_*_FOLLOW_LIST keys); if one changes,
 * every user's saved tab selections silently reset to defaults on upgrade,
 * because the decode failure is swallowed by parseTopFilterOrDefault.
 */
class TopFilterSerialNameTest {
    private val oldPrefix = "com.vitorpamplona.amethyst.model.TopFilter"
    private val address = Address(30000, "e28ffe100522bc27e01f0c7c1a0e42722f7d5b5a2ba1fb066bde54a2c53e91a5", "follows")

    private val expected: Map<String, TopFilter> =
        mapOf(
            "Global" to TopFilter.Global,
            "Selected" to TopFilter.Selected,
            "AllFollows" to TopFilter.AllFollows,
            "AllUserFollows" to TopFilter.AllUserFollows,
            "DefaultFollows" to TopFilter.DefaultFollows,
            "AroundMe" to TopFilter.AroundMe,
            "TeleportPicker" to TopFilter.TeleportPicker,
            "Mine" to TopFilter.Mine,
            "PeopleList" to TopFilter.PeopleList(address),
            "MuteList" to TopFilter.MuteList(address),
            "Community" to TopFilter.Community(address),
            "Hashtag" to TopFilter.Hashtag("nostr"),
            "Geohash" to TopFilter.Geohash("u4pruyd"),
            "Relay" to TopFilter.Relay("wss://relay.example.com"),
            "FavoriteAlgoFeed" to TopFilter.FavoriteAlgoFeed(address),
            "AllFavoriteAlgoFeeds" to TopFilter.AllFavoriteAlgoFeeds,
            "InterestSet" to TopFilter.InterestSet(address),
            "CustomFeed" to TopFilter.CustomFeed("6f1c2a"),
        )

    @Test
    fun everySubclassKeepsItsPreMoveSerialName() {
        expected.forEach { (simpleName, value) ->
            val encoded = JsonMapper.jsonInstance.encodeToString(TopFilter.serializer(), value)
            val type =
                JsonMapper.jsonInstance
                    .parseToJsonElement(encoded)
                    .jsonObject["type"]
                    ?.jsonPrimitive
                    ?.content
            assertEquals(
                "$oldPrefix.$simpleName",
                type,
                "TopFilter.$simpleName must keep the pre-move serial name or saved prefs reset",
            )
        }
    }

    @Test
    fun decodesPrefsWrittenBeforeTheMove() {
        // Literal JSON as written by the pre-move app: the base class's `code`
        // constructor property is a serialized field alongside the discriminator.
        val legacyHashtag = """{"type":"$oldPrefix.Hashtag","code":"Hashtag/nostr","tag":"nostr"}"""
        val decodedTag = JsonMapper.jsonInstance.decodeFromString(TopFilter.serializer(), legacyHashtag)
        assertEquals("Hashtag/nostr", decodedTag.code)

        // The address is stored in the same shape main wrote (Address's own fields).
        val legacyPeopleList =
            """{"type":"$oldPrefix.PeopleList","code":"${address.toValue()}","address":{"kind":30000,""" +
                """"pubKeyHex":"${address.pubKeyHex}","dTag":"follows"}}"""
        val decodedList = JsonMapper.jsonInstance.decodeFromString(TopFilter.serializer(), legacyPeopleList)
        assertEquals(address.toValue(), decodedList.code)
    }

    @Test
    fun roundTripsEverySubclass() {
        expected.forEach { (simpleName, value) ->
            val encoded = JsonMapper.jsonInstance.encodeToString(TopFilter.serializer(), value)
            val decoded = JsonMapper.jsonInstance.decodeFromString(TopFilter.serializer(), encoded)
            assertEquals(value.code, decoded.code, "TopFilter.$simpleName must round-trip")
        }
    }
}
