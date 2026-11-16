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
package com.vitorpamplona.nestsclient.trace

/**
 * Append-only event recorder for the moq-lite + Nests audio path.
 *
 * Designed to capture enough wire-level + decision-level data from a real
 * production session that the same communication can be replayed in a
 * unit test without a network — i.e., feed the recorded events back
 * through a `TraceReplayingTransport` (planned, not yet implemented) into
 * the unmodified [com.vitorpamplona.nestsclient.moq.lite.MoqLiteSession]
 * + [com.vitorpamplona.nestsclient.MoqLiteNestsListener] +
 * [com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel] stack
 * and assert on observable behaviour (frames received, cliff-detector
 * decisions, recycle counts).
 *
 * **Cost when disabled**: a single volatile-load + branch per call site
 * (the [emit] inline guards `if (!enabled) return` before the lambda
 * runs). Production builds that never call [setRecording] pay nothing.
 *
 * **Output format**: one JSON object per line, written via
 * [com.vitorpamplona.quartz.utils.Log] at the [TAG] tag. Capture from a
 * connected device with:
 *
 *   adb logcat -c
 *   adb logcat -s NestsTraceJsonl:D -v raw > nest-trace.jsonl
 *
 * The `-v raw` formatter strips the `D NestsTraceJsonl:` prefix so the
 * captured file is valid JSONL ready for replay tooling.
 *
 * **Schema**: each event is a JSON object with at least
 *   - `t_ms` — milliseconds since [setRecording] was called with `true`
 *   - `kind` — string discriminator naming the event
 * and additional kind-specific fields. Keep all field names lower-snake-
 * case and stable; the replay tool will pattern-match on `kind`.
 *
 * Anonymisation: pubkeys + track names are recorded verbatim because
 * they're already in the production logs the user is sharing (the
 * `NestRx` / `NestTx` tags). Frame payloads are NEVER recorded — only
 * sizes — so audio content can't leak through a trace dump.
 */
object NestsTrace {
    /** Tag the JSONL lines are emitted under. Filter logcat with `-s NestsTraceJsonl:D`. */
    const val TAG: String = "NestsTraceJsonl"

    // `@PublishedApi internal` rather than `private` because [emit] is
    // inline — its body becomes part of every call site's bytecode and
    // must be able to reach the backing fields. `private` would refuse
    // to compile ("Public-API inline function cannot access non-public-
    // API property"). Marking these `@PublishedApi internal` keeps them
    // unreachable from outside the module while satisfying the inliner.
    @PublishedApi
    @Volatile
    internal var enabled: Boolean = false

    @PublishedApi
    @Volatile
    internal var startMark: kotlin.time.TimeMark? = null

    /**
     * Idempotent. When `on` flips from false → true, [startMark] is
     * captured so subsequent [emit] calls record monotonic-time deltas
     * relative to enable. Flipping true → false stops further emits but
     * does NOT alter prior log output.
     *
     * Call from a debug-build app start-up hook, a debug menu toggle, or
     * a test `@Before`. Production release builds should leave the
     * default disabled.
     *
     * Named `setRecording` (not `setEnabled`) so it doesn't clash with
     * the JVM-generated setter for the `enabled` backing property —
     * that property is `@PublishedApi internal` for the inline [emit]
     * to access, which forces the setter to share the `setEnabled`
     * JVM name.
     */
    fun setRecording(on: Boolean) {
        if (on == enabled) return
        if (on) {
            startMark =
                kotlin.time.TimeSource.Monotonic
                    .markNow()
        }
        enabled = on
    }

    fun isRecording(): Boolean = enabled

    /**
     * Record an event. The lambda is invoked ONLY when tracing is
     * enabled, so the call site pays nothing in the disabled path beyond
     * the field-load + comparison.
     *
     * The lambda must return the kind-specific JSON fields portion
     * (no surrounding braces, no leading or trailing comma — empty
     * string when there are no fields). This recorder prepends
     * `{"t_ms":N,"kind":"K"`, joins fields with a comma when present,
     * and appends `}`.
     *
     * Use [jsonStr] / [jsonArrStr] to safely quote string values and
     * arrays. Numeric / boolean values can be interpolated directly.
     *
     * Example:
     *
     *   NestsTrace.emit("subscribe_send") {
     *       "\"id\":$id,\"broadcast\":${jsonStr(broadcast)},\"track\":${jsonStr(track)}"
     *   }
     */
    inline fun emit(
        kind: String,
        fieldsJson: () -> String = { "" },
    ) {
        if (!enabled) return
        val mark = startMark ?: return
        val tMs = mark.elapsedNow().inWholeMilliseconds
        val fields = fieldsJson()
        val sep = if (fields.isEmpty()) "" else ","
        com.vitorpamplona.quartz.utils.Log.d(TAG) {
            "{\"t_ms\":$tMs,\"kind\":\"$kind\"$sep$fields}"
        }
    }
}

/**
 * Quote a string as a JSON literal, escaping the small set of characters
 * that would otherwise produce invalid JSONL (`"`, `\`, control chars).
 * Keeps the implementation small + commonMain-portable rather than
 * pulling in a full JSON library for what is effectively a single
 * field-value path.
 */
fun jsonStr(value: String): String {
    val sb = StringBuilder(value.length + 2)
    sb.append('"')
    for (c in value) {
        when (c) {
            '"' -> {
                sb.append('\\').append('"')
            }

            '\\' -> {
                sb.append('\\').append('\\')
            }

            '\n' -> {
                sb.append('\\').append('n')
            }

            '\r' -> {
                sb.append('\\').append('r')
            }

            '\t' -> {
                sb.append('\\').append('t')
            }

            else -> {
                if (c.code < 0x20) {
                    sb.append("\\u00")
                    val h = c.code.toString(16)
                    if (h.length == 1) sb.append('0')
                    sb.append(h)
                } else {
                    sb.append(c)
                }
            }
        }
    }
    sb.append('"')
    return sb.toString()
}

/** JSON array of strings: `["a","b","c"]`. */
fun jsonArrStr(values: Iterable<String>): String = values.joinToString(separator = ",", prefix = "[", postfix = "]") { jsonStr(it) }
