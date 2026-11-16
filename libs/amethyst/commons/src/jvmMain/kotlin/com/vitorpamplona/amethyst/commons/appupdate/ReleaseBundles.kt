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

import com.vitorpamplona.amethyst.commons.service.upload.BlossomAuth
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.File

/** One app's signed release, ready to publish: assets first, then the release, then the listing. */
class SignedRelease(
    val appId: String,
    val relays: List<String>,
    val assets: List<SoftwareAssetEvent>,
    val release: ReleaseArtifactSetEvent,
    val listing: SoftwareApplicationEvent?,
    /** Per asset, what `amy` reports about the file. */
    val files: List<Map<String, Any?>>,
) {
    val events get() = assets + release + listOfNotNull(listing)
}

class ReleaseFilesException(
    message: String,
) : Exception(message)

/**
 * Turns a folder of release files into signed NIP-82 events, for `amy releases publish`: the
 * desktop files under [DesktopRelease.APP_ID], the APKs under [AndroidRelease.APP_ID] the way zsp
 * did, and the Flatpak bundles under [FlatpakRelease.APP_ID] for Orchard. Reading,
 * checking and hashing the files is here; publishing is the caller's.
 */
object ReleaseBundles {
    fun desktopAssets(
        dir: File,
        version: String,
        urlBase: String?,
    ): List<HashedDesktopAsset> =
        files(dir).mapNotNull { file ->
            DesktopAssetNames.classify(file.name, version)?.let {
                HashedDesktopAsset(it, ApkInspector.sha256(file), file.length(), urlFor(file.name, version, urlBase))
            }
        }

    suspend fun signDesktop(
        signer: NostrSigner,
        version: String,
        notes: String,
        assets: List<HashedDesktopAsset>,
        relays: List<String>,
        withListing: Boolean,
    ): SignedRelease =
        signRelease(
            appId = DesktopRelease.APP_ID,
            signer = signer,
            version = version,
            notes = notes,
            relays = relays,
            files = assets,
            asset = { asset, _ -> DesktopReleaseEvents.asset(asset, version) },
            listing = if (withListing) DesktopReleaseEvents.listing() else null,
        ) { asset, event ->
            report(asset.file.filename, listOf(asset.file.platform), asset.file.packageType.mimeType, asset.sha256, asset.sizeInBytes, listOf(asset.url), event)
        }

    /**
     * The APKs of [flavor] for [abis] in [dir], signature-checked and read. Refuses an APK of
     * another package or version, and a set signed by more than one certificate: Zapstore installs
     * an update only over the same signer.
     */
    fun androidApks(
        dir: File,
        version: String,
        flavor: AndroidRelease.Flavor,
        abis: List<String>,
        urlBase: String?,
    ): List<Pair<File, HashedApk>> {
        val apks =
            files(dir).mapNotNull { file ->
                AndroidRelease.classify(file.name, version)?.takeIf { it.flavor == flavor && it.abi in abis }?.let { file to it }
            }
        val hashed =
            apks.map { (file, name) ->
                val info =
                    try {
                        ApkInspector.inspect(file)
                    } catch (e: ApkInspector.InvalidApkException) {
                        throw ReleaseFilesException(e.message ?: "${file.name} does not verify")
                    }
                if (info.packageName != AndroidRelease.APP_ID) throw ReleaseFilesException("${file.name} is ${info.packageName}, not ${AndroidRelease.APP_ID}")
                if (info.versionName != version) throw ReleaseFilesException("${file.name} is version ${info.versionName}, not $version")
                file to HashedApk(name, info, ApkInspector.sha256(file), file.length(), listOf(urlFor(file.name, version, urlBase)))
            }
        val certificates = hashed.map { it.second.info.certificateSha256 }.distinct()
        if (certificates.size > 1) throw ReleaseFilesException("The APKs are signed by different certificates: $certificates")
        return hashed
    }

    /**
     * Signs the Android release. With several APKs, the arm64-v8a one gets the newest timestamp:
     * Zapstore's detail screen shows the newest asset of an app without asking for a platform, and
     * arm64-v8a is the platform Zapstore installs for.
     */
    suspend fun signAndroid(
        signer: NostrSigner,
        version: String,
        notes: String,
        apks: List<HashedApk>,
        supportedNips: List<String>,
        relays: List<String>,
        listing: ListingMedia?,
        config: ZapstoreConfig,
    ): SignedRelease {
        val now = TimeUtils.now()
        val platforms = apks.flatMap { it.info.platforms }.distinct()
        return signRelease(
            appId = AndroidRelease.APP_ID,
            signer = signer,
            version = version,
            notes = notes,
            relays = relays,
            files = apks,
            now = now,
            asset = { apk, createdAt -> AndroidReleaseEvents.asset(apk, version, supportedNips, if (apks.size == 1 || apk.file.abi == "arm64-v8a") createdAt else createdAt - 1) },
            listing = listing?.let { AndroidReleaseEvents.listing(config, it.iconUrl, it.imageUrls, platforms, now) },
        ) { apk, event ->
            report(apk.file.filename, apk.info.platforms, AndroidRelease.APK_MIME_TYPE, apk.sha256, apk.sizeInBytes, apk.urls, event) +
                mapOf(
                    "version_code" to apk.info.versionCode,
                    "min_sdk" to apk.info.minSdk,
                    "target_sdk" to apk.info.targetSdk,
                    "certificate_sha256" to apk.info.certificateSha256,
                    "variant" to apk.file.flavor.variant,
                )
        }
    }

    /**
     * The Flatpak bundles in [dir] (`amethyst-desktop-<version>-linux-<x64|arm64>.flatpak`), each
     * checked to be this app for its arch by the ref in its header, then hashed.
     */
    fun flatpakBundles(
        dir: File,
        version: String,
        urlBase: String?,
    ): List<HashedFlatpakBundle> =
        files(dir).mapNotNull { file ->
            FlatpakRelease.platformOfBundle(file.name, version)?.let { platform ->
                val head = file.inputStream().use { it.readNBytes(64 * 1024) }
                val ref = FlatpakRelease.bundleRef(head) ?: throw ReleaseFilesException("${file.name} is not a Flatpak bundle")
                val expected = "app/${FlatpakRelease.APP_ID}/${FlatpakRelease.refArch(platform)}/"
                if (!ref.startsWith(expected)) throw ReleaseFilesException("${file.name} is $ref, not $expected…")
                HashedFlatpakBundle(file.name, platform, ApkInspector.sha256(file), file.length(), urlFor(file.name, version, urlBase))
            }
        }

    /** Signs Orchard's events: an asset per bundle, the release naming them, the listing when asked. */
    suspend fun signFlatpak(
        signer: NostrSigner,
        version: String,
        notes: String,
        bundles: List<HashedFlatpakBundle>,
        relays: List<String>,
        withListing: Boolean,
    ): SignedRelease =
        signRelease(
            appId = FlatpakRelease.APP_ID,
            signer = signer,
            version = version,
            notes = notes,
            relays = relays,
            files = bundles,
            asset = { bundle, _ -> FlatpakReleaseEvents.asset(bundle, version) },
            listing = if (withListing) FlatpakReleaseEvents.listing() else null,
        ) { bundle, event ->
            report(bundle.filename, listOf(bundle.platform), FlatpakRelease.MIME_TYPE, bundle.sha256, bundle.sizeInBytes, listOf(bundle.url), event)
        }

    /**
     * One app's release: an asset per file (built by [asset], given the release's time), then the
     * release naming the signed assets ([Nip82ReleaseEvents.release]), then [listing]; with a
     * report line per file.
     */
    private suspend fun <F> signRelease(
        appId: String,
        signer: NostrSigner,
        version: String,
        notes: String,
        relays: List<String>,
        files: List<F>,
        now: Long = TimeUtils.now(),
        asset: (F, Long) -> EventTemplate<SoftwareAssetEvent>,
        listing: EventTemplate<SoftwareApplicationEvent>?,
        report: (F, SoftwareAssetEvent) -> Map<String, Any?>,
    ): SignedRelease {
        val hint = relays.firstNotNullOfOrNull(::normalize)
        val signedAssets = files.map { signer.sign(asset(it, now)) }
        return SignedRelease(
            appId = appId,
            relays = relays,
            assets = signedAssets,
            release = signer.sign(Nip82ReleaseEvents.release(appId, signer.pubKey, version, notes, signedAssets, hint, now)),
            listing = listing?.let { signer.sign(it) },
            files = files.zip(signedAssets).map { (file, event) -> report(file, event) },
        )
    }

    /** The hosted icon and screenshots of a listing. */
    class ListingMedia(
        val iconUrl: String?,
        val imageUrls: List<String>,
    )

    /**
     * The listing's icon and screenshots from `zapstore.yaml`: URLs as they are, local paths
     * (relative to [baseDir], the YAML's folder) uploaded to [server], as zsp does. Null when a
     * local file has no server to go to.
     */
    suspend fun listingMedia(
        config: ZapstoreConfig,
        baseDir: File,
        server: String?,
        signer: NostrSigner,
        dryRun: Boolean = false,
    ): ListingMedia {
        suspend fun host(pathOrUrl: String): String {
            if (pathOrUrl.startsWith("https://")) return pathOrUrl
            val file = File(baseDir, pathOrUrl).takeIf { it.isFile } ?: throw ReleaseFilesException("No such listing image: $pathOrUrl")
            server ?: throw ReleaseFilesException("$pathOrUrl is a local file: pass a Blossom server to host it (zsp uses ${AndroidRelease.ZAPSTORE_BLOSSOM})")
            return mirrorToBlossom(file, imageType(file), server, signer, dryRun = dryRun)
        }
        return ListingMedia(config.icon?.let { host(it) }, config.images.map { host(it) })
    }

    private fun imageType(file: File) =
        when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }

    /**
     * Uploads [file] to the Blossom server [server] as zsp does, and returns its URL there. The
     * server must return the same hash, or the copy is not used. With [dryRun], uploads nothing.
     */
    suspend fun mirrorToBlossom(
        file: File,
        contentType: String,
        server: String,
        signer: NostrSigner,
        client: BlossomClient = BlossomClient(),
        dryRun: Boolean = false,
        /** The file's SHA-256 when the caller already has it (an APK's, from [androidApks]). */
        sha256: String = ApkInspector.sha256(file),
    ): String {
        // A dry run uploads nothing and names where the blob would be (BUD-01: <server>/<sha256>).
        if (dryRun) return "${server.trimEnd('/')}/$sha256"
        val auth = BlossomAuth.createUploadAuth(sha256, file.length(), "Upload ${file.name}", signer)
        // A release run again sends files the server may already hold, which some refuse.
        val result = client.uploadOrHeld(sha256, file.length(), contentType, server) { client.upload(file, contentType, server, auth, sha256) }
        if (result.sha256 != null && result.sha256 != sha256) {
            throw ReleaseFilesException("$server stored ${file.name} as ${result.sha256}, not $sha256")
        }
        return result.url ?: "${server.trimEnd('/')}/$sha256"
    }

    private fun files(dir: File) =
        dir
            .listFiles()
            .orEmpty()
            .filter { it.isFile }
            .sortedBy { it.name }

    private fun urlFor(
        filename: String,
        version: String,
        urlBase: String?,
    ) = urlBase?.let { "${it.trimEnd('/')}/$filename" } ?: DesktopRelease.downloadUrl(version, filename)

    private fun normalize(relay: String): NormalizedRelayUrl? = RelayUrlNormalizer.normalizeOrNull(relay)

    private fun report(
        filename: String,
        platforms: List<String>,
        mimeType: String,
        sha256: String,
        size: Long,
        urls: List<String>,
        event: SoftwareAssetEvent,
    ): Map<String, Any?> =
        mapOf(
            "file" to filename,
            "platforms" to platforms,
            "mime_type" to mimeType,
            "sha256" to sha256,
            "size_bytes" to size,
            "urls" to urls,
            "event_id" to event.id,
        )
}
