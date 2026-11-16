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
package com.vitorpamplona.amethyst.service.cast

/**
 * How a cast request should reach its target receiver, given what is already connected.
 *
 * @param needsRouteSelection whether MediaRouter has to be asked to move.
 * @param expectsPreviousSessionToEnd whether an existing session will be torn down as a *result* of
 *   that move. The caller must not read that teardown as the new attempt failing.
 */
enum class CastRoutePlan(
    val needsRouteSelection: Boolean,
    val expectsPreviousSessionToEnd: Boolean,
) {
    /** Already connected to this very receiver — load straight onto the live session. */
    REUSE_SESSION(needsRouteSelection = false, expectsPreviousSessionToEnd = false),

    /** Connected to a *different* receiver — selecting the target ends that session first. */
    SWAP_RECEIVER(needsRouteSelection = true, expectsPreviousSessionToEnd = true),

    /** Nothing connected — select the route and wait for the session to start. */
    SELECT_FRESH(needsRouteSelection = true, expectsPreviousSessionToEnd = false),
}

/**
 * Chooses the [CastRoutePlan] for a cast to [targetRouteId].
 *
 * The distinction that matters is between reusing a session and swapping receivers. Treating any
 * connected session as reusable — as this used to — means a session on the soundbar is taken as
 * proof that a cast to the TV is already connected: the start completes instantly, `selectRoute()`
 * then tears that session down, and by the time the media is loaded the session is gone, so the
 * load is skipped and the TV sits on its splash screen having "connected" successfully.
 *
 * [hasConnectedSession] must reflect a session that is actually connected; a stale or suspended one
 * cannot take a load and has to go through a fresh start.
 */
fun planRouteSelection(
    targetRouteId: String,
    selectedRouteId: String?,
    hasConnectedSession: Boolean,
): CastRoutePlan =
    when {
        !hasConnectedSession -> CastRoutePlan.SELECT_FRESH
        selectedRouteId == targetRouteId -> CastRoutePlan.REUSE_SESSION
        else -> CastRoutePlan.SWAP_RECEIVER
    }
