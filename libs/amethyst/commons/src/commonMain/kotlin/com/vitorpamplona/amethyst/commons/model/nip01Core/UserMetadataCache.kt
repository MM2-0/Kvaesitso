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
package com.vitorpamplona.amethyst.commons.model.nip01Core

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.ProfileImageMetas
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip39ExtIdentities.IdentityClaimTag
import com.vitorpamplona.quartz.nip39ExtIdentities.identityClaims
import com.vitorpamplona.quartz.utils.DualCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@Stable
class UserInfo(
    val info: UserMetadata,
    val tags: ImmutableListOfLists<String>,
    val identities: List<IdentityClaimTag>,
    val createdAt: Long,
    val pubKey: HexKey? = null,
) {
    /**
     * The owner this profile declares through a valid NIP-OA `auth` tag, making the user an agent
     * of that owner (who may then edit the agent's Buzz messages), or null. Verified once per
     * profile version.
     */
    val nipOaOwner: HexKey? by lazy {
        pubKey?.let { OwnerAttestation.verifiedOwnerOf(it, MetadataEvent.KIND, createdAt, tags.lists) }
    }

    /** NIP-92 `imeta` descriptions (fallbacks, blurhash, dim, ...) of the [info] picture and banner. */
    val imageMetas: ProfileImageMetas by lazy {
        ProfileImageMetas.parse(tags.lists, info.picture, info.banner)
    }

    /** Alternative URLs to try, in order, when the profile picture fails to load. */
    fun pictureFallbacks(): List<String> = imageMetas.picture?.fallback ?: emptyList()

    /** Alternative URLs to try, in order, when the banner fails to load. */
    fun bannerFallbacks(): List<String> = imageMetas.banner?.fallback ?: emptyList()
}

@Stable
class UserMetadataCache {
    val flow: MutableStateFlow<UserInfo?> = MutableStateFlow(null)

    fun newMetadata(
        userInfo: UserMetadata,
        metaEvent: MetadataEvent,
    ) {
        flow.update {
            UserInfo(
                info = userInfo,
                tags = metaEvent.tags.toImmutableListOfLists(),
                identities = metaEvent.identityClaims(),
                createdAt = metaEvent.createdAt,
                pubKey = metaEvent.pubKey,
            )
        }
    }

    fun shouldUpdateWith(event: MetadataEvent) = event.createdAt > (flow.value?.createdAt ?: 0)

    fun anyNameOrAddressContains(terms: List<DualCase>): Boolean = flow.value?.info?.anyNameOrAddressContains(terms) ?: false

    fun anyNameStartsWith(terms: List<DualCase>): Boolean = flow.value?.info?.anyNameStartsWith(terms) ?: false

    fun anyAddressStartsWith(terms: List<DualCase>): Boolean = flow.value?.info?.anyAddressStartsWith(terms) ?: false

    fun anyPropertyContains(hiddenWordsCase: List<DualCase>): Boolean {
        if (hiddenWordsCase.isEmpty()) return false

        return flow.value?.info?.anyPropertyContains(hiddenWordsCase) ?: false
    }

    fun bestName(): String? = flow.value?.info?.bestName()

    fun nip05(): String? = flow.value?.info?.nip05

    fun profilePicture(): String? = flow.value?.info?.picture

    fun lnAddress(): String? = flow.value?.info?.lnAddress()
}
