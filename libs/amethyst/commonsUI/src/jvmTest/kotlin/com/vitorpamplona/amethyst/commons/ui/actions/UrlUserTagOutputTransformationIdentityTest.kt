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
package com.vitorpamplona.amethyst.commons.ui.actions

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * BasicTextField keys its transformed state on the OutputTransformation's equality and restarts a
 * focused field's input session when that state changes. A transformation built inline in
 * composition is a new instance on every recomposition: each one restarts the keyboard session and
 * drops the mention cache.
 */
class UrlUserTagOutputTransformationIdentityTest {
    /** Focuses a field, recomposes it three times, and counts the input sessions it started. */
    private fun sessionStarts(transformation: @Composable () -> OutputTransformation): Int {
        var tick by mutableIntStateOf(0)
        var starts = 0
        val focus = FocusRequester()
        val state = TextFieldState("hi")
        val counting =
            PlatformTextInputInterceptor { request, next ->
                starts++
                next.startInputMethod(request)
            }
        val scene =
            ImageComposeScene(width = 400, height = 200, density = Density(1f)) {
                InterceptPlatformTextInput(counting) {
                    // Read so this scope recomposes on every tick.
                    if (tick >= 0) {
                        BasicTextField(state, Modifier.focusRequester(focus), outputTransformation = transformation())
                    }
                }
            }
        try {
            scene.render()
            focus.requestFocus()
            scene.render()
            repeat(3) {
                tick++
                Snapshot.sendApplyNotifications()
                scene.render()
            }
            return starts
        } finally {
            scene.close()
        }
    }

    @Test
    fun inlineTransformation_restartsTheSessionOnEveryRecomposition() {
        // Documents the trap: one start for the focus, one per recomposition.
        assertEquals(4, sessionStarts { UrlUserTagOutputTransformation(Color.Blue) })
    }

    @Test
    fun rememberedTransformation_keepsOneSession() {
        assertEquals(1, sessionStarts { rememberUrlUserTagOutputTransformation(Color.Blue) })
    }
}
