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
package com.vitorpamplona.amethyst.commons.service

import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel
import com.vitorpamplona.quic.QuicLog

/**
 * Sends quic-kmp's log lines to Quartz's [Log]. quic logs nothing until a sink is set, so each
 * entry point (Android, desktop, `amy`) installs this once at startup. [isLoggable] reads
 * [Log.minLevel] on every call, so a line below it is skipped before its message is built.
 */
object QuicLogBridge : QuicLog.Sink {
    fun install() {
        QuicLog.sink = this
    }

    override fun isLoggable(level: QuicLog.Level): Boolean = Log.minLevel <= level.toLogLevel()

    override fun log(
        level: QuicLog.Level,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        when (level) {
            QuicLog.Level.DEBUG -> Log.d(tag, message, throwable)
            QuicLog.Level.INFO -> Log.i(tag, message, throwable)
            QuicLog.Level.WARN -> Log.w(tag, message, throwable)
            QuicLog.Level.ERROR -> Log.e(tag, message, throwable)
        }
    }

    private fun QuicLog.Level.toLogLevel(): LogLevel =
        when (this) {
            QuicLog.Level.DEBUG -> LogLevel.DEBUG
            QuicLog.Level.INFO -> LogLevel.INFO
            QuicLog.Level.WARN -> LogLevel.WARN
            QuicLog.Level.ERROR -> LogLevel.ERROR
        }
}
