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
package com.vitorpamplona.amethyst.commons.frontpage

/**
 * The paper's own Content-Security-Policy (paper boundary v2, `quartz/plans/2026-11-11-paper-boundary-v2.md`):
 * nothing runs, nothing loads but `https:` pictures, styles stay inline. Written by the app, never by
 * the model (the validator refuses any `http-equiv`).
 */
const val PAPER_CSP = "default-src 'none'; img-src https:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'"

const val PAPER_CSP_META = "<meta http-equiv=\"Content-Security-Policy\" content=\"$PAPER_CSP\">"

/**
 * [html] with [PAPER_CSP_META] as the first child of `<head>`: for the copies that leave the app's
 * sandbox (the Observer site, the Download), where the validator would otherwise be the only
 * barrier. Idempotent. Comments are skipped, so a `<head>` written inside one can't take the meta.
 * Without a `<head>`, the meta goes right after `<html …>` (or at the start), where a browser
 * still reads it as the head's.
 */
fun withPaperCsp(html: String): String {
    val at = afterOpenTag(html, "head") ?: afterOpenTag(html, "html") ?: afterDoctype(html)
    if (html.startsWith(PAPER_CSP_META, at)) return html
    return html.substring(0, at) + PAPER_CSP_META + html.substring(at)
}

/** The index right after the first `<name …>` outside comments, or null. */
private fun afterOpenTag(
    html: String,
    name: String,
): Int? {
    var i = 0
    while (i < html.length) {
        if (html.startsWith("<!--", i)) {
            val end = html.indexOf("-->", i + 4)
            if (end < 0) return null
            i = end + 3
            continue
        }
        if (html[i] == '<' && html.regionMatches(i + 1, name, 0, name.length, ignoreCase = true)) {
            val next = html.getOrNull(i + 1 + name.length)
            if (next == null || next == '>' || next == '/' || next.isWhitespace()) return tagEnd(html, i + 1 + name.length)
        }
        i++
    }
    return null
}

/** The index after the `>` that closes the tag whose attributes start at [from]; quoted values may hold `>`. */
private fun tagEnd(
    html: String,
    from: Int,
): Int? {
    var quote: Char? = null
    for (i in from until html.length) {
        val c = html[i]
        if (quote != null) {
            if (c == quote) quote = null
        } else if (c == '"' || c == '\'') {
            quote = c
        } else if (c == '>') {
            return i + 1
        }
    }
    return null
}

private fun afterDoctype(html: String): Int {
    val trimmed = html.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
    if (!html.regionMatches(trimmed, "<!doctype", 0, 9, ignoreCase = true)) return 0
    return html.indexOf('>', trimmed).let { if (it < 0) 0 else it + 1 }
}
