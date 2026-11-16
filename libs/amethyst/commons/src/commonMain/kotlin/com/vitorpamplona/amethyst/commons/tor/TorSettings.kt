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

data class TorSettings(
    val torType: TorType = TorType.INTERNAL,
    val externalSocksPort: Int = 9050,
    val onionRelaysViaTor: Boolean = true,
    val dmRelaysViaTor: Boolean = true,
    val newRelaysViaTor: Boolean = true,
    val trustedRelaysViaTor: Boolean = false,
    val urlPreviewsViaTor: Boolean = false,
    val profilePicsViaTor: Boolean = false,
    val imagesViaTor: Boolean = false,
    val videosViaTor: Boolean = false,
    val moneyOperationsViaTor: Boolean = false,
    val nip05VerificationsViaTor: Boolean = false,
    val mediaUploadsViaTor: Boolean = false,
    /**
     * Opt-in: show a notification with the internal Tor client's startup progress. Android only;
     * a display preference, so presets ignore it.
     */
    val progressNotification: Boolean = false,
    /**
     * Napplet files, and where nSites and websites in the browser start before the user picks Tor
     * or the open web for that site (the per-site switch still wins). Off: their Blossom files and
     * pages are megabytes, and several servers refuse Tor exits.
     */
    val appletsAndWebsitesViaTor: Boolean = false,
)

enum class TorType(
    val screenCode: Int,
) {
    OFF(0),
    INTERNAL(1),
    EXTERNAL(2),
}

fun parseTorType(code: Int?): TorType =
    when (code) {
        TorType.OFF.screenCode -> TorType.OFF
        TorType.INTERNAL.screenCode -> TorType.INTERNAL
        TorType.EXTERNAL.screenCode -> TorType.EXTERNAL
        else -> TorType.INTERNAL
    }

enum class TorPresetType(
    val screenCode: Int,
) {
    ONLY_WHEN_NEEDED(0),
    DEFAULT(1),
    SMALL_PAYLOADS(2),
    FULL_PRIVACY(3),
    CUSTOM(4),
}

fun parseTorPresetType(code: Int?): TorPresetType =
    when (code) {
        TorPresetType.ONLY_WHEN_NEEDED.screenCode -> TorPresetType.ONLY_WHEN_NEEDED
        TorPresetType.DEFAULT.screenCode -> TorPresetType.DEFAULT
        TorPresetType.SMALL_PAYLOADS.screenCode -> TorPresetType.SMALL_PAYLOADS
        TorPresetType.FULL_PRIVACY.screenCode -> TorPresetType.FULL_PRIVACY
        else -> TorPresetType.CUSTOM
    }

fun isPreset(
    torSettings: TorSettings,
    preset: TorSettings,
): Boolean =
    torSettings.onionRelaysViaTor == preset.onionRelaysViaTor &&
        torSettings.dmRelaysViaTor == preset.dmRelaysViaTor &&
        torSettings.newRelaysViaTor == preset.newRelaysViaTor &&
        torSettings.trustedRelaysViaTor == preset.trustedRelaysViaTor &&
        torSettings.urlPreviewsViaTor == preset.urlPreviewsViaTor &&
        // torSettings.profilePicsViaTor == preset.profilePicsViaTor &&
        torSettings.imagesViaTor == preset.imagesViaTor &&
        torSettings.videosViaTor == preset.videosViaTor &&
        torSettings.moneyOperationsViaTor == preset.moneyOperationsViaTor &&
        torSettings.nip05VerificationsViaTor == preset.nip05VerificationsViaTor &&
        torSettings.mediaUploadsViaTor == preset.mediaUploadsViaTor &&
        torSettings.appletsAndWebsitesViaTor == preset.appletsAndWebsitesViaTor

fun whichPreset(torSettings: TorSettings): TorPresetType {
    if (isPreset(torSettings, torOnlyWhenNeededPreset)) return TorPresetType.ONLY_WHEN_NEEDED
    if (isPreset(torSettings, torDefaultPreset)) return TorPresetType.DEFAULT
    if (isPreset(torSettings, torSmallPayloadsPreset)) return TorPresetType.SMALL_PAYLOADS
    if (isPreset(torSettings, torFullyPrivate)) return TorPresetType.FULL_PRIVACY
    return TorPresetType.CUSTOM
}

val torOnlyWhenNeededPreset =
    TorSettings(
        onionRelaysViaTor = true,
        dmRelaysViaTor = false,
        newRelaysViaTor = false,
        trustedRelaysViaTor = false,
        urlPreviewsViaTor = false,
        profilePicsViaTor = false,
        imagesViaTor = false,
        videosViaTor = false,
        moneyOperationsViaTor = false,
        nip05VerificationsViaTor = false,
        mediaUploadsViaTor = false,
        appletsAndWebsitesViaTor = false,
    )
val torDefaultPreset =
    TorSettings(
        onionRelaysViaTor = true,
        dmRelaysViaTor = true,
        newRelaysViaTor = true,
        trustedRelaysViaTor = false,
        urlPreviewsViaTor = false,
        profilePicsViaTor = false,
        imagesViaTor = false,
        videosViaTor = false,
        moneyOperationsViaTor = false,
        nip05VerificationsViaTor = false,
        mediaUploadsViaTor = false,
        appletsAndWebsitesViaTor = false,
    )
val torSmallPayloadsPreset =
    TorSettings(
        onionRelaysViaTor = true,
        dmRelaysViaTor = true,
        newRelaysViaTor = true,
        trustedRelaysViaTor = true,
        urlPreviewsViaTor = true,
        profilePicsViaTor = true,
        imagesViaTor = false,
        videosViaTor = false,
        moneyOperationsViaTor = true,
        nip05VerificationsViaTor = true,
        mediaUploadsViaTor = false,
        appletsAndWebsitesViaTor = false,
    )
val torFullyPrivate =
    TorSettings(
        onionRelaysViaTor = true,
        dmRelaysViaTor = true,
        newRelaysViaTor = true,
        trustedRelaysViaTor = true,
        urlPreviewsViaTor = true,
        profilePicsViaTor = true,
        imagesViaTor = true,
        videosViaTor = true,
        moneyOperationsViaTor = true,
        nip05VerificationsViaTor = true,
        mediaUploadsViaTor = true,
        appletsAndWebsitesViaTor = true,
    )
