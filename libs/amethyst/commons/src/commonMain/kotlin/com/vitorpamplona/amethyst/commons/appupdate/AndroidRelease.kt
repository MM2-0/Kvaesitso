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

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.Platform

/**
 * Amethyst for Android as a NIP-82 app: the listing, releases and APK assets Zapstore installs
 * from, under the Android package name. `amy releases publish` writes the same events `zsp`
 * (https://github.com/zapstore/zsp) did, so Zapstore keeps updating the installs it made.
 */
object AndroidRelease {
    /** The NIP-82 app id: the Android package name, as Zapstore keys it. */
    const val APP_ID = "com.vitorpamplona.amethyst"

    /** Zapstore's community, the `h` tag zsp puts on every listing. */
    const val ZAPSTORE_COMMUNITY = "acfeaea6e51420e8068fac446ca9d17d7a9ef6a5d20d93894e50fee3d4902a84"

    /** Zapstore's Blossom server, where zsp mirrors APKs, icons and screenshots. */
    const val ZAPSTORE_BLOSSOM = "https://cdn.zapstore.dev"

    /** The relay the Zapstore app reads, first, then the ones RELEASE_OPS.md fans out to. */
    val RELAYS =
        listOf(
            "wss://relay.zapstore.dev",
            "wss://nos.lol",
            "wss://nostr.mom",
            "wss://vitor.nostr1.com",
        )

    const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    /** Every platform an APK without native code runs on. */
    val ALL_PLATFORMS = listOf(Platform.ANDROID_ARM64_V8A, Platform.ANDROID_ARMEABI_V7A, Platform.ANDROID_X86, Platform.ANDROID_X86_64)

    /** The NIP-82 platform of an Android ABI (a `lib/<abi>/` folder in the APK). */
    fun platformOf(abi: String) = "android-$abi"

    /** The build flavors the release workflow ships, and the `variant` name zsp gave each. */
    enum class Flavor(
        val fileTag: String,
        val variant: String,
    ) {
        GOOGLE_PLAY("googleplay", "google"),
        FDROID("fdroid", "fdroid"),
        ;

        companion object {
            fun parse(name: String) = entries.firstOrNull { it.fileTag.equals(name, true) || it.variant.equals(name, true) }
        }
    }

    /** One APK of a release, recognized by its name. */
    data class ApkFile(
        val filename: String,
        val flavor: Flavor,
        val abi: String,
    )

    /** The ABI splits the release workflow builds, and the universal APK that holds them all. */
    val ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64", "universal")

    /** Reads `amethyst-<googleplay|fdroid>-<abi>-v<version>.apk`, the names the release workflow writes. */
    fun classify(
        filename: String,
        version: String,
    ): ApkFile? {
        if (!filename.startsWith("amethyst-") || !filename.endsWith("-v$version.apk")) return null
        val middle = filename.removePrefix("amethyst-").removeSuffix("-v$version.apk")
        val flavor = Flavor.entries.firstOrNull { middle.startsWith(it.fileTag + "-") } ?: return null
        val abi = middle.removePrefix(flavor.fileTag + "-").takeIf { it in ABIS } ?: return null
        return ApkFile(filename, flavor, abi)
    }
}

/** What an APK says about itself, read and signature-checked by `ApkInspector`. */
data class ApkInfo(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    /** The `lib/<abi>/` folders: empty for an APK without native code. */
    val nativeAbis: List<String>,
    /** SHA-256 of the signing certificate (the newest in a rotation), lowercase hex. */
    val certificateSha256: String,
) {
    /** NIP-82 platforms: one per native ABI, or every Android platform when there is no native code. */
    val platforms: List<String>
        get() = nativeAbis.map(AndroidRelease::platformOf).ifEmpty { AndroidRelease.ALL_PLATFORMS }
}
