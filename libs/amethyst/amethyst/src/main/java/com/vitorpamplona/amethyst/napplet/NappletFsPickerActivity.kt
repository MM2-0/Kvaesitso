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
package com.vitorpamplona.amethyst.napplet

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.result.contract.ActivityResultContracts
import com.vitorpamplona.amethyst.commons.browser.FileChooserAccept
import com.vitorpamplona.amethyst.commons.napplet.NappletPickedFiles
import com.vitorpamplona.amethyst.napplethost.NappletFileChooser
import com.vitorpamplona.quartz.utils.Log

/**
 * Invisible main-process host for one NAP-FS pick ([NappletFsPickerCoordinator]): the system's
 * document picker, reported on every exit by [PickerHostActivity].
 *
 * What comes back is filtered as a page's file input is ([NappletPickedFiles]): only other apps'
 * `content://` documents, never one of this app's own providers, whose files would otherwise be read
 * with this app's permissions and handed to the napplet.
 */
class NappletFsPickerActivity : PickerHostActivity<NappletFsPickerCoordinator.Ask, List<Uri>>(NappletFsPickerCoordinator.handoff) {
    // Field, not a local: registerForActivityResult must run before this activity reaches STARTED.
    private val picker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            report(if (result.resultCode == Activity.RESULT_OK) urisOf(result.data) else null)
            finish()
        }

    override fun launch(ask: NappletFsPickerCoordinator.Ask) {
        runCatching { picker.launch(intentFor(ask)) }
            .onFailure { e ->
                Log.w(TAG, "No document picker available", e)
                finish()
            }
    }

    private fun intentFor(ask: NappletFsPickerCoordinator.Ask): Intent {
        val save = ask.kind == NappletFsPickerCoordinator.Kind.SAVE
        val intent =
            Intent(if (save) Intent.ACTION_CREATE_DOCUMENT else Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (save || ask.wantsWrite) intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        val mimeMap = MimeTypeMap.getSingleton()
        val resolved = FileChooserAccept.resolve(ask.accept) { ext -> mimeMap.getMimeTypeFromExtension(ext) }
        if (save) {
            // A new document has one type: the one its name implies, else the first the napplet named.
            val fromName =
                ask.title
                    ?.substringAfterLast('.', "")
                    ?.lowercase()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { mimeMap.getMimeTypeFromExtension(it) }
            intent.type = fromName ?: resolved.mimeTypes.firstOrNull { !it.contains('*') } ?: FALLBACK_MIME
            ask.title?.let { intent.putExtra(Intent.EXTRA_TITLE, it) }
        } else {
            intent.type = resolved.primaryType
            if (resolved.mimeTypes.size > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, resolved.mimeTypes.toTypedArray())
            if (ask.kind == NappletFsPickerCoordinator.Kind.OPEN_MULTIPLE) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        return intent
    }

    private fun urisOf(data: Intent?): List<Uri>? {
        if (data == null) return null
        val clip = data.clipData
        val uris =
            if (clip != null && clip.itemCount > 0) {
                List(clip.itemCount) { clip.getItemAt(it).uri }
            } else {
                listOfNotNull(data.data)
            }
        return uris
            .filter { NappletPickedFiles.mayHandToPage(it.scheme, it.authority, isOwnCapture = false) { authority -> NappletFileChooser.isOwnAuthority(this, authority) } }
            .takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val TAG = "NappletFsPicker"
        const val FALLBACK_MIME = "application/octet-stream"
    }
}
