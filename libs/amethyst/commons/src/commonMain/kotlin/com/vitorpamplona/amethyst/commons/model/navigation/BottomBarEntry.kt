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
package com.vitorpamplona.amethyst.commons.model.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One slot in the bottom navigation bar. A single ordered list of these (persisted per-account in
 * the NIP-78 app-specific data event via
 * [com.vitorpamplona.amethyst.commons.model.AccountNavigationPreferencesInternal.bottomBarItems]) holds
 * built-in destinations, favorite apps, and individual joined chats/groups, so the user can pin and
 * drag-reorder them together in one list.
 *
 * - [BuiltIn] resolves its [Route][com.vitorpamplona.amethyst.commons.model.navigation.Route] (and its
 *   icon/label/notification badge) through [NavBarCatalog], like before.
 * - [Favorite] points at a [FavoriteApp][com.vitorpamplona.amethyst.commons.favorites.FavoriteApp] by
 *   its stable id (which already encodes the route's parameters — the `url` or addressable
 *   `coordinate`); the bar resolves it to a live favorite for its icon/label and to
 *   `Route.WebApp` / `Route.NostrApp` for navigation.
 * - [PublicChat], [RelayGroup] and [Concord] each pin one specific joined chat the user picked from
 *   their joined list (NIP-28 channel, NIP-29 relay group, or a Concord community). The bar resolves
 *   each to the chat's avatar + name from the local cache and to its chat/home route for navigation.
 * - [RelayServer] and [ConcordChannel] pin the *other* level of the two grouped chat systems: a NIP-29
 *   host relay (whose home page lists every group on it) and one channel inside a Concord community.
 *   A NIP-29 relay is the analog of a Concord community (the container), and a Concord channel is the
 *   analog of a NIP-29 group (the item) — so together the five group entries let the user pin either
 *   the whole server or a single room in both systems.
 */
@Serializable
sealed interface BottomBarEntry {
    // Stable discriminators so persisted bottom-bar configs survive class renames/moves (the default
    // polymorphic discriminator is the fully-qualified class name, which is fragile across refactors).
    @Serializable
    @SerialName("builtIn")
    data class BuiltIn(
        val item: NavBarItem,
    ) : BottomBarEntry

    @Serializable
    @SerialName("favorite")
    data class Favorite(
        val favoriteId: String,
    ) : BottomBarEntry

    /** A pinned NIP-28 public chat channel, keyed by its channel event id (hex). */
    @Serializable
    @SerialName("publicChat")
    data class PublicChat(
        val channelId: String,
    ) : BottomBarEntry

    /** A pinned NIP-29 relay group, keyed by the (group id, host relay) pair — the group's real key. */
    @Serializable
    @SerialName("relayGroup")
    data class RelayGroup(
        val groupId: String,
        val relayUrl: String,
    ) : BottomBarEntry

    /**
     * A pinned NIP-29 host relay ("server"), keyed by its relay url; opens the relay's home page that
     * lists every group the user has joined on it. The relay-level analog of pinning a whole [Concord]
     * community, so both grouped chat systems can pin the container as well as an individual room.
     */
    @Serializable
    @SerialName("relayServer")
    data class RelayServer(
        val relayUrl: String,
    ) : BottomBarEntry

    /**
     * A pinned Concord community, keyed by its community id; opens the community's channel list.
     *
     * [relays] are the community's bootstrap relays, captured from the joined-list entry at pin
     * time. A Concord community's private kind-13302 list often lives only on these relays (Armada/
     * Vector publish it there, never to the user's outbox), so without them a pinned community whose
     * list we haven't cached can never be found — the tab and its server screen would stay blank.
     * Carrying the relays on the tab lets the bootstrap re-fetch the list from the right place even
     * when nothing about the community is known yet. Optional (defaults empty) so older persisted
     * bottom-bar configs still decode.
     */
    @Serializable
    @SerialName("concord")
    data class Concord(
        val communityId: String,
        val relays: List<String> = emptyList(),
    ) : BottomBarEntry

    /**
     * A pinned Concord channel inside a community, keyed by the (community id, channel id) pair; opens
     * that specific channel. The channel-level analog of pinning a single [RelayGroup]. [relays] carry
     * the community's bootstrap relays (same reason as [Concord.relays]) so a pinned channel whose
     * community list we haven't cached can still be resolved.
     */
    @Serializable
    @SerialName("concordChannel")
    data class ConcordChannel(
        val communityId: String,
        val channelId: String,
        val relays: List<String> = emptyList(),
    ) : BottomBarEntry

    /** A pinned Bitchat geohash location channel, keyed by its geohash cell; opens the location chat. */
    @Serializable
    @SerialName("geohash")
    data class Geohash(
        val geohash: String,
    ) : BottomBarEntry
}

/**
 * Stable, type-discriminated identity for an entry — used as a Compose list key and for membership
 * checks / de-duplication in the settings picker.
 */
val BottomBarEntry.stableKey: String
    get() =
        when (this) {
            is BottomBarEntry.BuiltIn -> "builtIn:${item.name}"
            is BottomBarEntry.Favorite -> "favorite:$favoriteId"
            is BottomBarEntry.PublicChat -> "publicChat:$channelId"
            is BottomBarEntry.RelayGroup -> "relayGroup:$relayUrl|$groupId"
            is BottomBarEntry.RelayServer -> "relayServer:$relayUrl"
            is BottomBarEntry.Concord -> "concord:$communityId"
            is BottomBarEntry.ConcordChannel -> "concordChannel:$communityId|$channelId"
            is BottomBarEntry.Geohash -> "geohash:$geohash"
        }

/** The favorite-app ids in this bottom-bar config — the apps that should be kept warm as bottom-row tabs. */
fun List<BottomBarEntry>.favoriteIds(): List<String> = mapNotNull { (it as? BottomBarEntry.Favorite)?.favoriteId }
