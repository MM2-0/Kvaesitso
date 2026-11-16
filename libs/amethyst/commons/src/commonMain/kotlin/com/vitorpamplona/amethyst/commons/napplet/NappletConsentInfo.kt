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
package com.vitorpamplona.amethyst.commons.napplet

/** Everything the consent dialog needs to describe what an applet is asking permission to do. */
data class NappletConsentInfo(
    val appletTitle: String,
    val coordinate: String,
    val capabilityLabel: String,
    val operationSummary: String,
    /** Whether a persistent "Always allow" choice may be offered (false for per-use caps like payments). */
    val allowAlways: Boolean,
    val iconUrl: String? = null,
    /**
     * The full unsigned event the applet asked us to sign, pretty-printed, shown behind a
     * "Show Event" toggle. Blank for requests that sign nothing. [operationSummary] is a lossy
     * rendering — it truncates content and cannot spell out every tag — so this is the only place
     * the user can see exactly what a signature would cover.
     */
    val rawData: String = "",
    /**
     * The one account a follow/mute change is about, when the change names exactly one. Rendered as
     * an avatar + name so the user can recognize *who* at a glance instead of reading a bare count.
     * Null for multi-account edits and every other request.
     */
    val subject: ConsentSubject? = null,
)

/** A single account a consent dialog is about: enough to draw an avatar and a name. */
data class ConsentSubject(
    val pubKey: String,
    val name: String,
    val pictureUrl: String?,
)
