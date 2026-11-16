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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient.FileChooserParams
import com.vitorpamplona.amethyst.commons.browser.FileChooserAccept
import com.vitorpamplona.amethyst.commons.napplet.NappletPickedFiles
import java.io.File
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * The picker behind an HTML `<input type="file">` in any of Amethyst's WebViews.
 *
 * A WebView shows no picker of its own: unless the app overrides
 * `WebChromeClient.onShowFileChooser`, tapping a file input is a silent no-op. Every host here does
 * override it, and they all build their request through this so the browser, the full-screen napplet /
 * nSite sandbox, and both embedded surfaces filter, multi-select and capture identically.
 *
 * A request is described by plain data (accept list, multi-select, capture, title) rather than by a
 * ready-made Intent, because the two embedded hosts have no Activity of their own and must ask the
 * main process to run the picker for them. Shipping data keeps the keyless `:napplet` process unable
 * to hand the trusted process an arbitrary Intent to start — it can only ask for a file picker.
 */
object NappletFileChooser {
    /**
     * A built picker, plus the capture files behind whatever camera options it offers.
     *
     * The capture files have to outlive the Intent: a camera writes to `EXTRA_OUTPUT` and returns a
     * result with no data at all, so the only way to learn what was shot is to look at the files
     * afterwards ([parseResult]).
     */
    class Request internal constructor(
        val intent: Intent,
        internal val captures: List<Capture>,
    )

    /** One camera option's output file, its content URI, and who was granted access to write it. */
    class Capture internal constructor(
        internal val file: File,
        internal val uri: Uri,
        internal val grantedTo: List<String>,
    )

    /**
     * Builds the picker for a request described by [acceptTypes] / [allowMultiple] / [captureEnabled].
     *
     * `ACTION_GET_CONTENT` (rather than `ACTION_OPEN_DOCUMENT`) so gallery apps that are not document
     * providers still show up — the same trade-off Chrome makes; the page only needs to read the bytes
     * once, not hold a persistable grant. When the page accepts photos or video, the matching camera is
     * offered alongside the file sources, which is what makes an image-accepting input behave the way it
     * does in a mobile browser instead of only reaching already-saved files.
     *
     * [cameraAllowed] must be the caller's live CAMERA-permission state: `ACTION_IMAGE_CAPTURE` throws
     * `SecurityException` for an app that declares the CAMERA permission without holding it, and
     * Amethyst declares it. Callers request it first when the page asked for a camera outright
     * ([captureEnabled]); see [wantsCamera].
     */
    fun buildRequest(
        context: Context,
        acceptTypes: List<String>,
        allowMultiple: Boolean,
        captureEnabled: Boolean,
        cameraAllowed: Boolean,
        pageTitle: CharSequence? = null,
    ): Request {
        val mimeMap = MimeTypeMap.getSingleton()
        val resolved = FileChooserAccept.resolve(acceptTypes) { ext -> mimeMap.getMimeTypeFromExtension(ext) }

        val pick =
            Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(resolved.primaryType)
                // The provider sets this on the result too; asking for it up front makes the read grant
                // explicit. Grants are per-UID, so a URI picked by the main process is readable by the
                // WebView in `:napplet` without any re-granting.
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // Only worth sending when it says more than `type` already does.
        if (resolved.mimeTypes.size > 1) {
            pick.putExtra(Intent.EXTRA_MIME_TYPES, resolved.mimeTypes.toTypedArray())
        }
        if (allowMultiple) pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

        val captures = if (cameraAllowed) buildCaptureOptions(context, acceptTypes) else emptyList()

        val title = pageTitle?.takeIf { it.isNotBlank() } ?: context.getString(CommonsR.string.browser_file_chooser_title)
        val chooser = Intent.createChooser(pick, title)

        if (captures.isNotEmpty()) {
            // At most two entries (stills + video), which is also all the system chooser will display
            // from EXTRA_INITIAL_INTENTS — anything beyond that would be silently dropped.
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, captures.map { it.first }.toTypedArray())
        }

        return Request(chooser, captures.map { it.second })
    }

    /** Convenience overload for the two Activity hosts, which hold the real [FileChooserParams]. */
    fun buildRequest(
        context: Context,
        params: FileChooserParams,
        cameraAllowed: Boolean,
    ): Request =
        buildRequest(
            context = context,
            acceptTypes = params.acceptTypes?.toList().orEmpty(),
            allowMultiple = allowsMultiple(params.mode),
            captureEnabled = params.isCaptureEnabled,
            cameraAllowed = cameraAllowed,
            pageTitle = params.title,
        )

    /**
     * Whether a picker for [acceptTypes] would offer a camera at all — i.e. whether it is worth holding
     * (or asking for) the CAMERA permission. False for a page that only accepts documents, so opening a
     * PDF upload never triggers a camera prompt.
     */
    fun wantsCamera(acceptTypes: List<String>): Boolean {
        val mimeMap = MimeTypeMap.getSingleton()
        return FileChooserAccept.captureMedia(acceptTypes) { ext -> mimeMap.getMimeTypeFromExtension(ext) }.isNotEmpty()
    }

    /**
     * Whether [mode] should let the user pick more than one file.
     *
     * `MODE_OPEN_FOLDER` is a `webkitdirectory` input. Android has no picker that hands a WebView the
     * files of a directory — `ACTION_OPEN_DOCUMENT_TREE` returns a tree handle, not the file URIs the
     * page's callback takes — so the closest honest answer is to let the user select the files
     * themselves. They lose `webkitRelativePath`, but they can complete the upload instead of being
     * limited to one file. The two embedded providers resolve this before sending the request, so all
     * four surfaces agree on it.
     */
    @Suppress("InlinedApi") // MODE_OPEN_FOLDER is API 37; on an older runtime the compare never matches.
    fun allowsMultiple(mode: Int): Boolean = mode == FileChooserParams.MODE_OPEN_MULTIPLE || mode == FileChooserParams.MODE_OPEN_FOLDER

    /**
     * Turns an activity result into the array the page's `filePathCallback` expects, or null when
     * nothing was chosen. Handles the single-URI and `ClipData` multi-select shapes, and the camera
     * shape — where the result carries no URI at all and the evidence of a capture is a scratch file
     * that now has bytes in it. Every capture file this request created and did not return is deleted
     * here, so a cancelled or unused camera option leaves nothing behind.
     *
     * The URIs are read off the Intent here rather than through
     * [FileChooserParams.parseResult][android.webkit.WebChromeClient.FileChooserParams.parseResult],
     * which is a WebView *static*: calling it boots Chromium in whatever process calls it. This runs
     * in the main process too — [com.vitorpamplona.amethyst.napplet.WebFileChooserActivity] is the
     * chooser host for the embedded surfaces and declares no `android:process` — while `:napplet`
     * already holds the WebView data directory. That second init throws
     * `Using WebView from more than one process at once with the same data directory is not
     * supported` out of `AwDataDirLock` and takes the whole app down on every completed embedded
     * pick. The platform implementation reads exactly these two fields, so this is behaviour-for-
     * behaviour identical without dragging WebView into a process that must not have it.
     */
    fun parseResult(
        context: Context,
        request: Request,
        resultCode: Int,
        data: Intent?,
    ): Array<Uri>? {
        val picked =
            if (resultCode == Activity.RESULT_OK && data != null) {
                val clip = data.clipData
                if (clip != null && clip.itemCount > 0) {
                    Array(clip.itemCount) { clip.getItemAt(it).uri }
                } else {
                    data.data?.let { arrayOf(it) }
                }
            } else {
                null
            }?.takeIf { it.isNotEmpty() }

        // Not every camera reports a capture the same way. `ACTION_IMAGE_CAPTURE` returns no URI and
        // simply fills the file, but `ACTION_VIDEO_CAPTURE` on at least GoogleCamera echoes the
        // EXTRA_OUTPUT URI straight back in the result — including when the recording was abandoned
        // and nothing was ever written. Taken at face value that hands the page a 0-byte file and the
        // upload silently succeeds with no content. An echoed URI is therefore only worth anything if
        // the file behind it actually has bytes; when it does not, drop it and let the emptiness rules
        // below treat the request as dismissed. URIs that are not ours are never second-guessed.
        val usable =
            picked
                ?.filter { uri ->
                    val own = request.captures.firstOrNull { it.uri == uri }
                    // The page reads the file with this app's permissions: nothing of the app's own.
                    NappletPickedFiles.mayHandToPage(uri.scheme, uri.authority, own != null) { isOwnAuthority(context, it) } &&
                        (own == null || own.file.length() > 0)
                }?.toTypedArray()
                ?.takeIf { it.isNotEmpty() }

        // A camera that returns no URI at all reports success by filling the file we handed it, so an
        // empty one means it was dismissed. Only consulted when the picker returned nothing of its own.
        val captured =
            if (usable != null || resultCode != Activity.RESULT_OK) {
                null
            } else {
                request.captures.firstOrNull { it.file.length() > 0 }
            }

        // A capture whose URI is being handed to the page has to outlive this call, whether it got
        // there by being echoed back ([usable]) or by being the filled file ([captured]).
        val returned = usable?.toSet().orEmpty()

        request.captures.forEach { capture ->
            // Every camera app was granted write access up front because none of them could be ruled
            // out yet. The outcome is known now, so none of them needs it any more — including for the
            // photo being returned, which this app reads back through its own provider.
            NappletCaptureFiles.releaseGrants(context, capture.grantedTo, capture.uri)
            if (capture !== captured && capture.uri !in returned) NappletCaptureFiles.discard(context, capture.file)
        }

        return usable ?: captured?.let { arrayOf(it.uri) }
    }

    /**
     * Whether [authority] is a provider of this app (any of its processes), exported or not. NAP-FS
     * picks, made in the main process, refuse these too.
     */
    fun isOwnAuthority(
        context: Context,
        authority: String,
    ): Boolean =
        authority.startsWith(context.packageName) ||
            runCatching { context.packageManager.resolveContentProvider(authority, 0)?.packageName == context.packageName }.getOrDefault(true)

    /**
     * One camera option per medium the page accepts, each with its own output file. Media with no
     * installed handler are skipped so the chooser never shows an entry that dead-ends.
     */
    private fun buildCaptureOptions(
        context: Context,
        acceptTypes: List<String>,
    ): List<Pair<Intent, Capture>> {
        val mimeMap = MimeTypeMap.getSingleton()
        val media = FileChooserAccept.captureMedia(acceptTypes) { ext -> mimeMap.getMimeTypeFromExtension(ext) }
        if (media.isEmpty()) return emptyList()

        NappletCaptureFiles.sweepStale(context)

        return media.mapNotNull { medium ->
            val (action, extension) =
                when (medium) {
                    FileChooserAccept.CaptureMedia.IMAGE -> MediaStore.ACTION_IMAGE_CAPTURE to "jpg"
                    FileChooserAccept.CaptureMedia.VIDEO -> MediaStore.ACTION_VIDEO_CAPTURE to "mp4"
                }

            val handlers = context.packageManager.queryIntentActivities(Intent(action), PackageManager.MATCH_DEFAULT_ONLY)
            if (handlers.isEmpty()) return@mapNotNull null

            val (file, uri) = NappletCaptureFiles.create(context, extension) ?: return@mapNotNull null
            val packages = handlers.map { it.activityInfo.packageName }.distinct()
            NappletCaptureFiles.grantTo(context, packages, uri)

            val intent =
                Intent(action)
                    .putExtra(MediaStore.EXTRA_OUTPUT, uri)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

            intent to Capture(file, uri, packages)
        }
    }
}
