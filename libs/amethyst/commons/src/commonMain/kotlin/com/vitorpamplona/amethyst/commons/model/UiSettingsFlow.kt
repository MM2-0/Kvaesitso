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
import com.vitorpamplona.amethyst.commons.browser.SearchEngines
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

@Stable
class UiSettingsFlow(
    val theme: MutableStateFlow<ThemeType> = MutableStateFlow(ThemeType.SYSTEM),
    val preferredLanguage: MutableStateFlow<String?> = MutableStateFlow(null),
    val automaticallyShowImages: MutableStateFlow<ConnectivityType> = MutableStateFlow(ConnectivityType.ALWAYS),
    val automaticallyStartPlayback: MutableStateFlow<ConnectivityType> = MutableStateFlow(ConnectivityType.ALWAYS),
    val automaticallyPlayVideos: MutableStateFlow<BooleanType> = MutableStateFlow(BooleanType.ALWAYS),
    val automaticallyShowUrlPreview: MutableStateFlow<ConnectivityType> = MutableStateFlow(ConnectivityType.ALWAYS),
    val automaticallyHideNavigationBars: MutableStateFlow<BooleanType> = MutableStateFlow(BooleanType.ALWAYS),
    val automaticallyShowProfilePictures: MutableStateFlow<ConnectivityType> = MutableStateFlow(ConnectivityType.ALWAYS),
    val dontShowPushNotificationSelector: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val dontAskForNotificationPermissions: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val featureSet: MutableStateFlow<FeatureSetType> = MutableStateFlow(FeatureSetType.SIMPLIFIED),
    val gallerySet: MutableStateFlow<ProfileGalleryType> = MutableStateFlow(ProfileGalleryType.CLASSIC),
    val automaticallyProposeAiImprovements: MutableStateFlow<BooleanType> = MutableStateFlow(BooleanType.ALWAYS),
    val useTrackedBroadcasts: MutableStateFlow<BooleanType> = MutableStateFlow(BooleanType.ALWAYS),
    val automaticallyCreateDrafts: MutableStateFlow<BooleanType> = MutableStateFlow(BooleanType.ALWAYS),
    val showHomeNewThreadsTab: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val showHomeConversationsTab: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val showHomeEverythingTab: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val showProfileBadges: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val showProfileAppRecommendations: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val showProfileZapReceivedFeed: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val showProfileFollowersFeed: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val dontShowOnchainPublicWarning: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val suggestWorkoutsFromHealthConnect: MutableStateFlow<BooleanType> = MutableStateFlow(BooleanType.ALWAYS),
    val accentColor: MutableStateFlow<AccentColorType> = MutableStateFlow(AccentColorType.PURPLE),
    val fontFamily: MutableStateFlow<FontFamilyType> = MutableStateFlow(FontFamilyType.SYSTEM),
    val fontSize: MutableStateFlow<FontSizeType> = MutableStateFlow(FontSizeType.NORMAL),
    val composeSignature: MutableStateFlow<String> = MutableStateFlow(""),
    val showOnchainWallet: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val showPayToZapChip: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val searchEngine: MutableStateFlow<String> = MutableStateFlow(SearchEngines.DEFAULT.id),
    val spellCheck: MutableStateFlow<SpellCheckType> = MutableStateFlow(SpellCheckType.AUTOMATIC),
    val noteLayout: MutableStateFlow<NoteLayoutType> = MutableStateFlow(NoteLayoutType.AUTHOR_COLUMN),
    val automaticallyDownloadTranslationModels: MutableStateFlow<ConnectivityType> = MutableStateFlow(ConnectivityType.WIFI_ONLY),
    val hideWalletBalances: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val navigationLayout: MutableStateFlow<NavigationLayoutType> = MutableStateFlow(NavigationLayoutType.STACK),
    val chatLayout: MutableStateFlow<ChatLayoutType> = MutableStateFlow(ChatLayoutType.BUBBLES),
) {
    val listOfFlows: List<Flow<Any?>> =
        listOf<Flow<Any?>>(
            theme,
            preferredLanguage,
            automaticallyShowImages,
            automaticallyStartPlayback,
            automaticallyPlayVideos,
            automaticallyShowUrlPreview,
            automaticallyHideNavigationBars,
            automaticallyShowProfilePictures,
            dontShowPushNotificationSelector,
            dontAskForNotificationPermissions,
            featureSet,
            gallerySet,
            automaticallyProposeAiImprovements,
            useTrackedBroadcasts,
            automaticallyCreateDrafts,
            showHomeNewThreadsTab,
            showHomeConversationsTab,
            showHomeEverythingTab,
            showProfileBadges,
            showProfileAppRecommendations,
            showProfileZapReceivedFeed,
            showProfileFollowersFeed,
            dontShowOnchainPublicWarning,
            suggestWorkoutsFromHealthConnect,
            accentColor,
            fontFamily,
            fontSize,
            composeSignature,
            showOnchainWallet,
            showPayToZapChip,
            searchEngine,
            spellCheck,
            noteLayout,
            automaticallyDownloadTranslationModels,
            hideWalletBalances,
            navigationLayout,
            chatLayout,
        )

    // emits at every change in any of the propertyes.
    @Suppress("UNCHECKED_CAST")
    val propertyWatchFlow: Flow<UiSettings> =
        combine<Any?, UiSettings>(listOfFlows) { flows: Array<Any?> ->
            UiSettings(
                flows[0] as ThemeType,
                flows[1] as String?,
                flows[2] as ConnectivityType,
                flows[3] as ConnectivityType,
                flows[4] as BooleanType,
                flows[5] as ConnectivityType,
                flows[6] as BooleanType,
                flows[7] as ConnectivityType,
                flows[8] as Boolean,
                flows[9] as Boolean,
                flows[10] as FeatureSetType,
                flows[11] as ProfileGalleryType,
                flows[12] as BooleanType,
                flows[13] as BooleanType,
                flows[14] as BooleanType,
                flows[15] as Boolean,
                flows[16] as Boolean,
                flows[17] as Boolean,
                flows[18] as Boolean,
                flows[19] as Boolean,
                flows[20] as Boolean,
                flows[21] as Boolean,
                flows[22] as Boolean,
                flows[23] as BooleanType,
                flows[24] as AccentColorType,
                flows[25] as FontFamilyType,
                flows[26] as FontSizeType,
                flows[27] as String,
                flows[28] as Boolean,
                flows[29] as Boolean,
                flows[30] as String,
                flows[31] as SpellCheckType,
                flows[32] as NoteLayoutType,
                flows[33] as ConnectivityType,
                flows[34] as Boolean,
                flows[35] as NavigationLayoutType,
                flows[36] as ChatLayoutType,
            )
        }

    fun toSettings(): UiSettings =
        UiSettings(
            theme.value,
            preferredLanguage.value,
            automaticallyShowImages.value,
            automaticallyStartPlayback.value,
            automaticallyPlayVideos.value,
            automaticallyShowUrlPreview.value,
            automaticallyHideNavigationBars.value,
            automaticallyShowProfilePictures.value,
            dontShowPushNotificationSelector.value,
            dontAskForNotificationPermissions.value,
            featureSet.value,
            gallerySet.value,
            automaticallyProposeAiImprovements.value,
            useTrackedBroadcasts.value,
            automaticallyCreateDrafts.value,
            showHomeNewThreadsTab.value,
            showHomeConversationsTab.value,
            showHomeEverythingTab.value,
            showProfileBadges.value,
            showProfileAppRecommendations.value,
            showProfileZapReceivedFeed.value,
            showProfileFollowersFeed.value,
            dontShowOnchainPublicWarning.value,
            suggestWorkoutsFromHealthConnect.value,
            accentColor.value,
            fontFamily.value,
            fontSize.value,
            composeSignature.value,
            showOnchainWallet.value,
            showPayToZapChip.value,
            searchEngine.value,
            spellCheck.value,
            noteLayout.value,
            automaticallyDownloadTranslationModels.value,
            hideWalletBalances.value,
            navigationLayout.value,
            chatLayout.value,
        )

    fun update(torSettings: UiSettings): Boolean {
        var any = false

        if (theme.value != torSettings.theme) {
            theme.tryEmit(torSettings.theme)
            any = true
        }
        if (preferredLanguage.value != torSettings.preferredLanguage) {
            preferredLanguage.tryEmit(torSettings.preferredLanguage)
            any = true
        }
        if (automaticallyShowImages.value != torSettings.automaticallyShowImages) {
            automaticallyShowImages.tryEmit(torSettings.automaticallyShowImages)
            any = true
        }
        if (automaticallyStartPlayback.value != torSettings.automaticallyStartPlayback) {
            automaticallyStartPlayback.tryEmit(torSettings.automaticallyStartPlayback)
            any = true
        }
        if (automaticallyPlayVideos.value != torSettings.automaticallyPlayVideos) {
            automaticallyPlayVideos.tryEmit(torSettings.automaticallyPlayVideos)
            any = true
        }
        if (automaticallyShowUrlPreview.value != torSettings.automaticallyShowUrlPreview) {
            automaticallyShowUrlPreview.tryEmit(torSettings.automaticallyShowUrlPreview)
            any = true
        }
        if (automaticallyHideNavigationBars.value != torSettings.automaticallyHideNavigationBars) {
            automaticallyHideNavigationBars.tryEmit(torSettings.automaticallyHideNavigationBars)
            any = true
        }
        if (automaticallyShowProfilePictures.value != torSettings.automaticallyShowProfilePictures) {
            automaticallyShowProfilePictures.tryEmit(torSettings.automaticallyShowProfilePictures)
            any = true
        }
        if (dontShowPushNotificationSelector.value != torSettings.dontShowPushNotificationSelector) {
            dontShowPushNotificationSelector.tryEmit(torSettings.dontShowPushNotificationSelector)
            any = true
        }
        if (dontAskForNotificationPermissions.value != torSettings.dontAskForNotificationPermissions) {
            dontAskForNotificationPermissions.tryEmit(torSettings.dontAskForNotificationPermissions)
            any = true
        }
        if (featureSet.value != torSettings.featureSet) {
            featureSet.tryEmit(torSettings.featureSet)
            any = true
        }
        if (gallerySet.value != torSettings.gallerySet) {
            gallerySet.tryEmit(torSettings.gallerySet)
            any = true
        }
        if (automaticallyProposeAiImprovements.value != torSettings.automaticallyProposeAiImprovements) {
            automaticallyProposeAiImprovements.tryEmit(torSettings.automaticallyProposeAiImprovements)
            any = true
        }
        if (useTrackedBroadcasts.value != torSettings.useTrackedBroadcasts) {
            useTrackedBroadcasts.tryEmit(torSettings.useTrackedBroadcasts)
            any = true
        }
        if (automaticallyCreateDrafts.value != torSettings.automaticallyCreateDrafts) {
            automaticallyCreateDrafts.tryEmit(torSettings.automaticallyCreateDrafts)
            any = true
        }
        if (showHomeNewThreadsTab.value != torSettings.showHomeNewThreadsTab) {
            showHomeNewThreadsTab.tryEmit(torSettings.showHomeNewThreadsTab)
            any = true
        }
        if (showHomeConversationsTab.value != torSettings.showHomeConversationsTab) {
            showHomeConversationsTab.tryEmit(torSettings.showHomeConversationsTab)
            any = true
        }
        if (showHomeEverythingTab.value != torSettings.showHomeEverythingTab) {
            showHomeEverythingTab.tryEmit(torSettings.showHomeEverythingTab)
            any = true
        }
        if (showProfileBadges.value != torSettings.showProfileBadges) {
            showProfileBadges.tryEmit(torSettings.showProfileBadges)
            any = true
        }
        if (showProfileAppRecommendations.value != torSettings.showProfileAppRecommendations) {
            showProfileAppRecommendations.tryEmit(torSettings.showProfileAppRecommendations)
            any = true
        }
        if (showProfileZapReceivedFeed.value != torSettings.showProfileZapReceivedFeed) {
            showProfileZapReceivedFeed.tryEmit(torSettings.showProfileZapReceivedFeed)
            any = true
        }
        if (showProfileFollowersFeed.value != torSettings.showProfileFollowersFeed) {
            showProfileFollowersFeed.tryEmit(torSettings.showProfileFollowersFeed)
            any = true
        }
        if (dontShowOnchainPublicWarning.value != torSettings.dontShowOnchainPublicWarning) {
            dontShowOnchainPublicWarning.tryEmit(torSettings.dontShowOnchainPublicWarning)
            any = true
        }
        if (suggestWorkoutsFromHealthConnect.value != torSettings.suggestWorkoutsFromHealthConnect) {
            suggestWorkoutsFromHealthConnect.tryEmit(torSettings.suggestWorkoutsFromHealthConnect)
            any = true
        }
        if (accentColor.value != torSettings.accentColor) {
            accentColor.tryEmit(torSettings.accentColor)
            any = true
        }
        if (fontFamily.value != torSettings.fontFamily) {
            fontFamily.tryEmit(torSettings.fontFamily)
            any = true
        }
        if (fontSize.value != torSettings.fontSize) {
            fontSize.tryEmit(torSettings.fontSize)
            any = true
        }
        if (composeSignature.value != torSettings.composeSignature) {
            composeSignature.tryEmit(torSettings.composeSignature)
            any = true
        }
        if (showOnchainWallet.value != torSettings.showOnchainWallet) {
            showOnchainWallet.tryEmit(torSettings.showOnchainWallet)
            any = true
        }
        if (showPayToZapChip.value != torSettings.showPayToZapChip) {
            showPayToZapChip.tryEmit(torSettings.showPayToZapChip)
            any = true
        }
        if (searchEngine.value != torSettings.searchEngine) {
            searchEngine.tryEmit(torSettings.searchEngine)
            any = true
        }
        if (spellCheck.value != torSettings.spellCheck) {
            spellCheck.tryEmit(torSettings.spellCheck)
            any = true
        }
        if (noteLayout.value != torSettings.noteLayout) {
            noteLayout.tryEmit(torSettings.noteLayout)
            any = true
        }
        if (automaticallyDownloadTranslationModels.value != torSettings.automaticallyDownloadTranslationModels) {
            automaticallyDownloadTranslationModels.tryEmit(torSettings.automaticallyDownloadTranslationModels)
            any = true
        }
        if (hideWalletBalances.value != torSettings.hideWalletBalances) {
            hideWalletBalances.tryEmit(torSettings.hideWalletBalances)
            any = true
        }
        if (navigationLayout.value != torSettings.navigationLayout) {
            navigationLayout.tryEmit(torSettings.navigationLayout)
            any = true
        }
        if (chatLayout.value != torSettings.chatLayout) {
            chatLayout.tryEmit(torSettings.chatLayout)
            any = true
        }

        return any
    }

    fun dontShowPushNotificationSelector() {
        if (!dontShowPushNotificationSelector.value) {
            dontShowPushNotificationSelector.tryEmit(true)
        }
    }

    fun dontAskForNotificationPermissions() {
        if (!dontAskForNotificationPermissions.value) {
            dontAskForNotificationPermissions.tryEmit(true)
        }
    }

    fun dontShowOnchainPublicWarning() {
        if (!dontShowOnchainPublicWarning.value) {
            dontShowOnchainPublicWarning.tryEmit(true)
        }
    }

    companion object {
        fun build(uiSettings: UiSettings): UiSettingsFlow =
            UiSettingsFlow(
                MutableStateFlow(uiSettings.theme),
                MutableStateFlow(uiSettings.preferredLanguage),
                MutableStateFlow(uiSettings.automaticallyShowImages),
                MutableStateFlow(uiSettings.automaticallyStartPlayback),
                MutableStateFlow(uiSettings.automaticallyPlayVideos),
                MutableStateFlow(uiSettings.automaticallyShowUrlPreview),
                MutableStateFlow(uiSettings.automaticallyHideNavigationBars),
                MutableStateFlow(uiSettings.automaticallyShowProfilePictures),
                MutableStateFlow(uiSettings.dontShowPushNotificationSelector),
                MutableStateFlow(uiSettings.dontAskForNotificationPermissions),
                MutableStateFlow(uiSettings.featureSet),
                MutableStateFlow(uiSettings.gallerySet),
                MutableStateFlow(uiSettings.automaticallyProposeAiImprovements),
                MutableStateFlow(uiSettings.useTrackedBroadcasts),
                MutableStateFlow(uiSettings.automaticallyCreateDrafts),
                MutableStateFlow(uiSettings.showHomeNewThreadsTab),
                MutableStateFlow(uiSettings.showHomeConversationsTab),
                MutableStateFlow(uiSettings.showHomeEverythingTab),
                MutableStateFlow(uiSettings.showProfileBadges),
                MutableStateFlow(uiSettings.showProfileAppRecommendations),
                MutableStateFlow(uiSettings.showProfileZapReceivedFeed),
                MutableStateFlow(uiSettings.showProfileFollowersFeed),
                MutableStateFlow(uiSettings.dontShowOnchainPublicWarning),
                MutableStateFlow(uiSettings.suggestWorkoutsFromHealthConnect),
                MutableStateFlow(uiSettings.accentColor),
                MutableStateFlow(uiSettings.fontFamily),
                MutableStateFlow(uiSettings.fontSize),
                MutableStateFlow(uiSettings.composeSignature),
                MutableStateFlow(uiSettings.showOnchainWallet),
                MutableStateFlow(uiSettings.showPayToZapChip),
                MutableStateFlow(uiSettings.searchEngine),
                MutableStateFlow(uiSettings.spellCheck),
                MutableStateFlow(uiSettings.noteLayout),
                MutableStateFlow(uiSettings.automaticallyDownloadTranslationModels),
                MutableStateFlow(uiSettings.hideWalletBalances),
                MutableStateFlow(uiSettings.navigationLayout),
                MutableStateFlow(uiSettings.chatLayout),
            )
    }
}
