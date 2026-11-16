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

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry
import com.vitorpamplona.amethyst.commons.model.navigation.DrawerItemVisibility
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.amethyst.commons.model.navigation.navBarItemsFromNames
import com.vitorpamplona.amethyst.commons.model.navigation.toNames
import com.vitorpamplona.amethyst.commons.service.pow.PoWCategory
import com.vitorpamplona.amethyst.commons.service.pow.PoWPolicy
import com.vitorpamplona.amethyst.commons.util.equalImmutableLists
import com.vitorpamplona.amethyst.commons.util.getLanguagesSpokenByUser
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@Stable
class AccountSyncedSettings(
    internalSettings: AccountSyncedSettingsInternal,
) {
    val reactions =
        AccountReactionPreferences(
            MutableStateFlow(internalSettings.reactions.reactionChoices.toImmutableList()),
            MutableStateFlow(mergeWithDefaultReactionRowItems(internalSettings.reactions.reactionRowItems).toImmutableList()),
        )
    val zaps =
        AccountZapPreferences(
            MutableStateFlow(mergeZapAmounts(internalSettings.zaps).toImmutableList()),
            MutableStateFlow(internalSettings.zaps.defaultZapType),
        )
    val languages =
        AccountLanguagePreferences(
            MutableStateFlow(internalSettings.languages.dontTranslateFrom),
            MutableStateFlow(internalSettings.languages.languagePreferences),
            MutableStateFlow(internalSettings.languages.translateTo),
        )
    val security =
        AccountSecurityPreferences(
            MutableStateFlow(internalSettings.security.showSensitiveContent),
            MutableStateFlow(internalSettings.security.warnAboutPostsWithReports),
            MutableStateFlow(internalSettings.security.reportWarningThreshold),
            MutableStateFlow(internalSettings.security.filterSpamFromStrangers),
            MutableStateFlow(internalSettings.security.maxHashtagLimit),
            MutableStateFlow(internalSettings.security.sendKind0EventsToLocalRelay),
            MutableStateFlow(internalSettings.security.addClientTag),
            MutableStateFlow(internalSettings.security.minTrustScore.coerceIn(0, 100)),
        )
    val videoPlayer =
        AccountVideoPlayerPreferences(
            MutableStateFlow(mergeWithDefaultVideoPlayerButtons(internalSettings.videoPlayer.buttonItems).toImmutableList()),
            MutableStateFlow(internalSettings.videoPlayer.captionsEnabled),
        )
    val media =
        AccountMediaPreferences(
            MutableStateFlow(VisualizerStyle.fromName(internalSettings.media.audioVisualizer)),
        )
    val chats =
        AccountChatPreferences(
            MutableStateFlow(internalSettings.chats.toChatroomKeys()),
        )
    val proofOfWork =
        AccountPoWPreferences(
            MutableStateFlow(internalSettings.proofOfWork.difficulty),
            MutableStateFlow(PoWCategory.fromIds(internalSettings.proofOfWork.enabledCategories)),
        )
    val navigation =
        AccountNavigationPreferences(
            MutableStateFlow(internalSettings.navigation.bottomBarItems),
            MutableStateFlow(DrawerItemVisibility.sanitize(navBarItemsFromNames(internalSettings.navigation.hiddenDrawerItems))),
        )

    fun toInternal(mutedPublicChats: Set<String>): AccountSyncedSettingsInternal =
        AccountSyncedSettingsInternal(
            reactions = AccountReactionPreferencesInternal(reactions.reactionChoices.value, reactions.reactionRowItems.value),
            zaps =
                AccountZapPreferencesInternal(
                    zaps.zapAmountChoices.value,
                    // Write the on-chain-eligible subset into the legacy field so
                    // older clients still get sensible on-chain presets. Unioning
                    // it back on load is idempotent (subset ⊆ full list).
                    zaps.zapAmountChoices.value.filter { it >= MIN_ONCHAIN_ZAP_SATS },
                    zaps.defaultZapType.value,
                ),
            languages =
                AccountLanguagePreferencesInternal(
                    languages.dontTranslateFrom.value,
                    languages.languagePreferences.value,
                    languages.translateTo.value,
                ),
            security =
                AccountSecurityPreferencesInternal(
                    security.showSensitiveContent.value,
                    security.warnAboutPostsWithReports.value,
                    security.reportWarningThreshold.value,
                    security.filterSpamFromStrangers.value,
                    security.maxHashtagLimit.value,
                    security.sendKind0EventsToLocalRelay.value,
                    security.addClientTag.value,
                    security.minTrustScore.value,
                ),
            videoPlayer = AccountVideoPlayerPreferencesInternal(videoPlayer.buttonItems.value, videoPlayer.captionsEnabled.value),
            media = AccountMediaPreferencesInternal(media.audioVisualizer.value.name),
            chats =
                AccountChatPreferencesInternal(
                    chats.pinnedChatrooms.value.map { it.users.sorted() },
                    // sorted so the serialized form is deterministic
                    mutedPublicChats.sorted(),
                ),
            proofOfWork =
                AccountPoWPreferencesInternal(
                    proofOfWork.difficulty.value,
                    // sorted so the serialized form is deterministic
                    proofOfWork.enabledCategories.value
                        .map { it.id }
                        .sorted(),
                ),
            navigation =
                AccountNavigationPreferencesInternal(
                    navigation.bottomBarItems.value,
                    navigation.hiddenDrawerItems.value.toNames(),
                ),
        )

    fun updateFrom(syncedSettingsInternal: AccountSyncedSettingsInternal) {
        val newReactionChoices = syncedSettingsInternal.reactions.reactionChoices.toImmutableList()
        if (!equalImmutableLists(reactions.reactionChoices.value, newReactionChoices)) {
            reactions.reactionChoices.tryEmit(newReactionChoices)
        }

        val newReactionRowItems =
            mergeWithDefaultReactionRowItems(syncedSettingsInternal.reactions.reactionRowItems).toImmutableList()
        if (!equalImmutableLists(reactions.reactionRowItems.value, newReactionRowItems)) {
            reactions.reactionRowItems.tryEmit(newReactionRowItems)
        }

        val newZapChoices = mergeZapAmounts(syncedSettingsInternal.zaps).toImmutableList()
        if (!equalImmutableLists(zaps.zapAmountChoices.value, newZapChoices)) {
            zaps.zapAmountChoices.tryEmit(newZapChoices)
        }

        if (zaps.defaultZapType.value != syncedSettingsInternal.zaps.defaultZapType) {
            zaps.defaultZapType.tryEmit(syncedSettingsInternal.zaps.defaultZapType)
        }

        if (languages.dontTranslateFrom.value != syncedSettingsInternal.languages.dontTranslateFrom) {
            languages.dontTranslateFrom.value = syncedSettingsInternal.languages.dontTranslateFrom
        }

        if (languages.languagePreferences.value != syncedSettingsInternal.languages.languagePreferences) {
            languages.languagePreferences.value = syncedSettingsInternal.languages.languagePreferences
        }

        if (languages.translateTo.value != syncedSettingsInternal.languages.translateTo) {
            languages.translateTo.value = syncedSettingsInternal.languages.translateTo
        }

        if (security.showSensitiveContent.value != syncedSettingsInternal.security.showSensitiveContent) {
            security.showSensitiveContent.tryEmit(syncedSettingsInternal.security.showSensitiveContent)
        }

        if (security.filterSpamFromStrangers.value != syncedSettingsInternal.security.filterSpamFromStrangers) {
            security.filterSpamFromStrangers.tryEmit(syncedSettingsInternal.security.filterSpamFromStrangers)
        }

        if (security.warnAboutPostsWithReports.value != syncedSettingsInternal.security.warnAboutPostsWithReports) {
            security.warnAboutPostsWithReports.tryEmit(syncedSettingsInternal.security.warnAboutPostsWithReports)
        }

        if (security.reportWarningThreshold.value != syncedSettingsInternal.security.reportWarningThreshold) {
            security.reportWarningThreshold.tryEmit(syncedSettingsInternal.security.reportWarningThreshold)
        }

        if (security.maxHashtagLimit.value != syncedSettingsInternal.security.maxHashtagLimit) {
            security.maxHashtagLimit.tryEmit(syncedSettingsInternal.security.maxHashtagLimit)
        }

        if (security.sendKind0EventsToLocalRelay.value != syncedSettingsInternal.security.sendKind0EventsToLocalRelay) {
            security.sendKind0EventsToLocalRelay.tryEmit(syncedSettingsInternal.security.sendKind0EventsToLocalRelay)
        }

        if (security.addClientTag.value != syncedSettingsInternal.security.addClientTag) {
            security.addClientTag.tryEmit(syncedSettingsInternal.security.addClientTag)
        }

        // Clamped like updateMinTrustScore: another client's 500 would hide every non-follow.
        val minTrustScore = syncedSettingsInternal.security.minTrustScore.coerceIn(0, 100)
        if (security.minTrustScore.value != minTrustScore) {
            security.minTrustScore.tryEmit(minTrustScore)
        }

        val newVideoPlayerButtonItems =
            mergeWithDefaultVideoPlayerButtons(syncedSettingsInternal.videoPlayer.buttonItems).toImmutableList()
        if (!equalImmutableLists(videoPlayer.buttonItems.value, newVideoPlayerButtonItems)) {
            videoPlayer.buttonItems.tryEmit(newVideoPlayerButtonItems)
        }

        if (videoPlayer.captionsEnabled.value != syncedSettingsInternal.videoPlayer.captionsEnabled) {
            videoPlayer.captionsEnabled.tryEmit(syncedSettingsInternal.videoPlayer.captionsEnabled)
        }

        val newAudioVisualizer = VisualizerStyle.fromName(syncedSettingsInternal.media.audioVisualizer)
        if (media.audioVisualizer.value != newAudioVisualizer) {
            media.audioVisualizer.tryEmit(newAudioVisualizer)
        }

        val newPinnedChatrooms = syncedSettingsInternal.chats.toChatroomKeys()
        if (chats.pinnedChatrooms.value != newPinnedChatrooms) {
            chats.pinnedChatrooms.tryEmit(newPinnedChatrooms)
        }

        // clamp like the local setter: a synced NIP-78 event from another
        // client could carry an out-of-range value that would crash the miner
        // (>256) or mine forever (41+).
        val newDifficulty = syncedSettingsInternal.proofOfWork.difficulty.coerceIn(0, PoWPolicy.MAX_DIFFICULTY)
        if (proofOfWork.difficulty.value != newDifficulty) {
            proofOfWork.difficulty.tryEmit(newDifficulty)
        }

        val newPoWCategories = PoWCategory.fromIds(syncedSettingsInternal.proofOfWork.enabledCategories)
        if (proofOfWork.enabledCategories.value != newPoWCategories) {
            proofOfWork.enabledCategories.tryEmit(newPoWCategories)
        }

        val newBottomBarItems = syncedSettingsInternal.navigation.bottomBarItems
        if (navigation.bottomBarItems.value != newBottomBarItems) {
            navigation.bottomBarItems.tryEmit(newBottomBarItems)
        }

        val newHiddenDrawerItems = DrawerItemVisibility.sanitize(navBarItemsFromNames(syncedSettingsInternal.navigation.hiddenDrawerItems))
        if (navigation.hiddenDrawerItems.value != newHiddenDrawerItems) {
            navigation.hiddenDrawerItems.tryEmit(newHiddenDrawerItems)
        }
    }

    fun dontTranslateFromFilteredBySpokenLanguages(): Set<String> = languages.dontTranslateFrom.value - getLanguagesSpokenByUser()
}

@Stable
class AccountReactionPreferences(
    var reactionChoices: MutableStateFlow<ImmutableList<String>>,
    var reactionRowItems: MutableStateFlow<ImmutableList<ReactionRowItem>>,
)

@Stable
class AccountVideoPlayerPreferences(
    var buttonItems: MutableStateFlow<ImmutableList<VideoPlayerButtonItem>>,
    val captionsEnabled: MutableStateFlow<Boolean>,
)

@Stable
class AccountZapPreferences(
    var zapAmountChoices: MutableStateFlow<ImmutableList<Long>>,
    val defaultZapType: MutableStateFlow<ZapReceiptEvent.ZapType>,
)

/**
 * Union the (historically separate) zap and on-chain preset lists into the
 * single sorted set the app now uses everywhere. Preserves amounts customized
 * on either side and amounts synced from an older client that still writes the
 * legacy `onchainZapAmountChoices`. Idempotent: [AccountSyncedSettings.toInternal]
 * re-derives the on-chain field as a subset of this list.
 */
fun mergeZapAmounts(zaps: AccountZapPreferencesInternal): List<Long> = (zaps.zapAmountChoices + zaps.onchainZapAmountChoices).distinct()

@Stable
class AccountLanguagePreferences(
    var dontTranslateFrom: MutableStateFlow<Set<String>>,
    var languagePreferences: MutableStateFlow<Map<String, String>>,
    var translateTo: MutableStateFlow<String>,
) {
    // ---
    // language services
    // ---
    fun toggleDontTranslateFrom(languageCode: String) {
        dontTranslateFrom.update {
            if (it.contains(languageCode)) {
                it - languageCode
            } else {
                it + languageCode
            }
        }
    }

    fun addDontTranslateFrom(languageCode: String) {
        dontTranslateFrom.update { it + languageCode }
    }

    fun removeDontTranslateFrom(languageCode: String) {
        dontTranslateFrom.update { it - languageCode }
    }

    fun translateToContains(languageCode: String) = translateTo.value.contains(languageCode)

    fun updateTranslateTo(languageCode: String): Boolean {
        if (translateTo.value != languageCode) {
            translateTo.tryEmit(languageCode)
            return true
        }
        return false
    }

    fun prefer(
        source: String,
        target: String,
        preference: String,
    ) {
        val key = "$source,$target"
        languagePreferences.update {
            if (key !in it) {
                it + Pair(key, preference)
            } else {
                if (it.get(key) == preference) {
                    it.minus(key)
                } else {
                    it + Pair(key, preference)
                }
            }
        }
    }

    fun preferenceBetween(
        source: String,
        target: String,
    ): String? = languagePreferences.value["$source,$target"]
}

@Stable
class AccountMediaPreferences(
    val audioVisualizer: MutableStateFlow<VisualizerStyle>,
)

@Stable
class AccountNavigationPreferences(
    val bottomBarItems: MutableStateFlow<List<BottomBarEntry>>,
    /** Drawer rows switched off by the user. Empty = the stock drawer; see DrawerItemVisibility. */
    val hiddenDrawerItems: MutableStateFlow<Set<NavBarItem>>,
)

@Stable
class AccountChatPreferences(
    val pinnedChatrooms: MutableStateFlow<Set<ChatroomKey>>,
)

@Stable
class AccountPoWPreferences(
    val difficulty: MutableStateFlow<Int> = MutableStateFlow(0),
    val enabledCategories: MutableStateFlow<Set<PoWCategory>> = MutableStateFlow(PoWCategory.DEFAULT_ENABLED),
) {
    fun updateDifficulty(newDifficulty: Int): Boolean {
        // compare the coerced value: reporting a change for an out-of-range
        // input that clamps to the current value would republish identical
        // settings to relays.
        val coerced = newDifficulty.coerceIn(0, MAX_POW_DIFFICULTY)
        return if (difficulty.value != coerced) {
            difficulty.tryEmit(coerced)
            true
        } else {
            false
        }
    }

    fun updateCategory(
        category: PoWCategory,
        enabled: Boolean,
    ): Boolean {
        val current = enabledCategories.value
        val updated = if (enabled) current + category else current - category
        return if (updated != current) {
            enabledCategories.tryEmit(updated)
            true
        } else {
            false
        }
    }

    companion object {
        const val MAX_POW_DIFFICULTY = PoWPolicy.MAX_DIFFICULTY
    }
}

fun AccountChatPreferencesInternal.toChatroomKeys(): Set<ChatroomKey> = pinnedRooms.mapTo(mutableSetOf()) { ChatroomKey(it.toSet()) }

@Stable
class AccountSecurityPreferences(
    val showSensitiveContent: MutableStateFlow<Boolean?> = MutableStateFlow(null),
    val warnAboutPostsWithReports: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val reportWarningThreshold: MutableStateFlow<Int> = MutableStateFlow(DefaultReportWarningThreshold),
    var filterSpamFromStrangers: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val maxHashtagLimit: MutableStateFlow<Int> = MutableStateFlow(8),
    var sendKind0EventsToLocalRelay: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val addClientTag: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val minTrustScore: MutableStateFlow<Int> = MutableStateFlow(DefaultMinTrustScore),
) {
    fun updateMinTrustScore(score: Int): Boolean {
        val clamped = score.coerceIn(0, 100)
        return if (minTrustScore.value != clamped) {
            minTrustScore.update { clamped }
            true
        } else {
            false
        }
    }

    fun updateShowSensitiveContent(show: Boolean?): Boolean {
        if (showSensitiveContent.value != show) {
            showSensitiveContent.update { show }
            return true
        }
        return false
    }

    fun updateWarnReports(warnReports: Boolean): Boolean =
        if (warnAboutPostsWithReports.value != warnReports) {
            warnAboutPostsWithReports.tryEmit(warnReports)
            true
        } else {
            false
        }

    fun updateReportWarningThreshold(threshold: Int): Boolean =
        if (reportWarningThreshold.value != threshold) {
            reportWarningThreshold.update { threshold }
            true
        } else {
            false
        }

    fun updateFilterSpam(filterSpam: Boolean): Boolean =
        if (filterSpam != filterSpamFromStrangers.value) {
            filterSpamFromStrangers.tryEmit(filterSpam)
            true
        } else {
            false
        }

    fun updateMaxHashtagLimit(limit: Int): Boolean =
        if (maxHashtagLimit.value != limit) {
            maxHashtagLimit.update { limit }
            true
        } else {
            false
        }

    fun updateSendKind0EventsToLocalRelay(send: Boolean): Boolean =
        if (send != sendKind0EventsToLocalRelay.value) {
            sendKind0EventsToLocalRelay.tryEmit(send)
            true
        } else {
            false
        }

    fun updateAddClientTag(add: Boolean): Boolean =
        if (add != addClientTag.value) {
            addClientTag.tryEmit(add)
            true
        } else {
            false
        }
}
