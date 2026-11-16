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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Pure, file- and JNI-free parsers over Arti's persisted guard sample
 * (`<filesDir>/arti/state/state/guards.json`).
 *
 * Both recovery heuristics in the Tor stack read the same on-disk file, so the
 * decisions live here where they can be unit-tested against captured fixtures
 * (see `ArtiGuardStateTest`). [TorService] does the file IO and delegates the
 * parsing; [TorManager] consumes [hasConfirmedGuard] to seed its
 * `hasEverBootstrapped` flag across process restarts.
 *
 * The file shape is a map of guard-set selection name (e.g. `"default"`) to an
 * object with a `"guards"` array. Each guard entry carries:
 * - `disabled`: non-null once Arti permanently retires the guard (e.g.
 *   `TooManyIndeterminateFailures` on a flaky network).
 * - `unlisted_since`: non-null once the guard drops out of the consensus.
 * - `confirmed_at`: non-null once the guard has actually been used to build a
 *   circuit — i.e. a real bootstrap reached the guard-confirmation stage.
 */
object ArtiGuardState {
    /** Convenience for tests/callers holding the raw file text. */
    fun parse(json: String): JsonElement = Json.parseToJsonElement(json)

    /**
     * Smallest sample worth judging by ratio. Below this a low usable count is just a young
     * sample that Arti is still filling, and wiping it would loop the bootstrap.
     */
    const val MIN_SAMPLE_TO_JUDGE_RATIO = 10

    /** A sample is wedged when fewer than 1 in [USABLE_RATIO_DIVISOR] of its guards are usable. */
    const val USABLE_RATIO_DIVISOR = 10

    /**
     * True when a non-empty guard selection has no usable guards left, or so few that Arti cannot
     * realistically recover from them — every guard permanently `disabled` or dropped from the
     * consensus (`unlisted_since`).
     *
     * This is the AllGuardsDown wedge: Arti can neither build circuits nor replenish the sample, so
     * it stays broken across restarts until the on-disk state is wiped.
     *
     * ### Why this is not `usable == 0`
     *
     * It used to be, on the assumption that "a single usable guard is enough to recover". Observed in
     * the field: a sample of 60 with **59 disabled and 1 usable**, while Arti rejected all sixty at
     * runtime (`AllGuardsDown { n_accepted: 0, n_rejected: 60 }`) across repeated app restarts. The
     * lone survivor was just as unreachable as the rest — useless for recovery, but enough to veto
     * it, because `usable == 0` never became true. ~87% of relay connections failed indefinitely.
     *
     * So the test is proportional: a sample of at least [MIN_SAMPLE_TO_JUDGE_RATIO] whose usable
     * guards are under 1/[USABLE_RATIO_DIVISOR] of the total is wedged. A small, young sample is
     * still only judged by the strict `usable == 0` rule, so a legitimate first bootstrap that has
     * sampled one or two guards is never wiped out from under itself.
     */
    fun hasNoUsableGuards(root: JsonElement): Boolean {
        var wedged = false
        forEachGuardList(root) { guards ->
            if (guards.isNotEmpty()) {
                val usable = guards.count { !it.has("disabled") && !it.has("unlisted_since") }
                if (usable == 0) {
                    wedged = true
                } else if (guards.size >= MIN_SAMPLE_TO_JUDGE_RATIO && usable * USABLE_RATIO_DIVISOR < guards.size) {
                    wedged = true
                }
            }
        }
        return wedged
    }

    /**
     * True when the sample contains a guard Arti has *confirmed* (non-null
     * `confirmed_at`), even if that guard is now disabled or unlisted.
     *
     * Confirmation only happens after a guard has successfully built circuits, so
     * its presence on disk is durable proof that a real bootstrap completed at
     * least once on this install — surviving the process restarts that reset
     * [TorManager]'s in-memory `hasEverBootstrapped` flag to false. This lets the
     * stuck-Connecting watchdog treat persisted state as stale/poisoned (wipe it)
     * rather than as a pristine slow first bootstrap (leave it alone).
     */
    fun hasConfirmedGuard(root: JsonElement): Boolean {
        var confirmed = false
        forEachGuardList(root) { guards ->
            if (guards.any { it.has("confirmed_at") }) confirmed = true
        }
        return confirmed
    }

    /** Each selection's `guards` array: the selections are the values of an object root, or the entries of an array one. */
    private inline fun forEachGuardList(
        root: JsonElement,
        action: (JsonArray) -> Unit,
    ) {
        val selections =
            when (root) {
                is JsonObject -> root.values
                is JsonArray -> root
                else -> return
            }
        selections.forEach { selection ->
            val guards = (selection as? JsonObject)?.get("guards") as? JsonArray ?: return@forEach
            action(guards)
        }
    }

    /** The guard carries [key] with a non-null value. */
    private fun JsonElement.has(key: String): Boolean {
        val value = (this as? JsonObject)?.get(key)
        return value != null && value !is JsonNull
    }
}
