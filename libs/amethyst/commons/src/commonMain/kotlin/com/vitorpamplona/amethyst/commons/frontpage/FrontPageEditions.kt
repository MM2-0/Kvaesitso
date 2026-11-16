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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.util.deleteQuietly
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/** What the reader sees about a stored edition before opening it, and on its chips once open. */
@Immutable
@Serializable
data class SavedEdition(
    /** The local day it was printed, `yyyy-MM-dd`: one edition per day, the newest wins. */
    val day: String,
    val code: String,
    val printedAt: Long,
    val model: String,
    val tokens: Long? = null,
    /** In US dollars, from the provider's own prices; null when it lists none. */
    val costUsd: Double? = null,
    val quotes: Int = 0,
    val posts: Int = 0,
    /** The page's first headline, for the "ready" notification and the history. */
    val headline: String? = null,
    /** What resolve removed: the visible edge of somebody trying to edit the paper. */
    val removed: List<String> = emptyList(),
    /** The digest's size, for the next print's cost estimate. */
    val digestChars: Int = 0,
    val rounds: Int = 1,
    /** Hex sha256 of the page: its blob on the reader's Observer site. Null on editions kept before it. */
    val sha256: String? = null,
    /** On the reader's Observer site: published from here, or read back from it. */
    val published: Boolean = false,
    /** Printed by the runner the reader's schedule names (their Assistant), read from its own Observer site. */
    val byRunner: Boolean = false,
    /** The Kitchen's recipe this edition ran, as its address ([recipeAddress]): the next editions don't offer it again. */
    val recipe: String? = null,
)

/**
 * The reader's last week of front pages, on the device, one folder per account: each day's
 * checked HTML and its [SavedEdition]. Only pages that passed the boundary are ever stored.
 *
 * This is not the Observer's settled "no caching": that decision is about PROMPT caching (no
 * shared wire/personal split, every edition written standalone from its own feed). Keeping the
 * pages a reader already paid for is what the Observer's own skill does with its `editions/`
 * folder; it changes nothing about how a page is written.
 */
class FrontPageEditionStore(
    private val root: Path,
    private val fs: FileSystem = platformFileSystem,
    private val keep: Int = KEEP,
) {
    constructor(rootPath: String) : this(rootPath.toPath())

    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    // Days are written by FrontPageClock; anything else in the folder is not ours to read.
    private val dayName = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    private fun dir(account: HexKey) = root / account.lowercase()

    /** Newest first. */
    suspend fun list(account: HexKey): List<SavedEdition> =
        withContext(Dispatchers.IO) {
            lock.withLock { readAll(account) }
        }

    suspend fun html(
        account: HexKey,
        day: String,
    ): String? =
        withContext(Dispatchers.IO) {
            if (!dayName.matches(day)) return@withContext null
            lock.withLock {
                try {
                    fs.read(dir(account) / "$day.html") { readUtf8() }
                } catch (e: IOException) {
                    null
                }
            }
        }

    /** Stores [edition]; a second print on the same day replaces the first. Keeps the newest [keep]. */
    suspend fun save(
        account: HexKey,
        edition: SavedEdition,
        html: String,
    ) = withContext(Dispatchers.IO) {
        require(dayName.matches(edition.day)) { "Not a day: ${edition.day}" }
        lock.withLock {
            val dir = dir(account)
            fs.createDirectories(dir)
            write(dir / "${edition.day}.html", html)
            write(dir / "${edition.day}.json", json.encodeToString(SavedEdition.serializer(), edition))
            readAll(account).drop(keep).forEach { old ->
                fs.deleteQuietly(dir / "${old.day}.html")
                fs.deleteQuietly(dir / "${old.day}.json")
            }
        }
    }

    /** Deletes every page kept for [account]: for a removed account. */
    suspend fun removeAccount(account: HexKey) =
        withContext(Dispatchers.IO) {
            lock.withLock {
                try {
                    fs.deleteRecursively(dir(account), mustExist = false)
                } catch (e: IOException) {
                    Log.w(TAG, "Could not delete a removed account's papers", e)
                }
            }
        }

    /** Written beside, then moved: a crash mid-write never leaves a half page where a whole one was. */
    private fun write(
        target: Path,
        text: String,
    ) {
        val tmp = target.parent!! / (target.name + ".tmp")
        fs.write(tmp) { writeUtf8(text) }
        fs.atomicMove(tmp, target)
    }

    private fun readAll(account: HexKey): List<SavedEdition> {
        val dir = dir(account)
        val files = fs.listOrNull(dir) ?: return emptyList()
        return files
            .filter { it.name.endsWith(".json") && dayName.matches(it.name.removeSuffix(".json")) }
            .mapNotNull { path ->
                try {
                    json.decodeFromString(SavedEdition.serializer(), fs.read(path) { readUtf8() })
                } catch (e: IOException) {
                    null
                } catch (e: SerializationException) {
                    Log.w(TAG, "Unreadable edition record ${path.name}", e)
                    null
                } catch (e: IllegalArgumentException) {
                    null
                }
            }.filter { fs.exists(dir / "${it.day}.html") }
            .sortedByDescending { it.day }
    }

    companion object {
        const val KEEP = 7
        const val FOLDER = "front-pages"
        private const val TAG = "FrontPageEditions"
    }
}
