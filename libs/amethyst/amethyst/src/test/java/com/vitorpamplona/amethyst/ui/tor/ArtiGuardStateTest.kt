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
package com.vitorpamplona.amethyst.ui.tor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtiGuardStateTest {
    private fun load(resource: String): String =
        javaClass.getResourceAsStream(resource)?.bufferedReader()?.use { it.readText() }
            ?: error("Missing test resource: $resource")

    /**
     * Real `guards.json` captured from an emulator stuck in "Connecting": two of the four
     * confirmed primary guards were poisoned (`TooManyIndeterminateFailures`), but usable
     * guards remained. This is the case that exposed the bug — full AllGuardsDown never trips,
     * so only the [ArtiGuardState.hasConfirmedGuard] seed recovers it.
     */
    @Test
    fun `real device sample - confirmed guard present, not fully wedged`() {
        val root = ArtiGuardState.parse(load("/tor/guards-confirmed-with-poisoned.json"))

        assertTrue(
            "device sample has confirmed guards → prior bootstrap proven",
            ArtiGuardState.hasConfirmedGuard(root),
        )
        assertFalse(
            "2 disabled of 22 guards is not a total wipeout → not wedged by the AllGuardsDown check",
            ArtiGuardState.hasNoUsableGuards(root),
        )
    }

    @Test
    fun `confirmed guard counts even when disabled or unlisted`() {
        val root =
            ArtiGuardState.parse(
                """
                { "default": { "guards": [
                    { "confirmed_at": "2026-06-17T01:36:40Z",
                      "disabled": { "type": "TooManyIndeterminateFailures" } },
                    { "confirmed_at": null, "disabled": null, "unlisted_since": null }
                ] } }
                """.trimIndent(),
            )

        assertTrue(ArtiGuardState.hasConfirmedGuard(root))
    }

    @Test
    fun `fresh sample with no confirmed guard - first bootstrap, nothing to wipe`() {
        val root =
            ArtiGuardState.parse(
                """
                { "default": { "guards": [
                    { "confirmed_at": null, "disabled": null, "unlisted_since": null },
                    { "confirmed_at": null, "disabled": null, "unlisted_since": null }
                ] } }
                """.trimIndent(),
            )

        assertFalse(ArtiGuardState.hasConfirmedGuard(root))
        assertFalse(ArtiGuardState.hasNoUsableGuards(root))
    }

    @Test
    fun `all guards disabled or unlisted - AllGuardsDown wedge`() {
        val root =
            ArtiGuardState.parse(
                """
                { "default": { "guards": [
                    { "confirmed_at": "2026-06-10T15:52:00Z",
                      "disabled": { "type": "TooManyIndeterminateFailures" } },
                    { "confirmed_at": "2026-06-11T15:52:00Z",
                      "disabled": null, "unlisted_since": "2026-06-12T00:00:00Z" }
                ] } }
                """.trimIndent(),
            )

        assertTrue("every guard unusable → wedged", ArtiGuardState.hasNoUsableGuards(root))
        // Even when fully wedged, prior confirmation is still proven.
        assertTrue(ArtiGuardState.hasConfirmedGuard(root))
    }

    /**
     * The field case this ratio rule exists for: a 60-guard sample with 59 permanently disabled and
     * exactly ONE still nominally usable, while Arti rejected all sixty at runtime
     * (`AllGuardsDown { n_accepted: 0, n_rejected: 60 }`) across repeated app restarts. Under the old
     * `usable == 0` rule the lone survivor vetoed the wipe forever, so Tor stayed ~87% broken and
     * Concord never folded.
     */
    @Test
    fun `one usable guard out of sixty is wedged, not healthy`() {
        val guards =
            buildString {
                append("""{ "default": { "guards": [""")
                repeat(59) {
                    append("""{ "confirmed_at": "2026-07-01T00:00:00Z", "disabled": { "type": "TooManyIndeterminateFailures" }, "unlisted_since": null },""")
                }
                append("""{ "confirmed_at": null, "disabled": null, "unlisted_since": null }""")
                append("] } }")
            }
        val root = ArtiGuardState.parse(guards)

        assertTrue(
            "1 usable of 60 is unrecoverable in practice → must wipe",
            ArtiGuardState.hasNoUsableGuards(root),
        )
    }

    /**
     * The opposite guard-rail: a young sample must never be wiped just for being small, or a genuine
     * first bootstrap would loop. Only the strict `usable == 0` rule applies below
     * [ArtiGuardState.MIN_SAMPLE_TO_JUDGE_RATIO].
     */
    @Test
    fun `small fresh sample is never wedged by the ratio rule`() {
        val root =
            ArtiGuardState.parse(
                """
                { "default": { "guards": [
                    { "confirmed_at": null, "disabled": null, "unlisted_since": null },
                    { "confirmed_at": null, "disabled": { "type": "TooManyIndeterminateFailures" } },
                    { "confirmed_at": null, "disabled": { "type": "TooManyIndeterminateFailures" } }
                ] } }
                """.trimIndent(),
            )

        assertFalse(
            "1 usable of 3 is a young sample Arti is still filling → leave it alone",
            ArtiGuardState.hasNoUsableGuards(root),
        )
    }

    /** A healthy majority must not trip the ratio rule. */
    @Test
    fun `mostly usable large sample is not wedged`() {
        val guards =
            buildString {
                append("""{ "default": { "guards": [""")
                repeat(20) {
                    append("""{ "confirmed_at": null, "disabled": null, "unlisted_since": null },""")
                }
                append("""{ "confirmed_at": null, "disabled": { "type": "TooManyIndeterminateFailures" } }""")
                append("] } }")
            }

        assertFalse(
            "20 usable of 21 is healthy",
            ArtiGuardState.hasNoUsableGuards(ArtiGuardState.parse(guards)),
        )
    }

    @Test
    fun `empty selection is neither wedged nor confirmed`() {
        val root = ArtiGuardState.parse("""{ "default": { "guards": [] }, "restricted": { "guards": [] } }""")

        assertFalse(ArtiGuardState.hasNoUsableGuards(root))
        assertFalse(ArtiGuardState.hasConfirmedGuard(root))
    }
}
