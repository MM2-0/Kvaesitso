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
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class TopFilter(
    val code: String,
) {
    interface AddressableTopFilter {
        @Contextual val address: Address
    }

    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.Global")
    object Global : TopFilter(" Global ")

    /**
     * Notifications-only curated mode: like [Global] it admits authors the
     * user doesn't follow, but it also applies per-kind relevance heuristics
     * to remove less interesting notes (reactions/reposts that don't target
     * the user's own notes, unrelated thread replies, etc.). In Notifications,
     * [Global] shows every event that p-tags the user instead.
     */
    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.Selected")
    object Selected : TopFilter(" Selected ")

    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.AllFollows")
    object AllFollows : TopFilter(" All Follows ")

    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.AllUserFollows")
    object AllUserFollows : TopFilter(" All User Follows ")

    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.DefaultFollows")
    object DefaultFollows : TopFilter(" Main User Follows ")

    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.AroundMe")
    object AroundMe : TopFilter(" Around Me ")

    /**
     * Not a real selection: a sentinel for the "Teleport" chip in the top-nav filter.
     * The spinner intercepts it to open the map picker and then applies the chosen
     * [Geohash] instead — it is never persisted or dispatched to a feed flow.
     */
    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.TeleportPicker")
    object TeleportPicker : TopFilter(" Teleport ")

    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.Mine")
    object Mine : TopFilter(" Mine ")

    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.PeopleList")
    class PeopleList(
        @Contextual override val address: Address,
    ) : TopFilter(address.toValue()),
        AddressableTopFilter

    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.MuteList")
    class MuteList(
        @Contextual override val address: Address,
    ) : TopFilter(address.toValue()),
        AddressableTopFilter

    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.Community")
    class Community(
        @Contextual override val address: Address,
    ) : TopFilter("Community/${address.toValue()}"),
        AddressableTopFilter

    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.Hashtag")
    class Hashtag(
        val tag: String,
    ) : TopFilter("Hashtag/$tag")

    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.Geohash")
    class Geohash(
        val tag: String,
    ) : TopFilter("Geohash/$tag")

    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.Relay")
    class Relay(
        val url: String,
    ) : TopFilter("Relay/$url")

    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.FavoriteAlgoFeed")
    class FavoriteAlgoFeed(
        @Contextual val address: Address,
    ) : TopFilter("FavoriteAlgoFeed/${address.toValue()}")

    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.AllFavoriteAlgoFeeds")
    object AllFavoriteAlgoFeeds : TopFilter(" All Favourite DVMs ")

    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.InterestSet")
    class InterestSet(
        @Contextual val address: Address,
    ) : TopFilter("InterestSet/${address.toValue()}")

    /** A feed the user built (authors, hashtags, kinds, relays), stored with the account by [id]. */
    @Serializable
    @SerialName("com.vitorpamplona.amethyst.model.TopFilter.CustomFeed")
    class CustomFeed(
        val id: String,
    ) : TopFilter("CustomFeed/$id")
}
