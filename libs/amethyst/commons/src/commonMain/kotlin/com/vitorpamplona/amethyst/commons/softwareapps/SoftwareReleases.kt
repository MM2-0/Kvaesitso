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
package com.vitorpamplona.amethyst.commons.softwareapps

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.isNip82SoftwareRelease
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.AppIdTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.ReleaseChannel
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.Nip82VersionComparator
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent

/**
 * Which NIP-82 releases (kind 30063) belong to an application (kind 32267), and in what order.
 *
 * A release names its app with the required `a` tag, whose pubkey is the app's publisher and
 * need not be the release's signer. Anyone can sign a release pointing at any app, so a release
 * only counts for an app when its signer is the app's publisher or a pubkey the publisher lists
 * as a `p` tag on the app (NIP-82 Appendix E). Without that, a stranger could publish a newer
 * "version" with their own download links and it would surface as the app's latest release.
 */
object SoftwareReleases {
    /** The app's publisher, then the pubkeys it credits with `p` tags. */
    fun trustedSigners(app: SoftwareApplicationEvent): List<HexKey> = (listOf(app.pubKey) + app.tags.mapNotNull(PTag::parseKey)).distinct()

    /** True when [release] is a NIP-82 release of [app] signed by one of its [trustedSigners]. */
    fun isReleaseOf(
        release: ReleaseArtifactSetEvent,
        app: SoftwareApplicationEvent,
    ): Boolean = ReleaseMatcher(app).matches(release)

    /**
     * [isReleaseOf] for many releases of one app: the trusted signers and the app's
     * coordinates are read once, not once per release.
     */
    class ReleaseMatcher(
        app: SoftwareApplicationEvent,
    ) {
        private val publisher = app.pubKey
        private val appId = app.appId()
        private val signers = trustedSigners(app).toHashSet()

        fun matches(release: ReleaseArtifactSetEvent): Boolean {
            if (release.pubKey !in signers) return false
            if (!release.isNip82SoftwareRelease()) return false

            val pointer = release.app()
            return if (pointer != null) {
                pointer.pubKeyHex == publisher && pointer.dTag == appId
            } else {
                // Releases without the required `a` can only name the signer's own app.
                release.pubKey == publisher && release.appId() == appId
            }
        }
    }

    /**
     * True when the release names an app in its own signer's namespace (its `a` pubkey is
     * the signer, or it has no `a` and so falls back to the signer's app). Such a release
     * cannot pass itself off as someone else's app, so it is safe to show on its own when that
     * app event is missing; most releases in the wild have no `a` and no findable app.
     */
    fun isSelfPublished(release: ReleaseArtifactSetEvent): Boolean = (release.app()?.pubKeyHex ?: release.pubKey) == release.pubKey

    /**
     * Whether a release's app branding and downloads may be shown. With [app] loaded, the
     * release must be one of its own ([isReleaseOf]); without it, only a [isSelfPublished]
     * release may be, since one pointing at another publisher's app is not verifiable yet.
     */
    fun canShow(
        release: ReleaseArtifactSetEvent,
        app: SoftwareApplicationEvent?,
    ): Boolean = if (app != null) isReleaseOf(release, app) else isSelfPublished(release)

    /**
     * The OSes a release ships for: its aggregate `f` tags, or, for the releases that omit
     * them (NIP-82 requires them), its app's.
     */
    fun oses(
        release: ReleaseArtifactSetEvent,
        app: SoftwareApplicationEvent?,
    ): List<SoftwareOs> = SoftwarePlatforms.osesOfPlatforms(release.platforms().ifEmpty { app?.platforms().orEmpty() })

    /**
     * The cache/relay filter that can hold [app]'s releases. `i` is required on every release and
     * equals the app's `d`; [isReleaseOf] then checks the `a` address and the signer.
     */
    fun filter(app: SoftwareApplicationEvent) =
        Filter(
            kinds = listOf(ReleaseArtifactSetEvent.KIND),
            authors = trustedSigners(app),
            tags = mapOf(AppIdTag.TAG_NAME to listOf(app.appId())),
        )

    /** Highest version first (NIP-82 Appendix D); the newer event breaks ties. */
    val NewestFirst: Comparator<ReleaseArtifactSetEvent> =
        Comparator { a, b ->
            val byVersion = Nip82VersionComparator.compare(b.version().orEmpty(), a.version().orEmpty())
            if (byVersion != 0) byVersion else b.createdAt.compareTo(a.createdAt)
        }

    /** [releases] of one app, newest version first. */
    fun sorted(releases: Collection<ReleaseArtifactSetEvent>): List<ReleaseArtifactSetEvent> = releases.sortedWith(NewestFirst)

    /**
     * The release to offer as current: the highest `main` version, since `main` is the default
     * channel and a nightly should not replace it. Falls back to the highest of any channel for
     * apps that publish no `main` release.
     */
    fun latest(releases: Collection<ReleaseArtifactSetEvent>): ReleaseArtifactSetEvent? =
        releases.filter { isMainChannel(it.channel()) }.minWithOrNull(NewestFirst)
            ?: releases.minWithOrNull(NewestFirst)

    /** An app's releases arranged for its page; see [arrange]. */
    class Arranged(
        val latest: ReleaseArtifactSetEvent?,
        val preReleases: List<ReleaseArtifactSetEvent>,
        val older: List<ReleaseArtifactSetEvent>,
    )

    /**
     * Splits newest-first [sortedReleases] around [latest]: the beta/nightly builds ahead of it
     * are [Arranged.preReleases], and everything below it is [Arranged.older].
     */
    fun arrange(sortedReleases: List<ReleaseArtifactSetEvent>): Arranged {
        val latest = latest(sortedReleases) ?: return Arranged(null, emptyList(), emptyList())
        val index = sortedReleases.indexOf(latest)
        return Arranged(latest, sortedReleases.subList(0, index), sortedReleases.subList(index + 1, sortedReleases.size))
    }

    /** A missing channel is treated as `main`, the spec's default. */
    fun isMainChannel(channel: String?) = channel == null || channel.equals(ReleaseChannel.MAIN, ignoreCase = true)
}
