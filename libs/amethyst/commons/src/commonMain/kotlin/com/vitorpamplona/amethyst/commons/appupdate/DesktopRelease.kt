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

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.ReleaseChannel
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.Platform

/**
 * Where Amethyst Desktop's releases are announced: NIP-82 events under their own app id, signed by
 * Amethyst's key. The APK (Zapstore, `com.vitorpamplona.amethyst`) and the Flatpak (Orchard,
 * `com.vitorpamplona.amethyst.Amethyst`) have their own ids, so publishing one never replaces
 * another's release. See `desktopApp/plans/2026-10-15-desktop-self-update-study.md`.
 */
object DesktopRelease {
    /** The NIP-82 app id: the macOS bundle id. */
    const val APP_ID = "com.vitorpamplona.amethyst.desktop"

    const val APP_NAME = "Amethyst"

    /**
     * Amethyst's key (npub142gywvjkq0dv6nupggyn2euhx4nduwc7yz5f24ah9rpmunr2s39se3xrj0), the one that
     * signs the Zapstore and Orchard listings. Only releases signed by it are installed.
     */
    const val PUBLISHER = "aa9047325603dacd4f8142093567973566de3b1e20a89557b728c3be4c6a844b"

    /** Relays the release events go to, and the ones the app reads them from. */
    val RELAYS =
        listOf(
            "wss://relay.zapstore.dev",
            "wss://nos.lol",
            "wss://relay.primal.net",
            "wss://relay.damus.io",
        )

    const val GITHUB_OWNER = "vitorpamplona"
    const val GITHUB_REPO = "amethyst"
    const val RELEASES_PAGE = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    fun releasePage(version: String) = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/tag/v$version"

    /** Where the release workflow uploads `filename` for the tag `v<version>`. */
    fun downloadUrl(
        version: String,
        filename: String,
    ) = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/v$version/$filename"

    /** Stable releases: no channel, NIP-82's `main`, or `stable` (Orchard's name for it). */
    fun isStableChannel(channel: String?) = channel == null || channel.equals(ReleaseChannel.MAIN, ignoreCase = true) || channel.equals("stable", ignoreCase = true)
}

/**
 * The installable files of a desktop release, told apart by MIME type (the asset's `m` tag), since
 * one platform carries several (an MSI and a zip for Windows; an AppImage, a tarball, a .deb and an
 * .rpm for Linux).
 */
enum class DesktopPackage(
    val mimeType: String,
    val extension: String,
) {
    DMG("application/x-apple-diskimage", "dmg"),
    MSI("application/x-msi", "msi"),
    WINDOWS_ZIP("application/zip", "zip"),
    APPIMAGE("application/vnd.appimage", "AppImage"),
    LINUX_TARBALL("application/gzip", "tar.gz"),
    DEB("application/vnd.debian.binary-package", "deb"),
    RPM("application/x-rpm", "rpm"),
    ;

    companion object {
        fun fromMimeType(mimeType: String?) = entries.firstOrNull { it.mimeType == mimeType }
    }
}

/** One release file, recognized by its name. */
data class DesktopAssetFile(
    val filename: String,
    val packageType: DesktopPackage,
    val platform: String,
)

/**
 * Reads the release asset names `scripts/asset-name.sh` writes:
 * `amethyst-desktop-<version>-<macos|windows|linux>-<x64|arm64>.<ext>`, and for the AppImage
 * `amethyst-desktop-<version>-<x86_64|aarch64>.AppImage`.
 */
object DesktopAssetNames {
    private const val PREFIX = "amethyst-desktop-"

    fun classify(
        filename: String,
        version: String,
    ): DesktopAssetFile? {
        val rest = filename.removePrefix("$PREFIX$version-").takeIf { it != filename } ?: return null

        if (rest.endsWith(".AppImage")) {
            val platform =
                when (rest.removeSuffix(".AppImage")) {
                    "x86_64" -> Platform.LINUX_X86_64
                    "aarch64" -> Platform.LINUX_AARCH64
                    else -> return null
                }
            return DesktopAssetFile(filename, DesktopPackage.APPIMAGE, platform)
        }

        val family = rest.substringBefore('-')
        val archAndExt = rest.substringAfter('-', "")
        val arch = archAndExt.substringBefore('.')
        val ext = archAndExt.substringAfter('.', "")

        val packageType =
            when (family to ext) {
                "macos" to "dmg" -> DesktopPackage.DMG
                "windows" to "msi" -> DesktopPackage.MSI
                "windows" to "zip" -> DesktopPackage.WINDOWS_ZIP
                "linux" to "tar.gz" -> DesktopPackage.LINUX_TARBALL
                "linux" to "deb" -> DesktopPackage.DEB
                "linux" to "rpm" -> DesktopPackage.RPM
                else -> return null
            }
        val platform = platformOf(family, arch) ?: return null
        return DesktopAssetFile(filename, packageType, platform)
    }

    /** NIP-82 platform ids (Appendix A) for the release workflow's family/arch names. */
    fun platformOf(
        family: String,
        arch: String,
    ): String? =
        when (family to arch) {
            "macos" to "arm64" -> Platform.DARWIN_ARM64
            "macos" to "x64" -> Platform.DARWIN_X86_64
            "windows" to "x64" -> Platform.WINDOWS_X86_64
            "windows" to "arm64" -> Platform.WINDOWS_AARCH64
            "linux" to "x64" -> Platform.LINUX_X86_64
            "linux" to "arm64" -> Platform.LINUX_AARCH64
            else -> null
        }
}
