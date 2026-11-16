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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_sign_events
import com.vitorpamplona.amethyst.commons.resources.read_only_user
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadToDefaultServer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_CHANNEL_TYPE_FORUM
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_CHANNEL_TYPE_STREAM
import com.vitorpamplona.quartz.buzz.workspace.canonicalBuzzChannelName
import com.vitorpamplona.quartz.buzz.workspace.isValidBuzzChannelName
import com.vitorpamplona.quartz.buzz.workspace.newBuzzChannelId
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

/**
 * Backs the create/edit NIP-29 group metadata screens. Holds the full editable metadata
 * (name, about, picture, banner, and the four status flags), lets the user pick a picture from the
 * gallery, uploads it to their configured media server on submit, then publishes the kind
 * 9007+9002 (create) or 9002 (edit) events. Mirrors [EmojiPackMetadataViewModel].
 */
@Stable
class RelayGroupMetadataViewModel : ViewModel() {
    private lateinit var account: Account

    /** Non-null in edit mode; null while creating a new group. */
    private var channel: RelayGroupChannel? = null
    val isNewGroup by derivedStateOf { channel == null }

    /**
     * Host relay (create + edit) and the group id (generated in create mode).
     *
     * Snapshot state rather than a plain var: it is assigned by initCreate/initEdit *after* the
     * first composition and [isBuzzRelay] derives from it, so a plain var would leave the screen
     * rendering its NIP-29 shape forever.
     */
    var relay: NormalizedRelayUrl? by mutableStateOf(null)
        private set

    /**
     * True when the target relay speaks the Buzz dialect. Buzz calls these **channels**, and honours
     * only a subset of NIP-29's metadata: `name`, `about` and a two-valued `visibility`. Its create
     * path also takes a `channel_type`, which is what [isForum] selects.
     */
    val isBuzzRelay by derivedStateOf { relay?.let { BuzzRelayDialect.isBuzz(it) } == true }

    /** Buzz only: create a `forum` channel (threaded posts) instead of a `stream` (chat) one. */
    var isForum by mutableStateOf(false)

    var groupId: String = ""
        private set

    val name = mutableStateOf(TextFieldValue())
    val about = mutableStateOf(TextFieldValue())
    val picture = mutableStateOf(TextFieldValue())

    /** NIP-29 `banner`: a wide header image URL for the group. */
    val banner = mutableStateOf(TextFieldValue())

    /** Comma/space-separated topic hashtags; drives the discovery hashtag filter. */
    val topics = mutableStateOf(TextFieldValue())

    /** A single geohash for the group's location; drives the discovery geo filter. */
    val geohash = mutableStateOf(TextFieldValue())

    var isPrivate by mutableStateOf(false)
    var isClosed by mutableStateOf(false)
    var isHidden by mutableStateOf(false)
    var isRestricted by mutableStateOf(false)

    /**
     * Subgroups: the parent group this group nests under, or null for a top-level group.
     * Editable via the parent picker. Seeded from the current metadata in [prefillFrom].
     */
    var parentGroupId by mutableStateOf<String?>(null)
        private set

    /**
     * True once the user actually picks a parent in this session. Until then we let the save
     * read the group's live parent rather than the (possibly not-yet-loaded) prefilled value, so
     * a plain rename can never re-root a subgroup just because its metadata hadn't arrived yet.
     */
    private var parentTouched by mutableStateOf(false)

    var pickedMedia by mutableStateOf<SelectedMedia?>(null)
        private set

    var isWorking by mutableStateOf(false)

    /** Set once the user edits any field, so late-arriving metadata won't clobber their input. */
    var touched by mutableStateOf(false)
        private set

    /**
     * A Buzz relay strips leading `#`s and whitespace from a channel name and refuses one that ends
     * up empty, so on Buzz a name like `"# #"` can't be submitted at all.
     */
    val canPost by derivedStateOf {
        !isWorking && name.value.text.isNotBlank() && (!isBuzzRelay || isValidBuzzChannelName(name.value.text))
    }

    fun hasImage(): Boolean = pickedMedia != null || picture.value.text.isNotBlank()

    fun initCreate(
        accountViewModel: AccountViewModel,
        relay: NormalizedRelayUrl,
    ) {
        this.account = accountViewModel.account
        if (this.relay == null) {
            this.relay = relay
            // Random NIP-29 group id: 8 secure bytes, hex-encoded (matches Armada) — except on a
            // Buzz relay, which keys channels by UUID and ignores an id it cannot parse as one.
            this.groupId = if (BuzzRelayDialect.isBuzz(relay)) newBuzzChannelId() else RandomInstance.bytes(8).toHexKey()
        }
    }

    fun initEdit(
        accountViewModel: AccountViewModel,
        channel: RelayGroupChannel,
    ) {
        this.account = accountViewModel.account
        this.channel = channel
        this.relay = channel.groupId.relayUrl
        this.groupId = channel.groupId.id
    }

    /** Seed the fields from the group's current relay-signed metadata, unless the user has edited. */
    fun prefillFrom(channel: RelayGroupChannel) {
        if (touched) return
        val event = channel.event
        name.value = TextFieldValue(event?.name() ?: "")
        about.value = TextFieldValue(event?.about() ?: "")
        picture.value = TextFieldValue(event?.picture() ?: "")
        banner.value = TextFieldValue(event?.banner() ?: "")
        isPrivate = channel.isPrivate()
        isClosed = channel.isClosed()
        isHidden = event?.isHidden() ?: false
        isRestricted = event?.isRestricted() ?: false
        topics.value = TextFieldValue(event?.hashtags()?.distinct()?.joinToString(" ") ?: "")
        // Stored geohashes are mip-mapped into every prefix; the last (longest) is the real one.
        geohash.value = TextFieldValue(event?.geohashes()?.maxByOrNull { it.length } ?: "")
        parentGroupId = channel.parentGroupId()
    }

    /** Set (or clear, with null) the group's parent from the picker; marks the form touched. */
    fun setParent(groupId: String?) {
        parentGroupId = groupId
        parentTouched = true
        markTouched()
    }

    /** Split the topics field into distinct, non-blank, lowercased hashtags (leading `#` dropped). */
    private fun parseTopics(): List<String> =
        topics.value.text
            .split(',', ' ', '\n', '\t')
            .map { it.trim().removePrefix("#").lowercase() }
            .filter { it.isNotBlank() }
            .distinct()

    /** The single geohash (lowercased, `#` stripped), or empty when none. */
    private fun parseGeohashes(): List<String> =
        geohash.value.text
            .trim()
            .removePrefix("#")
            .lowercase()
            .ifBlank { null }
            ?.let { listOf(it) }
            ?: emptyList()

    fun markTouched() {
        touched = true
    }

    fun pickMedia(media: SelectedMedia) {
        pickedMedia = media
        touched = true
    }

    fun submit(
        uploader: MediaUploader,
        onSuccess: () -> Unit,
        onError: (String, String) -> Unit,
    ) {
        if (isWorking) return
        viewModelScope.launch(Dispatchers.IO) {
            isWorking = true
            try {
                val local = pickedMedia
                if (local != null) {
                    val uploadedUrl = uploadImage(local, uploader, onError) ?: return@launch
                    picture.value = TextFieldValue(uploadedUrl)
                    pickedMedia = null
                }

                try {
                    publish()
                } catch (e: SignerExceptions.ReadOnlyException) {
                    onError(
                        loadStringRes(Res.string.read_only_user),
                        loadStringRes(Res.string.login_with_a_private_key_to_be_able_to_sign_events),
                    )
                    return@launch
                } catch (_: SignerExceptions) {
                    // The user rejected it in their signer, or the signer was unavailable: nothing
                    // was published, so keep the form open instead of reporting success.
                    return@launch
                }
                onSuccess()
            } finally {
                isWorking = false
            }
        }
    }

    private suspend fun publish() {
        // Send Buzz the name it would store anyway (no leading `#`s), so our optimistic copy and
        // the relay's 39000 agree.
        val name = if (isBuzzRelay) canonicalBuzzChannelName(name.value.text) else name.value.text.trim()
        val about =
            about.value.text
                .trim()
                .ifBlank { null }
        val picture =
            picture.value.text
                .trim()
                .ifBlank { null }
        val banner =
            banner.value.text
                .trim()
                .ifBlank { null }
        val hashtags = parseTopics()
        val geohashes = parseGeohashes()
        val existing = channel
        if (existing == null) {
            account.relayGroups.createRelayGroup(
                relay = relay!!,
                groupId = groupId,
                name = name,
                about = about,
                picture = picture,
                banner = banner,
                isPrivate = isPrivate,
                isClosed = isClosed,
                isHidden = isHidden,
                isRestricted = isRestricted,
                hashtags = hashtags,
                geohashes = geohashes,
                parent = parentGroupId,
                channelType = if (isBuzzRelay) (if (isForum) BUZZ_CHANNEL_TYPE_FORUM else BUZZ_CHANNEL_TYPE_STREAM) else null,
            )
        } else {
            account.relayGroups.editRelayGroupMetadata(
                channel = existing,
                name = name,
                about = about,
                picture = picture,
                // Buzz has no banner field: keep whatever the channel carries rather than clear it.
                banner = if (isBuzzRelay) existing.bannerPicture() else banner,
                isPrivate = isPrivate,
                isClosed = isClosed,
                isHidden = isHidden,
                isRestricted = isRestricted,
                hashtags = hashtags,
                geohashes = geohashes,
                // Only override the parent when the user actually re-parented; otherwise let
                // Account read the group's live parent so a rename can't accidentally re-root it.
                // children likewise defaults to the live child list, so a concurrently-added
                // subgroup isn't dropped by this metadata edit.
                parent = if (parentTouched) parentGroupId else existing.parentGroupId(),
            )
        }
    }

    private suspend fun uploadImage(
        galleryUri: SelectedMedia,
        uploader: MediaUploader,
        onError: (String, String) -> Unit,
    ): String? = uploadToDefaultServer(galleryUri, account, uploader, onError)
}
