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
package com.vitorpamplona.amethyst.commons.profile

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip39ExtIdentities.ExternalIdentitiesEvent
import com.vitorpamplona.quartz.nip39ExtIdentities.GitHubIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.MastodonIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.TwitterIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.identityClaims
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Platform-agnostic state holder for profile editing form fields.
 * Uses [MutableStateFlow] matching the codebase convention (e.g. ChatNewMessageState).
 *
 * This class owns ONLY the editable field values and dirty tracking.
 * Signing, broadcasting, and image upload are handled by the platform-specific
 * wiring composable that consumes this state.
 */
@Stable
class EditProfileFields {
    // Core profile
    val name = MutableStateFlow("")
    val displayName = MutableStateFlow("")
    val about = MutableStateFlow("")

    // Media
    val picture = MutableStateFlow("")
    val banner = MutableStateFlow("")

    // Verification & payment
    val website = MutableStateFlow("")
    val pronouns = MutableStateFlow("")
    val nip05 = MutableStateFlow("")
    val lnAddress = MutableStateFlow("")
    val lnURL = MutableStateFlow("")
    val clinkOffer = MutableStateFlow("")

    // Social proofs (NIP-39)
    val twitter = MutableStateFlow("")
    val github = MutableStateFlow("")
    val mastodon = MutableStateFlow("")

    private var snapshot = emptyMap<String, String>()

    val isDirty: Boolean get() = currentValues() != snapshot

    fun loadFrom(
        metadata: MetadataEvent?,
        identities: ExternalIdentitiesEvent?,
    ) {
        metadata?.contactMetaData()?.let { info ->
            name.value = info.name ?: ""
            displayName.value = info.displayName ?: ""
            about.value = info.about ?: ""
            picture.value = info.picture ?: ""
            banner.value = info.banner ?: ""
            website.value = info.website ?: ""
            pronouns.value = info.pronouns ?: ""
            nip05.value = info.nip05 ?: ""
            lnAddress.value = info.lud16 ?: ""
            lnURL.value = info.lud06 ?: ""
            clinkOffer.value = info.clinkOffer ?: ""
        }

        twitter.value = ""
        github.value = ""
        mastodon.value = ""

        // Load identities from kind 10011, fall back to kind 0
        val claims =
            identities?.identityClaims()
                ?: metadata?.identityClaims()
                ?: emptyList()

        claims.forEach { claim ->
            when (claim) {
                is TwitterIdentity -> twitter.value = claim.toProofUrl()
                is GitHubIdentity -> github.value = claim.toProofUrl()
                is MastodonIdentity -> mastodon.value = claim.toProofUrl()
                else -> {} // skip unsupported
            }
        }

        snapshot = currentValues()
    }

    fun clear() {
        name.value = ""
        displayName.value = ""
        about.value = ""
        picture.value = ""
        banner.value = ""
        website.value = ""
        pronouns.value = ""
        nip05.value = ""
        lnAddress.value = ""
        lnURL.value = ""
        clinkOffer.value = ""
        twitter.value = ""
        github.value = ""
        mastodon.value = ""
        snapshot = emptyMap()
    }

    private fun currentValues(): Map<String, String> =
        mapOf(
            "name" to name.value,
            "displayName" to displayName.value,
            "about" to about.value,
            "picture" to picture.value,
            "banner" to banner.value,
            "website" to website.value,
            "pronouns" to pronouns.value,
            "nip05" to nip05.value,
            "lnAddress" to lnAddress.value,
            "lnURL" to lnURL.value,
            "clinkOffer" to clinkOffer.value,
            "twitter" to twitter.value,
            "github" to github.value,
            "mastodon" to mastodon.value,
        )
}
