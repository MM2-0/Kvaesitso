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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The feed's animated elements defer building their `Transition` until a value actually changes,
 * because first composition has nothing to animate and building one per card per scroll is pure
 * waste (measured: roughly half the composition cost of every reaction-row button).
 *
 * The whole point of deferring rather than removing is that the animation must still play. These
 * tests pin that: they drive the clock manually and assert that the **first** change — the one that
 * happens right after the transition is lazily created — still shows outgoing and incoming content
 * simultaneously, which only a running animation does. A regression that turned the deferral into a
 * plain snap would show exactly one of them and fail here.
 */
@RunWith(AndroidJUnit4::class)
class DeferredAnimationTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun deferredCrossfadeStillAnimatesTheFirstChange() {
        val state = mutableStateOf("A")
        rule.mainClock.autoAdvance = false

        rule.setContent {
            // enabled = true is the deferred path (DeferredCrossfade, internal to commonsUI).
            CrossfadeIfEnabled(
                targetState = state.value,
                enabled = true,
                modifier = Modifier,
                contentAlignment = Alignment.TopStart,
                animationSpec = tween(DURATION_MS),
                label = "test",
            ) { value ->
                Text(value, modifier = Modifier.testTag("text_$value"))
            }
        }

        // Before any change the transition has not been built, and only the current value renders.
        rule.onNodeWithTag("text_A").assertIsDisplayed()
        rule.onNodeWithTag("text_B").assertDoesNotExist()

        state.value = "B"
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(DURATION_MS / 3L)

        // Mid-crossfade both are in the tree. This is the assertion that a snap would fail.
        rule.onNodeWithTag("text_A").assertExists()
        rule.onNodeWithTag("text_B").assertExists()

        rule.mainClock.advanceTimeBy(DURATION_MS * 3L)
        rule.onNodeWithTag("text_B").assertIsDisplayed()
        rule.onNodeWithTag("text_A").assertDoesNotExist()
    }

    companion object {
        const val DURATION_MS = 300
    }
}
