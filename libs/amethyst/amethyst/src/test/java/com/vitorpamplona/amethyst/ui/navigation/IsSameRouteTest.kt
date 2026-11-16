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
package com.vitorpamplona.amethyst.ui.navigation

import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.isSameRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `isSameRoute` is what stops a deep link from stacking a second copy of the screen already on
 * top. The QR launcher shortcut routes to `QRDisplay(startScanning = true)`, which is an action
 * ("open the camera"), not a place: if the user closed the scanner and is still sitting on that
 * entry, the shortcut must fire again rather than be swallowed as a duplicate.
 */
class IsSameRouteTest {
    private val me = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    @Test
    fun scanShortcutOnTopOfItselfIsNotADuplicate() {
        val scan = Route.QRDisplay(me, startScanning = true)
        assertFalse(isSameRoute(scan, Route.QRDisplay(me, startScanning = true)))
    }

    @Test
    fun scanShortcutOnTopOfTheCodeScreenIsNotADuplicate() {
        assertFalse(isSameRoute(Route.QRDisplay(me), Route.QRDisplay(me, startScanning = true)))
    }

    @Test
    fun showingTheSameCodeTwiceIsStillADuplicate() {
        assertTrue(isSameRoute(Route.QRDisplay(me), Route.QRDisplay(me)))
    }

    @Test
    fun ordinaryRoutesStillDedupe() {
        assertTrue(isSameRoute(Route.Profile(me), Route.Profile(me)))
    }
}
