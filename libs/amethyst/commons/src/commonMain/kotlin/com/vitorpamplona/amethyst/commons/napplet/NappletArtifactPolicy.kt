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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest

/**
 * Pure NIP-5D artifact checks at the launch boundary, shared by every launch surface. The launcher's
 * trusted entry point takes plain paths and a hash, not a manifest, so it re-checks here what quartz's
 * NappletManifest.artifactFile() already guarantees: one `/index.html`, named by the signed `x`.
 */
object NappletArtifactPolicy {
    /** Returns the runtime-computed artifact identity, or null when the manifest must not execute. */
    fun verifiedArtifactHash(
        paths: List<PathTag>,
        declaredArtifactHash: HexKey?,
    ): HexKey? {
        val entry = paths.singleOrNull() ?: return null
        if (entry.path != NappletManifest.INDEX_PATH || !SHA256.matches(entry.hash)) return null
        if (declaredArtifactHash != entry.hash) return null
        return entry.hash
    }

    private val SHA256 = Regex("^[0-9a-f]{64}$")
}
