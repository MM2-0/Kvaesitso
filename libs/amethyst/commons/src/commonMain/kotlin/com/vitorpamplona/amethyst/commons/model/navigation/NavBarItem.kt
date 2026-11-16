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

import kotlinx.serialization.Serializable

/**
 * Stable identifiers for every destination the navigation surfaces can show — the bottom bar pins a
 * subset in a user-chosen order, the drawer lists them under fixed headings (see DrawerSections).
 * Order in this enum has no semantic meaning.
 */
@Serializable
enum class NavBarItem {
    HOME,
    MESSAGES,
    VIDEO,
    DISCOVER,
    NOTIFICATIONS,
    PROFILE,
    MY_FITNESS,
    MY_LISTS,
    BOOKMARKS,
    WEB_BOOKMARKS,
    DRAFTS,
    FRONT_PAGE,
    SCHEDULED_POSTS,
    INTEREST_SETS,
    BLOSSOM_DATA,
    EMOJI_PACKS,
    WALLET,
    NOSTR_SIGNER,
    COMMUNITIES,
    ARTICLES,
    PICTURES,
    WORKOUTS,
    GIT_REPOSITORIES,
    NIPS,
    HIGHLIGHTS,
    SOFTWARE_APPS,
    NAPPLETS,
    NSITES,
    BROWSER,
    FAVORITE_APPS,
    CALENDARS,
    CALENDAR_COLLECTIONS,
    SHORTS,
    MUSIC_TRACKS,
    MUSIC_PLAYLISTS,
    PODCAST_EPISODES,
    PODCASTS,
    PUBLIC_CHATS,
    RELAY_GROUPS,
    CONCORD,
    MARMOT_GROUPS,
    CORDN_GROUPS,
    GEOHASH_CHATS,
    FOLLOW_PACKS,
    LIVE_STREAMS,
    NESTS,
    LONGS,
    POLLS,
    CHESS,
    GEOCACHES,
    GEOCACHE_HUNTS,
    BADGES,
    PRODUCTS,
    EMOJI_SETS,
    SETTINGS,
    FAVORITE_ALGO_FEEDS,
}

private val NavBarItemsByName = NavBarItem.entries.associateBy { it.name }

/**
 * Parses persisted [NavBarItem] names, silently dropping any this build doesn't know — a settings
 * blob synced from a newer client can name a destination that doesn't exist here yet, and that must
 * degrade to "ignore this one row" rather than failing the decode of the whole blob.
 */
fun navBarItemsFromNames(names: Collection<String>): Set<NavBarItem> = names.mapNotNullTo(mutableSetOf()) { NavBarItemsByName[it] }

/** The inverse of [navBarItemsFromNames]; sorted so the serialized form is deterministic. */
fun Set<NavBarItem>.toNames(): List<String> = map { it.name }.sorted()
