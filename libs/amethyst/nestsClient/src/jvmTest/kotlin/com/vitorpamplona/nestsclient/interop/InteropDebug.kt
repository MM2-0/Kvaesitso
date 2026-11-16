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
package com.vitorpamplona.nestsclient.interop

import com.vitorpamplona.nestsclient.NestsListenerState
import com.vitorpamplona.nestsclient.NestsSpeakerState
import kotlin.test.fail

/**
 * Shared debug helpers for the interop tests. Gradle captures stdout
 * per test and renders it in the HTML report — these helpers make
 * the report explain exactly which sub-step of a multi-step interop
 * scenario fired (or didn't), without forcing every assert into a
 * giant message string.
 *
 * Output is silent unless `-DnestsInteropDebug=true` (or the harness
 * is enabled). That keeps the default test run noise-free.
 */
object InteropDebug {
    private val enabled: Boolean by lazy {
        // If the harness is on, the test is going to talk to a real
        // network anyway — surface every step. Otherwise allow an
        // explicit opt-in.
        System.getProperty(NostrNestsHarness.ENABLE_PROPERTY) == "true" ||
            System.getProperty("nestsInteropDebug") == "true"
    }

    /** Print a labelled checkpoint. Cheap; safe in inner loops. */
    fun checkpoint(
        scope: String,
        message: String,
    ) {
        if (!enabled) return
        println("· [$scope] $message")
    }

    /**
     * Run [block] surrounded by an "▶ start" / "✔ ok" / "✘ fail" log.
     * Re-throws any exception so the test still fails — but the report
     * makes it obvious which step actually exploded.
     */
    inline fun <T> step(
        scope: String,
        name: String,
        block: () -> T,
    ): T {
        checkpoint(scope, "▶ $name")
        return try {
            block().also { checkpoint(scope, "✔ $name") }
        } catch (t: Throwable) {
            checkpoint(scope, "✘ $name — ${describe(t)}")
            throw t
        }
    }

    /**
     * Async-friendly variant for `suspend` step bodies.
     */
    suspend inline fun <T> stepSuspending(
        scope: String,
        name: String,
        block: () -> T,
    ): T {
        checkpoint(scope, "▶ $name")
        return try {
            block().also { checkpoint(scope, "✔ $name") }
        } catch (t: Throwable) {
            checkpoint(scope, "✘ $name — ${describe(t)}")
            throw t
        }
    }

    /**
     * One-line summary of [t] including the chained cause(s) — useful
     * because the surface message often hides the real reason in
     * `cause.cause.cause...` (network → NestsException → AssertionError
     * is a common stack here).
     */
    fun describe(t: Throwable): String =
        buildString {
            var current: Throwable? = t
            var depth = 0
            while (current != null && depth < 5) {
                if (depth > 0) append(" ⟵ ")
                append(current::class.simpleName).append(": ").append(current.message ?: "(no message)")
                current = current.cause
                depth += 1
            }
        }

    /**
     * Pretty-print a [NestsSpeakerState] — for [Failed] in particular,
     * unwraps the chained cause so the assertion message names the
     * real network error rather than the wrapping string.
     */
    fun describe(state: NestsSpeakerState): String =
        when (state) {
            is NestsSpeakerState.Failed -> {
                "Failed(reason='${state.reason}'" +
                    (state.cause?.let { ", cause=${describe(it)}" } ?: "") +
                    ")"
            }

            is NestsSpeakerState.Connected -> {
                "Connected(room=${state.room.moqNamespace()}, moqVersion=0x${state.negotiatedMoqVersion.toString(16)})"
            }

            is NestsSpeakerState.Broadcasting -> {
                "Broadcasting(room=${state.room.moqNamespace()}, isMuted=${state.isMuted})"
            }

            else -> {
                state.toString()
            }
        }

    /** Mirror of [describe] for the listener side. */
    fun describe(state: NestsListenerState): String =
        when (state) {
            is NestsListenerState.Failed -> {
                "Failed(reason='${state.reason}'" +
                    (state.cause?.let { ", cause=${describe(it)}" } ?: "") +
                    ")"
            }

            is NestsListenerState.Connected -> {
                "Connected(room=${state.room.moqNamespace()}, moqVersion=0x${state.negotiatedMoqVersion.toString(16)})"
            }

            else -> {
                state.toString()
            }
        }

    /**
     * Assert [actual] is [NestsSpeakerState.Connected] or
     * [NestsSpeakerState.Broadcasting], printing a richly-described
     * failure message including any nested cause.
     */
    fun assertSpeakerReached(
        scope: String,
        expectedClass: String,
        actual: NestsSpeakerState,
    ) {
        val ok =
            when (expectedClass) {
                "Connected" -> actual is NestsSpeakerState.Connected
                "Broadcasting" -> actual is NestsSpeakerState.Broadcasting
                else -> error("unsupported expectedClass=$expectedClass")
            }
        if (!ok) {
            // Land the rich state dump on stdout too — JUnit captures the
            // fail message separately, but the standard-output section is
            // what people glance at first when debugging.
            checkpoint(scope, "✘ speaker NOT @ $expectedClass — actual=${describe(actual)}")
            fail("[$scope] speaker did not reach $expectedClass — was ${describe(actual)}")
        }
        checkpoint(scope, "speaker @ $expectedClass — ${describe(actual)}")
    }

    fun assertListenerReached(
        scope: String,
        expectedClass: String,
        actual: NestsListenerState,
    ) {
        val ok =
            when (expectedClass) {
                "Connected" -> actual is NestsListenerState.Connected
                else -> error("unsupported expectedClass=$expectedClass")
            }
        if (!ok) {
            checkpoint(scope, "✘ listener NOT @ $expectedClass — actual=${describe(actual)}")
            fail("[$scope] listener did not reach $expectedClass — was ${describe(actual)}")
        }
        checkpoint(scope, "listener @ $expectedClass — ${describe(actual)}")
    }
}
