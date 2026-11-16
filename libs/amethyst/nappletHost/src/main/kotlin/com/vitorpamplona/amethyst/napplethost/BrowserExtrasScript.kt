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

/**
 * A document-start script injected only into the **browser** surfaces (never into sandboxed napplets or
 * nsites), filling in what an installed Chrome PWA gets and a WebView doesn't:
 *
 * - `navigator.share` / `navigator.canShare` — WebView has no Web Share. Text, title and url go to the
 *   Android share sheet (`browser.share`); files are declined, as `canShare` reports.
 * - `<meta name="theme-color">` — reported (`browser.themeColor`, normalized to `rgb(r, g, b)`) whenever it
 *   or the colour scheme changes, so the window can tint its system bars and Recents entry.
 * - `blob:` / `data:` downloads — only the page can read a `blob:` URL, so a click on (or a programmatic
 *   `.click()` of) an `<a download>` pointing at one is turned into its bytes (`browser.download`). The
 *   native side treats it as a request: the user confirms every save on a consent card, so a page that
 *   posts the envelope itself gains nothing over using the script.
 *
 * Messages travel over the same origin-scoped native bridge as NIP-07; the host handles `browser.*` types
 * itself and never forwards them to the broker.
 */
object BrowserExtrasScript {
    val JS: String =
        """
        (function () {
          if (window.top !== window || window.__amethystBrowserExtras) return;
          window.__amethystBrowserExtras = true;
          var MAX_BYTES = ${BrowserDownloads.MAX_INLINE_BYTES};

          function send(message) {
            try { var b = window.__nappletBridge; if (b) b.postMessage(JSON.stringify(message)); } catch (_) {}
          }

          // ---- Web Share ----
          if (!navigator.share) {
            var hasFiles = function (data) { return !!(data && data.files && data.files.length); };
            var shareUrl = function (data) {
              if (!data || data.url === undefined || data.url === null) return '';
              try { return new URL(String(data.url), document.baseURI).href; } catch (_) { return null; }
            };
            navigator.share = function (data) {
              data = data || {};
              var activation = navigator.userActivation;
              if (activation && !activation.isActive) {
                return Promise.reject(new DOMException('Must be handling a user gesture to perform a share request.', 'NotAllowedError'));
              }
              if (hasFiles(data)) return Promise.reject(new DOMException('Sharing files is not supported.', 'NotAllowedError'));
              var url = shareUrl(data);
              if (url === null) return Promise.reject(new TypeError('Invalid URL'));
              if (!data.title && !data.text && !url) return Promise.reject(new TypeError('No data to share.'));
              send({ type: 'browser.share', title: data.title ? String(data.title) : '', text: data.text ? String(data.text) : '', url: url });
              return Promise.resolve();
            };
            navigator.canShare = function (data) {
              if (!data || hasFiles(data) || shareUrl(data) === null) return false;
              return !!(data.title || data.text || data.url);
            };
          }

          // ---- theme-color ----
          var lastRaw;
          var lastColor;
          function pickThemeColor() {
            var metas = document.querySelectorAll('meta[name="theme-color"]');
            for (var i = 0; i < metas.length; i++) {
              var media = metas[i].getAttribute('media');
              try { if (!media || window.matchMedia(media).matches) return metas[i].getAttribute('content'); } catch (_) {}
            }
            return null;
          }
          function normalize(color) {
            if (!color) return '';
            var root = document.body || document.documentElement;
            if (!root) return '';
            var probe = document.createElement('span');
            probe.style.display = 'none';
            probe.style.color = color;
            if (!probe.style.color) return '';
            root.appendChild(probe);
            var computed = getComputedStyle(probe).color;
            probe.remove();
            return computed || '';
          }
          function reportTheme() {
            var raw = pickThemeColor();
            // Only a changed declaration is worth a style computation.
            if (raw === lastRaw && lastColor !== undefined) return;
            lastRaw = raw;
            var color = normalize(raw);
            if (color === lastColor) return;
            lastColor = color;
            send({ type: 'browser.themeColor', color: color });
          }
          var themeTimer = 0;
          function scheduleTheme() { clearTimeout(themeTimer); themeTimer = setTimeout(reportTheme, 50); }
          function watchTheme() {
            reportTheme();
            // <meta> lives in <head>: watching only there keeps busy pages (feeds re-rendering the body)
            // from waking this up on every DOM change.
            try {
              if (document.head) {
                new MutationObserver(scheduleTheme).observe(document.head, {
                  subtree: true, childList: true, attributes: true, attributeFilter: ['content', 'media', 'name']
                });
              }
            } catch (_) {}
            try {
              window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function () { lastRaw = undefined; scheduleTheme(); });
            } catch (_) {}
          }
          if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', watchTheme, { once: true });
          else watchTheme();

          // ---- blob: / data: downloads ----
          // Pages often revoke a blob URL right after clicking it, before an async fetch could read it, so
          // keep a handle on each Blob until a minute after its URL is revoked (the page holds it until the
          // revoke anyway, so this doesn't change its lifetime by more than that minute).
          var blobs = new Map();
          try {
            var nativeCreate = URL.createObjectURL;
            var nativeRevoke = URL.revokeObjectURL;
            URL.createObjectURL = function (obj) {
              var url = nativeCreate.apply(URL, arguments);
              try { if (obj instanceof Blob) blobs.set(url, obj); } catch (_) {}
              return url;
            };
            URL.revokeObjectURL = function (url) {
              setTimeout(function () { blobs.delete(url); }, 60000);
              return nativeRevoke.apply(URL, arguments);
            };
          } catch (_) {}
          function isInlineDownload(a) {
            return !!(a && a.hasAttribute && a.hasAttribute('download') && /^(blob|data):/i.test(a.href || ''));
          }
          function deliver(a) {
            var name = a.getAttribute('download') || '';
            var known = blobs.get(a.href);
            (known ? Promise.resolve(known) : fetch(a.href).then(function (r) { return r.blob(); })).then(function (blob) {
              if (blob.size > MAX_BYTES) return;
              var reader = new FileReader();
              reader.onload = function () { send({ type: 'browser.download', name: name, mime: blob.type || '', data: String(reader.result) }); };
              reader.readAsDataURL(blob);
            }).catch(function () {});
          }
          document.addEventListener('click', function (e) {
            var a = e.target && e.target.closest ? e.target.closest('a[download]') : null;
            if (!isInlineDownload(a)) return;
            e.preventDefault();
            deliver(a);
          }, true);
          // Libraries usually build a detached <a download href="blob:…"> and call .click() on it; a click
          // on a detached element never reaches the document listener above.
          var nativeClick = HTMLAnchorElement.prototype.click;
          HTMLAnchorElement.prototype.click = function () {
            if (!this.isConnected && isInlineDownload(this)) { deliver(this); return; }
            return nativeClick.apply(this, arguments);
          };
        })();
        """.trimIndent()
}
