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
package com.vitorpamplona.amethyst.commons.napplet.fs

/**
 * NAP-FS's path model, and the names Amethyst gives what the user picks.
 *
 * Picked files appear under one virtual root, [PICKED_ROOT], by name: `/picked/photo.png`. The name
 * is the platform's display name made safe for the path model, with ` (2)`, ` (3)`, … added when the
 * session already shows that name. Nothing about where the file really lives (its folder, its
 * provider, the account it belongs to) reaches the napplet.
 */
object NappletFsPaths {
    /** Where picked files appear. */
    const val PICKED_ROOT = "/picked"

    /** The longest name a picked file gets, extension included. */
    const val MAX_NAME_LENGTH = 120

    private const val FALLBACK_NAME = "file"

    /**
     * Whether [path] follows the NAP-FS path model: absolute, `/`-separated, no empty, `.` or `..`
     * segment, and no backslash, control or bidirectional formatting character.
     */
    fun isValid(path: String): Boolean {
        if (!path.startsWith("/")) return false
        if (path == "/") return true
        return path.substring(1).split('/').all { segment ->
            segment.isNotEmpty() && segment != "." && segment != ".." && segment.none { it == '\\' || isForbidden(it) }
        }
    }

    /**
     * [displayName] as one path segment: separators, control, bidirectional and zero-width
     * characters become `_`, and a name left empty, `.` or `..` becomes `file`. Long names are cut,
     * keeping their extension.
     */
    fun sanitizeName(displayName: String): String {
        val cleaned =
            buildString {
                displayName.forEach { c -> append(if (c == '/' || c == '\\' || isForbidden(c)) '_' else c) }
            }.trim()
        val name = if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") FALLBACK_NAME else cleaned
        if (name.length <= MAX_NAME_LENGTH) return name
        val extension = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length < 16 }
        return if (extension == null) {
            name.take(MAX_NAME_LENGTH)
        } else {
            name.take(MAX_NAME_LENGTH - extension.length - 1).trimEnd() + "." + extension
        }
    }

    /**
     * The virtual path for a picked file called [displayName], one that none of [taken] already is.
     * Paths are compared ignoring case, so two names that differ only by case never coexist (some
     * file systems a napplet may mirror them to could not tell them apart).
     */
    fun pickedPath(
        displayName: String,
        taken: Set<String>,
    ): String {
        val name = sanitizeName(displayName)
        val lowered = taken.mapTo(HashSet()) { it.lowercase() }
        val first = "$PICKED_ROOT/$name"
        if (first.lowercase() !in lowered) return first
        val stem = name.substringBeforeLast('.', name)
        val extension = if (stem.length < name.length) name.substring(stem.length) else ""
        var n = 2
        while (true) {
            val candidate = "$PICKED_ROOT/$stem ($n)$extension"
            if (candidate.lowercase() !in lowered) return candidate
            n++
        }
    }

    /** The last segment of [path]. */
    fun nameOf(path: String): String = path.substringAfterLast('/')

    // C0 and C1 controls, DEL, the bidirectional formatting characters (LRM, RLM, ALM, the
    // embeddings, overrides and isolates) and the zero-width ones.
    private fun isForbidden(c: Char): Boolean {
        val code = c.code
        return code < 0x20 ||
            code in 0x7F..0x9F ||
            code == 0x061C ||
            code in 0x200B..0x200F ||
            code in 0x202A..0x202E ||
            code in 0x2060..0x2069 ||
            code == 0xFEFF
    }
}
