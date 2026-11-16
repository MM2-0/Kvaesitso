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
package com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis

import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag

/**
 * Turning a batch of files into NIP-30 emojis: which files are images, what shortcode each gets,
 * and the `shortcode url` lists people trade packs as. Shared by the app's bulk import and
 * `amy emoji import`.
 */
object EmojiImport {
    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "avif", "apng", "svg", "bmp")

    private val INVALID_SHORTCODE_CHARS = Regex("[^A-Za-z0-9_-]+")
    private val LIST_SEPARATOR = Regex("[\\s,]+")

    fun extensionOf(fileName: String) = baseName(fileName).substringAfterLast('.', "").lowercase()

    fun isImageFileName(fileName: String) = extensionOf(fileName) in IMAGE_EXTENSIONS

    fun isZipFileName(fileName: String) = extensionOf(fileName) == "zip"

    fun isZipMimeType(mimeType: String?) = mimeType == "application/zip" || mimeType == "application/x-zip-compressed"

    /** The MIME type of an image file name, by extension; null when it is not an image. */
    fun imageMimeTypeOf(fileName: String): String? =
        when (val ext = extensionOf(fileName)) {
            "jpg", "jpeg" -> "image/jpeg"
            "svg" -> "image/svg+xml"
            "png", "gif", "webp", "avif", "apng", "bmp" -> "image/$ext"
            else -> null
        }

    /** The file name without its directories, for `/` and `\` separators alike. */
    fun baseName(path: String) = path.substringAfterLast('/').substringAfterLast('\\')

    /**
     * The shortcode a file name suggests: its base name without the extension, with every run of
     * characters NIP-30 forbids turned into one `_`. `party parrot.gif` becomes `party_parrot`.
     * Null when nothing usable is left.
     */
    fun shortcodeFromFileName(fileName: String): String? {
        val base = baseName(fileName)
        val stem = base.substringBeforeLast('.')
        return stem.replace(INVALID_SHORTCODE_CHARS, "_").trim('_').ifEmpty { null }
    }

    /**
     * Gives each of [fileNames] a shortcode, in order. A shortcode that an earlier file of the
     * batch already took gets a `_2`, `_3`, … suffix, so `smile.png` and `smile.gif` both come in.
     * Files without a usable name map to null.
     */
    fun assignShortcodes(fileNames: List<String>): List<String?> {
        val taken = HashSet<String>()
        return fileNames.map { name ->
            val code = shortcodeFromFileName(name) ?: return@map null
            var unique = code
            var n = 2
            while (!taken.add(unique)) unique = "${code}_${n++}"
            unique
        }
    }

    /** What to do with a batch of image files, decided before anything is uploaded. */
    class Plan(
        /** For each file to upload: its index in the batch and the shortcode it gets. */
        val toUpload: List<Pair<Int, String>>,
        /** Shortcodes already in the pack, left alone (and not uploaded). */
        val skipped: List<String>,
        /** Files whose name leaves no usable shortcode. */
        val unnamed: List<String>,
    )

    /**
     * Names [fileNames] with [assignShortcodes] and sets aside the ones whose shortcode is in
     * [existingCodes] (unless [replace]) or that have no usable name.
     */
    fun plan(
        fileNames: List<String>,
        existingCodes: Set<String>,
        replace: Boolean = false,
    ): Plan {
        val toUpload = mutableListOf<Pair<Int, String>>()
        val skipped = mutableListOf<String>()
        val unnamed = mutableListOf<String>()
        assignShortcodes(fileNames).forEachIndexed { index, code ->
            when {
                code == null -> unnamed.add(fileNames[index])
                !replace && code in existingCodes -> skipped.add(code)
                else -> toUpload.add(index to code)
            }
        }
        return Plan(toUpload, skipped, unnamed)
    }

    class ParsedList(
        val emojis: List<EmojiUrlTag>,
        /** 1-based line numbers that are neither blank, a `#` comment, nor a valid entry. */
        val badLines: List<Int>,
    )

    /**
     * Reads a pack shared as text, one emoji per line: `shortcode url`, `:shortcode: url`,
     * `shortcode,url` or `shortcode<TAB>url`. Blank lines and `#` comments are ignored.
     */
    fun parseList(text: String): ParsedList {
        val emojis = mutableListOf<EmojiUrlTag>()
        val badLines = mutableListOf<Int>()
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val parts = line.split(LIST_SEPARATOR, limit = 2)
            val code = parts[0].removePrefix(":").removeSuffix(":")
            val url = parts.getOrNull(1)?.trim()
            if (url != null && EmojiUrlTag.isValidShortcode(code) && (url.startsWith("https://") || url.startsWith("http://"))) {
                emojis.add(EmojiUrlTag(code, url))
            } else {
                badLines.add(index + 1)
            }
        }
        return ParsedList(emojis, badLines)
    }
}
