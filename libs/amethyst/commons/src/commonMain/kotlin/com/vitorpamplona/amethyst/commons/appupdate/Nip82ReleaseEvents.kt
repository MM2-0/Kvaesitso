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

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.ReleaseChannel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The release event every app `amy releases publish` signs for shares one shape: the kind 30063
 * `<app-id>@<version>` that names the app's listing and its signed assets, on the channel the version
 * says. Only the app id differs ([DesktopRelease], [AndroidRelease], [FlatpakRelease]).
 */
object Nip82ReleaseEvents {
    private val STABLE_VERSION = Regex("""\d+\.\d+\.\d+""")

    /**
     * `main` for a stable version (`X.Y.Z`, as the release workflow classifies tags), `beta` for
     * anything else: the desktop app, Zapstore and Orchard only install from `main`.
     */
    fun channelFor(version: String) = if (STABLE_VERSION.matches(version)) ReleaseChannel.MAIN else ReleaseChannel.BETA

    /** The release must be built from the *signed* assets, since it names them by id. */
    fun release(
        appId: String,
        publisher: HexKey,
        version: String,
        releaseNotes: String,
        signedAssets: List<SoftwareAssetEvent>,
        relayHint: NormalizedRelayUrl?,
        createdAt: Long = TimeUtils.now(),
    ) = ReleaseArtifactSetEvent.buildSoftwareRelease(
        app = ATag(SoftwareApplicationEvent.KIND, publisher, appId, relayHint),
        version = version,
        channel = channelFor(version),
        assets = signedAssets.map { EventHintBundle(it, relayHint) },
        releaseNotes = releaseNotes,
        createdAt = createdAt,
    )
}
