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
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import okio.IOException

/**
 * Every top-nav feed whose author filter the user can change, with the
 * preference key it has always been stored under and the filter it falls back
 * to when unset.
 *
 * The keys are the strings the Android app wrote from its first release, so
 * this table is a compatibility surface: renaming an entry silently resets that
 * feed for everyone who had customised it.
 *
 * Defaults are not uniform and the differences are deliberate — [PRODUCTS] and
 * [GEOCACHES] open to what is physically nearby, [BADGES] and
 * [RELAY_GROUPS_DISCOVERY] to the user's own, most others to Global.
 */
enum class FollowListSlot(
    val prefKey: String,
    val default: TopFilter,
) {
    HOME("defaultHomeFollowList", TopFilter.AllFollows),
    STORIES("defaultStoriesFollowList", TopFilter.Global),
    NOTIFICATION("defaultNotificationFollowList", TopFilter.Selected),
    DISCOVERY("defaultDiscoveryFollowList", TopFilter.Global),
    POLLS("defaultPollsFollowList", TopFilter.Global),
    PICTURES("defaultPicturesFollowList", TopFilter.Global),
    RELAY_GROUPS_DISCOVERY("defaultRelayGroupsDiscoveryFollowList", TopFilter.Mine),
    NAPPLETS("defaultNappletsFollowList", TopFilter.Global),
    NSITES("defaultNsitesFollowList", TopFilter.Global),
    WORKOUTS("defaultWorkoutsFollowList", TopFilter.Global),
    GIT_REPOSITORIES("defaultGitRepositoriesFollowList", TopFilter.Global),
    HIGHLIGHTS("defaultHighlightsFollowList", TopFilter.Global),
    CALENDARS("defaultCalendarsFollowList", TopFilter.Global),
    PRODUCTS("defaultProductsFollowList", TopFilter.AroundMe),
    GEOCACHES("defaultGeocachesFollowList", TopFilter.AroundMe),
    SHORTS("defaultShortsFollowList", TopFilter.Global),
    PUBLIC_CHATS("defaultPublicChatsFollowList", TopFilter.Global),
    LIVE_STREAMS("defaultLiveStreamsFollowList", TopFilter.Global),
    NESTS("defaultNestsFollowList", TopFilter.Global),
    LONGS("defaultLongsFollowList", TopFilter.Global),
    ARTICLES("defaultArticlesFollowList", TopFilter.AllFollows),
    MUSIC_TRACKS("defaultMusicTracksFollowList", TopFilter.Global),
    MUSIC_PLAYLISTS("defaultMusicPlaylistsFollowList", TopFilter.Global),
    PODCAST_EPISODES("defaultPodcastEpisodesFollowList", TopFilter.Global),
    PODCASTS("defaultPodcastsFollowList", TopFilter.Global),
    SOFTWARE_APPS("defaultSoftwareAppsFollowList", TopFilter.Global),
    BADGES("defaultBadgesFollowList", TopFilter.Mine),
    BROWSE_EMOJI_SETS("defaultBrowseEmojiSetsFollowList", TopFilter.Global),
    COMMUNITIES("defaultCommunitiesFollowList", TopFilter.AllFollows),
    FOLLOW_PACKS("defaultFollowPacksFollowList", TopFilter.Global),
    APP_RECOMMENDATIONS("defaultAppRecommendationsFollowList", TopFilter.Global),
    CHESS("defaultChessFollowList", TopFilter.Mine),
    ;

    val key: Preferences.Key<String> = stringPreferencesKey(prefKey)
}

/**
 * The per-account top-nav filter selections, stored one key per feed.
 *
 * Values are [TopFilter] as JSON, the same encoding the SharedPreferences
 * implementation used, so a migrated store and a legacy one hold byte-identical
 * strings.
 */
class TopNavFollowListStore(
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
                "migrated.followLists",
                FollowListSlot.entries.map { LegacyStringKey(it.prefKey, it.key) },
            )
    }

    /**
     * Every slot's current filter, falling back to [FollowListSlot.default]
     * where the key is unset or unreadable.
     *
     * A value that fails to parse yields the default rather than propagating:
     * one corrupt entry should cost the user that feed's filter, not the whole
     * account load.
     */
    suspend fun load(): Map<FollowListSlot, TopFilter> {
        val prefs =
            store.data
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .first()

        return FollowListSlot.entries.associateWith { slot -> parse(prefs[slot.key], slot) }
    }

    /** Writes every slot in one edit, so a crash cannot leave a half-applied set. */
    suspend fun saveAll(values: Map<FollowListSlot, TopFilter>) {
        store.edit { prefs ->
            values.forEach { (slot, filter) -> prefs[slot.key] = JsonMapper.toJson(filter) }
        }
    }

    suspend fun save(
        slot: FollowListSlot,
        filter: TopFilter,
    ) {
        store.edit { prefs -> prefs[slot.key] = JsonMapper.toJson(filter) }
    }

    private fun parse(
        value: String?,
        slot: FollowListSlot,
    ): TopFilter {
        if (value.isNullOrEmpty() || value == "null") return slot.default
        return try {
            JsonMapper.fromJson<TopFilter>(value)
        } catch (e: Exception) {
            Log.w("TopNavFollowListStore") { "Could not decode ${slot.prefKey}; falling back to its default: ${e.message}" }
            slot.default
        }
    }
}
