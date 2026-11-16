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
 * The Messenger wire contract between the untrusted `:napplet` process (the WebView host) and
 * the main-process [NappletBrokerService]. Kept tiny and string-only on purpose: nothing the
 * applet controls is ever interpreted as a Binder object, and the only payloads are JSON
 * strings (`NappletProtocolJson`, in commons) plus the applet's identity coordinate.
 */
object NappletIpc {
    /** Host → broker: a capability request. Carries [KEY_REQUEST_ID], the identity keys, and [KEY_PAYLOAD]. */
    const val MSG_REQUEST = 1

    /** Broker → host: the matching reply. Carries [KEY_REQUEST_ID] and [KEY_PAYLOAD]. */
    const val MSG_RESPONSE = 2

    /**
     * Broker → host: an **unsolicited push** (a live `relay.event`/`relay.eose` for a subscription).
     * Carries only [KEY_PAYLOAD] — a full envelope keyed by the applet's `subId`, not a request id —
     * which the host forwards to the applet verbatim.
     */
    const val MSG_PUSH = 3

    /**
     * Host → broker: persist this nSite's network-routing choice (Tor vs open web). Carries
     * [KEY_LAUNCH_TOKEN] (so the broker resolves the trusted coordinate) and [KEY_NETWORK_USE_TOR].
     * The sandbox never persists anything itself; the main process owns the preference.
     */
    const val MSG_SET_NETWORK_MODE = 4

    /**
     * Host → broker (browser mode): mint a per-origin launch token for the visited origin in
     * [KEY_BROWSER_ORIGIN]. The broker registers a synthetic per-origin identity (so NIP-07 consent is
     * scoped to that one site) and answers with [MSG_BROWSER_TOKEN].
     */
    const val MSG_MINT_BROWSER_TOKEN = 5

    /** Broker → host: the [KEY_LAUNCH_TOKEN] minted for [KEY_BROWSER_ORIGIN]. */
    const val MSG_BROWSER_TOKEN = 6

    /** How long a host waits for [MSG_BROWSER_TOKEN] before failing the origin's queued calls. */
    const val MINT_TIMEOUT_MS = 20_000L

    /**
     * Host → broker: this sandbox surface entered ([KEY_FOREGROUND] true) or left ([KEY_FOREGROUND]
     * false) the foreground. The `:napplet` host runs in its own process and so can't touch the main
     * process's resource lifecycle directly; this lets the broker hold the main process resumed (Tor,
     * relays, AUTH) while a napplet/nSite is foreground, since launching it backgrounded `MainActivity`.
     * Keyed by [KEY_LAUNCH_TOKEN] so the broker tracks each surface independently.
     */
    const val MSG_SET_FOREGROUND = 7

    /**
     * Host → broker: persist the per-host Tor choice for the direct-WebView browser
     * ([NappletBrowserActivity]). Carries [KEY_WEB_HOST] and [KEY_NETWORK_USE_TOR]. The `:napplet`
     * process can't touch the main process's preference store, so it relays the choice here.
     */
    const val MSG_SET_WEB_TOR = 8

    /**
     * Host → broker (browser mode): record a *successfully loaded* page in the device-local visit history
     * (main process only). Carries [KEY_HISTORY_URL] (the landed URL), [KEY_HISTORY_TITLE] and, when the page
     * replaced the previously recorded one in place, [KEY_HISTORY_REPLACES]. Sent only
     * after a clean main-frame page-finish — never for a typed-but-failed address — so misspellings never
     * enter history. The `:napplet` process can't touch the main process's store, so it relays it here.
     */
    const val MSG_RECORD_HISTORY = 9

    /**
     * Host → broker (browser mode): store the favicon for a visited site. Carries [KEY_ICON_HOST] and
     * [KEY_ICON_BYTES] (a small PNG, scaled down by the host before sending). Captured from the WebView
     * that loaded the page, so it rides the page's own (Tor-routed) network path; the main process never
     * fetches it itself. Bytes stay well under the Binder transaction limit.
     */
    const val MSG_RECORD_ICON = 10

    /**
     * Host → broker (browser mode): add or remove a URL in the main-process favorites registry. Carries
     * [KEY_FAVORITE_URL], [KEY_FAVORITE_LABEL] and [KEY_FAVORITE_IS_FAVORITE] (the state the user asked
     * for). The explicit target matters: a blind flip against a stale chrome would remove a pin the user
     * meant to add. Without [KEY_FAVORITE_IS_FAVORITE] the broker falls back to flipping the current
     * state. When `replyTo` is set, the broker answers with [MSG_WEB_FAVORITE_STATE].
     */
    const val MSG_TOGGLE_WEB_FAVORITE = 11

    /**
     * Host → broker: open this surface's editable permission screen (the main-process "Connected Apps"
     * detail) so the user can change its trust level / grants while it's running. A sandbox napplet/nsite
     * carries [KEY_LAUNCH_TOKEN] (the broker resolves it to the trusted coordinate, since the sandbox
     * can't state its own); a browser carries [KEY_BROWSER_ORIGIN] (keyed as `browser:<origin>`). The
     * broker launches the main activity at that coordinate. Fire-and-forget; no reply needed.
     */
    const val MSG_OPEN_PERMISSIONS = 12

    /**
     * Host → broker: this surface is being destroyed — drop every reference the broker holds to its
     * `replyTo` [android.os.Messenger] (inc-bus topic subscriptions, live relay subscriptions, and its
     * foreground lease when [KEY_LAUNCH_TOKEN] is supplied). [KEY_RELEASED_TOKENS], when present, lists
     * launch tokens the surface minted and will never use again (a closed browser tab's per-origin
     * tokens); the broker unregisters them so dead sessions stop crowding live ones out of the registry.
     *
     * A `Messenger` handed to the broker is a **binder**, so the main process holding it keeps a JNI
     * global reference alive in `:napplet`. Because the sandbox's reply handler is a bound method
     * reference on the Activity, that one retained Messenger pins the whole Activity → window → WebView,
     * and no GC in the sandbox can ever reclaim it (only process death can). Sending this on destroy is
     * the release half of the fix; the reply handler holding the Activity weakly is the other half, so a
     * missed or dropped release can never pin a surface again. Fire-and-forget; no reply needed.
     */
    const val MSG_RELEASE_CLIENT = 13

    /**
     * Host → broker (browser mode): is [KEY_FAVORITE_URL] a favorite? Sent whenever the displayed page
     * changes, so the star reflects the registry instead of guessing. The broker answers `replyTo` with
     * [MSG_WEB_FAVORITE_STATE]; it keeps no reference to the Messenger.
     */
    const val MSG_QUERY_WEB_FAVORITE = 14

    /**
     * Broker → host: the favorite state of [KEY_FAVORITE_URL] ([KEY_FAVORITE_IS_FAVORITE]). The reply to
     * [MSG_QUERY_WEB_FAVORITE], and to a [MSG_TOGGLE_WEB_FAVORITE] that carried a `replyTo`.
     */
    const val MSG_WEB_FAVORITE_STATE = 15

    /**
     * Host → broker (browser mode): the remembered camera / microphone / location answers for
     * [KEY_BROWSER_ORIGIN]. Carries [KEY_REQUEST_ID] (a long) for correlation; the broker answers `replyTo`
     * with [MSG_SITE_PERMISSIONS], one `perm.<key>` string per permission holding its
     * `BrowserSitePermission.Decision` name.
     */
    const val MSG_QUERY_SITE_PERMISSIONS = 16

    /** Broker → host: the reply to [MSG_QUERY_SITE_PERMISSIONS]. */
    const val MSG_SITE_PERMISSIONS = 17

    /**
     * Host → broker (browser mode): remember the user's answer ([KEY_SITE_DECISION]) for one permission
     * ([KEY_SITE_PERMISSION], a `BrowserSitePermission.key`) on [KEY_BROWSER_ORIGIN]. Fire-and-forget.
     */
    const val MSG_SET_SITE_PERMISSION = 18

    /**
     * Host → broker (browser mode): pin a launcher shortcut to [KEY_FAVORITE_URL] labelled
     * [KEY_FAVORITE_LABEL]. The shortcut opens the page full screen in Amethyst's browser. Fire-and-forget.
     */
    const val MSG_ADD_TO_HOME_SCREEN = 19

    /**
     * Broker → host: a request carried [KEY_LAUNCH_TOKEN] the registry no longer knows (it was evicted, or
     * released). The request itself was answered with a failure; a browser host drops that token so the
     * origin's next call mints a fresh one instead of failing forever.
     */
    const val MSG_TOKEN_UNKNOWN = 20

    /**
     * Host → broker: whether the user is looking at the surface behind [android.os.Message.replyTo]
     * ([KEY_ATTENDED]). The broker decrypts relay reads for a page — events pushed to its subscriptions, and
     * `relay.query` results — only while it is; until then encrypted events wait. A surface is unattended
     * until it says otherwise, and after each [MSG_RELEASE_CLIENT].
     */
    const val MSG_SET_ATTENDED = 21

    /** Boolean for [MSG_SET_ATTENDED]. */
    const val KEY_ATTENDED = "attended"

    const val KEY_REQUEST_ID = "requestId"
    const val KEY_PAYLOAD = "payload"

    /** The landed URL of a successfully loaded browser page, for the visit-history record. */
    const val KEY_HISTORY_URL = "historyUrl"

    /** The page title of a successfully loaded browser page, for the visit-history record. */
    const val KEY_HISTORY_TITLE = "historyTitle"

    /** The previously recorded URL a page replaced in place (a redirect or a rewritten query), if any. */
    const val KEY_HISTORY_REPLACES = "historyReplaces"

    /** The host a captured favicon belongs to. */
    const val KEY_ICON_HOST = "iconHost"

    /** The captured favicon as PNG bytes. */
    const val KEY_ICON_BYTES = "iconBytes"

    /** The bare host (e.g. `example.com`) a browser Tor choice belongs to. */
    const val KEY_WEB_HOST = "webHost"

    /** The full URL (e.g. `https://example.com`) to toggle as a web favorite. */
    const val KEY_FAVORITE_URL = "favoriteUrl"

    /** A human-readable label for the favorited URL (typically the host). */
    const val KEY_FAVORITE_LABEL = "favoriteLabel"

    /** Boolean: whether [KEY_FAVORITE_URL] is (or should become) a favorite. */
    const val KEY_FAVORITE_IS_FAVORITE = "favoriteIsFavorite"

    /** A `BrowserSitePermission.key` (`camera`, `microphone`, `location`). */
    const val KEY_SITE_PERMISSION = "sitePermission"

    /** A `BrowserSitePermission.Decision` name (`ASK`, `ALLOW`, `BLOCK`). */
    const val KEY_SITE_DECISION = "siteDecision"

    /** Prefix of the per-permission decision entries in a [MSG_SITE_PERMISSIONS] reply. */
    const val KEY_SITE_PERMISSION_PREFIX = "perm."

    /** Boolean: this sandbox surface is now foreground (true) or backgrounded (false). */
    const val KEY_FOREGROUND = "foreground"

    /** The visited web origin (e.g. `https://example.com`) a browser-mode request belongs to. */
    const val KEY_BROWSER_ORIGIN = "browserOrigin"

    /** [MSG_MINT_BROWSER_TOKEN]: the opaque storage profile the asking surface runs in (its account's jar). */
    const val KEY_WEBVIEW_PROFILE = "webViewProfile"

    /** Boolean: route this site through Tor (true) or over the open web (false). */
    const val KEY_NETWORK_USE_TOR = "networkUseTor"

    /**
     * Opaque per-launch token. The sandbox cannot be trusted to state its own identity, so it sends
     * only this token; the broker resolves it through [NappletLaunchRegistry] (main process) back to
     * the trusted identity + declared capability set the launch was registered with.
     */
    const val KEY_LAUNCH_TOKEN = "launchToken"

    /** Launch tokens a [MSG_RELEASE_CLIENT] gives back (a string array). */
    const val KEY_RELEASED_TOKENS = "releasedTokens"
}
