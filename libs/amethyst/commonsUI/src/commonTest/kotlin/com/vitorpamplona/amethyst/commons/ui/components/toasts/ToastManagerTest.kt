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
package com.vitorpamplona.amethyst.commons.ui.components.toasts

import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.error_dialog_details
import com.vitorpamplona.amethyst.commons.ui.components.toasts.multiline.MultiErrorToastMsg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class ToastManagerTest {
    private fun ToastManager.shownTitle() = (toasts.value as? StringToastMsg)?.title

    @Test
    fun aSecondMessageWaitsInsteadOfReplacingTheFirst() {
        val manager = ToastManager()
        manager.toast("Zap failed", "timeout")
        manager.toast("Upload failed", "too big")

        assertEquals("Zap failed", manager.shownTitle())
        manager.clearToasts()
        assertEquals("Upload failed", manager.shownTitle())
        manager.clearToasts()
        assertNull(manager.toasts.value)
    }

    @Test
    fun theSameMessageTwiceIsShownOnce() {
        val manager = ToastManager()
        manager.toast("Zap failed", "timeout")
        manager.toast("Zap failed", "timeout")
        manager.toast("Other", "x")
        manager.toast("Other", "x")

        manager.clearToasts()
        assertEquals("Other", manager.shownTitle())
        manager.clearToasts()
        assertNull(manager.toasts.value, "each duplicate was dropped")
    }

    @Test
    fun aBurstCannotStackUpMoreThanTheCap() {
        val manager = ToastManager()
        repeat(50) { manager.toast("Failure $it", "x") }

        var shown = 0
        while (manager.toasts.value != null) {
            shown++
            manager.clearToasts()
        }
        assertEquals(11, shown, "the one on screen plus ten queued")
    }

    @Test
    fun errorsWithOneTitleGatherEvenWhenQueued() {
        val manager = ToastManager()
        manager.toast("Something else", "first")
        manager.toast(Res.string.close, "relay A refused", null)
        manager.toast(Res.string.close, "relay B refused", null)
        manager.toast(Res.string.error_dialog_details, "unrelated", null)

        manager.clearToasts()
        val grouped = assertIs<MultiErrorToastMsg>(manager.toasts.value)
        assertEquals(listOf("relay A refused", "relay B refused"), grouped.errors.value.map { it.error })
        manager.clearToasts()
        val other = assertIs<MultiErrorToastMsg>(manager.toasts.value)
        assertSame(Res.string.error_dialog_details, other.titleResId)
    }

    @Test
    fun exceptionsAreErrorsAndPlainMessagesWarnings() {
        val manager = ToastManager()
        manager.toast(Res.string.close, "boom", RuntimeException("boom"))
        assertEquals(ToastSeverity.ERROR, manager.toasts.value?.severity)
        manager.clearToasts()
        manager.toast("Heads up", "x")
        assertEquals(ToastSeverity.WARNING, manager.toasts.value?.severity)
    }
}
