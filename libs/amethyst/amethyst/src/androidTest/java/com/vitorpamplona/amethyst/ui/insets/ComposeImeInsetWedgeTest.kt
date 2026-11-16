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
package com.vitorpamplona.amethyst.ui.insets

import android.view.View
import android.view.animation.LinearInterpolator
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Upstream regression test for androidx.compose.foundation:foundation-layout.
 *
 * A `WindowInsetsAnimation` that is prepared and started but never ended — which is what a
 * cancelled IME animation looks like — leaves `InsetsListener.runningAnimation` set forever.
 * `onApplyWindowInsets` then matches neither of its two branches, so `composeInsets.update()`
 * is never called again and `WindowInsets.ime` is dead for the life of the window.
 *
 * Introduced in 1.4.0 (absent in 1.3.0, where `onApplyWindowInsets` updated unconditionally
 * once `onStart` had cleared `prepared`). Still present in 1.12.0 and 1.13.0-alpha01. The
 * compensating self-heal (`view.post(this)` -> `run()`) is scoped to `SDK_INT == R`, so on
 * API 31+ nothing clears the flag; `WindowInsetsHolder.resetState()` only runs when the
 * holder's accessCount transitions 0 -> 1, which never happens in an app whose shell always
 * reads insets.
 *
 * Filed upstream as b/552500419.
 *
 * [aCancelledImeAnimationMustNotWedgeTheAnimatedInset] FAILS on every version from 1.4.0 on, so it
 * is [Ignore]d to keep CI green. It is not a test of Amethyst code — it is the upstream repro we
 * attached to the bug. **Re-run it by hand after every Compose upgrade**: when it passes, the
 * upstream fix has landed and [com.vitorpamplona.amethyst.commons.ui.insets.SafeImeInsets] can be retired.
 *
 * [theAnimationTargetSurvivesTheWedge] documents the asymmetry that makes a workaround possible
 * and is expected to PASS — `updateImeAnimationTarget` is called outside the guard. It stays
 * enabled, because it guards the premise [com.vitorpamplona.amethyst.commons.ui.insets.SafeImeInsets]
 * depends on: if a future Compose release stopped keeping `imeAnimationTarget` current, our
 * fallback would silently start reading a dead value too.
 */
class ComposeImeInsetWedgeTest {
    @get:Rule
    val rule = createComposeRule()

    private val keyboardHeight = 957

    private fun imeInsets(bottom: Int): WindowInsetsCompat =
        WindowInsetsCompat
            .Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
            .setVisible(WindowInsetsCompat.Type.ime(), bottom > 0)
            .build()

    /** Compose's own listener for this view. Private class, but both interfaces it exposes are public. */
    private fun listenerFor(view: View): Any {
        val holderClass = Class.forName("androidx.compose.foundation.layout.WindowInsetsHolder")
        val companion =
            holderClass.getDeclaredField("Companion").run {
                isAccessible = true
                get(null)
            }
        val holder =
            companion.javaClass
                .getDeclaredMethod("getOrCreateFor", View::class.java)
                .run {
                    isAccessible = true
                    invoke(companion, view)
                }
        return holderClass.getDeclaredField("insetsListener").run {
            isAccessible = true
            get(holder)!!
        }
    }

    private fun anim() = WindowInsetsAnimationCompat(WindowInsetsCompat.Type.ime(), LinearInterpolator(), 250L)

    private fun bounds() =
        WindowInsetsAnimationCompat.BoundsCompat(
            Insets.NONE,
            Insets.of(0, 0, 0, keyboardHeight),
        )

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    @Ignore("Fails by design until upstream fixes b/552500419 — re-run by hand on every Compose upgrade")
    fun aCancelledImeAnimationMustNotWedgeTheAnimatedInset() {
        var animated by mutableIntStateOf(-1)
        lateinit var view: View

        rule.setContent {
            view = LocalView.current
            val density = LocalDensity.current
            animated = WindowInsets.ime.getBottom(density)
        }
        rule.waitForIdle()

        val listener = listenerFor(view)
        val onApply = listener as OnApplyWindowInsetsListener
        val callback = listener as WindowInsetsAnimationCompat.Callback

        // Baseline: with no animation in flight the inset tracks normally.
        rule.runOnUiThread { onApply.onApplyWindowInsets(view, imeInsets(keyboardHeight)) }
        rule.waitForIdle()
        assertEquals("baseline: the inset must follow a plain dispatch", keyboardHeight, animated)

        // A cancelled animation: prepared and started, but onEnd never arrives.
        rule.runOnUiThread {
            callback.onPrepare(anim())
            callback.onStart(anim(), bounds())
        }
        rule.waitForIdle()

        // The keyboard is gone and the window says so. The animated inset must follow.
        rule.runOnUiThread { onApply.onApplyWindowInsets(view, imeInsets(0)) }
        rule.waitForIdle()

        assertEquals(
            "WindowInsets.ime must still track the window after an animation was cancelled " +
                "without onEnd; it is instead frozen at the keyboard height forever",
            0,
            animated,
        )
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun theAnimationTargetSurvivesTheWedge() {
        var target by mutableIntStateOf(-1)
        lateinit var view: View

        rule.setContent {
            view = LocalView.current
            val density = LocalDensity.current
            target = WindowInsets.imeAnimationTarget.getBottom(density)
        }
        rule.waitForIdle()

        val listener = listenerFor(view)
        val onApply = listener as OnApplyWindowInsetsListener
        val callback = listener as WindowInsetsAnimationCompat.Callback

        rule.runOnUiThread { onApply.onApplyWindowInsets(view, imeInsets(keyboardHeight)) }
        rule.waitForIdle()
        assertEquals(keyboardHeight, target)

        rule.runOnUiThread {
            callback.onPrepare(anim())
            callback.onStart(anim(), bounds())
        }
        rule.waitForIdle()

        rule.runOnUiThread { onApply.onApplyWindowInsets(view, imeInsets(0)) }
        rule.waitForIdle()

        assertEquals(
            "updateImeAnimationTarget is called outside the guard, so this reading stays truthful",
            0,
            target,
        )
    }
}
