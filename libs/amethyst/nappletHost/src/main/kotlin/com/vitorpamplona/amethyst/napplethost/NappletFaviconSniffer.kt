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

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.WebView
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.util.parseJsonObjectOrNull
import com.vitorpamplona.amethyst.commons.util.stringOrNull
import com.vitorpamplona.quartz.utils.Log

/**
 * Declared-favicon capture for the sandboxed browser WebViews, complementing
 * `WebChromeClient.onReceivedIcon`.
 *
 * `onReceivedIcon` only ever hands back a **rasterized** `Bitmap`, and Android WebView simply does not
 * rasterize an SVG favicon into it. A site that declares *only* `<link rel="icon" type="image/svg+xml">`
 * (ditto.pub) therefore never fires the callback at all, while a site that also declares a PNG
 * alternative (brainstorm.nosfabrica.com) does. This closes that gap: after the page settles we ask the
 * **page itself** for its declared icon and let the **page** fetch the bytes.
 *
 * Privacy: every byte here is fetched by `fetch()` running *inside the loaded page's own JS context*, so
 * the request rides the exact network path the page already rides — the sandbox WebView's proxy, i.e.
 * Tor when Tor is on. The main process still never touches an icon URL, which is the property
 * `BrowserIconRegistry`'s KDoc protects. Fetches are `credentials: 'omit'` so no cookie rides along.
 *
 * Trust: the returned bytes come from a page we do not trust, so they are size-bounded and magic-byte
 * validated here before they are relayed. Anything unrecognized is dropped silently — a site with no
 * usable icon simply has no icon.
 */
internal object NappletFaviconSniffer {
    private const val TAG = "NappletFaviconSniffer"

    /** Hard cap on the icon payload. Well under the Binder limit and far above any sane favicon. */
    private const val MAX_ICON_BYTES = 384 * 1024

    /** Polls for the in-page async fetch to settle. Bounded: gives up (silently) after the last one. */
    private val POLL_DELAYS_MS = longArrayOf(400, 900, 1_800, 3_000, 5_000)

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Asks [webView]'s current page for its best declared icon and relays the bytes to [onIcon].
     *
     * No-ops unless the page is a real http(s) page with a parseable host. [onIcon] fires at most once
     * per call, on the main thread; it never fires when the page declares nothing usable, when every
     * candidate fails to load, or when the page navigates away mid-flight.
     */
    fun capture(
        webView: WebView,
        onIcon: (host: String, bytes: ByteArray) -> Unit,
    ) {
        val url = webView.url ?: return
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        val host = OmniboxInput.hostOf(url) ?: return

        // Scopes this attempt to this navigation: a poll that lands after the page moved on sees a
        // different (or absent) seq and drops out instead of attributing a stale icon to the new host.
        val seq = System.nanoTime().toString()
        runCatching { webView.evaluateJavascript(startScript(seq), null) }
            .onFailure { return }

        poll(webView, host, seq, url, 0, onIcon)
    }

    private fun poll(
        webView: WebView,
        host: String,
        seq: String,
        pageUrl: String,
        attempt: Int,
        onIcon: (String, ByteArray) -> Unit,
    ) {
        if (attempt >= POLL_DELAYS_MS.size) return
        handler.postDelayed({
            // The page navigated away — this navigation's icon is no longer interesting.
            if (webView.url != pageUrl) return@postDelayed
            runCatching {
                webView.evaluateJavascript(pollScript(seq)) { raw ->
                    val state = parse(raw)
                    when {
                        state == null -> poll(webView, host, seq, pageUrl, attempt + 1, onIcon)
                        state.first == "ok" -> decode(state.second)?.let { onIcon(host, it) }
                        state.first == "pending" -> poll(webView, host, seq, pageUrl, attempt + 1, onIcon)
                        else -> Unit // "none" — the page has no icon we could load. Leave it undecorated.
                    }
                }
            }
        }, POLL_DELAYS_MS[attempt])
    }

    /** `state` to base64 payload, or null when the page has not produced a result for this seq yet. */
    private fun parse(raw: String?): Pair<String, String>? {
        if (raw == null || raw == "null") return null
        val json = parseJsonObjectOrNull(raw) ?: return null
        val state = json.stringOrNull("state")?.ifBlank { null } ?: return null
        return state to json.stringOrNull("data").orEmpty()
    }

    /**
     * Base64 → validated image bytes. Rejects anything that is not a recognizable image, and trims any
     * leading whitespace/BOM off an SVG so Coil's content sniffing (which requires `<` at offset 0 to
     * recognize an SVG) still fires even though the registry stores every icon under a `.png` name.
     */
    private fun decode(base64: String): ByteArray? {
        if (base64.isBlank()) return null
        // 4 base64 chars per 3 bytes; reject before allocating the decoded array.
        if (base64.length > (MAX_ICON_BYTES / 3) * 4 + 4) return null
        val bytes =
            runCatching { Base64.decode(base64, Base64.DEFAULT) }
                .onFailure { Log.w(TAG, "Undecodable favicon payload", it) }
                .getOrNull() ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_ICON_BYTES) return null
        if (isRaster(bytes)) return bytes
        return trimmedSvg(bytes)
    }

    private fun isRaster(b: ByteArray): Boolean {
        if (b.size < 4) return false

        fun at(i: Int) = b[i].toInt() and 0xFF
        // PNG, JPEG, GIF, BMP, ICO/CUR, RIFF (WebP).
        if (at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47) return true
        if (at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF) return true
        if (at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x38) return true
        if (at(0) == 0x42 && at(1) == 0x4D) return true
        if (at(0) == 0x00 && at(1) == 0x00 && (at(2) == 0x01 || at(2) == 0x02) && at(3) == 0x00) return true
        if (at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46) return true
        return false
    }

    /** Non-null only when the payload really is XML/SVG text, re-based so byte 0 is the opening `<`. */
    private fun trimmedSvg(b: ByteArray): ByteArray? {
        var start = 0
        // UTF-8 BOM.
        if (b.size >= 3 && (b[0].toInt() and 0xFF) == 0xEF && (b[1].toInt() and 0xFF) == 0xBB && (b[2].toInt() and 0xFF) == 0xBF) start = 3
        while (start < b.size && b[start].toInt().toChar().isWhitespace()) start++
        if (start >= b.size || b[start].toInt().toChar() != '<') return null
        // Must actually mention an <svg tag, not just be arbitrary XML/HTML (a 404 page, say).
        val head = String(b, start, minOf(b.size - start, 2048), Charsets.UTF_8)
        if (!head.contains("<svg", ignoreCase = true)) return null
        return if (start == 0) b else b.copyOfRange(start, b.size)
    }

    /**
     * Picks the best declared icon and fetches it **in page context**, stashing the result on `window`
     * for [pollScript] to collect. Candidates, best first: a raster `rel="icon"`, the SVG `rel="icon"`,
     * an `apple-touch-icon`, then `/favicon.ico` as a last resort. Each is tried in turn until one
     * loads, so a declared-but-404 icon falls through instead of losing to nothing.
     */
    private fun startScript(seq: String): String =
        """
        (function () {
          try {
            var K = '__amethystFavicon';
            var st = { seq: '$seq', state: 'pending', data: '' };
            window[K] = st;
            var cands = [];
            var links = document.querySelectorAll('link[rel]');
            for (var i = 0; i < links.length; i++) {
              var l = links[i];
              var rel = (l.getAttribute('rel') || '').toLowerCase();
              var type = (l.getAttribute('type') || '').toLowerCase();
              var href = l.href;
              if (!href) continue;
              var isIcon = /(^|\s)(shortcut\s+)?icon(\s|${'$'})/.test(rel);
              var isApple = rel.indexOf('apple-touch-icon') >= 0;
              var isSvg = type.indexOf('svg') >= 0 || /\.svg([?#]|${'$'})/i.test(href);
              if (isIcon && !isSvg) cands.push({ h: href, s: 100 });
              else if (isIcon && isSvg) cands.push({ h: href, s: 80 });
              else if (isApple) cands.push({ h: href, s: 60 });
            }
            if (location.origin && location.origin.indexOf('http') === 0) {
              cands.push({ h: location.origin + '/favicon.ico', s: 10 });
            }
            cands.sort(function (a, b) { return b.s - a.s; });
            if (!cands.length) { st.state = 'none'; return; }
            (function next(i) {
              if (i >= cands.length) { st.state = 'none'; return; }
              fetch(cands[i].h, { credentials: 'omit', redirect: 'follow' })
                .then(function (r) { if (!r.ok) throw 0; return r.blob(); })
                .then(function (blob) {
                  if (!blob || !blob.size || blob.size > $MAX_ICON_BYTES) throw 0;
                  return new Promise(function (res, rej) {
                    var fr = new FileReader();
                    fr.onload = function () { res(String(fr.result)); };
                    fr.onerror = function () { rej(0); };
                    fr.readAsDataURL(blob);
                  });
                })
                .then(function (durl) {
                  var c = durl.indexOf(',');
                  if (c < 0) throw 0;
                  st.data = durl.substring(c + 1);
                  st.state = 'ok';
                })
                .catch(function () { next(i + 1); });
            })(0);
          } catch (e) {
            window['__amethystFavicon'] = { seq: '$seq', state: 'none', data: '' };
          }
        })();
        """.trimIndent()

    /** Returns the stashed result as an object (WebView JSON-encodes it), or null if it is not ours. */
    private fun pollScript(seq: String): String =
        """
        (function () {
          var s = window['__amethystFavicon'];
          if (!s || s.seq !== '$seq') return null;
          return { state: s.state, data: s.data || '' };
        })();
        """.trimIndent()
}
