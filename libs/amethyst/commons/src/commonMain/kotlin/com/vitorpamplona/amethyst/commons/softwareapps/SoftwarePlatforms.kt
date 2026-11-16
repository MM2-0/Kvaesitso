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

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent

/** The operating systems NIP-82 assets are grouped under, in display order. */
enum class SoftwareOs {
    ANDROID,
    IOS,
    MACOS,
    WINDOWS,
    LINUX,
    FREEBSD,
    WASM,
    OTHER,
}

/** The CPUs NIP-82 Appendix A names, as a person would; the UI holds their labels. */
enum class SoftwareCpu {
    APPLE_SILICON,
    INTEL_MAC,
    ARM64,
    ARMV7,
    X86_64,
    X86,
    RISCV64,
    WASM32,
    WASM64,
}

/**
 * Reads NIP-82 platform identifiers (Appendix A, `<os>-<arch>` loosely after `uname -sm`) and
 * MIME types (Appendix C) into the OS and CPU a person recognises.
 */
object SoftwarePlatforms {
    /** The OS of an Appendix A identifier; [SoftwareOs.OTHER] for custom ones. */
    fun os(platformId: String): SoftwareOs {
        val id = platformId.lowercase()
        return when {
            id.startsWith("android-") -> SoftwareOs.ANDROID
            id.startsWith("ios-") -> SoftwareOs.IOS
            // `macos-` is not Appendix A, but publishers use it.
            id.startsWith("darwin-") || id.startsWith("macos-") -> SoftwareOs.MACOS
            id.startsWith("windows-") -> SoftwareOs.WINDOWS
            id.startsWith("linux-") -> SoftwareOs.LINUX
            id.startsWith("freebsd-") -> SoftwareOs.FREEBSD
            id.startsWith("wasm") || id.startsWith("wasi-") -> SoftwareOs.WASM
            else -> SoftwareOs.OTHER
        }
    }

    /** The architecture part of an identifier as written (`arm64-v8a`, `x86_64`), or null. */
    fun rawArch(platformId: String): String? {
        val id = platformId.lowercase()
        return when (os(id)) {
            SoftwareOs.WASM -> id.removePrefix("wasi-").takeIf { it.startsWith("wasm") }
            SoftwareOs.OTHER -> null
            else -> id.substringAfter('-', "").takeIf { it.isNotEmpty() }
        }
    }

    /**
     * The CPU of an Appendix A identifier: Apple silicon/Intel on macOS, ARM64, x86-64 and so
     * on elsewhere. Null for an architecture this does not know; show [rawArch] then.
     */
    fun cpu(platformId: String): SoftwareCpu? {
        val os = os(platformId)
        val arch = rawArch(platformId) ?: return null
        if (os == SoftwareOs.MACOS) {
            when (arch) {
                "arm64", "aarch64" -> return SoftwareCpu.APPLE_SILICON
                "x86_64" -> return SoftwareCpu.INTEL_MAC
            }
        }
        return when (arch) {
            "arm64", "aarch64", "arm64-v8a" -> SoftwareCpu.ARM64
            "armeabi-v7a", "armv7l" -> SoftwareCpu.ARMV7
            "x86_64" -> SoftwareCpu.X86_64
            "x86" -> SoftwareCpu.X86
            "riscv64" -> SoftwareCpu.RISCV64
            "wasm32" -> SoftwareCpu.WASM32
            "wasm64" -> SoftwareCpu.WASM64
            else -> null
        }
    }

    /** The OS a MIME type implies on its own (Appendix C), or null when it implies none. */
    fun osForMime(mime: String?): SoftwareOs? =
        when (mime?.lowercase()) {
            "application/vnd.android.package-archive" -> SoftwareOs.ANDROID
            "application/vnd.apple.ipa" -> SoftwareOs.IOS
            "application/x-apple-diskimage", "application/vnd.apple.installer+xml", "application/x-mach-binary" -> SoftwareOs.MACOS
            "application/x-msi", "application/vnd.microsoft.portable-executable", "application/x-msdownload" -> SoftwareOs.WINDOWS
            "application/vnd.appimage", "application/vnd.flatpak", "application/x-executable",
            "application/vnd.debian.binary-package", "application/x-rpm", "application/x-redhat-package-manager",
            -> SoftwareOs.LINUX
            "application/wasm" -> SoftwareOs.WASM
            else -> null
        }

    /**
     * The OSes an asset runs on: from its `f` tags, or, when it has none, from its MIME type,
     * since NIP-82 says an asset without `f` is restricted by its MIME type alone.
     */
    fun osesOf(
        platforms: List<String>,
        mime: String?,
    ): List<SoftwareOs> =
        if (platforms.isNotEmpty()) {
            platforms.map(::os).distinct()
        } else {
            listOf(osForMime(mime) ?: SoftwareOs.OTHER)
        }

    fun osesOf(asset: SoftwareAssetEvent) = osesOf(asset.platforms(), asset.mimeType())

    /** The OSes behind a release's aggregate `f` tags, in [SoftwareOs] order. */
    fun osesOfPlatforms(platforms: List<String>): List<SoftwareOs> = platforms.map(::os).distinct().sorted()

    /**
     * How well an asset fits this device: 0 is the device's preferred platform, higher numbers
     * are platforms it can still run (an emulated architecture, say), and null means it does
     * not run here. [devicePlatforms] is [devicePlatformIds], preferred first.
     */
    fun deviceFit(
        platforms: List<String>,
        mime: String?,
        devicePlatforms: List<String>,
    ): Int? {
        if (devicePlatforms.isEmpty()) return null
        if (platforms.isNotEmpty()) {
            val offered = platforms.mapTo(HashSet(), ::canonical)
            return devicePlatforms.indexOfFirst { canonical(it) in offered }.takeIf { it >= 0 }
        }
        // No `f` tags: any device of the OS the MIME type implies, ranked after exact matches.
        val os = osForMime(mime) ?: return null
        return if (os == os(devicePlatforms.first())) devicePlatforms.size else null
    }

    /**
     * One spelling per platform, so aliases match: `macos-arm64`, `darwin-aarch64` and
     * `darwin-arm64` are the same Mac, `linux-arm64` and `linux-aarch64` the same Linux box.
     */
    private fun canonical(platformId: String): String {
        val os = os(platformId)
        if (os == SoftwareOs.OTHER) return platformId.lowercase()
        return os.name + "-" + (cpu(platformId)?.name ?: rawArch(platformId))
    }
}
