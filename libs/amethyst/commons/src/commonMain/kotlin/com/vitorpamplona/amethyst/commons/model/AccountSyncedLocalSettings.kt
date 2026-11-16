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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.feeds.custom.FeedDefinition
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedDefinitionSerializer
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.concord.ConcordViewMode
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupViewMode
import com.vitorpamplona.amethyst.commons.model.preferences.FollowListSlot
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlin.coroutines.cancellation.CancellationException

/**
 * The account settings that sync through the NIP-78 blob but live in [AccountSettings] rather
 * than in [AccountSyncedSettings]: a snapshot of them, and the conversions to and from the blob's
 * groups. Kept apart from [AccountSettings], which cannot be built in a JVM test, so the
 * encoding is testable.
 */
class SyncedLocalSettings(
    val accentColor: AccentColorType?,
    val relayGroupViewMode: RelayGroupViewMode,
    val concordViewMode: ConcordViewMode,
    val enabledChatFeeds: Set<ChatFeedType>,
    val enabledHomeFeedTypes: Set<HomeFeedType>,
    val feedFilters: Map<FollowListSlot, TopFilter>,
    val customFeeds: List<FeedDefinition>,
    val hideCommunityRulesViolations: Boolean,
    val relayAuthPolicy: RelayAuthPolicy,
    val relayAuthTrustMyRelaysAndVenues: Boolean,
    val relayAuthTrustReadFollows: Boolean,
    val relayAuthTrustMessageFollows: Boolean,
    val relayAuthTrustMessageStrangers: Boolean,
    val defaultFileServer: ServerName,
    val stripLocationOnUpload: Boolean,
    val mirrorUploadsToAllServers: Boolean,
    val optimizeMediaOnUpload: Boolean,
    val callsEnabled: Boolean,
    /** Null when this account never set a signature of its own. */
    val composeSignature: String? = null,
)

/** Fills this blob's newer groups from [local]. Sets and maps keep a fixed order, so the JSON is deterministic. */
fun AccountSyncedSettingsInternal.withLocalGroups(local: SyncedLocalSettings): AccountSyncedSettingsInternal =
    apply {
        appearance = AccountAppearancePreferencesInternal(local.accentColor?.name)
        messages =
            AccountMessagesPreferencesInternal(
                relayGroupViewMode = local.relayGroupViewMode.name,
                concordViewMode = local.concordViewMode.name,
                disabledChatFeeds = ChatFeedType.encode(ChatFeedType.ALL - local.enabledChatFeeds),
            )
        feeds =
            AccountFeedPreferencesInternal(
                disabledHomeFeedTypes = HomeFeedType.encode(HomeFeedType.ALL - local.enabledHomeFeedTypes),
                feedFilters = encodeFeedFilters(local.feedFilters),
                customFeeds = FeedDefinitionSerializer.serializeList(local.customFeeds),
                hideCommunityRulesViolations = local.hideCommunityRulesViolations,
            )
        relayAuth =
            AccountRelayAuthPreferencesInternal(
                policy = local.relayAuthPolicy.name,
                trustMyRelaysAndVenues = local.relayAuthTrustMyRelaysAndVenues,
                trustReadFollows = local.relayAuthTrustReadFollows,
                trustMessageFollows = local.relayAuthTrustMessageFollows,
                trustMessageStrangers = local.relayAuthTrustMessageStrangers,
            )
        uploads =
            AccountUploadPreferencesInternal(
                defaultFileServer = local.defaultFileServer.let { SyncedFileServer(it.name, it.baseUrl, it.type.name) },
                stripLocation = local.stripLocationOnUpload,
                mirrorToAllServers = local.mirrorUploadsToAllServers,
                optimizeMedia = local.optimizeMediaOnUpload,
            )
        calls = AccountCallPreferencesInternal(local.callsEnabled)
        compose = local.composeSignature?.let { AccountComposePreferencesInternal(it) }
    }

/** The [E] named [name], or [current] when the name is missing or one this version does not know. */
inline fun <reified E : Enum<E>> enumByName(
    name: String?,
    current: E,
): E = enumValues<E>().firstOrNull { it.name == name } ?: current

/**
 * The accent [name] stands for: null follows the application color, and a color this version
 * does not know keeps [current] rather than silently falling back to the application color.
 */
fun decodeAccentColor(
    name: String?,
    current: AccentColorType?,
): AccentColorType? = if (name == null) null else AccentColorType.entries.firstOrNull { it.name == name } ?: current

/** Every slot's filter as TopFilter JSON, keyed by slot name, in [FollowListSlot] order. */
fun encodeFeedFilters(filters: Map<FollowListSlot, TopFilter>): Map<String, String> =
    buildMap {
        FollowListSlot.entries.forEach { slot ->
            filters[slot]?.let { put(slot.name, JsonMapper.toJson(it)) }
        }
    }

/**
 * The filters in [encoded] this version can read, as TopFilter JSON keyed by slot. Kept as JSON
 * because [TopFilter]s that carry an id or address compare by identity: comparing the text is
 * how a caller tells an unchanged filter from a changed one without reloading that feed.
 */
fun readableFeedFilters(encoded: Map<String, String>): Map<FollowListSlot, String> =
    buildMap {
        encoded.forEach { (name, json) ->
            val slot = FollowListSlot.entries.firstOrNull { it.name == name } ?: return@forEach
            if (decodeFeedFilter(json) != null) put(slot, json)
        }
    }

/** One filter's JSON as a [TopFilter], or null when it is one this version cannot read. */
fun decodeFeedFilter(json: String): TopFilter? =
    try {
        JsonMapper.fromJson<TopFilter>(json)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("SyncedLocalSettings", "Skipping a synced feed filter this version cannot read", e)
        null
    }

/** The custom feeds in [json], or null when the list cannot be read at all. */
fun decodeCustomFeeds(json: String): List<FeedDefinition>? =
    try {
        FeedDefinitionSerializer.deserializeList(json)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("SyncedLocalSettings", "Skipping synced custom feeds this version cannot read", e)
        null
    }

/** The server [synced] names, or null when its type is one this version does not know. */
fun decodeFileServer(synced: SyncedFileServer?): ServerName? {
    if (synced == null) return null
    val type = ServerType.entries.firstOrNull { it.name == synced.type } ?: return null
    return ServerName(synced.name, synced.baseUrl, type)
}

/**
 * When to publish the NIP-78 blob for the changes it is told about: [settingDelayMs] after the
 * last [settingChanges], or [feedFilterDelayMs] after the last [feedFilterChanges]. One wait
 * serves both, so a feed switch never holds back a setting changed just before it, and a pending
 * feed switch rides along with the next publish instead of costing one of its own.
 */
fun syncedSettingsPublishes(
    settingChanges: Flow<Unit>,
    feedFilterChanges: Flow<Unit>,
    settingDelayMs: Long = 1_000,
    feedFilterDelayMs: Long = 30_000,
): Flow<Unit> =
    channelFlow {
        var settingPending = false
        merge(settingChanges.map { true }, feedFilterChanges.map { false }).collectLatest { isSetting ->
            if (isSetting) settingPending = true
            delay(if (settingPending) settingDelayMs else feedFilterDelayMs)
            settingPending = false
            send(Unit)
        }
    }
