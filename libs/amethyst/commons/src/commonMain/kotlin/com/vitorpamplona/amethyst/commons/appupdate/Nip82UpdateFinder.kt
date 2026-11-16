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
package com.vitorpamplona.amethyst.commons.appupdate

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.isNip82SoftwareRelease
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.Nip82VersionComparator
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent

/**
 * Picks the update to offer from NIP-82 events, trusting nothing a relay says on its own: a release
 * counts only when [publisher] signed it, for [appId], on a stable channel, for the target's
 * platform, and newer than the running version. Its file must be one of the release's own assets
 * (the release's `e` tags fix their ids, and so their content, under the release's signature), signed
 * by the same key, for the same version and platform, with an https URL and a SHA-256.
 *
 * Pure: the relay fetch is [Nip82UpdateSource]'s.
 */
class Nip82UpdateFinder(
    private val appId: String = DesktopRelease.APP_ID,
    private val publisher: HexKey = DesktopRelease.PUBLISHER,
) {
    fun find(
        currentVersion: String,
        target: UpdateTarget,
        releases: List<Event>,
        assets: List<Event>,
    ): AvailableUpdate? = newestRelease(currentVersion, target, releases)?.let { update(it, target, assets) }

    /**
     * The newest trusted release above [currentVersion]. The cheap checks and the sort run first and
     * the signature only on the candidates in order, so the releases a relay sends that are older than
     * the running version cost no signature check.
     */
    fun newestRelease(
        currentVersion: String,
        target: UpdateTarget,
        releases: List<Event>,
    ): ReleaseArtifactSetEvent? =
        releases
            .mapNotNull { it.asCandidateRelease(target.platform) }
            .filter { Nip82VersionComparator.compare(it.version()!!, currentVersion) > 0 }
            .sortedWith(compareByDescending<ReleaseArtifactSetEvent, String>(Nip82VersionComparator) { it.version()!! }.thenByDescending { it.createdAt })
            .firstOrNull { it.verify() }

    /** The update [release] offers, with its file for [target] from [assets] (the release's own, signed ones only). */
    fun update(
        release: ReleaseArtifactSetEvent,
        target: UpdateTarget,
        assets: List<Event>,
    ): AvailableUpdate {
        val version = release.version()!!
        val ownAssets = release.assets().mapTo(HashSet()) { it.eventId }
        val candidates =
            assets
                .asSequence()
                .filter { it.id in ownAssets }
                .mapNotNull { it.asTrustedAsset(version, target.platform) }
                .toList()

        val chosen =
            if (target.packages.isEmpty()) {
                candidates.firstOrNull()
            } else {
                target.packages.firstNotNullOfOrNull { wanted -> candidates.firstOrNull { it.packageType == wanted } }
            }

        return AvailableUpdate(
            version = version,
            releaseNotes = release.releaseNotes(),
            // An install the app only tells about takes any file as proof the platform has a build,
            // but has nothing to apply.
            asset = chosen?.takeIf { target.packages.isNotEmpty() },
            pageUrl = DesktopRelease.releasePage(version),
            publishedAt = release.createdAt,
        )
    }

    /** A release that passes every check but the signature, which [newestRelease] checks last. */
    private fun Event.asCandidateRelease(platform: String): ReleaseArtifactSetEvent? {
        if (kind != ReleaseArtifactSetEvent.KIND || pubKey != publisher) return null
        if (!isNip82SoftwareRelease()) return null
        val release = this as? ReleaseArtifactSetEvent ?: ReleaseArtifactSetEvent(id, pubKey, createdAt, tags, content, sig)
        if (release.appId() != appId) return null
        if (!DesktopRelease.isStableChannel(release.channel())) return null
        val version = release.version()
        if (version.isNullOrBlank()) return null
        val platforms = release.platforms()
        if (platforms.isNotEmpty() && platform !in platforms) return null
        return release
    }

    private fun Event.asTrustedAsset(
        version: String,
        platform: String,
    ): UpdateAsset? {
        if (kind != SoftwareAssetEvent.KIND || pubKey != publisher) return null
        val asset = this as? SoftwareAssetEvent ?: SoftwareAssetEvent(id, pubKey, createdAt, tags, content, sig)
        if (asset.appId() != appId) return null
        asset.version()?.let { if (it != version) return null }
        if (platform !in asset.platforms()) return null
        val url = asset.url()?.takeIf { it.startsWith("https://") } ?: return null
        val hash = asset.hash()?.lowercase()?.takeIf { SHA256.matches(it) } ?: return null
        if (!verify()) return null
        return UpdateAsset(
            url = url,
            sha256 = hash,
            sizeInBytes = asset.sizeInBytes()?.toLong(),
            packageType = DesktopPackage.fromMimeType(asset.mimeType()),
            filename = asset.filename(),
        )
    }

    companion object {
        private val SHA256 = Regex("[0-9a-f]{64}")
    }
}
