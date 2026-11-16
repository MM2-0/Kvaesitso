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
package com.vitorpamplona.amethyst.commons.model.navigation

import kotlin.reflect.KClass

/**
 * One request to show [route], whatever the layout: each [NavModel] reads it its own way. The stack
 * pushes, the tabs open or focus a tab, the windows open or raise a window.
 *
 * [origin] is the screen the request comes from (a tab or window opens beside it, a replace acts on
 * it); null means the active screen. [background] keeps the focus where it is (Ctrl/Cmd-click).
 * [forceNew] opens another copy even when one is open (a fresh search, a duplicate).
 */
data class NavRequest(
    val route: Route,
    val kind: Kind = Kind.Push,
    val origin: Long? = null,
    val background: Boolean = false,
    val forceNew: Boolean = false,
) {
    sealed interface Kind {
        /** An in-app link: a screen on top of the one asking. */
        data object Push : Kind

        /** A top-level place from the navigation drawer: it can be left, but keeps the bar. */
        data object Drawer : Kind

        /** A place on the bottom bar or rail (Home, Messages, …): there is one of each. */
        data object Section : Kind

        /** Shows [route] as the start of a fresh stretch of history (deep links, notifications). */
        data object Restart : Kind

        /** The next step of a flow: [route] takes the place of the latest screen of [klass], or of [origin]. */
        data class Replace(
            val klass: KClass<out Route>,
        ) : Kind
    }
}
