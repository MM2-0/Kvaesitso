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
 * Messenger contract between the main app (client) and [NappletBrowserService] (provider, in the
 * keyless `:napplet` process) for the **embedded** in-app browser. The provider hosts the WebView and
 * ships its rendered surface back through `androidx.privacysandbox.ui` (SurfaceControlViewHost), so the
 * page never renders in the key-holding main process. This channel only carries the surface handshake
 * (the `coreLibInfo` Bundle) plus chrome controls (navigate/reload/back/Tor) and URL updates — the
 * trusted address bar is drawn by the main process around the embedded surface.
 */
object NappletBrowserContract {
    /** FQN of the provider service, bound by name so the client needs no compile-time reference. */
    const val BROWSER_SERVICE_CLASS = "com.vitorpamplona.amethyst.napplethost.NappletBrowserService"

    /** Client → provider: create a browser session. Carries [KEY_URL], [KEY_PROXY_PORT], [KEY_USE_TOR], [KEY_BG_COLOR]. */
    const val MSG_CREATE_SESSION = 1

    /** Provider → client: the session's [KEY_CORE_LIB_INFO] Bundle (the SandboxedUiAdapter handle). */
    const val MSG_SESSION_READY = 2

    /** Client → provider: load [KEY_URL]. */
    const val MSG_NAVIGATE = 3

    /** Client → provider: reload the current page. */
    const val MSG_RELOAD = 4

    /** Client → provider: go back in the page history. */
    const val MSG_BACK = 5

    /** Client → provider: route this session over Tor ([KEY_USE_TOR]) or the open web. */
    const val MSG_SET_TOR = 6

    /**
     * Provider → client: the page navigated or retitled; carries [KEY_URL], [KEY_CAN_GO_BACK] and, once the
     * document has one, its `<title>` in [KEY_TITLE] (absent while a new page is still loading).
     */
    const val MSG_URL_CHANGED = 7

    /**
     * Provider → client: the page reported an IME event (a focused editable, a blur, or an external
     * text change). The raw JSON envelope (`{type:"ime.focus"|...}`) is carried in [KEY_IME_PAYLOAD]; the
     * embedded surface can't host the soft keyboard, so the main app shows it and relays editing.
     */
    const val MSG_IME_EVENT = 8

    /** Client → provider: an IME editing op for the focused field; raw JSON in [KEY_IME_PAYLOAD]. */
    const val MSG_IME_OP = 9

    /**
     * Provider → client: the main-frame load state changed. Carries [KEY_IS_LOADING] (a navigation is in
     * flight), [KEY_LOAD_FAILED] (the main frame errored), and [KEY_URL] (the page it settled on). Lets
     * the main process draw a loading spinner / error overlay over the embedded surface, and recover a
     * favorite whose session came up on a blank page (re-navigate to its real URL). [KEY_RENDERER_GONE]
     * marks a failure caused by the WebView renderer dying: the tab has no WebView left, so the client
     * must [MSG_RELOAD] (which rebuilds it on the page it was showing).
     */
    const val MSG_LOAD_STATE = 10

    /**
     * Provider → client: a JavaScript console message (console.log / warn / error / debug). Carries
     * [KEY_CONSOLE_LEVEL], [KEY_CONSOLE_MESSAGE], [KEY_CONSOLE_SOURCE], and [KEY_CONSOLE_LINE].
     */
    const val MSG_CONSOLE_LOG = 11

    /**
     * Client → provider: capture a magnified slice of the live page for the native-style selection loupe.
     * Host-side `PixelCopy` of the sandboxed surface returns `ERROR_SOURCE_NO_DATA` (the WebView pixels live
     * in a child SurfaceControl the host never draws into), so we capture INSIDE the provider — where the
     * WebView is a real in-window view. Carries [KEY_MAG_X]/[KEY_MAG_Y] (surface px center),
     * [KEY_MAG_BOX_W]/[KEY_MAG_BOX_H] (source rectangle, px), [KEY_MAG_ZOOM], and [KEY_MAG_REQ_T] (the
     * client's `nanoTime` send stamp, echoed back so the client can drop stale out-of-order frames).
     */
    const val MSG_MAGNIFIER_REQUEST = 12

    /**
     * Provider → client: the captured loupe frame. Carries [KEY_MAG_BYTES] (a PNG well under the Binder
     * limit), [KEY_MAG_W]/[KEY_MAG_H], [KEY_MAG_CAPTURE_MS] (provider-side draw+encode time), and the echoed
     * [KEY_MAG_REQ_T] so the client matches it to its request / drops stale frames.
     */
    const val MSG_MAGNIFIER_FRAME = 13

    /**
     * Provider → client: the page tapped an HTML file input and needs the system picker. The keyless
     * `:napplet` process has no Activity of its own (this provider is windowless), and its streamed
     * surface can't host one, so the main process runs the picker and sends the chosen URIs back.
     *
     * Carries the request description as data — [KEY_FILE_CHOOSER_ID], [KEY_FILE_CHOOSER_ACCEPT] (the raw `accept` entries),
     * [KEY_FILE_CHOOSER_MULTIPLE] and [KEY_FILE_CHOOSER_TITLE] — never a ready-made Intent, so the sandbox can ask the trusted
     * process for a file picker and for nothing else.
     */
    const val MSG_FILE_CHOOSER_REQUEST = 14

    /**
     * Client → provider: the picker for [KEY_FILE_CHOOSER_ID] finished. [KEY_FILE_CHOOSER_URIS] holds the picked
     * `content://` URIs, or is absent when the user cancelled — the page's file input stays busy until
     * one of the two arrives, so this is sent on every outcome. URI read grants are per-UID, so the URIs
     * the main process was granted are readable by the WebView here without re-granting.
     */
    const val MSG_FILE_CHOOSER_RESULT = 15

    // ---- PWA-parity controls (see BrowserChrome). Client → provider unless noted. ----

    /** Go forward in the page history. */
    const val MSG_FORWARD = 16

    /** Stop the current load. */
    const val MSG_STOP = 17

    /** Find [KEY_FIND_QUERY] in the page (empty clears). Provider answers with [MSG_FIND_RESULT]. */
    const val MSG_FIND = 18

    /** Move to the next ([KEY_FIND_FORWARD] = true) or previous match. */
    const val MSG_FIND_NEXT = 19

    /** Provider → client: [KEY_FIND_ACTIVE] (0-based) of [KEY_FIND_TOTAL] matches. */
    const val MSG_FIND_RESULT = 20

    /** Switch desktop-site mode ([KEY_ENABLED]); the page reloads. */
    const val MSG_SET_DESKTOP = 21

    /** Set the text size to [KEY_TEXT_ZOOM] percent. */
    const val MSG_SET_TEXT_ZOOM = 22

    /** Step back to the most recent page on [KEY_URL]'s origin (the app's home), or load it. */
    const val MSG_BACK_TO_SCOPE = 23

    /** Clear the current site's cookies and storage in this account's profile, then reload. */
    const val MSG_CLEAR_SITE_DATA = 24

    /** Clear every site's cookies and storage in this account's profile, then reload. */
    const val MSG_CLEAR_ALL_SITE_DATA = 42

    /** Ask for the page's certificate for page info; answered with [MSG_PAGE_INFO]. */
    const val MSG_PAGE_INFO_REQUEST = 25

    /**
     * Provider → client: the certificate of the page on screen — [KEY_CERT_ISSUED_TO], [KEY_CERT_ISSUED_BY]
     * and [KEY_CERT_VALID_UNTIL], all absent for a page without one (plain HTTP).
     */
    const val MSG_PAGE_INFO = 26

    /**
     * Provider → client: the page opened a JS dialog. [KEY_DIALOG_ID], [KEY_DIALOG_TYPE] (`alert`, `confirm`,
     * `prompt`, `beforeunload`), [KEY_URL], [KEY_DIALOG_MESSAGE], [KEY_DIALOG_DEFAULT], and
     * [KEY_DIALOG_OFFER_BLOCK] when "Block dialogs from this page" should be offered. The page's JS waits
     * until [MSG_JS_DIALOG_RESULT] arrives.
     */
    const val MSG_JS_DIALOG = 27

    /** The user's answer: [KEY_DIALOG_ID], [KEY_DIALOG_CONFIRMED], [KEY_DIALOG_TEXT], [KEY_DIALOG_BLOCK]. */
    const val MSG_JS_DIALOG_RESULT = 28

    /**
     * Provider → client: the page asked for camera / microphone / location. [KEY_PERMISSION_ID],
     * [KEY_BROWSER_ORIGIN], [KEY_PERMISSIONS] (`BrowserSitePermission` keys). Answered with
     * [MSG_PERMISSION_RESULT].
     */
    const val MSG_PERMISSION_REQUEST = 29

    /** The granted subset: [KEY_PERMISSION_ID], [KEY_PERMISSIONS]. */
    const val MSG_PERMISSION_RESULT = 30

    /** Provider → client: the page withdrew request [KEY_PERMISSION_ID]; drop its prompt. */
    const val MSG_PERMISSION_CANCEL = 31

    /** Provider → client: HTML fullscreen entered or left ([KEY_ENABLED]). */
    const val MSG_FULLSCREEN = 32

    /** Leave HTML fullscreen (the user pressed back). */
    const val MSG_EXIT_FULLSCREEN = 33

    /**
     * Provider → client: a download the page started (an attachment, `<a download>`, or an inline
     * `browser.download`) needs the user's consent before anything is fetched or written into the shared
     * Downloads collection. Carries [KEY_DOWNLOAD_ID], the WebView-reported [KEY_BROWSER_ORIGIN] (never a
     * page-supplied field), the sanitized [KEY_DOWNLOAD_NAME], [KEY_DOWNLOAD_SIZE], [KEY_DOWNLOAD_SOURCE], and
     * [KEY_DOWNLOAD_RISKY] (an install-or-script-like extension). Answered with
     * [MSG_DOWNLOAD_CONSENT_RESULT] on every outcome; the bytes themselves never leave the `:napplet` process.
     */
    const val MSG_DOWNLOAD_CONSENT = 34

    /** Client → provider: the user's answer to [MSG_DOWNLOAD_CONSENT]: [KEY_DOWNLOAD_ID] + [KEY_DOWNLOAD_ALLOWED]. */
    const val MSG_DOWNLOAD_CONSENT_RESULT = 35

    /** Provider → client: withdraw the [MSG_DOWNLOAD_CONSENT] card [KEY_DOWNLOAD_ID] (its tab closed). */
    const val MSG_DOWNLOAD_CANCEL = 36

    /**
     * Client → provider: the tab left the screen (parked off-screen by the tab layer). The page's WebView is
     * paused — animations, media and geolocation stop — so warm tabs in the background don't keep burning
     * CPU and battery. Mirrors [NappletEmbedContract.MSG_PAUSE] for napplets.
     */
    const val MSG_PAUSE = 39

    /** Client → provider: the tab is the visible one again; resume its WebView. */
    const val MSG_RESUME = 40

    /**
     * Client → provider: the tab was torn down (evicted, or rebuilt for a theme/account change). Drops the
     * session and its WebView even if the surface never opened — a session created for a view that was
     * disposed before it attached would otherwise sit in the provider forever, pinning its client.
     */
    const val MSG_CLOSE_SESSION = 41

    /**
     * Client → provider: whether the user is looking at this tab ([KEY_ENABLED]) — it's the visible tab AND
     * the app is on screen. While not, the provider holds the page's requests that act for the user or use
     * their key (NIP-07 sign / encrypt / decrypt) and sends them once the user is back, so a parked or
     * backgrounded site can't sign — even with "allow always" — while nobody is watching. Reads still flow.
     */
    const val MSG_SET_ATTENDED = 37

    /**
     * Provider → client: whether the process's pages currently go through Tor ([KEY_USE_TOR]). Sent on every
     * change. Tor always wins process-wide, so a tab set to the open web can still be on Tor because another
     * open page needs it — the client shows why.
     */
    const val MSG_ROUTE = 38

    /**
     * On [MSG_LOAD_STATE]: the page was not loaded because its network route can't be honored (Tor wanted but
     * not running yet, this WebView can't proxy, or applying the proxy failed). Nothing went out; the client
     * shows the error + Retry even over a page that loaded before.
     */
    const val KEY_ROUTE_BLOCKED = "routeBlocked"

    const val KEY_CAN_GO_FORWARD = "canGoForward"
    const val KEY_FIND_QUERY = "findQuery"
    const val KEY_FIND_FORWARD = "findForward"
    const val KEY_FIND_ACTIVE = "findActive"
    const val KEY_FIND_TOTAL = "findTotal"
    const val KEY_ENABLED = "enabled"
    const val KEY_TEXT_ZOOM = "textZoom"
    const val KEY_CERT_ISSUED_TO = "certIssuedTo"
    const val KEY_CERT_ISSUED_BY = "certIssuedBy"
    const val KEY_CERT_VALID_UNTIL = "certValidUntil"
    const val KEY_DIALOG_ID = "dialogId"
    const val KEY_DIALOG_TYPE = "dialogType"
    const val KEY_DIALOG_MESSAGE = "dialogMessage"
    const val KEY_DIALOG_DEFAULT = "dialogDefault"
    const val KEY_DIALOG_OFFER_BLOCK = "dialogOfferBlock"
    const val KEY_DIALOG_CONFIRMED = "dialogConfirmed"
    const val KEY_DIALOG_TEXT = "dialogText"
    const val KEY_DIALOG_BLOCK = "dialogBlock"
    const val KEY_PERMISSION_ID = "permissionId"
    const val KEY_PERMISSIONS = "permissions"
    const val KEY_BROWSER_ORIGIN = "browserOrigin"

    /** Correlates a [MSG_DOWNLOAD_CONSENT] prompt with its [MSG_DOWNLOAD_CONSENT_RESULT] answer. */
    const val KEY_DOWNLOAD_ID = "downloadId"

    /** The sanitized file name the consented download will be stored under (already path/control-char stripped). */
    const val KEY_DOWNLOAD_NAME = "downloadName"

    /** The size in bytes the consented download will write, or -1 when the server didn't say. */
    const val KEY_DOWNLOAD_SIZE = "downloadSize"

    /** The host a network download is fetched from (absent for inline data), shown when it isn't the page's. */
    const val KEY_DOWNLOAD_SOURCE = "downloadSource"

    /** Whether [KEY_DOWNLOAD_NAME]'s extension is one the user should double-check (installer/script-like). */
    const val KEY_DOWNLOAD_RISKY = "downloadRisky"

    /** The user's answer in [MSG_DOWNLOAD_CONSENT_RESULT]: true = save, false = discard. */
    const val KEY_DOWNLOAD_ALLOWED = "downloadAllowed"

    const val KEY_FILE_CHOOSER_ID = "fileChooserId"
    const val KEY_FILE_CHOOSER_ACCEPT = "fileChooserAccept"
    const val KEY_FILE_CHOOSER_MULTIPLE = "fileChooserMultiple"

    /**
     * The input's `capture` attribute: the page wants a camera, not a stored file. It decides
     * whether the main process may ask for the CAMERA permission on this request, so it has to
     * cross with it rather than being re-derived from the accept list.
     */
    const val KEY_FILE_CHOOSER_CAPTURE = "fileChooserCapture"
    const val KEY_FILE_CHOOSER_TITLE = "fileChooserTitle"
    const val KEY_FILE_CHOOSER_URIS = "fileChooserUris"

    const val KEY_MAG_X = "magX"
    const val KEY_MAG_Y = "magY"
    const val KEY_MAG_BOX_W = "magBoxW"
    const val KEY_MAG_BOX_H = "magBoxH"
    const val KEY_MAG_ZOOM = "magZoom"
    const val KEY_MAG_REQ_T = "magReqT"
    const val KEY_MAG_BYTES = "magBytes"
    const val KEY_MAG_W = "magW"
    const val KEY_MAG_H = "magH"
    const val KEY_MAG_CAPTURE_MS = "magCaptureMs"

    const val KEY_IS_LOADING = "isLoading"
    const val KEY_LOAD_FAILED = "loadFailed"
    const val KEY_RENDERER_GONE = "rendererGone"

    const val KEY_CONSOLE_LEVEL = "consoleLevel"
    const val KEY_CONSOLE_MESSAGE = "consoleMessage"
    const val KEY_CONSOLE_SOURCE = "consoleSource"
    const val KEY_CONSOLE_LINE = "consoleLine"

    const val KEY_IME_PAYLOAD = "imePayload"

    const val KEY_URL = "url"
    const val KEY_PROXY_PORT = "proxyPort"
    const val KEY_USE_TOR = "useTor"
    const val KEY_CORE_LIB_INFO = "coreLibInfo"
    const val KEY_CAN_GO_BACK = "canGoBack"
    const val KEY_TITLE = "title"

    /**
     * ARGB of Amethyst's theme background, passed from the main process. The WebView (and the surface
     * before its first frame) lives in the keyless `:napplet` process, which has no access to the main
     * app's Compose theme, so without this the pre-load background defaults to white instead of the
     * app's (often dark) background.
     */
    const val KEY_BG_COLOR = "bgColor"

    /**
     * The user's theme preference name ("DARK", "LIGHT", or "SYSTEM"), passed from the main process so
     * the keyless `:napplet` process can apply the matching night mode and let embedded WebView content
     * respond correctly to `prefers-color-scheme`.
     */
    const val KEY_THEME = "theme"

    /**
     * The user's omnibox search engine, as a [com.vitorpamplona.amethyst.commons.browser.SearchEngines]
     * id, so an address typed into a popup window this tab opens searches with the same engine.
     */
    const val KEY_SEARCH_ENGINE = "searchEngine"

    /**
     * Opaque per-account WebView storage-profile name (a truncated SHA-256 of the account pubkey,
     * minted in the main process). Partitions cookies/localStorage/IndexedDB/service workers per
     * account so an embedded site can't carry one npub's session into another. See
     * [NappletHostContract.EXTRA_WEBVIEW_PROFILE].
     */
    const val KEY_WEBVIEW_PROFILE = "webViewProfile"

    /**
     * Opaque per-tab session id the client stamps on [MSG_CREATE_SESSION] and every control message
     * ([MSG_NAVIGATE]/[MSG_RELOAD]/[MSG_BACK]/[MSG_SET_TOR]). A single provider instance is shared by all
     * embedded browser tabs (they bind the same Intent), so this scopes a control to the right surface —
     * and routes URL updates / NIP-07 traffic back to the right tab.
     */
    const val KEY_SESSION_ID = "sessionId"
}
