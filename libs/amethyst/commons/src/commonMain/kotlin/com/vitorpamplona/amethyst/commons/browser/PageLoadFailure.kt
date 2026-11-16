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
package com.vitorpamplona.amethyst.commons.browser

/**
 * Why a page's main frame failed to load, grouped by what the person can do about it. The WebView's own
 * error page only prints the network error code, and over Tor that code is a SOCKS failure whatever the
 * real cause was, so "net::ERR_SOCKS_CONNECTION_FAILED" is all someone sees for a misspelled address.
 */
enum class PageLoadFailure {
    /** The name does not exist (DNS said so). */
    NOT_FOUND,

    /** The site did not answer: refused, reset, or (through a proxy) unreachable for a reason we can't see. */
    UNREACHABLE,

    /** The local proxy itself refused the connection: Tor is not running or not ready yet. */
    PROXY_DOWN,

    TIMED_OUT,
    OFFLINE,

    /** Certificate or TLS failure. */
    INSECURE,
    REDIRECT_LOOP,

    /** A `.onion` address that went out without Tor; it can only resolve over Tor. */
    ONION_NEEDS_TOR,

    /** Plain http to a host the platform only allows over https. */
    CLEARTEXT_BLOCKED,
    OTHER,
    ;

    companion object {
        // WebViewClient.ERROR_* values, so this stays free of android.webkit.
        private const val ERROR_HOST_LOOKUP = -2
        private const val ERROR_PROXY_AUTHENTICATION = -5
        private const val ERROR_CONNECT = -6
        private const val ERROR_TIMEOUT = -8
        private const val ERROR_REDIRECT_LOOP = -9
        private const val ERROR_FAILED_SSL_HANDSHAKE = -11

        /**
         * Classifies a main-frame error from its Chromium [description] ("net::ERR_NAME_NOT_RESOLVED"),
         * falling back to the WebView [errorCode]. Returns null for a navigation that was cancelled rather
         * than failed (ERR_ABORTED: a stop, a download, a redirect to another app), which needs no error page.
         */
        fun classify(
            errorCode: Int,
            description: String?,
        ): PageLoadFailure? {
            val code =
                description
                    ?.trim()
                    ?.removePrefix("net::")
                    ?.uppercase()
                    .orEmpty()
            return when {
                code == "ERR_ABORTED" -> null
                code == "ERR_NAME_NOT_RESOLVED" || code == "ERR_NAME_RESOLUTION_FAILED" -> NOT_FOUND
                code == "ERR_PROXY_CONNECTION_FAILED" -> PROXY_DOWN
                code.startsWith("ERR_SOCKS_") || code == "ERR_TUNNEL_CONNECTION_FAILED" -> UNREACHABLE
                code == "ERR_INTERNET_DISCONNECTED" || code == "ERR_NETWORK_CHANGED" -> OFFLINE
                code == "ERR_TIMED_OUT" || code == "ERR_CONNECTION_TIMED_OUT" -> TIMED_OUT
                code == "ERR_TOO_MANY_REDIRECTS" -> REDIRECT_LOOP
                code == "ERR_CLEARTEXT_NOT_PERMITTED" -> CLEARTEXT_BLOCKED
                code.startsWith("ERR_CERT_") || code.startsWith("ERR_SSL_") || code == "ERR_BAD_SSL_CLIENT_AUTH_CERT" -> INSECURE
                code.startsWith("ERR_CONNECTION_") || code == "ERR_EMPTY_RESPONSE" || code == "ERR_ADDRESS_UNREACHABLE" -> UNREACHABLE
                else -> fromErrorCode(errorCode) ?: OTHER
            }
        }

        /**
         * [classify] for a page at [url]: an onion address that failed while the page was not on Tor is
         * reported as [ONION_NEEDS_TOR] (its "name not resolved" would otherwise read as "doesn't exist").
         */
        fun forPage(
            errorCode: Int,
            description: String?,
            url: String,
            viaTor: Boolean,
        ): PageLoadFailure? {
            val failure = classify(errorCode, description) ?: return null
            return if (!viaTor && OmniboxInput.isOnion(url)) ONION_NEEDS_TOR else failure
        }

        /** Whether retrying the same page on the open web might help: Tor reached nothing, or too slowly. */
        fun mayBeTorBlocked(failure: PageLoadFailure): Boolean = failure == UNREACHABLE || failure == TIMED_OUT

        private fun fromErrorCode(errorCode: Int): PageLoadFailure? =
            when (errorCode) {
                ERROR_HOST_LOOKUP -> NOT_FOUND
                ERROR_PROXY_AUTHENTICATION -> PROXY_DOWN
                ERROR_CONNECT -> UNREACHABLE
                ERROR_TIMEOUT -> TIMED_OUT
                ERROR_REDIRECT_LOOP -> REDIRECT_LOOP
                ERROR_FAILED_SSL_HANDSHAKE -> INSECURE
                else -> null
            }
    }
}
