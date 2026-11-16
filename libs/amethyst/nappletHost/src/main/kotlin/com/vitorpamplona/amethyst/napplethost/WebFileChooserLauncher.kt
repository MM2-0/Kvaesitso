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

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * Runs one HTML file-input pick from [activity], camera options included, and reports the result to
 * [onResult] — exactly once per [launch], with null meaning "nothing chosen".
 *
 * Every surface that can start an Activity uses this, so the full-screen browser, the full-screen
 * napplet / nSite sandbox, and the main-process host that serves the two embedded surfaces cannot
 * drift apart in how they filter, multi-select, or offer the camera.
 *
 * Must be constructed as a **field** of [activity]: it registers its activity-result contracts in the
 * constructor, and `registerForActivityResult` has to run before the activity reaches STARTED.
 */
class WebFileChooserLauncher(
    private val activity: ComponentActivity,
    private val onResult: (Array<Uri>?) -> Unit,
) {
    /** What [launch] was asked for, held across a permission round-trip. */
    private class Ask(
        val acceptTypes: List<String>,
        val allowMultiple: Boolean,
        val captureEnabled: Boolean,
        val pageTitle: CharSequence?,
    )

    private var ask: Ask? = null
    private var request: NappletFileChooser.Request? = null

    private val chooser =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val pending = request
            request = null
            val uris = pending?.let { NappletFileChooser.parseResult(activity, it, result.resultCode, result.data) }
            onResult(uris)
        }

    private val cameraPermission =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Denied is not a failure: the page still gets the file sources, it just cannot shoot a new
            // photo. Falling back beats reporting nothing and leaving the input dead.
            open(cameraAllowed = granted)
        }

    /**
     * Opens the picker for a page's file input.
     *
     * When the page asked for a camera outright (`capture`) and CAMERA is not held yet, the permission
     * is requested first — the user tapped a control whose whole purpose is to take a photo, so the
     * prompt is expected. Without `capture` the camera is offered only if the permission is already
     * held, so choosing a document never raises a camera prompt out of nowhere.
     */
    fun launch(
        acceptTypes: List<String>,
        allowMultiple: Boolean,
        captureEnabled: Boolean,
        pageTitle: CharSequence?,
    ) {
        ask = Ask(acceptTypes, allowMultiple, captureEnabled, pageTitle)

        if (captureEnabled && !hasCameraPermission() && NappletFileChooser.wantsCamera(acceptTypes)) {
            val requested = runCatching { cameraPermission.launch(Manifest.permission.CAMERA) }.isSuccess
            if (requested) return
        }
        open(cameraAllowed = hasCameraPermission())
    }

    private fun open(cameraAllowed: Boolean) {
        val pending = ask ?: return onResult(null)
        ask = null
        // A second file input can ask before the first pick returns. The page's own callback is already
        // released by PendingFileChooser, but the superseded request still owns scratch files and camera
        // grants that nothing else would ever come back for.
        abandonInFlight()

        val built =
            NappletFileChooser.buildRequest(
                context = activity,
                acceptTypes = pending.acceptTypes,
                allowMultiple = pending.allowMultiple,
                captureEnabled = pending.captureEnabled,
                cameraAllowed = cameraAllowed,
                pageTitle = pending.pageTitle,
            )
        request = built

        runCatching { chooser.launch(built.intent) }
            .onFailure { e ->
                Log.w(TAG, "No activity available to pick a file", e)
                request = null
                // Runs the cancel path so the capture scratch files this request created are cleaned up.
                NappletFileChooser.parseResult(activity, built, Activity.RESULT_CANCELED, null)
                Toast.makeText(activity, activity.getString(CommonsR.string.browser_file_chooser_unavailable), Toast.LENGTH_LONG).show()
                onResult(null)
            }
    }

    /** Releases the scratch files and camera grants of a request whose result will never be read. */
    private fun abandonInFlight() {
        val stale = request ?: return
        request = null
        NappletFileChooser.parseResult(activity, stale, Activity.RESULT_CANCELED, null)
    }

    /**
     * Called when the host is going away with a pick still open, so the request's scratch files and
     * camera grants are not left behind for the stale sweep to find a day later.
     */
    fun teardown() = abandonInFlight()

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private companion object {
        private const val TAG = "WebFileChooser"
    }
}
