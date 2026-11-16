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
package com.vitorpamplona.amethyst.commons.ui.navigation.routes

import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import kotlin.reflect.KClass

inline fun <reified T : Route> isBaseRoute(nav: Nav): Boolean = nav.currentRoute is T

/**
 * The composers that swallow a *redelivered* ACTION_SEND themselves: each registers its own
 * onNewIntent listener and drops the shared text/media into the draft already on screen.
 *
 * Microsoft's SwiftKey delivers GIFs as fresh share intents, so without this the global share
 * router would answer a GIF sent from one of these screens by starting a brand-new short-note
 * composer — throwing away the reply the user was in the middle of writing.
 *
 * Only guards the onNewIntent path. A share that *launches* the activity has no composer
 * listening yet, so it must still navigate.
 */
fun consumesSharesInPlace(nav: Nav): Boolean =
    isBaseRoute<Route.NewShortNote>(nav) ||
        isBaseRoute<Route.GenericCommentPost>(nav) ||
        isBaseRoute<Route.HashtagPost>(nav) ||
        isBaseRoute<Route.GeoPost>(nav) ||
        isBaseRoute<Route.UrlPost>(nav)

/** The route on top, with its arguments, when it is a [klazz]; null when another screen is on top. */
fun <T : Route> getRouteWithArguments(
    klazz: KClass<T>,
    nav: Nav,
): Route? = nav.currentRoute.takeIf { klazz.isInstance(it) }
