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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopAssetNamesTest {
    private fun classify(name: String) = DesktopAssetNames.classify(name, "1.21.0")?.let { it.packageType to it.platform }

    @Test
    fun readsEveryNameTheReleaseWorkflowWrites() {
        assertEquals(DesktopPackage.DMG to "darwin-arm64", classify("amethyst-desktop-1.21.0-macos-arm64.dmg"))
        assertEquals(DesktopPackage.MSI to "windows-x86_64", classify("amethyst-desktop-1.21.0-windows-x64.msi"))
        assertEquals(DesktopPackage.WINDOWS_ZIP to "windows-x86_64", classify("amethyst-desktop-1.21.0-windows-x64.zip"))
        assertEquals(DesktopPackage.WINDOWS_ZIP to "windows-aarch64", classify("amethyst-desktop-1.21.0-windows-arm64.zip"))
        assertEquals(DesktopPackage.DEB to "linux-x86_64", classify("amethyst-desktop-1.21.0-linux-x64.deb"))
        assertEquals(DesktopPackage.RPM to "linux-aarch64", classify("amethyst-desktop-1.21.0-linux-arm64.rpm"))
        assertEquals(DesktopPackage.LINUX_TARBALL to "linux-x86_64", classify("amethyst-desktop-1.21.0-linux-x64.tar.gz"))
        assertEquals(DesktopPackage.APPIMAGE to "linux-x86_64", classify("amethyst-desktop-1.21.0-x86_64.AppImage"))
        assertEquals(DesktopPackage.APPIMAGE to "linux-aarch64", classify("amethyst-desktop-1.21.0-aarch64.AppImage"))
    }

    @Test
    fun skipsWhatIsNotAnInstallableDesktopFile() {
        assertNull(classify("amethyst-desktop-1.21.0-linux-x64.flatpak"))
        assertNull(classify("amethyst-desktop-1.21.0-x86_64.AppImage.zsync"))
        assertNull(classify("amethyst-desktop-1.20.0-linux-x64.deb"))
        assertNull(classify("amy-1.21.0-linux-x64.tar.gz"))
        assertNull(classify("amethyst-googleplay-universal-v1.21.0.apk"))
    }
}
