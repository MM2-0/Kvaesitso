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

import com.vitorpamplona.amethyst.commons.util.prettyMime
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent

/**
 * What a download row says about one asset. The UI localizes [cpuPlatforms] (through
 * [SoftwarePlatforms.cpu]) and formats [sizeBytes]; everything else is shown as is.
 *
 * [title] is the format and variant ("ZIP · msvc") when the MIME type is one people know,
 * else the file name ("SHA256SUMS.txt"): `application/octet-stream` says nothing, and
 * publishers list checksums and readmes next to the installers.
 */
data class DownloadRowText(
    val title: String,
    /** True when [title] is the file name, whose distinguishing part is often its end. */
    val titleIsFileName: Boolean,
    /**
     * For a file-name title, its type from the extension ("ISO", "SHA256", "JSON"), which a
     * truncated name can hide; null otherwise.
     */
    val fileKind: String?,
    val cpuPlatforms: List<String>,
    val sizeBytes: Long?,
    val minPlatformVersion: String?,
    val assetVersion: String?,
)

/** A release's downloads arranged for display; see [SoftwareAssetDownloads.group]. */
class DownloadGroups(
    val forThisDevice: SoftwareAssetEvent?,
    val deviceOs: SoftwareOs?,
    val byOs: List<Pair<SoftwareOs, List<SoftwareAssetEvent>>>,
)

/** Where to download a NIP-82 asset from, and what to call the file. */
object SoftwareAssetDownloads {
    private val SINGLE_SUFFIX_TARBALL = mapOf("tar.gz" to "tgz", "tar.xz" to "txz", "tar.bz2" to "tbz2", "tar.zst" to "tzst")
    private val TARBALL = Regex("""\.(tar\.(?:gz|xz|bz2|zst)|tgz)$""", RegexOption.IGNORE_CASE)

    /**
     * The file extension to save the asset under: its `filename`'s ("zip", "AppImage",
     * "tar.gz"), else the usual one for its MIME type (NIP-82 Appendix C). Null when neither
     * says.
     */
    fun extension(asset: SoftwareAssetEvent): String? {
        val filename = asset.filename()
        if (filename != null) {
            TARBALL.find(filename)?.let { return it.groupValues[1] }
            filename
                .substringAfterLast('.', "")
                .takeIf { it.isNotEmpty() && it.length <= 16 && it.all { c -> c.isLetterOrDigit() } }
                ?.let { return it }
        }
        return extensionForMime(asset.mimeType())
    }

    /**
     * The asset's format as people name it ("APK", "ZIP", "TAR.GZ"), or null when its MIME
     * type is missing or not one [prettyMime] knows.
     */
    fun formatLabel(asset: SoftwareAssetEvent): String? {
        val mime = asset.mimeType() ?: return null
        val label = prettyMime(mime).takeIf { it != mime } ?: return null
        // A gzip/xz MIME type on a tarball: the archive is the tar, the compression is a detail.
        if (label == "GZ" || label == "XZ" || label == "TAR") {
            asset.filename()?.let { name -> TARBALL.find(name)?.let { return it.groupValues[1].uppercase() } }
        }
        return label
    }

    /** The text of [asset]'s row under [os]; see [DownloadRowText]. */
    fun describe(
        asset: SoftwareAssetEvent,
        os: SoftwareOs,
        releaseVersion: String?,
    ): DownloadRowText {
        val format = formatLabel(asset)
        val variant = asset.variant()
        val filename = asset.filename()
        val title =
            if (format != null) {
                listOfNotNull(format, variant).joinToString(" · ")
            } else {
                filename ?: variant ?: asset.mimeType() ?: os.name
            }
        val titleIsFileName = format == null && title == filename
        return DownloadRowText(
            title = title,
            titleIsFileName = titleIsFileName,
            fileKind = if (titleIsFileName) extension(asset)?.uppercase() else null,
            cpuPlatforms = asset.platforms().filter { SoftwarePlatforms.os(it) == os }.distinct(),
            sizeBytes = asset.sizeInBytes()?.toLong(),
            minPlatformVersion = asset.minPlatformVersion(),
            assetVersion = asset.version()?.takeIf { it != releaseVersion },
        )
    }

    /**
     * [assets] grouped by OS in [SoftwareOs] order (an asset for several OSes is listed under
     * each), with the best fit for [devicePlatforms] picked out and left out of the groups, so
     * it is not listed twice; a group it leaves empty is dropped.
     */
    fun group(
        assets: List<SoftwareAssetEvent>,
        devicePlatforms: List<String>,
    ): DownloadGroups {
        val forThisDevice =
            assets
                .mapNotNull { asset -> SoftwarePlatforms.deviceFit(asset.platforms(), asset.mimeType(), devicePlatforms)?.let { asset to it } }
                .minByOrNull { it.second }
                ?.first
        val oses = assets.associateWith { SoftwarePlatforms.osesOf(it) }
        val byOs =
            SoftwareOs.entries.mapNotNull { os ->
                assets.filter { it !== forThisDevice && os in oses.getValue(it) }.takeIf { it.isNotEmpty() }?.let { os to it }
            }
        return DownloadGroups(forThisDevice, devicePlatforms.firstOrNull()?.let(SoftwarePlatforms::os), byOs)
    }

    fun extensionForMime(mime: String?): String? =
        when (mime?.lowercase()) {
            "application/vnd.android.package-archive" -> "apk"
            "application/vnd.apple.ipa" -> "ipa"
            "application/x-apple-diskimage" -> "dmg"
            "application/vnd.apple.installer+xml" -> "pkg"
            "application/x-msi" -> "msi"
            "application/vnd.appimage" -> "AppImage"
            "application/vnd.flatpak" -> "flatpak"
            "application/vnd.microsoft.portable-executable", "application/x-msdownload" -> "exe"
            "application/vnd.debian.binary-package" -> "deb"
            "application/x-rpm", "application/x-redhat-package-manager" -> "rpm"
            "application/zip" -> "zip"
            "application/wasm" -> "wasm"
            "application/vsix" -> "vsix"
            "application/x-chrome-extension" -> "crx"
            "application/x-xpinstall" -> "xpi"
            else -> null
        }

    /**
     * The asset's `url`, with the [extension] added when the url is a bare Blossom
     * hash link (`…/<sha256>`, the asset's own `x`). Blossom serves `/<sha256>.<ext>` too
     * (BUD-01), and without it a browser saves a file named after the hash with no extension.
     * Null when the asset has no `url`: NIP-82 then has clients find it by hash on Blossom.
     */
    fun url(asset: SoftwareAssetEvent): String? {
        val url = asset.url() ?: return null
        val hash = asset.hash() ?: return url
        // A compound extension in its one-suffix form (`tar.gz` -> `tgz`): some Blossom servers
        // only read the last dot, and 404 on `<hash>.tar.gz`.
        val ext = extension(asset)?.let { SINGLE_SUFFIX_TARBALL[it.lowercase()] ?: it } ?: return url

        val pathEnd = url.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) url.length else it }
        val path = url.substring(0, pathEnd)
        if (!path.endsWith("/$hash", ignoreCase = true)) return url

        return "$path.$ext${url.substring(pathEnd)}"
    }
}
