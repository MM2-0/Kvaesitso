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

import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability

/**
 * Android-resource twin of [labelRes], for [NappletLauncher].
 *
 * Launch parameters are minted from a plain onClick, which can call neither the
 * composable nor the suspend accessor, and the sandbox process has no resources
 * of its own - so the labels have to be resolved here and passed across.
 */
fun NappletCapability.labelResId(): Int =
    when (this) {
        NappletCapability.IDENTITY -> R.string.napplet_cap_identity
        NappletCapability.KEYS -> R.string.napplet_cap_keys
        NappletCapability.RELAY -> R.string.napplet_cap_relay
        NappletCapability.SIGNER -> R.string.napplet_cap_signer
        NappletCapability.STORAGE -> R.string.napplet_cap_storage
        NappletCapability.VALUE -> R.string.napplet_cap_value
        NappletCapability.RESOURCE -> R.string.napplet_cap_resource
        NappletCapability.UPLOAD -> R.string.napplet_cap_upload
        NappletCapability.THEME -> R.string.napplet_cap_theme
        NappletCapability.NOTIFY -> R.string.napplet_cap_notify
        NappletCapability.INC -> R.string.napplet_cap_inc
        NappletCapability.OUTBOX -> R.string.napplet_cap_outbox
        NappletCapability.FS -> R.string.napplet_cap_fs
        NappletCapability.COMMON -> R.string.napplet_cap_common
        NappletCapability.INTENT -> R.string.napplet_cap_intent
    }
