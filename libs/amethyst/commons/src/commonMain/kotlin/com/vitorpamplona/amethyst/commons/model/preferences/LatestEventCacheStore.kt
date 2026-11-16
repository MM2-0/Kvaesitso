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
package com.vitorpamplona.amethyst.commons.model.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import okio.IOException

/**
 * The account-level events cached on disk so a cold start can show something
 * before any relay answers — the user's own metadata, contact list, relay
 * lists, mute list and the rest.
 *
 * As with [FollowListSlot], the key strings are the ones the Android app has
 * always written, so renaming one throws away that cache for existing users.
 * Losing it is not fatal — the event is re-fetched from relays — but it costs a
 * blank first screen, so the names are still a compatibility surface.
 */
enum class LatestEventSlot(
    val prefKey: String,
    /** False for a slot added after the SharedPreferences era: there is nothing to migrate. */
    val existedInLegacyPrefs: Boolean = true,
) {
    CONTACT_LIST("latestContactList"),
    USER_METADATA("latestUserMetadata"),
    DM_RELAY_LIST("latestDMRelayList"),
    NIP65_RELAY_LIST("latestNIP65RelayList"),
    SEARCH_RELAY_LIST("latestSearchRelayList"),
    INDEX_RELAY_LIST("latestIndexRelayList"),
    RELAY_FEEDS_LIST("latestRelayFeedsList"),
    BLOCKED_RELAY_LIST("latestBlockedRelayList"),
    TRUSTED_RELAY_LIST("latestTrustedRelayList"),
    MUTE_LIST("latestMuteList"),
    PRIVATE_HOME_RELAY_LIST("latestPrivateHomeRelayList"),
    APP_SPECIFIC_DATA("latestAppSpecificData"),
    CHANNEL_LIST("latestChannelList"),
    COMMUNITY_LIST("latestCommunityList"),
    HASHTAG_LIST("latestHashtagList"),
    GEOHASH_LIST("latestGeohashList"),
    EPHEMERAL_LIST("latestEphemeralChatList"),
    RELAY_GROUP_LIST("latestRelayGroupList"),
    CONCORD_LIST("latestConcordList"),

    /** The kind-33302 Community List fragments (CORD-02 §8), one event JSON per line. */
    CONCORD_LIST_FRAGMENTS("latestConcordListFragments", existedInLegacyPrefs = false),
    TRUST_PROVIDER_LIST("latestTrustProviderList"),
    KEY_PACKAGE_RELAY_LIST("latestKeyPackageRelayList"),
    FAVORITE_ALGO_FEEDS_LIST("latestFavoriteAlgoFeedsList"),
    PAYMENT_TARGETS("latestPaymentTargets"),
    BOLT12_OFFERS("latestBolt12Offers"),
    CASHU_WALLET("latestCashuWallet"),
    NUTZAP_INFO("latestNutzapInfo"),
    ;

    val key: Preferences.Key<String> = stringPreferencesKey(prefKey)
}

/**
 * Raw storage for [LatestEventSlot], deliberately untyped.
 *
 * Each slot holds a different event type and the app parses them in parallel
 * with the right parser for each, so this store moves strings and leaves
 * encoding to the caller. That also keeps the bytes identical to what the
 * SharedPreferences implementation wrote, which the migration relies on.
 */
class LatestEventCacheStore(
    private val store: DataStore<Preferences>,
) {
    companion object {
        /**
         * The one-shot copy out of `secret_keeper_<npub>`.
         *
         * Both names come off the same enum entry, so this table cannot drift
         * from the slots the store actually reads.
         */
        val legacyTable =
            LegacyKeyTable(
                "migrated.latestEvents",
                LatestEventSlot.entries.filter { it.existedInLegacyPrefs }.map { LegacyStringKey(it.prefKey, it.key) },
            )
    }

    /** Only the slots actually present; an absent slot means nothing was cached. */
    suspend fun load(): Map<LatestEventSlot, String> {
        val prefs =
            store.data
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .first()

        return LatestEventSlot.entries
            .mapNotNull { slot ->
                prefs[slot.key]?.let { Pair(slot, it) }
            }.toMap()
    }

    /**
     * Writes every slot in one edit. A null value removes the key, so an event
     * the account no longer has stops being served from cache instead of
     * lingering as a stale copy.
     */
    suspend fun saveAll(values: Map<LatestEventSlot, String?>) {
        store.edit { prefs ->
            values.forEach { (slot, json) ->
                if (json != null) prefs[slot.key] = json else prefs.remove(slot.key)
            }
        }
    }
}
