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
package com.vitorpamplona.amethyst.commons.model.nip01UserMetadata

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip39ExtIdentities.ExternalIdentitiesEvent
import com.vitorpamplona.quartz.nip68Picture.PictureMeta
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class UserMetadataState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val user = cache.getOrCreateUser(signer.pubKey)

    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val note = cache.getOrCreateAddressableNote(getMetadataEventAddress())

    fun getMetadataEventAddress() = MetadataEvent.createAddress(signer.pubKey)

    // fun getEphemeralChatListAddress() = cache.getOrCreateUser(signer.pubKey)

    fun getUserMetadataFlow() = note.flow().metadata.stateFlow

    fun getUserMetadataEvent(): MetadataEvent? = note.event as? MetadataEvent

    fun getIdentitiesEventAddress() = ExternalIdentitiesEvent.createAddress(signer.pubKey)

    val identitiesNote = cache.getOrCreateAddressableNote(getIdentitiesEventAddress())

    fun getExternalIdentitiesEvent(): ExternalIdentitiesEvent? = identitiesNote.event as? ExternalIdentitiesEvent

    suspend fun sendNewUserMetadata(
        name: String? = null,
        displayName: String? = null,
        picture: String? = null,
        banner: String? = null,
        website: String? = null,
        pronouns: String? = null,
        about: String? = null,
        nip05: String? = null,
        lnAddress: String? = null,
        lnURL: String? = null,
        clinkOffer: String? = null,
        pictureMeta: PictureMeta? = null,
        bannerMeta: PictureMeta? = null,
    ): MetadataEvent {
        val latest = getUserMetadataEvent()

        val template =
            if (latest != null) {
                MetadataEvent.updateFromPast(
                    latest = latest,
                    name = name,
                    displayName = displayName,
                    picture = picture,
                    banner = banner,
                    website = website,
                    pronouns = pronouns,
                    about = about,
                    nip05 = nip05,
                    lnAddress = lnAddress,
                    lnURL = lnURL,
                    clinkOffer = clinkOffer,
                    pictureMeta = pictureMeta,
                    bannerMeta = bannerMeta,
                )
            } else {
                MetadataEvent.createNew(
                    name = name,
                    displayName = displayName,
                    picture = picture,
                    banner = banner,
                    website = website,
                    pronouns = pronouns,
                    about = about,
                    nip05 = nip05,
                    lnAddress = lnAddress,
                    lnURL = lnURL,
                    clinkOffer = clinkOffer,
                    pictureMeta = pictureMeta,
                    bannerMeta = bannerMeta,
                )
            }

        return signer.sign(template)
    }

    suspend fun sendNewUserIdentities(
        twitter: String? = null,
        mastodon: String? = null,
        github: String? = null,
    ): ExternalIdentitiesEvent {
        val latest = getExternalIdentitiesEvent()

        val template =
            if (latest != null) {
                ExternalIdentitiesEvent.updateFromPast(
                    latest = latest,
                    twitter = twitter,
                    mastodon = mastodon,
                    github = github,
                )
            } else {
                ExternalIdentitiesEvent.createNew(
                    twitter = twitter,
                    mastodon = mastodon,
                    github = github,
                )
            }

        return signer.sign(template)
    }

    init {
        settings.backupUserMetadata?.let {
            Log.d("AccountRegisterObservers") { "Loading saved user metadata ${it.toJson()}" }

            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) { LocalCache.justConsumeMyOwnEvent(it) }
        }

        // saves contact list for the next time.
        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Kind 0 Collector Start")
            getUserMetadataFlow().collect {
                Log.d("AccountRegisterObservers") { "Updating Kind 0 ${user.toBestDisplayName()}" }
                (it.note.event as? MetadataEvent)?.let {
                    settings.updateUserMetadata(it)
                }
            }
        }
    }
}
