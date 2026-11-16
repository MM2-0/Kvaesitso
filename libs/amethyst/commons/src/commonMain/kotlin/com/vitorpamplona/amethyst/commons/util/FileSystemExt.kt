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
package com.vitorpamplona.amethyst.commons.util

import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/*
 * The java.io.File idioms that okio spells differently, for stores moving off
 * File without changing what they do on failure.
 *
 * okio reports a failed delete or rename by throwing; File reported it by
 * returning false, and the stores were written around that. These keep the
 * File behaviour, so a port is a port and not a quiet change in error handling.
 */

/** A file next to this one: `File(parentFile, name)`. */
fun Path.sibling(name: String): Path = parent?.div(name) ?: name.toPath()

/**
 * Deletes a file or an empty directory, and never throws: `File.delete()`.
 *
 * @return true when [path] is gone, including when it never existed.
 */
fun FileSystem.deleteQuietly(path: Path): Boolean =
    try {
        delete(path)
        true
    } catch (e: IOException) {
        !exists(path)
    }

/**
 * Deletes [path] and everything under it, carrying on past anything it cannot
 * delete: `File.deleteRecursively()`.
 *
 * One difference, deliberately kept: symlinks are deleted, never followed.
 * `File.deleteRecursively` walks into a linked directory and empties it, which
 * no store here ever wanted.
 *
 * @return true when nothing is left, including when [path] never existed.
 */
fun FileSystem.deleteRecursivelyQuietly(path: Path): Boolean {
    var deleted = true
    // metadataOrNull does not follow symlinks, so a link reads as a non-directory
    // and is deleted itself rather than walked into.
    if (metadataOrNull(path)?.isDirectory == true) {
        // Listed one directory at a time, so a directory that cannot be listed
        // costs only its own subtree: its siblings are still deleted, as
        // File.deleteRecursively did.
        val children =
            try {
                list(path)
            } catch (e: IOException) {
                deleted = false
                emptyList()
            }
        for (child in children) deleted = deleteRecursivelyQuietly(child) && deleted
    }
    return deleteQuietly(path) && deleted
}

/**
 * Replaces [target] with [source]: an atomic rename, or, where the filesystem
 * refuses one, a copy over [target] and a delete of [source].
 *
 * The fallback is what `if (!temp.renameTo(file)) { temp.copyTo(file, true); temp.delete() }`
 * did. It is not atomic, and is only here so that a filesystem without rename
 * still gets written.
 */
fun FileSystem.moveOrCopy(
    source: Path,
    target: Path,
) {
    try {
        atomicMove(source, target)
    } catch (e: IOException) {
        copy(source, target)
        deleteQuietly(source)
    }
}
