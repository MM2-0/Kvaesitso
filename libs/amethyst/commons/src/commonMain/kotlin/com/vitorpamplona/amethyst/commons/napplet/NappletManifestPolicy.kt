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

import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest

/*
 * Amethyst's policy over a NIP-5D manifest. The manifest itself (its artifact, description and
 * R/O declarations) is quartz's NappletManifest; what this shell does with those declarations is
 * decided here, against the NAP domains Amethyst implements ([NappletCapability.supportedNapDomains]).
 */

/**
 * The NAP domains to expose to this napplet: its declared required and optional ones, limited to
 * what Amethyst implements. A declaration never widens that set; the broker still asks for consent.
 */
fun NappletManifest.exposedDomains(): List<String> = (required() + optional()).distinct().filter { it in NappletCapability.supportedNapDomains }

/**
 * Required domains Amethyst does not implement. NIP-5D says a shell SHOULD reject such a napplet,
 * so it is not launched while this is non-empty. Missing optional domains never count.
 */
fun NappletManifest.unsupportedRequired(): List<String> = missingRequired(NappletCapability.supportedNapDomains)

/** Whether this manifest may be launched here: valid NIP-5D, and every required domain implemented. */
fun NappletManifest.isLaunchable(): Boolean = isValid() && unsupportedRequired().isEmpty()
