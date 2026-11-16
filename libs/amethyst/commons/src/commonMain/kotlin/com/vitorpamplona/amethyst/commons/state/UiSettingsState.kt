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
package com.vitorpamplona.amethyst.commons.state

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.BooleanType
import com.vitorpamplona.amethyst.commons.model.ConnectivityType
import com.vitorpamplona.amethyst.commons.model.FeatureSetType
import com.vitorpamplona.amethyst.commons.model.ProfileGalleryType
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Stable
class UiSettingsState(
    val uiSettingsFlow: UiSettingsFlow,
    val isMobileOrMeteredConnection: StateFlow<Boolean>,
    val scope: CoroutineScope,
) {
    val showProfilePictures = connectivityState(uiSettingsFlow.automaticallyShowProfilePictures)

    val showUrlPreview = connectivityState(uiSettingsFlow.automaticallyShowUrlPreview)

    val startVideoPlayback = connectivityState(uiSettingsFlow.automaticallyStartPlayback)

    val showImages = connectivityState(uiSettingsFlow.automaticallyShowImages)

    /** When the on-device translator may download a language model, on the current connection. */
    val translationModelDownloads: StateFlow<ModelDownloads> =
        combine(
            uiSettingsFlow.automaticallyDownloadTranslationModels,
            isMobileOrMeteredConnection,
        ) { choice, isOnMobileOrMeteredConnection ->
            ModelDownloads.of(choice, isOnMobileOrMeteredConnection)
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            ModelDownloads.of(uiSettingsFlow.automaticallyDownloadTranslationModels.value, isMobileOrMeteredConnection.value),
        )

    private fun connectivityState(choice: StateFlow<ConnectivityType>): StateFlow<Boolean> =
        combine(choice, isMobileOrMeteredConnection) { type, isOnMobileOrMeteredConnection ->
            type.allows(isOnMobileOrMeteredConnection)
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            choice.value.allows(isMobileOrMeteredConnection.value),
        )

    fun modernGalleryStyle() =
        when (uiSettingsFlow.gallerySet.value) {
            ProfileGalleryType.CLASSIC -> false
            ProfileGalleryType.MODERN -> true
        }

    fun isPerformanceMode() = uiSettingsFlow.featureSet.value == FeatureSetType.PERFORMANCE

    fun isNotPerformanceMode() = uiSettingsFlow.featureSet.value != FeatureSetType.PERFORMANCE

    fun isCompleteUIMode() = uiSettingsFlow.featureSet.value == FeatureSetType.COMPLETE

    fun useTrackedBroadcasts() = uiSettingsFlow.useTrackedBroadcasts.value == BooleanType.ALWAYS

    fun automaticallyCreateDrafts() = uiSettingsFlow.automaticallyCreateDrafts.value == BooleanType.ALWAYS

    fun isImmersiveScrollingActive() = uiSettingsFlow.automaticallyHideNavigationBars.value == BooleanType.ALWAYS

    fun showProfilePictures() = showProfilePictures.value

    fun showUrlPreview() = showUrlPreview.value

    fun startVideoPlayback() = startVideoPlayback.value

    fun autoPlayVideos() = uiSettingsFlow.automaticallyPlayVideos.value == BooleanType.ALWAYS

    val autoPlayVideosFlow: StateFlow<Boolean> =
        uiSettingsFlow.automaticallyPlayVideos
            .map { it == BooleanType.ALWAYS }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                uiSettingsFlow.automaticallyPlayVideos.value == BooleanType.ALWAYS,
            )

    fun showImages() = showImages.value
}

/** Whether a model download may start now and, if it may, whether it must stay on unmetered networks. */
enum class ModelDownloads {
    ANY_NETWORK,
    UNMETERED_ONLY,
    NONE,
    ;

    companion object {
        fun of(
            choice: ConnectivityType,
            isOnMobileOrMeteredConnection: Boolean,
        ): ModelDownloads =
            when {
                !choice.allows(isOnMobileOrMeteredConnection) -> NONE
                choice == ConnectivityType.WIFI_ONLY -> UNMETERED_ONLY
                else -> ANY_NETWORK
            }
    }
}
