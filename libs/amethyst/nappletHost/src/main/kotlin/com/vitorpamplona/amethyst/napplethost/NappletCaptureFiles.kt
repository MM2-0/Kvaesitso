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
package com.vitorpamplona.amethyst.napplethost

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.vitorpamplona.quartz.utils.Log
import java.io.File

/**
 * The scratch files a camera app writes a WebView capture into, and the [FileProvider] URIs that
 * expose them.
 *
 * `ACTION_IMAGE_CAPTURE` only returns a full-resolution photo when it is handed somewhere to put it
 * (`EXTRA_OUTPUT`); without one it returns a thumbnail in the result extras, which is useless as an
 * upload. So each capture option gets its own empty file here first.
 *
 * They live in `cacheDir`, which is one directory shared by the main and `:napplet` processes, so it
 * does not matter which side ran the picker. They cannot be deleted as soon as the capture returns —
 * the page may not read the file until the user submits the form, which can be minutes later — so
 * [sweepStale] reclaims them on a later run instead, and the OS can evict the whole directory under
 * storage pressure regardless.
 */
object NappletCaptureFiles {
    private const val DIR = "webview-captures"
    private const val TAG = "NappletCapture"

    /** Files older than this are assumed read (or abandoned) and are deleted on the next request. */
    private const val STALE_AFTER_MS = 24L * 60 * 60 * 1000

    /** Authority of the dedicated provider declared in this module's manifest. */
    private fun authority(context: Context) = "${context.packageName}.napplethost.captures"

    /**
     * Creates an empty capture file and its content URI, or null when the cache directory cannot be
     * written — in which case the caller simply omits that camera option rather than offering one that
     * would come back empty.
     */
    fun create(
        context: Context,
        extension: String,
    ): Pair<File, Uri>? =
        runCatching {
            val dir = File(context.cacheDir, DIR).apply { mkdirs() }
            // createTempFile, not a name built from a clock and a per-object sequence: the main and
            // `:napplet` processes each hold their OWN copy of this object, so those sequences run
            // independently and two picks started in the same millisecond could name the same file —
            // one capture would then silently overwrite the other. The extension is what FileProvider
            // types the URI from, so it has to survive into the suffix.
            val file = File.createTempFile("capture-", ".$extension", dir)
            file to FileProvider.getUriForFile(context, authority(context), file)
        }.onFailure { Log.w(TAG, "Could not create a capture file", it) }
            .getOrNull()

    /** Deletes capture files left behind by earlier requests. Called before creating new ones. */
    fun sweepStale(context: Context) {
        runCatching {
            val cutoff = System.currentTimeMillis() - STALE_AFTER_MS
            File(context.cacheDir, DIR)
                .listFiles()
                ?.filter { it.isFile && it.lastModified() < cutoff }
                ?.forEach { it.delete() }
        }.onFailure { Log.w(TAG, "Could not sweep stale capture files", it) }
    }

    /**
     * Grants every installed camera app write access to [uri].
     *
     * A chooser entry supplied through `EXTRA_INITIAL_INTENTS` is started by the system chooser, not by
     * us, and the URI grant flags on that Intent are not reliably carried across the hop on every
     * Android version — the camera then cannot open the output file and returns nothing. Granting each
     * resolved package up front is the part that works everywhere.
     *
     * The cost is that the grant necessarily goes to every camera app, not just the one the user is
     * about to choose (nobody knows that yet), so [releaseGrants] takes it all back the moment the
     * outcome is known.
     */
    fun grantTo(
        context: Context,
        packages: Collection<String>,
        uri: Uri,
    ) {
        packages.forEach { pkg ->
            runCatching { context.grantUriPermission(pkg, uri, GRANT_FLAGS) }
                .onFailure { Log.w(TAG, "Could not grant capture access to $pkg", it) }
        }
    }

    /**
     * Takes back the grants [grantTo] handed out, so no other app keeps a handle on a photo the user
     * just took. Called for the kept capture as well as the discarded ones.
     *
     * Revokes per package rather than with the whole-URI overload, which is the point: this app reads
     * the file back through its OWN provider, and same-UID access to a non-exported provider never went
     * through a grant in the first place. Naming the packages makes it impossible for this to clip our
     * own read on the way past.
     */
    fun releaseGrants(
        context: Context,
        packages: Collection<String>,
        uri: Uri,
    ) {
        packages.forEach { pkg ->
            runCatching { context.revokeUriPermission(pkg, uri, GRANT_FLAGS) }
        }
    }

    /** Drops a capture file the user never filled — a cancelled camera, or the option they didn't take. */
    fun discard(
        context: Context,
        file: File,
    ) {
        runCatching { file.delete() }
    }

    private const val GRANT_FLAGS = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
}
