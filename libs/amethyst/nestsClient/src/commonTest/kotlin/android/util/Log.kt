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
package android.util

import kotlin.jvm.JvmStatic

class Log {
    companion object {
        @JvmStatic
        fun isLoggable(
            tag: String?,
            msg: Int?,
        ): Boolean = true

        @JvmStatic
        fun d(
            tag: String?,
            msg: String?,
        ): Int {
            println("DEBUG: $tag: $msg")
            return 0
        }

        @JvmStatic
        fun i(
            tag: String?,
            msg: String?,
        ): Int {
            println("INFO: $tag: $msg")
            return 0
        }

        @JvmStatic
        fun w(
            tag: String?,
            msg: String?,
        ): Int {
            println("WARN: $tag: $msg")
            return 0
        }

        @JvmStatic
        fun w(
            tag: String?,
            msg: String?,
            e: Throwable,
        ): Int {
            println("WARN: $tag: $msg")
            e.printStackTrace()
            return 0
        }

        @JvmStatic
        fun e(
            tag: String?,
            msg: String?,
        ): Int {
            println("ERROR: $tag: $msg")
            return 0
        }

        @JvmStatic
        fun e(
            tag: String?,
            msg: String?,
            e: Throwable,
        ): Int {
            println("ERROR: $tag: $msg")
            e.printStackTrace()
            return 0
        }
    }
}
