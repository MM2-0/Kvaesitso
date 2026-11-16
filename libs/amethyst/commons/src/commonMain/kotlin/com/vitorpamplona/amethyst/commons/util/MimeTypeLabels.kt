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
package com.vitorpamplona.amethyst.commons.util

/**
 * The short label a user recognises for a distributable file type — "APK", not
 * "application/vnd.android.package-archive".
 *
 * Unmapped types return the raw MIME unchanged, which is the honest fallback: a bare
 * `application/x-webxdc` still tells the reader more than an invented label would.
 *
 * Used by the NIP-82 software-app chips and by the file-attachment card that stands in for any
 * blob no viewer can render.
 */
fun prettyMime(mime: String): String =
    when (mime) {
        "application/vnd.android.package-archive" -> "APK"
        "application/vnd.apple.ipa" -> "IPA"
        "application/x-apple-diskimage" -> "DMG"
        "application/vnd.apple.installer+xml" -> "PKG"
        "application/x-msi" -> "MSI"
        "application/vnd.appimage" -> "AppImage"
        "application/vnd.flatpak" -> "Flatpak"
        "application/vnd.oci.image.manifest.v1+json" -> "OCI"
        "application/x-executable" -> "ELF"
        "application/x-mach-binary" -> "Mach-O"
        "application/vnd.microsoft.portable-executable" -> "EXE"
        "application/x-msdownload" -> "EXE"
        "application/vnd.debian.binary-package" -> "DEB"
        "application/x-rpm", "application/x-redhat-package-manager" -> "RPM"
        "application/zip" -> "ZIP"
        "application/x-tar" -> "TAR"
        "application/gzip", "application/x-gzip" -> "GZ"
        "application/x-xz" -> "XZ"
        "application/x-7z-compressed" -> "7Z"
        "application/vsix" -> "VSIX"
        "application/x-chrome-extension" -> "CRX"
        "application/x-xpinstall" -> "XPI"
        "application/wasm" -> "WASM"
        "application/webbundle" -> "Web Bundle"
        else -> mime
    }

/** The last path segment of [url], without query or fragment; the whole [url] when that is blank. */
fun extractFilename(url: String): String {
    val afterQuery = url.substringBefore('?').substringBefore('#')
    val name = afterQuery.substringAfterLast('/', afterQuery)
    return if (name.isBlank()) url else name
}
