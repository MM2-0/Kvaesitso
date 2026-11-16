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
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.filename
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.platform
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The NIP-82 events Orchard installs the Flatpak from, in the shape Orchard 0.2.0 reads for a
 * bundle served over https (the shape Zapstore uses for its own files):
 *
 * - per bundle, an asset with the GitHub URL, its sha256 as `x`, `m` = [FlatpakRelease.MIME_TYPE]
 *   and the arch as `f` (Orchard picks the asset whose `f` is the machine's);
 * - one release `<id>@<version>` naming both ([Nip82ReleaseEvents.release]), on `main` (Orchard
 *   reads `main` as its `stable`);
 * - the listing, whose `h` tag is the homepage in Orchard's reading (Zapstore reads `h` as its
 *   community, which is why the two listings can never share an app id).
 */
object FlatpakReleaseEvents {
    fun asset(
        bundle: HashedFlatpakBundle,
        version: String,
        createdAt: Long = TimeUtils.now(),
    ) = SoftwareAssetEvent.build(
        appId = FlatpakRelease.APP_ID,
        mimeType = FlatpakRelease.MIME_TYPE,
        hash = bundle.sha256,
        version = version,
        assetUrl = bundle.url,
        sizeInBytes = bundle.sizeInBytes.toInt(),
        createdAt = createdAt,
    ) {
        platform(bundle.platform)
        filename(bundle.filename)
    }

    /** Tag for tag the listing orchard-publish signed, so a republish changes nothing Orchard shows. */
    fun listing(createdAt: Long = TimeUtils.now()) =
        eventTemplate<SoftwareApplicationEvent>(SoftwareApplicationEvent.KIND, FlatpakRelease.DESCRIPTION, createdAt) {
            add(arrayOf("d", FlatpakRelease.APP_ID))
            add(arrayOf("name", FlatpakRelease.NAME))
            add(arrayOf("summary", FlatpakRelease.SUMMARY))
            add(arrayOf("icon", FlatpakRelease.ICON))
            add(arrayOf("license", FlatpakRelease.LICENSE))
            add(arrayOf("repository", FlatpakRelease.REPOSITORY))
            add(arrayOf("h", FlatpakRelease.HOMEPAGE))
            FlatpakRelease.CATEGORIES.forEach { add(arrayOf("t", it)) }
            FlatpakRelease.PLATFORMS.forEach { add(arrayOf("f", it)) }
        }
}
