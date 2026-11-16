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
package com.vitorpamplona.amethyst.commons.tor

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

@Stable
class TorSettingsFlow(
    val torType: MutableStateFlow<TorType> = MutableStateFlow(TorType.INTERNAL),
    val externalSocksPort: MutableStateFlow<Int> = MutableStateFlow(9050),
    val onionRelaysViaTor: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val dmRelaysViaTor: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val newRelaysViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val trustedRelaysViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val urlPreviewsViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val profilePicsViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val imagesViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val videosViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val moneyOperationsViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val nip05VerificationsViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val mediaUploadsViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val progressNotification: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val appletsAndWebsitesViaTor: MutableStateFlow<Boolean> = MutableStateFlow(false),
) {
    // emits at every change in any of the properties.
    val propertyWatchFlow =
        combine<Any, TorSettings>(
            listOf(
                torType,
                externalSocksPort,
                onionRelaysViaTor,
                dmRelaysViaTor,
                newRelaysViaTor,
                trustedRelaysViaTor,
                urlPreviewsViaTor,
                profilePicsViaTor,
                imagesViaTor,
                videosViaTor,
                moneyOperationsViaTor,
                nip05VerificationsViaTor,
                mediaUploadsViaTor,
                progressNotification,
                appletsAndWebsitesViaTor,
            ),
        ) { flows ->
            TorSettings(
                flows[0] as TorType,
                flows[1] as Int,
                flows[2] as Boolean,
                flows[3] as Boolean,
                flows[4] as Boolean,
                flows[5] as Boolean,
                flows[6] as Boolean,
                flows[7] as Boolean,
                flows[8] as Boolean,
                flows[9] as Boolean,
                flows[10] as Boolean,
                flows[11] as Boolean,
                flows[12] as Boolean,
                flows[13] as Boolean,
                flows[14] as Boolean,
            )
        }

    fun toSettings(): TorSettings =
        TorSettings(
            torType.value,
            externalSocksPort.value,
            onionRelaysViaTor.value,
            dmRelaysViaTor.value,
            newRelaysViaTor.value,
            trustedRelaysViaTor.value,
            urlPreviewsViaTor.value,
            profilePicsViaTor.value,
            imagesViaTor.value,
            videosViaTor.value,
            moneyOperationsViaTor.value,
            nip05VerificationsViaTor.value,
            mediaUploadsViaTor.value,
            progressNotification.value,
            appletsAndWebsitesViaTor.value,
        )

    fun update(torSettings: TorSettings): Boolean {
        var any = false

        if (torType.value != torSettings.torType) {
            torType.tryEmit(torSettings.torType)
            any = true
        }
        if (externalSocksPort.value != torSettings.externalSocksPort) {
            externalSocksPort.tryEmit(torSettings.externalSocksPort)
            any = true
        }
        if (onionRelaysViaTor.value != torSettings.onionRelaysViaTor) {
            onionRelaysViaTor.tryEmit(torSettings.onionRelaysViaTor)
            any = true
        }
        if (dmRelaysViaTor.value != torSettings.dmRelaysViaTor) {
            dmRelaysViaTor.tryEmit(torSettings.dmRelaysViaTor)
            any = true
        }
        if (newRelaysViaTor.value != torSettings.newRelaysViaTor) {
            newRelaysViaTor.tryEmit(torSettings.newRelaysViaTor)
            any = true
        }
        if (trustedRelaysViaTor.value != torSettings.trustedRelaysViaTor) {
            trustedRelaysViaTor.tryEmit(torSettings.trustedRelaysViaTor)
            any = true
        }
        if (urlPreviewsViaTor.value != torSettings.urlPreviewsViaTor) {
            urlPreviewsViaTor.tryEmit(torSettings.urlPreviewsViaTor)
            any = true
        }
        if (profilePicsViaTor.value != torSettings.profilePicsViaTor) {
            profilePicsViaTor.tryEmit(torSettings.profilePicsViaTor)
            any = true
        }
        if (imagesViaTor.value != torSettings.imagesViaTor) {
            imagesViaTor.tryEmit(torSettings.imagesViaTor)
            any = true
        }
        if (videosViaTor.value != torSettings.videosViaTor) {
            videosViaTor.tryEmit(torSettings.videosViaTor)
            any = true
        }
        if (moneyOperationsViaTor.value != torSettings.moneyOperationsViaTor) {
            moneyOperationsViaTor.tryEmit(torSettings.moneyOperationsViaTor)
            any = true
        }

        if (nip05VerificationsViaTor.value != torSettings.nip05VerificationsViaTor) {
            nip05VerificationsViaTor.tryEmit(torSettings.nip05VerificationsViaTor)
            any = true
        }
        if (mediaUploadsViaTor.value != torSettings.mediaUploadsViaTor) {
            mediaUploadsViaTor.tryEmit(torSettings.mediaUploadsViaTor)
            any = true
        }
        if (progressNotification.value != torSettings.progressNotification) {
            progressNotification.tryEmit(torSettings.progressNotification)
            any = true
        }
        if (appletsAndWebsitesViaTor.value != torSettings.appletsAndWebsitesViaTor) {
            appletsAndWebsitesViaTor.tryEmit(torSettings.appletsAndWebsitesViaTor)
            any = true
        }

        return any
    }

    companion object {
        fun build(torSettings: TorSettings): TorSettingsFlow =
            TorSettingsFlow(
                MutableStateFlow(torSettings.torType),
                MutableStateFlow(torSettings.externalSocksPort),
                MutableStateFlow(torSettings.onionRelaysViaTor),
                MutableStateFlow(torSettings.dmRelaysViaTor),
                MutableStateFlow(torSettings.newRelaysViaTor),
                MutableStateFlow(torSettings.trustedRelaysViaTor),
                MutableStateFlow(torSettings.urlPreviewsViaTor),
                MutableStateFlow(torSettings.profilePicsViaTor),
                MutableStateFlow(torSettings.imagesViaTor),
                MutableStateFlow(torSettings.videosViaTor),
                MutableStateFlow(torSettings.moneyOperationsViaTor),
                MutableStateFlow(torSettings.nip05VerificationsViaTor),
                MutableStateFlow(torSettings.mediaUploadsViaTor),
                MutableStateFlow(torSettings.progressNotification),
                MutableStateFlow(torSettings.appletsAndWebsitesViaTor),
            )
    }
}
