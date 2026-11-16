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
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.icon
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.images
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.license
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.repository
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.summary
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.url
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.apkCertificateHash
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.filename
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.minPlatformVersion
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.platforms
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.supportedNips
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.targetPlatformVersion
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.variant
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.versionCode
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.PlatformTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.UrlTag
import com.vitorpamplona.quartz.utils.TimeUtils

/** One APK, inspected and hashed, with the URLs it is served from (GitHub first, then mirrors). */
data class HashedApk(
    val file: AndroidRelease.ApkFile,
    val info: ApkInfo,
    val sha256: String,
    val sizeInBytes: Long,
    val urls: List<String>,
)

/**
 * The NIP-82 events of an Android release, tag for tag what zsp publishes, so Zapstore reads them
 * as before: per APK a kind 3063 asset with `version_code`, `apk_certificate_hash`, the SDK range
 * and the ABIs as platforms; the kind 30063 release naming the assets; and, when asked, the kind
 * 32267 listing from `zapstore.yaml`.
 */
object AndroidReleaseEvents {
    fun asset(
        apk: HashedApk,
        version: String,
        supportedNips: List<String>,
        createdAt: Long = TimeUtils.now(),
    ) = SoftwareAssetEvent.build(
        appId = apk.info.packageName,
        mimeType = AndroidRelease.APK_MIME_TYPE,
        hash = apk.sha256,
        version = version,
        sizeInBytes = apk.sizeInBytes.toInt(),
        createdAt = createdAt,
    ) {
        apk.urls.forEach { add(UrlTag.assemble(it)) }
        platforms(apk.info.platforms)
        minPlatformVersion(apk.info.minSdk.toString())
        targetPlatformVersion(apk.info.targetSdk.toString())
        filename(apk.file.filename)
        variant(apk.file.flavor.variant)
        supportedNips(supportedNips)
        versionCode(apk.info.versionCode)
        apkCertificateHash(apk.info.certificateSha256)
    }

    /**
     * The listing, from [config] and the hosted [iconUrl] and [imageUrls] (zsp uploads both to
     * Blossom). [platforms] are the ones the release's APKs cover.
     */
    fun listing(
        config: ZapstoreConfig,
        iconUrl: String?,
        imageUrls: List<String>,
        platforms: List<String>,
        createdAt: Long = TimeUtils.now(),
    ) = SoftwareApplicationEvent.build(AndroidRelease.APP_ID, config.name ?: DesktopRelease.APP_NAME, config.description.orEmpty(), createdAt) {
        config.summary?.let { summary(it) }
        iconUrl?.let { icon(it) }
        images(imageUrls)
        config.tags.forEach { add(HashtagTag.assemble(it)) }
        config.website?.let { url(it) }
        config.repository?.let { repository(it) }
        platforms.distinct().forEach { add(PlatformTag.assemble(it)) }
        config.license?.let { license(it) }
        config.communities.ifEmpty { listOf(AndroidRelease.ZAPSTORE_COMMUNITY) }.forEach { add(arrayOf("h", it)) }
    }
}
