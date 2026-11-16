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
package com.vitorpamplona.amethyst.commons.service.http

import okhttp3.internal.concurrent.TaskRunner
import okhttp3.internal.http2.Http2
import java.io.Closeable
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import java.util.logging.ConsoleHandler
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import kotlin.reflect.KClass

object OkHttpDebugLogging {
    // Keep references to loggers to prevent their configuration from being GC'd.
    private val configuredLoggers = CopyOnWriteArraySet<Logger>()

    fun enableHttp2() = enable(Http2::class)

    fun enableTaskRunner() = enable(TaskRunner::class)

    fun logHandler() =
        ConsoleHandler().apply {
            level = Level.FINE
            formatter =
                object : SimpleFormatter() {
                    override fun format(record: LogRecord) = String.format(Locale.ROOT, $$"[%1$tF %1$tT] %2$s %n", record.millis, record.message)
                }
        }

    fun enable(
        loggerClass: String,
        handler: Handler = logHandler(),
    ): Closeable {
        val logger = Logger.getLogger(loggerClass)
        if (configuredLoggers.add(logger)) {
            logger.addHandler(handler)
            logger.level = Level.FINEST
        }
        return Closeable {
            logger.removeHandler(handler)
        }
    }

    fun enable(loggerClass: KClass<*>) = enable(loggerClass.java.name)
}
