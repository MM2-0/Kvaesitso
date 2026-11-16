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
package com.vitorpamplona.amethyst.commons.tor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TorDialogViewModelTest {
    private fun dialogFor(settings: TorSettings) = TorDialogViewModel().apply { reset(settings) }

    @Test
    fun externalSavesTheTypedPort() {
        val dialog = dialogFor(TorSettings(torType = TorType.EXTERNAL, externalSocksPort = 9050))
        dialog.socksPortStr.value = "9150"

        assertEquals(9150, dialog.save().externalSocksPort)
    }

    @Test
    fun externalRejectsAPortThatIsNotANumberOrOutOfRange() {
        val dialog = dialogFor(TorSettings(torType = TorType.EXTERNAL))

        for (bad in listOf("90a0", "", "0", "70000", "-1")) {
            dialog.socksPortStr.value = bad
            assertThrows(IllegalArgumentException::class.java) { dialog.save() }
        }
    }

    @Test
    fun aHiddenJunkPortDoesNotBlockSavingInternal() {
        // The port field only shows for External. Typing junk there and then switching to
        // Internal used to make every save fail with "invalid port", with the field hidden.
        val dialog = dialogFor(TorSettings(torType = TorType.EXTERNAL, externalSocksPort = 9150))
        dialog.socksPortStr.value = "90a0"
        dialog.torType.value = TorType.INTERNAL

        val saved = dialog.save()

        assertEquals(TorType.INTERNAL, saved.torType)
        assertEquals(9150, saved.externalSocksPort)
    }
}
