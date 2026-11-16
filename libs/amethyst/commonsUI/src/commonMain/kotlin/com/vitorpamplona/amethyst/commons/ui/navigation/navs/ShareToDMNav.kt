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
package com.vitorpamplona.amethyst.commons.ui.navigation.navs

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.ShareToDMRouteRewriter

/**
 * Wraps an [INav] so that navigating to a chatroom ([Route.Room]) from the
 * Share-to-DM picker carries the shared message and attachment into the composer.
 * All other navigation behavior is delegated unchanged.
 */
@Stable
class ShareToDMNav(
    private val delegate: INav,
    private val message: String?,
    private val attachment: String?,
) : INav by delegate {
    override fun nav(route: Route) {
        val rewritten = ShareToDMRouteRewriter.rewrite(route, message, attachment)
        if (route is Route.Room) {
            // One-shot: replace the picker in the back stack so backing out of the
            // chat exits the share flow instead of returning to the picker, which
            // would re-inject the shared text on re-tap and create duplicate drafts.
            delegate.popUpTo(rewritten, Route.ShareToDM::class)
        } else {
            delegate.nav(rewritten)
        }
    }
}
