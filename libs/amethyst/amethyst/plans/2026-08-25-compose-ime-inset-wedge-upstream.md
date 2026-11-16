# Upstream issue draft — Compose `WindowInsets.ime` permanently wedges after a cancelled IME animation

Target: Google IssueTracker → **component 612128 (Jetpack Compose)**.
The library-specific component the docs link to (856989, from the "Create a new issue" button on
the Compose Foundation release notes) does not grant public Create Issues permission, so this is
filed one level up with a routing request at the top of the body.

Status: **FILED as https://issuetracker.google.com/issues/552500419 (b/552500419)** on 2026-08-25,
against component 612128 with a routing request. Remaining open item: the AOSP commit that introduced `runningAnimation`
between 1.3.0 and 1.4.0-alpha01 has not been identified (android.googlesource.com returned 403
to automated fetch). Adding the commit link before filing would help triage.

---

## Title

`WindowInsets.ime` stops updating permanently when an IME animation is cancelled without `onEnd` (regression in 1.4.0, still present in 1.13.0-alpha01)

## Routing

Please reassign to the owner of **`androidx.compose.foundation` / `foundation-layout`**
(WindowInsets). Filing here because component 856989 — the target of the "Create a new issue"
button on the [Compose Foundation release notes](https://developer.android.com/jetpack/androidx/releases/compose-foundation)
— does not grant Create Issues permission to external accounts. That documented path being
unusable by the public is arguably a separate docs bug worth fixing.

## Affected versions

* **Broken:** `androidx.compose.foundation:foundation-layout` **1.4.0 → 1.12.0 (current stable) and 1.13.0-alpha01**
* **Not broken:** 1.3.0 and earlier
* Verified by inspecting published `-sources.jar` for 1.2.0, 1.3.0, 1.4.0-alpha01…rc01, 1.4.0,
  1.5.0, 1.6.0, 1.7.0, 1.8.0, 1.9.0, 1.10.0, 1.11.0, 1.12.0, 1.13.0-alpha01.
  `runningAnimation` and its guard are absent in 1.3.0 and present from 1.4.0-alpha01 onward,
  textually unchanged since.
* Reproduced on a Pixel 8, Android 17 (API 37). The API-30-only self-heal (below) means API 31+
  has no recovery path at all.

## Summary

If a `WindowInsetsAnimation` is prepared and started but never ended — what a cancelled IME
animation looks like — `InsetsListener.runningAnimation` stays `true` forever. From that point
`onApplyWindowInsets` matches neither of its two branches, so `composeInsets.update()` is never
called again and **`WindowInsets.ime` is frozen for the remaining life of the window**.

Every `Modifier.imePadding()` in the app then holds a keyboard-height gap open with no keyboard
on screen, permanently. `WindowInsets.imeAnimationTarget` keeps reporting correctly, because
`updateImeAnimationTarget()` is called outside the guard — that asymmetry is the only reason a
workaround is possible at all.

## Reproduction

Deterministic instrumented test, ~3s, no gestures and no timing dependence. It drives Compose's
own listener through the cancelled-animation sequence using **public** interfaces
(`WindowInsetsAnimationCompat.Callback`, `OnApplyWindowInsetsListener`); reflection is used only
to obtain the listener instance for the view. Inside the androidx codebase `InsetsListener` is
directly accessible, so `listenerFor()` can be deleted and the rest of the test used verbatim.

```
FAIL  aCancelledImeAnimationMustNotWedgeTheAnimatedInset
      expected:<0> but was:<957>
PASS  theAnimationTargetSurvivesTheWedge
```

The second test is expected to pass and is included on purpose: it pins the asymmetry between the
two readings, and would catch a "fix" that broke `imeAnimationTarget` instead.

The full test source is attached below.

## Root cause

`compose/foundation/foundation-layout/src/androidMain/kotlin/androidx/compose/foundation/layout/WindowInsets.android.kt`

```kotlin
override fun onPrepare(animation: WindowInsetsAnimationCompat) {
    prepared = true
    runningAnimation = true          // set here…
}

override fun onStart(animation, bounds): BoundsCompat {
    prepared = false                 // …prepared cleared, runningAnimation left set
    return super.onStart(animation, bounds)
}

override fun onEnd(animation: WindowInsetsAnimationCompat) {
    prepared = false
    runningAnimation = false         // …cleared ONLY here
    …
}

override fun onApplyWindowInsets(view: View, insets: WindowInsetsCompat): WindowInsetsCompat {
    savedInsets = insets
    composeInsets.updateImeAnimationTarget(insets)   // unconditional — stays correct
    if (prepared) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            view.post(this)                          // self-heal, API 30 ONLY
        }
    } else if (!runningAnimation) {
        composeInsets.updateImeAnimationSource(insets)
        composeInsets.update(insets)                 // the animated inset — never reached when wedged
    }
    …
}
```

After a cancelled animation: `prepared == false` (cleared by `onStart`) and
`runningAnimation == true` (never cleared, because `onEnd` never came). Neither branch runs.
`composeInsets.update()` is dead.

### Why the existing self-heal does not help

`run()` exists precisely to handle a cancelled animation, but:

1. it is gated to `Build.VERSION.SDK_INT == Build.VERSION_CODES.R` (API 30 only), and
2. it is posted only from the `if (prepared)` branch, and returns early unless `prepared` is still
   `true` — which `onStart` has already cleared.

So it covers "cancelled between `onPrepare` and `onStart`, on API 30". It does not cover
"cancelled after `onStart`", on any API level.

### Why applications cannot recover

The only reset is `insetsListener.resetState()`, called from `WindowInsetsHolder.incrementAccessors()`
when `accessCount` transitions `0 → 1`. `accessCount` is driven by `WindowInsetsHolder.current()`'s
`DisposableEffect`, so it only reaches 0 when *every* insets consumer leaves composition
simultaneously.

In a single-Activity app whose shell (scaffold / bottom bar / drawer) always reads insets, that
never happens — the holder is created once and lives for the whole process. There is no public API
to force the reset. `WindowInsetsHolder` is `internal`.

Multi-Activity apps mask this: a new Activity means a new `View`, a new holder, and fresh state, so
the wedge dies with the Activity and reads as a transient glitch.

### Regression point

1.3.0's `onApplyWindowInsets` had no such gate and could not wedge:

```kotlin
override fun onApplyWindowInsets(view: View, insets: WindowInsetsCompat): WindowInsetsCompat {
    if (prepared) {
        savedInsets = insets
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) view.post(this)
        return insets
    }
    composeInsets.update(insets)      // unconditional once onStart cleared `prepared`
    return …
}
```

1.4.0 introduced `runningAnimation` and the `else if (!runningAnimation)` guard. Its own comment
states the intent:

> `// If an animation is running, rely on onProgress() to update the insets`
> `// On APIs less than 30 where the IME animation is backported, this avoids reporting`
> `// the final insets for a frame while the animation is running.`

i.e. a **one-frame** cosmetic flash on **API < 30** was fixed by making the update path conditional
on a flag that only `onEnd` clears — trading a single wrong frame on old devices for permanent
state corruption on all of them. The compensating recovery was never widened past `SDK_INT == R`.

## Real-world impact

Observed in a production Compose app (Amethyst, a Nostr client; single-Activity, `NavHost`,
77 `imePadding()` sites):

* On a Pixel 8 / Android 17, after ordinary manual use, `WindowInsets.ime` pinned at 957px while
  the window reported `ime frame=[0,0][0,0]` — keyboard gone — and stayed pinned for 85+ seconds
  until the process was restarted. Nothing in the app cleared it.
* Instrumented `WindowInsets.ime` vs `WindowInsets.imeAnimationTarget` across the failure:

  ```
  17:12:58.803  animated=882  target=957   ← healthy open, 13 intermediate frames
  17:12:58.902  animated=957  target=957
  17:13:00.584  animated=957  target=0     ← dismissed; animated frozen
  17:13:02.430  animated=0    target=957   ← reopened; snaps, no intermediate frames
  17:13:03.479  animated=957  target=0     ← dismissed; frozen permanently
  ```

  Note the loss of per-frame updates after the wedge: healthy transitions carry ~13 intermediate
  values over ~264ms; post-wedge transitions carry none.
* Because the app never navigates away from its single Activity and its shell always reads insets,
  `accessCount` never returns to 0, so the wedge is permanent for the session. Sessions in this app
  routinely run for days.

The trigger for the underlying cancellation was not isolated — it is infrequent and required
extended manual use to hit. The defect being reported is not the cancellation itself but that
Compose enters a state it can never leave when one occurs. The attached test reproduces that state
directly and deterministically.

## Suggested fixes

Roughly in order of how targeted they are:

1. **Generalise the existing self-heal.** Post the `run()` reconciliation on all API levels, and
   arm it after `onStart` as well as after `onPrepare`, so that an `onApplyWindowInsets` that
   arrives with no intervening `onProgress` clears `runningAnimation` and applies `savedInsets`.
   This preserves the API<30 one-frame behaviour the guard was added for, while bounding the
   failure to a frame rather than forever.
2. **Reconcile on dispatch.** In `onApplyWindowInsets`, if `runningAnimation` is set but no
   `onProgress` has been received since `onStart`, treat the animation as finished and update.
3. **Expose a reset.** A public way to reach `WindowInsetsHolder.resetState()` (or a documented
   condition under which it runs) would at least let applications self-heal. Today they cannot,
   short of reflection into an `internal` class — which R8 can rename or strip in exactly the
   release builds where this occurs.

(1) or (2) is preferable: (3) only makes the bug survivable rather than fixing it.

## Environment

* `androidx.compose.foundation:foundation-layout` 1.12.0 (Compose BOM 2026.08.00)
* Pixel 8 (`shiba`), Android 17 / API 37, gesture navigation, Gboard, 120Hz
* Also inspected: 1.13.0-alpha01 — identical listener code

---

## Attachment — the failing test

```kotlin
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
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
 * [aCancelledImeAnimationMustNotWedgeTheAnimatedInset] FAILS on every version from 1.4.0 on.
 * [theAnimationTargetSurvivesTheWedge] documents the asymmetry that makes a workaround possible
 * and is expected to PASS — `updateImeAnimationTarget` is called outside the guard.
 */
class ComposeImeInsetWedgeTest {
    @get:Rule val rule = createComposeRule()

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
```
