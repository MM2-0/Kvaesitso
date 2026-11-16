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

/**
 * What this install can take: files for [platform], in order of preference. An empty [packages]
 * takes any file for the platform (an install the app only tells about, e.g. a Homebrew one).
 */
data class UpdateTarget(
    val platform: String,
    val packages: List<DesktopPackage>,
)

/** A file of a release, as its signed NIP-82 asset event describes it. */
data class UpdateAsset(
    val url: String,
    val sha256: String,
    val sizeInBytes: Long?,
    val packageType: DesktopPackage?,
    val filename: String?,
)

/**
 * A newer version than the running one.
 *
 * [asset] is null when the release has no file this install can apply, or when the news came from
 * a source the app does not trust to install from (the GitHub API): the app can then only say so
 * and point at [pageUrl].
 */
data class AvailableUpdate(
    val version: String,
    val releaseNotes: String,
    val asset: UpdateAsset?,
    val pageUrl: String,
    val publishedAt: Long,
) {
    val canInstall get() = asset != null
}
