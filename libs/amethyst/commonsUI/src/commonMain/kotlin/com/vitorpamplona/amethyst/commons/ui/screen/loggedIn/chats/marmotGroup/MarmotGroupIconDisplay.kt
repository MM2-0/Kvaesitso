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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.commons.service.BlossomServerFinder
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.marmot.appComponents.GroupAvatarUrlV1
import com.vitorpamplona.marmot.appComponents.MarmotWebUrl
import com.vitorpamplona.marmot.client.MarmotGroupImage
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUri

/**
 * Resolve the URL from which a Marmot group's encrypted avatar can be loaded, and
 * register its decryption cipher in the encrypted-blob HTTP cache so any Coil load
 * of that URL transparently yields the decrypted image (via `EncryptedBlobInterceptor`).
 *
 * The blob is content-addressed on Blossom by [MarmotGroupImage.hash]; the canonical
 * scheme stores only the hash, and the icon usually lives on the *uploader's* Blossom
 * server rather than the viewer's. So we resolve it through the app's BUD-10 resolver ([BlossomServerFinder]),
 * which probes the viewer's default server (passed as a first-try `xs` hint) and the
 * group admins' configured servers (via their pubkeys as `as` authors, BUD-03). While
 * that async probe runs, we optimistically load from the viewer's default server so the
 * common case (shared server) shows instantly; if nothing resolves, callers fall back to
 * the relay icon.
 *
 * Returns null when there is no image to show.
 */
@Composable
fun rememberMarmotGroupIconUrl(
    image: MarmotGroupImage?,
    accountViewModel: AccountViewModel,
    adminPubkeys: List<HexKey> = emptyList(),
): String? {
    if (image == null) return null

    val serverBaseUrl = accountViewModel.account.settings.defaultFileServer.baseUrl
    val fallbackUrl = remember(image.hash, serverBaseUrl) { BlossomServerUrl.blob(serverBaseUrl, image.hash) }

    // A blossom: URI carrying the default server as a first-try hint and the admins as
    // authors. Extension "bin" keeps the resolver's HEAD check type-agnostic, matching the
    // application/octet-stream encrypted blob.
    val blossomUri =
        remember(image.hash, serverBaseUrl, adminPubkeys) {
            BlossomUri(
                sha256 = image.hash,
                extension = "bin",
                servers = listOf(serverBaseUrl),
                authors = adminPubkeys,
                size = null,
            ).toUriString()
        }

    val finder = LocalAppServices.current.blossomServerFinder
    val url by produceState(finder.cachedServerUrl(blossomUri) ?: fallbackUrl, blossomUri) {
        value = finder.findServerUrl(blossomUri) ?: fallbackUrl
    }

    // The scheme follows the group's avatar carrier: `0x8002` (raw key, media type in the
    // AAD) for a current-profile group, MIP-01 v2 for a legacy one.
    val cipher = remember(image) { image.cipher() }
    // Register the cipher only when the URL or cipher changes (not on every recomposition),
    // and synchronously during composition so the interceptor can decrypt before Coil fetches.
    // MIP-01 stores no plaintext type, so Coil sniffs the format from the bytes there.
    remember(url, cipher) {
        accountViewModel.account.encryptionKeyCache.add(url, cipher, image.mediaType)
        url
    }

    return url
}

/**
 * The avatar URL for a group that may carry either avatar carrier, applying the
 * components' precedence: `marmot.group.avatar-url.v1` wins over
 * `marmot.group.blossom.image.v1`, and clearing the URL one falls back to the
 * Blossom blob.
 *
 * The URL avatar is a plain link with no key material, so there is no cipher to
 * register — it just goes to Coil. It does get a contact check first: a URL can
 * be valid group state and still be somewhere we refuse to fetch from, and the
 * spec puts that decision squarely on the client. An unsafe destination renders
 * as no URL avatar rather than as an error, which lets the Blossom image (or the
 * relay icon) take over.
 *
 * Returns null when the group has neither carrier.
 */
@Composable
fun rememberMarmotGroupAvatarUrl(
    avatarUrl: GroupAvatarUrlV1?,
    image: MarmotGroupImage?,
    accountViewModel: AccountViewModel,
    adminPubkeys: List<HexKey> = emptyList(),
): String? {
    val link =
        remember(avatarUrl) {
            avatarUrl?.url?.takeIf { it.isNotEmpty() && MarmotWebUrl.isSafeToContact(it) }
        }
    // Branch rather than resolving both: the Blossom path registers a
    // decryption cipher and probes servers as a side effect, and neither is
    // worth doing for an avatar the renderer is not going to show.
    return if (link != null) link else rememberMarmotGroupIconUrl(image, accountViewModel, adminPubkeys)
}

/**
 * The NIP-11 icon of the group's first resolvable relay, used as a fallback avatar
 * when the group has no image of its own. Fetches the relay's NIP-11 document on a
 * cache miss. Returns null when the group has no valid relay or the relay advertises
 * no icon.
 */
@Composable
fun loadMarmotRelayIcon(relays: List<String>): String? {
    val relay = remember(relays) { relays.firstNotNullOfOrNull { RelayUrlNormalizer.normalizeOrNull(it) } } ?: return null
    val relayInfo by loadRelayInfo(relay)
    return relayInfo.icon?.ifBlank { null }
}
