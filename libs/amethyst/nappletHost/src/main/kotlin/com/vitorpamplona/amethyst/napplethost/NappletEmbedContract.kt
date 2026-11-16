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
 * Messenger contract between the main app (client) and [NappletHostService] (provider, in the keyless
 * `:napplet` process) for an **embedded** nsite/napplet — the in-app-tab counterpart of the full-screen
 * [NappletHostActivity]. The provider hosts the verified-blob WebView and ships its rendered surface
 * back through `androidx.privacysandbox.ui` (SurfaceControlViewHost), so applet JS never runs in the
 * key-holding main process. The session config (verified manifest paths/hashes/servers, identity,
 * launch token, …) is carried in the [MSG_CREATE_SESSION] bundle using the same keys as
 * [NappletHostContract] (the activity's intent extras), so the two host paths stay in lockstep.
 *
 * The trusted chrome (shield, title, "what it can access") is drawn by the main process around the
 * embedded surface — the sandbox can't draw chrome the user should trust.
 */
object NappletEmbedContract {
    /** FQN of the provider service, bound by name so the client needs no compile-time reference. */
    const val SERVICE_CLASS = "com.vitorpamplona.amethyst.napplethost.NappletHostService"

    /** Client → provider: create the session. Bundle uses [NappletHostContract] EXTRA_* keys. */
    const val MSG_CREATE_SESSION = 1

    /** Client → provider: go back in the applet's page history. */
    const val MSG_BACK = 2

    /** Client → provider: reload. */
    const val MSG_RELOAD = 3

    /**
     * Client → provider: pause the applet's JS + timers (the tab left the foreground). Mirrors
     * [NappletHostActivity]'s onPause gating, so a backgrounded napplet — even one with an "allow
     * always" capability — can't act on the user's behalf while they aren't looking at it.
     */
    const val MSG_PAUSE = 4

    /** Client → provider: resume the applet's JS + timers (the tab returned to the foreground). */
    const val MSG_RESUME = 5

    /** Provider → client: the session's [KEY_CORE_LIB_INFO] (the SandboxedUiAdapter handle). */
    const val MSG_SESSION_READY = 10

    /** Provider → client: navigation state changed; carries [KEY_CAN_GO_BACK]. */
    const val MSG_STATE = 11

    /**
     * Provider → client: a sensitive "allow always" capability just acted on the user's behalf
     * (carries [KEY_NOTICE] — one of [NOTICE_PUBLISHED] / [NOTICE_UPLOADED] / [NOTICE_PAID]). The main
     * process surfaces it so a granted relay-publish / upload / payment can never run fully silently.
     */
    const val MSG_NOTICE = 12

    /**
     * Provider → client: the applet reported an IME event (focused editable / blur / external change);
     * raw JSON in [KEY_IME_PAYLOAD]. The embedded surface can't host the keyboard, so the main app does.
     */
    const val MSG_IME_EVENT = 13

    /** Client → provider: an IME editing op for the focused field; raw JSON in [KEY_IME_PAYLOAD]. */
    const val MSG_IME_OP = 14

    /**
     * Provider → client: the main-frame load state changed. Carries [KEY_IS_LOADING] (a load is in
     * flight) and [KEY_LOAD_FAILED] (the main frame errored). Lets the main process draw a loading
     * spinner / error+retry overlay over the embedded surface instead of a bare black/white void.
     * [KEY_RENDERER_GONE] marks a failure caused by the WebView renderer dying: the tab has no WebView
     * left, so the client must [MSG_RELOAD] (which rebuilds it) — the page on screen is gone, not failed.
     */
    const val MSG_LOAD_STATE = 15

    /**
     * Client → provider: capture a magnified slice of the live page for the selection loupe. Host-side
     * `PixelCopy` of the sandbox surface returns `ERROR_SOURCE_NO_DATA` (the WebView pixels live in a child
     * SurfaceControl), so capture happens here, in the provider, where the WebView is a real in-window view.
     * Carries [KEY_MAG_X]/[KEY_MAG_Y] (surface px center), [KEY_MAG_BOX_W]/[KEY_MAG_BOX_H] (source rect, px),
     * [KEY_MAG_ZOOM], and [KEY_MAG_REQ_T] (echoed send stamp). Mirrors the browser path.
     */
    const val MSG_MAGNIFIER_REQUEST = 16

    /** Provider → client: the captured loupe frame — [KEY_MAG_BYTES] PNG, [KEY_MAG_W]/[KEY_MAG_H], [KEY_MAG_CAPTURE_MS], echoed [KEY_MAG_REQ_T]. */
    const val MSG_MAGNIFIER_FRAME = 17

    /**
     * Provider → client: the page tapped an HTML file input and needs the system picker. The keyless
     * `:napplet` process has no Activity of its own (this provider is windowless), and its streamed
     * surface can't host one, so the main process runs the picker and sends the chosen URIs back.
     *
     * Carries the request description as data — [KEY_FILE_CHOOSER_ID], [KEY_FILE_CHOOSER_ACCEPT] (the raw `accept` entries),
     * [KEY_FILE_CHOOSER_MULTIPLE] and [KEY_FILE_CHOOSER_TITLE] — never a ready-made Intent, so the sandbox can ask the trusted
     * process for a file picker and for nothing else.
     */
    const val MSG_FILE_CHOOSER_REQUEST = 18

    /**
     * Client → provider: the picker for [KEY_FILE_CHOOSER_ID] finished. [KEY_FILE_CHOOSER_URIS] holds the picked
     * `content://` URIs, or is absent when the user cancelled — the page's file input stays busy until
     * one of the two arrives, so this is sent on every outcome. URI read grants are per-UID, so the URIs
     * the main process was granted are readable by the WebView here without re-granting.
     */
    const val MSG_FILE_CHOOSER_RESULT = 19

    /** Client → provider: find [KEY_FIND_QUERY] in the page; an empty query clears the highlights. */
    const val MSG_FIND = 20

    /** Client → provider: move to the next ([KEY_FIND_FORWARD] true) or previous match. */
    const val MSG_FIND_NEXT = 21

    /** Provider → client: [KEY_FIND_ACTIVE] (0-based) of [KEY_FIND_TOTAL] matches. */
    const val MSG_FIND_RESULT = 22

    /** Client → provider: set the page's text size to [KEY_TEXT_ZOOM] percent. */
    const val MSG_SET_TEXT_ZOOM = 23

    /**
     * Provider → client: one line for the developer console — [KEY_CONSOLE_LEVEL] (WebView's
     * `ConsoleMessage.MessageLevel` name), [KEY_CONSOLE_MESSAGE], [KEY_CONSOLE_SOURCE], [KEY_CONSOLE_LINE].
     */
    const val MSG_CONSOLE_LOG = 24

    /**
     * Client → provider: the tab was torn down (evicted, or rebuilt for a theme/account change). Drops the
     * session and its WebView even if the surface never opened — a session created for a view that was
     * disposed before it attached would otherwise sit in the provider forever, pinning its client.
     */
    const val MSG_CLOSE_SESSION = 25

    /**
     * Client → provider: whether the user is looking at this napplet ([KEY_ATTENDED]) — it's the visible tab
     * AND the app is on screen. While not, the provider holds the napplet's requests that act for the user
     * (publish, pay, upload, notify, `inc.emit`) and sends them once the user is back. Separate from
     * [MSG_PAUSE]: the page itself is only paused a while after the app leaves the screen, but nothing may
     * act on the user's behalf the moment they stop watching.
     */
    const val MSG_SET_ATTENDED = 26

    /**
     * Provider → client: whether the process's pages currently go through Tor ([KEY_ROUTE_TOR]). Sent on every
     * change for an nSite (it has off-origin traffic of its own). Tor always wins process-wide, so an nSite
     * set to the open web can still be on Tor because another open page needs it — the client shows why.
     */
    const val MSG_ROUTE = 27

    /**
     * Provider → client: how far the download the tab waits on has come ([KEY_DOWNLOAD_BYTES] of
     * [KEY_DOWNLOAD_TOTAL], -1 when the server gave no size), so the client can draw a bar instead of a
     * spinner. A webxdc archive downloads before its page loads. No [KEY_DOWNLOAD_BYTES]: the download ended.
     */
    const val MSG_DOWNLOAD = 28

    const val KEY_FIND_QUERY = "findQuery"
    const val KEY_FIND_FORWARD = "findForward"
    const val KEY_FIND_ACTIVE = "findActive"
    const val KEY_FIND_TOTAL = "findTotal"
    const val KEY_TEXT_ZOOM = "textZoom"
    const val KEY_CONSOLE_LEVEL = "consoleLevel"
    const val KEY_CONSOLE_MESSAGE = "consoleMessage"
    const val KEY_CONSOLE_SOURCE = "consoleSource"
    const val KEY_CONSOLE_LINE = "consoleLine"

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

    const val KEY_CORE_LIB_INFO = "coreLibInfo"
    const val KEY_CAN_GO_BACK = "canGoBack"
    const val KEY_IS_LOADING = "isLoading"
    const val KEY_LOAD_FAILED = "loadFailed"
    const val KEY_RENDERER_GONE = "rendererGone"
    const val KEY_ROUTE_TOR = "routeTor"
    const val KEY_DOWNLOAD_BYTES = "downloadBytes"
    const val KEY_DOWNLOAD_TOTAL = "downloadTotal"

    /**
     * On [MSG_LOAD_STATE]: the applet was not loaded because its network route can't be honored (Tor wanted
     * but not running yet, this WebView can't proxy, or applying the proxy failed). Nothing went out; the
     * client offers Retry, whose [MSG_RELOAD] carries the current Tor port
     * ([NappletHostContract.EXTRA_PROXY_PORT]).
     */
    const val KEY_ROUTE_BLOCKED = "routeBlocked"
    const val KEY_NOTICE = "notice"
    const val KEY_IME_PAYLOAD = "imePayload"

    /**
     * Opaque per-tab session id the client stamps on [MSG_CREATE_SESSION] and every control message. A
     * single provider instance is shared by all embedded napplet/nSite tabs (they bind the same Intent),
     * so this scopes a control to the right surface and routes state/notices/IME back to the right tab.
     */
    const val KEY_SESSION_ID = "sessionId"
    const val KEY_ATTENDED = "attended"

    const val NOTICE_PUBLISHED = "published"
    const val NOTICE_UPLOADED = "uploaded"
    const val NOTICE_PAID = "paid"
}
