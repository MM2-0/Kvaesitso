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

import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.Path
import kotlin.concurrent.Volatile

/**
 * Device-local favicon store for browsed sites, keyed by host. Favicons are **captured from the WebView
 * that already loaded the page** — on Android, in the keyless `:napplet` browser host, where they ride the
 * page's own (Tor-routed) network path — and handed here as PNG bytes; this is the privacy-preserving
 * alternative to the app fetching `host/favicon.ico` itself, which would bypass Tor and leak the visit.
 * Used to decorate favorite cards and omnibox suggestion rows.
 *
 * Bytes are persisted as one small PNG per host under [iconDir], so the only in-memory state is [keys] —
 * the set of hosts that currently have an icon — which exists purely to drive Compose recomposition (and
 * to keep filesystem existence checks out of composition).
 *
 * [iconDir] is a function rather than a path for the same reason [com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores]
 * takes `rootFilesDir`: the front end owns where its files live, and resolving it lazily keeps this class
 * free of any platform's notion of an app directory. The Android app passes
 * `{ appContext.filesDir.toOkioPath() / DIR }`.
 *
 * One instance per process. On Android the launcher/UI in the **main** process own it; the keyless
 * `:napplet` sandbox never builds one and relays captured bytes over IPC instead.
 */
class BrowserIconRegistry(
    private val iconDir: () -> Path,
    private val scope: CoroutineScope,
) {
    // Resolved once, not per call. [iconDir] is a lambda so the front end owns the location and
    // nothing resolves at construction; but on Android it is `appContext.filesDir`, and
    // Context.getFilesDir() takes a lock and mkdir()s the directory every time it is asked. The
    // object this replaced cached the File in init(), and iconModelFor is read from composition on
    // the main thread by the bottom bar, the favorites grid, the omnibox suggestions and the
    // napplet icon — so re-invoking the lambda there put a filesystem syscall on every frame that
    // draws an icon.
    private val dir: Path by lazy { iconDir() }

    private val _keys = MutableStateFlow<Set<String>>(emptySet())

    /** Sanitized host keys that currently have a stored icon. Observe to recompose when an icon arrives. */
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    @Volatile private var started = false

    /**
     * Indexes already-stored icons. Idempotent.
     *
     * Only the directory scan is deferred; [iconModelFor] and [record] resolve [iconDir] themselves and
     * work immediately. Until the scan lands [keys] is empty, so an icon renders its placeholder for one
     * frame and then recomposes — [keys] is a StateFlow precisely so that arrival drives recomposition.
     */
    fun init() {
        if (started) return
        started = true
        scope.launch {
            try {
                platformFileSystem.createDirectories(dir)
                val scanned =
                    platformFileSystem
                        .list(dir)
                        .mapNotNull { it.name.removeSuffix(PNG).takeIf { name -> name.isNotBlank() } }
                        .toSet()
                // Merged rather than assigned: a record() that lands while the scan is in flight has
                // already written its file and added its key, and overwriting the set wholesale would
                // drop it — the icon would sit on disk unshown until the next launch.
                _keys.update { it + scanned }
            } catch (e: Exception) {
                Log.w("BrowserIconRegistry", "Failed to index stored favicons", e)
            }
        }
    }

    /** Persists [bytes] as the favicon for [host] and marks it available. */
    fun record(
        host: String,
        bytes: ByteArray,
    ) {
        if (host.isBlank() || bytes.isEmpty()) return
        val key = sanitize(host)
        // Fire-and-forget: a favicon is a decoration, and the caller (on Android, the broker's IPC
        // handler, which runs on the main looper) must not wait on disk.
        // [keys] updates only after the bytes are actually on disk, so a reader can never be told an
        // icon exists before the file backing it does.
        scope.launch {
            try {
                platformFileSystem.createDirectories(dir)
                platformFileSystem.write(dir / (key + PNG)) { write(bytes) }
                _keys.update { it + key }
            } catch (e: Exception) {
                Log.w("BrowserIconRegistry", "Failed to store favicon for $host", e)
            }
        }
    }

    /**
     * A Coil model (`file://…`) for [host]'s favicon, or null when none is stored. Reads [keys] so callers
     * that observe the flow recompose as icons arrive — pass [keys]'s value as a `remember` key.
     */
    fun iconModelFor(host: String): String? {
        val key = sanitize(host)
        if (key !in _keys.value) return null
        return "file://" + (dir / (key + PNG))
    }

    companion object {
        /** Same directory the Android registry used: `filesDir/browser_icons`. */
        const val DIR = "browser_icons"

        private const val PNG = ".png"

        // Hosts map to a flat, filesystem-safe filename. Collisions (two hosts → one key) only mean a
        // shared icon file, which is harmless for a decoration.
        private fun sanitize(host: String): String =
            host
                .lowercase()
                .map { if (it.isLetterOrDigit() || it == '.' || it == '-') it else '_' }
                .joinToString("")
                .take(120)
    }
}
