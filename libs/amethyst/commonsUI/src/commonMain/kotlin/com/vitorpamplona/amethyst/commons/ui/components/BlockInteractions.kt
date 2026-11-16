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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach

private val ACTIVATION_KEYS = setOf(Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionCenter)

/**
 * True inside content shown for inspection only (see [blockInteractions]). [blockInteractions] makes
 * taps inert; rows that carry their own action buttons (follow, add to list) read this to leave the
 * buttons out, since a button that looks live and does nothing reads as broken.
 */
val LocalReadOnlyPreview = staticCompositionLocalOf { false }

/**
 * Makes everything inside read-only: taps, long-presses and keyboard activation never reach a
 * child, but drags pass through, so the content can sit inside a scrolling container. Used where
 * a note is shown for inspection only, e.g. a signer consent prompt, whose renderers would
 * otherwise vote, RSVP, accept badges or open quick actions on an event that does not exist yet.
 *
 * Presses and releases are consumed in the [PointerEventPass.Initial] pass, before any child sees
 * them, and tap/click detectors ignore a consumed press. Movement is left alone, so the parent's
 * scroll still claims the drag. Only the activation keys are swallowed: Tab still moves focus out.
 */
fun Modifier.blockInteractions(): Modifier =
    this
        .onPreviewKeyEvent { it.key in ACTIVATION_KEYS }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.fastForEach {
                        if (it.changedToDownIgnoreConsumed() || it.changedToUpIgnoreConsumed()) it.consume()
                    }
                } while (event.changes.fastAny { it.pressed })
            }
        }
