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
package com.vitorpamplona.nestsclient.moq.lite

/**
 * Mandatory path-normalization for moq-lite (Lite-03), mirroring
 * `kixelated/moq-rs/rs/moq-lite/src/path.rs`. moq-lite represents
 * broadcast and announce paths as plain UTF-8 strings; the wire format
 * is `varint(length) + UTF-8 bytes`. Both peers are required to
 * normalize before encoding and after decoding — without that, a wire
 * path `"/foo//bar/"` and `"foo/bar"` would be treated as distinct and
 * broadcast lookups silently fail.
 *
 * Normalization rules (mirroring `Path::new` semantics):
 *   - strip every leading `/`
 *   - strip every trailing `/`
 *   - collapse runs of `/` into a single `/`
 *
 * Empty paths are valid (the relay's "everything under root" prefix).
 */
object MoqLitePath {
    /**
     * Apply the moq-lite normalization rules. Returns the canonical path
     * string for [s].
     */
    fun normalize(s: String): String {
        if (s.isEmpty()) return s
        val out = StringBuilder(s.length)
        var lastWasSlash = true
        for (i in s.indices) {
            val c = s[i]
            if (c == '/') {
                if (!lastWasSlash) out.append('/')
                lastWasSlash = true
            } else {
                out.append(c)
                lastWasSlash = false
            }
        }
        // Strip the trailing `/` left behind by an input ending in `/`.
        if (out.isNotEmpty() && out[out.length - 1] == '/') {
            out.deleteCharAt(out.length - 1)
        }
        return out.toString()
    }

    /**
     * Concatenate two path components per moq-lite semantics. Each side
     * is normalized first; if either is empty, returns the other (no
     * leading or trailing `/`). Otherwise emits `"<prefix>/<suffix>"`.
     */
    fun join(
        prefix: String,
        suffix: String,
    ): String {
        val a = normalize(prefix)
        val b = normalize(suffix)
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        return "$a/$b"
    }

    /**
     * If [path] starts with [prefix] (path-component-aware — `"foo"` does
     * NOT match `"foobar"`), return the suffix that remains. Returns
     * null if the prefix does not match.
     *
     * Both inputs are normalized before comparison.
     */
    fun stripPrefix(
        prefix: String,
        path: String,
    ): String? {
        val p = normalize(prefix)
        val full = normalize(path)
        if (p.isEmpty()) return full
        if (full == p) return ""
        if (full.length > p.length && full.startsWith(p) && full[p.length] == '/') {
            return full.substring(p.length + 1)
        }
        return null
    }
}
