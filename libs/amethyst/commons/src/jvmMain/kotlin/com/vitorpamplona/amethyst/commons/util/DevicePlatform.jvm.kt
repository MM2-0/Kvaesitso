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

actual fun devicePlatformIds(): List<String> = desktopPlatformIds(System.getProperty("os.name").orEmpty(), System.getProperty("os.arch").orEmpty())

/**
 * NIP-82 identifiers for a desktop JVM's `os.name`/`os.arch`. ARM Macs and Windows on ARM also
 * run x86-64 builds through Rosetta 2 and Windows' emulator, so those follow the native one.
 */
internal fun desktopPlatformIds(
    osName: String,
    osArch: String,
): List<String> {
    val name = osName.lowercase()
    val os =
        when {
            name.startsWith("mac") || name.startsWith("darwin") -> "darwin"
            name.startsWith("windows") -> "windows"
            name.startsWith("linux") -> "linux"
            name.startsWith("freebsd") -> "freebsd"
            else -> return emptyList()
        }

    val arch =
        when (osArch.lowercase()) {
            "aarch64", "arm64" -> if (os == "darwin") "arm64" else "aarch64"
            "amd64", "x86_64", "x64" -> "x86_64"
            "arm", "armv7l", "armv7" -> "armv7l"
            "riscv64" -> "riscv64"
            else -> return emptyList()
        }

    val native = "$os-$arch"
    val emulated = if ((os == "darwin" || os == "windows") && arch != "x86_64") listOf("$os-x86_64") else emptyList()
    return listOf(native) + emulated
}
