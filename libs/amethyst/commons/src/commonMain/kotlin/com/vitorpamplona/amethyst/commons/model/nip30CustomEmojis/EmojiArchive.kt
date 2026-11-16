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

import okio.Path

/** One image taken out of an emoji archive, written to a temp file the caller deletes. */
class ExtractedEmojiFile(
    /** The entry's file name, without the directories it had inside the archive. */
    val fileName: String,
    val path: Path,
)

class ExtractedEmojiArchive(
    val files: List<ExtractedEmojiFile>,
    /** Image entries left out for being larger than [EmojiArchiveLimits.MAX_ENTRY_BYTES]. */
    val tooLarge: List<String>,
    /** True when reading stopped at [EmojiArchiveLimits.MAX_IMAGES] or [EmojiArchiveLimits.MAX_TOTAL_BYTES]. */
    val truncated: Boolean = false,
)

object EmojiArchiveLimits {
    /** The largest archive read at all: it is held in memory while it is unpacked. */
    const val MAX_ARCHIVE_BYTES = 128L * 1024 * 1024

    /** The largest single image taken in, from an archive or on its own. */
    const val MAX_ENTRY_BYTES = 10L * 1024 * 1024

    /** Reading stops (and the archive counts as truncated) past this many images or bytes of them. */
    const val MAX_IMAGES = 2_000
    const val MAX_TOTAL_BYTES = 256L * 1024 * 1024

    /** Everything inflated, kept or not: past this the archive is refused as a zip bomb. */
    const val MAX_INFLATED_BYTES = 512L * 1024 * 1024
}

/**
 * Writes the images inside the zip archive [zip] to temp files, in archive order, skipping
 * folders, other files, and macOS metadata (`__MACOSX/`, `._*`). The caller deletes the files
 * through `platformFileSystem`. Null when this platform cannot read zip archives.
 *
 * Stops early, flagged [ExtractedEmojiArchive.truncated], at [EmojiArchiveLimits.MAX_IMAGES] or
 * [EmojiArchiveLimits.MAX_TOTAL_BYTES]. Throws [IllegalArgumentException] for a corrupt archive or
 * one that inflates past [EmojiArchiveLimits.MAX_INFLATED_BYTES] (a zip bomb), deleting whatever
 * it already wrote.
 */
expect suspend fun extractEmojiArchive(zip: ByteArray): ExtractedEmojiArchive?
