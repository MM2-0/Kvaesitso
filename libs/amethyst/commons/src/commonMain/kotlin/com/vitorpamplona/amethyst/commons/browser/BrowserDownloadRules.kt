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
package com.vitorpamplona.amethyst.commons.browser

import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The platform-free rules behind the in-app browser's download consent: what a saved file may be named,
 * which names deserve a warning, how a page-supplied `data:` URL turns into the bytes a consent card
 * describes, and how often one site may ask.
 *
 * A page can start a download without any gesture (a navigation to an attachment, a script `.click()`
 * on an `<a download>`, or a hand-forged bridge envelope), so every page-initiated save asks the user
 * first. The card shows exactly the name and size these rules produce, and the save writes exactly that.
 */
object BrowserDownloadRules {
    /** Cap for bytes a page hands over inline (a `blob:`/`data:` download travels as base64 over the bridge). */
    const val MAX_INLINE_BYTES = 25 * 1024 * 1024

    /** Longest file name kept; longer ones are shortened in the middle so the extension survives. */
    const val MAX_NAME_LENGTH = 120

    /**
     * Extensions something can install, run, or open as a live page from. The consent card flags them;
     * the name itself is never rewritten, so the user judges the real one.
     */
    val RISKY_EXTENSIONS =
        setOf(
            // Android
            "apk",
            "apks",
            "apkm",
            "xapk",
            "aab",
            "jar",
            "dex",
            "so",
            // Windows
            "exe",
            "msi",
            "msix",
            "appx",
            "bat",
            "cmd",
            "com",
            "cpl",
            "pif",
            "reg",
            "scr",
            "hta",
            "ps1",
            "js",
            "jse",
            "vbs",
            "vbe",
            "wsf",
            "lnk",
            "url",
            // Apple
            "dmg",
            "pkg",
            "app",
            "ipa",
            "command",
            // Linux
            "deb",
            "rpm",
            "appimage",
            "run",
            "sh",
            "zsh",
            "bash",
            "desktop",
            // Pages that run script when opened
            "html",
            "htm",
            "xhtml",
            "xht",
            "mht",
            "mhtml",
            "svg",
            "svgz",
        )

    /** Whether [fileName]'s last extension is one a user should double-check before saving. */
    fun isRisky(fileName: String): Boolean = fileName.substringAfterLast('.', "").trim().lowercase() in RISKY_EXTENSIONS

    // Path-reserved characters become "_"; C0/C1 controls, DEL, bidi controls and zero-width characters
    // are dropped outright, since they can make "invoice[RLO]gpj.apk" render as "invoicekpa.jpg".
    private val reservedNameChars = Regex("[:*?\"<>|]")
    private val invisibleNameChars = Regex("[\\u0000-\\u001f\\u007f-\\u009f\\u061c\\u200b-\\u200f\\u202a-\\u202e\\u2060-\\u2069\\ufeff]")

    /**
     * A plain file name from a page's suggestion: no path parts, no reserved or invisible characters, at
     * most [MAX_NAME_LENGTH] long with its extension kept. Null when nothing usable is left.
     */
    fun safeFileName(suggested: String?): String? {
        val base =
            suggested
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.replace(invisibleNameChars, "")
                ?.replace(reservedNameChars, "_")
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it != "." && it != ".." }
                ?: return null
        if (base.length <= MAX_NAME_LENGTH) return base
        val ext = base.substringAfterLast('.', "")
        return if (ext.isNotEmpty() && ext.length <= 16) {
            base.take(MAX_NAME_LENGTH - ext.length - 1).trimEnd() + "." + ext
        } else {
            base.take(MAX_NAME_LENGTH)
        }
    }

    /** A decoded `data:` URL: the declared MIME type (null when absent) and the payload bytes. */
    class DataUrl(
        val mimeType: String?,
        val bytes: ByteArray,
    )

    private val lenientBase64 = Base64.Mime.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

    /**
     * Decodes `data:[<mime>][;param=v…][;base64],<payload>`, base64 or percent-encoded. Null when it is
     * not a data URL, is malformed, or decodes to more than [maxBytes] — a refused download, never a
     * truncated one. The encoded length is checked first, so an oversized payload is never decoded.
     */
    fun decodeDataUrl(
        dataUrl: String,
        maxBytes: Int = MAX_INLINE_BYTES,
    ): DataUrl? {
        if (!dataUrl.startsWith("data:", ignoreCase = true)) return null
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return null
        val params = dataUrl.substring(5, comma).split(';')
        val mime =
            params
                .first()
                .trim()
                .lowercase()
                .takeIf { it.isNotEmpty() }
        val isBase64 = params.size > 1 && params.last().trim().equals("base64", ignoreCase = true)
        val payloadLength = dataUrl.length - comma - 1
        val bytes =
            if (isBase64) {
                // 4 chars per 3 bytes, plus room for the CRLF a MIME encoder puts every 76 chars.
                if (payloadLength.toLong() > maxBytes / 3 * 4L + maxBytes / 57 * 2L + 1024) return null
                runCatching { lenientBase64.decode(dataUrl, comma + 1, dataUrl.length) }.getOrNull() ?: return null
            } else {
                // A percent escape is 3 chars per byte; the exact size is counted before allocating.
                if (payloadLength.toLong() > maxBytes * 3L) return null
                percentDecode(dataUrl, comma + 1, maxBytes) ?: return null
            }
        if (bytes.size > maxBytes) return null
        return DataUrl(mime, bytes)
    }

    /**
     * RFC 3986 percent-decoding of [text] from [start] (a `+` stays a `+`, other characters are UTF-8).
     * Null on a broken escape or when the result would exceed [maxBytes], checked before allocating it.
     */
    private fun percentDecode(
        text: String,
        start: Int,
        maxBytes: Int,
    ): ByteArray? {
        var size = 0L
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (c == '%') {
                if (i + 2 >= text.length || hexValue(text[i + 1]) < 0 || hexValue(text[i + 2]) < 0) return null
                size += 1
                i += 3
            } else if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                size += 4
                i += 2
            } else {
                // A lone surrogate encodes as U+FFFD, 3 bytes, like any other char from U+0800 up.
                size +=
                    when {
                        c.code < 0x80 -> 1
                        c.code < 0x800 -> 2
                        else -> 3
                    }
                i++
            }
            if (size > maxBytes) return null
        }
        val out = ByteArray(size.toInt())
        var written = 0
        i = start
        var runStart = start
        while (i <= text.length) {
            if (i == text.length || text[i] == '%') {
                if (i > runStart) {
                    val run = text.encodeToByteArray(runStart, i)
                    run.copyInto(out, written)
                    written += run.size
                }
                if (i == text.length) break
                out[written++] = ((hexValue(text[i + 1]) shl 4) or hexValue(text[i + 2])).toByte()
                i += 3
                runStart = i
            } else {
                i++
            }
        }
        return if (written == out.size) out else out.copyOf(written)
    }

    private fun hexValue(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> -1
        }
}

/**
 * How often one browser surface (a window, or one embedded tab) lets a site show a download card. After
 * the user refuses a site's card, it waits [base] before it may ask again, and each further refusal
 * doubles that, up to [max]; a Save resets it. Without this a refused page could re-ask in a loop until
 * the user gives in. Main-thread only.
 */
class DownloadCooldown(
    private val base: Duration = 1.seconds,
    private val max: Duration = 1.minutes,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private class Hold(
        val since: TimeMark,
        val window: Duration,
    )

    private val holds = HashMap<String, Hold>()

    /** Whether [origin] may show a card now. */
    fun allows(origin: String): Boolean = holds[origin]?.let { it.since.elapsedNow() >= it.window } ?: true

    /** The user answered [origin]'s card: [allowed] resets its wait, a refusal (or dismissal) doubles it. */
    fun answered(
        origin: String,
        allowed: Boolean,
    ) {
        val previous = holds[origin]?.window
        val window = if (allowed || previous == null) base else minOf(previous * 2, max)
        holds[origin] = Hold(timeSource.markNow(), window)
    }
}
