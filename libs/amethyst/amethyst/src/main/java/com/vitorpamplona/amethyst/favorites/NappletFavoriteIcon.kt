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
package com.vitorpamplona.amethyst.favorites

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobPrefetcher
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.napplet.NappletWebViewProfiles
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The chosen icon blob and the servers that hold it — resolved from the live manifest. */
private class IconBlob(
    val path: PathTag,
    val servers: List<String>,
    // The site it belongs to, whose cache partition holds it.
    val author: String,
    val identifier: String,
    val mime: String? = null,
)

/**
 * A Coil model (`file://…`) for an nsite/napplet's app icon — a napplet's declared, content-addressed
 * `icon` blob (decoded and checked against its declared type), or the blob the
 * [NappletIconPath][com.vitorpamplona.quartz.nip5aStaticWebsites.NappletIconPath] heuristic picks from an
 * nsite's `path` tags — or null when there is none (or it hasn't downloaded yet). Used to decorate an nsite/napplet favorite the same way a captured favicon
 * decorates a web favorite, except this rides the same Tor-routed, sha256-verified blob path as the rest of
 * the site instead of a clearnet favicon fetch.
 *
 * Unlike a webapp's favicon, this can't be captured live: the applet runs in a cross-origin sandboxed
 * iframe under the trusted shell, so `WebChromeClient.onReceivedIcon` only ever reports the shell's icon,
 * never the applet's. Deriving it from the bundled blobs is the only path that sees the real icon.
 *
 * Observes the addressable note, so the icon resolves whenever the manifest arrives or updates in
 * LocalCache — not only if it already happened to be cached at first composition (on a cold start the
 * event streams in from relays a moment later). Returns null until the blob is on disk; the icon then
 * appears on the next recomposition. All disk + network work runs off the composition thread. The blob is
 * usually already cached (the browse/feed card prefetches every manifest blob, this one included); the
 * on-demand fetch here just covers favorites whose card isn't currently on screen.
 */
@Composable
fun rememberNappletIconModel(coordinate: String): String? {
    // checkGetOrCreate returns null only for a malformed coordinate, so this early return is stable for a
    // given coordinate (it never flips across recompositions, which would break composition structure).
    val note = remember(coordinate) { LocalCache.checkGetOrCreateAddressableNote(coordinate) } ?: return null
    val noteState by note
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()

    // Key on the event itself, not the NoteState wrapper: re-resolve only when the manifest actually
    // changes, not on every unrelated metadata bump (a reaction/zap tracked on the note).
    val event = noteState.note.event
    val icon = remember(event) { resolveIconBlob(event) } ?: return null

    return rememberVerifiedIconModel(icon)
}

@Composable
private fun rememberVerifiedIconModel(icon: IconBlob?): String? {
    val context = LocalContext.current
    var model by remember(icon?.path?.hash, icon?.mime) { mutableStateOf<String?>(null) }
    LaunchedEffect(icon?.path?.hash, icon?.mime, icon?.servers) {
        if (icon == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val partition = NappletBlobCache.partitionOf(NappletWebViewProfiles.current().orEmpty(), icon.author, icon.identifier)
            val cache = NappletBlobCache(NappletBlobCache.dirFor(context.cacheDir)).forSite(partition)
            if (cache.file(icon.path.hash) == null) {
                val torPort = Amethyst.instance.torManager.activePortOrNull.value
                // Never in the clear while the user wants Tor and it isn't up yet: the icon can wait.
                if (torPort != null || Amethyst.instance.torPrefs.torType.value == TorType.OFF) {
                    runCatchingCancellable { NappletBlobPrefetcher.prefetch(listOf(icon.path), icon.servers, context.cacheDir, torPort ?: -1, partition) }
                }
            }
            val file = cache.file(icon.path.hash)
            if (file != null && (icon.mime == null || verifiedIcon(file, icon))) model = "file://" + file.absolutePath
        }
    }
    return model
}

private fun verifiedIcon(
    file: File,
    icon: IconBlob,
): Boolean =
    runCatching {
        // Icons are optional. Bound decoding work and fall back to generic artwork for oversized input.
        if (file.length() > 2 * 1024 * 1024) return@runCatching false
        val bytes = file.readBytes()
        if (!StaticSiteResolver.verify(bytes, icon.path.hash)) return@runCatching false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outMimeType != icon.mime || bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return@runCatching false
        val options = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 256) }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@runCatching false
        decoded.recycle()
        true
    }.getOrDefault(false)

/** Asks each nsite/napplet event type for its bundled icon blob + the servers that hold it. */
private fun resolveIconBlob(event: Event?): IconBlob? =
    when (event) {
        // Checked here, not assumed from LocalCache: decrypted rumors (NIP-59, Marmot, Concord) are
        // stored unsigned yet marked verified, and one can become the addressable manifest. Runs once
        // per event inside remember(event). The blob itself is then hash-checked and decoded.
        is NappletManifest ->
            if (event.isValid() && event.verify()) {
                event.icon()?.let { IconBlob(PathTag("/icon", it.hash), event.servers(), event.pubKey, (event as? NamedNappletEvent)?.identifier() ?: "", it.mimeType) }
            } else {
                null
            }
        is RootSiteEvent -> event.iconBlob()?.let { IconBlob(it, event.servers(), event.pubKey, "") }
        is NamedSiteEvent -> event.iconBlob()?.let { IconBlob(it, event.servers(), event.pubKey, event.identifier()) }
        else -> null
    }

/**
 * A Coil model (`file://…`) for the cached favicon of [url]'s host, or null when no favicon
 * has been captured yet. The favicon is stored by [com.vitorpamplona.amethyst.commons.browser.BrowserIconRegistry] at browse time (the
 * WebView captures it in the sandboxed `:napplet` process); this composable just reads the cache.
 *
 * Early-returns null when [url] is blank or has no parseable host — this early return is stable
 * for a given [url] (the host either always parses or never does), so composition structure is
 * preserved across recompositions.
 */
@Composable
fun rememberWebAppIconModel(url: String): String? {
    val host = remember(url) { OmniboxInput.hostOf(url) } ?: return null
    val iconKeys by Amethyst.instance.browserIcons.keys
        .collectAsStateWithLifecycle()
    return remember(host, iconKeys) { Amethyst.instance.browserIcons.iconModelFor(host) }
}

/**
 * A Coil model for the bundled icon of an napplet or nsite identified by [author] and
 * [identifier]. Tries the napplet manifest (kinds 15129 / 35129) first, then falls back to the
 * nsite manifest (kinds 15128 / 35128). Returns null until the blob lands on disk.
 */
@Composable
fun rememberManifestIconModel(
    author: String,
    identifier: String,
): String? {
    val nappletCoord =
        remember(author, identifier) {
            if (identifier.isEmpty()) "${RootNappletEvent.KIND}:$author:" else "${NamedNappletEvent.KIND}:$author:$identifier"
        }
    val nsiteCoord =
        remember(author, identifier) {
            if (identifier.isEmpty()) "${RootSiteEvent.KIND}:$author:" else "${NamedSiteEvent.KIND}:$author:$identifier"
        }
    val nappletIcon = rememberNappletIconModel(nappletCoord)
    val nappletNote = remember(nappletCoord) { LocalCache.checkGetOrCreateAddressableNote(nappletCoord) }
    if (nappletNote != null) {
        val state by nappletNote
            .flow()
            .metadata.stateFlow
            .collectAsStateWithLifecycle()
        // A declared napplet with no usable icon gets generic artwork, not another app's icon.
        if (state.note.event is NappletManifest) return nappletIcon
    }
    return nappletIcon ?: rememberNappletIconModel(nsiteCoord)
}
