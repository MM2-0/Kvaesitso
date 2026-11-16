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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.communities.newCommunity

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media_no_details
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_sign_events
import com.vitorpamplona.amethyst.commons.resources.read_only_user
import com.vitorpamplona.amethyst.commons.resources.upload_no_image_url_returned
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.ModeratorTag
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.RelayTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.KindRuleTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.PubkeyRuleTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.WotTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
data class CommunityRelayEntry(
    val url: NormalizedRelayUrl,
    val marker: String? = null,
)

/**
 * Editor-side draft of a NIP-9B `k` rule. Empty/null limit fields mean "no limit";
 * they are dropped from the published tag.
 */
@Immutable
data class KindRuleDraft(
    val kind: Int,
    val maxBytes: Int? = null,
    val maxPerAuthorPerDay: Int? = null,
) {
    fun toTag(): KindRuleTag = KindRuleTag(kind, maxBytes, maxPerAuthorPerDay)
}

/**
 * Editor-side draft of a NIP-9B `p` rule for a banned pubkey. v1 only writes
 * `deny` policies; allow-listing is deferred to a follow-up.
 */
@Immutable
data class BannedPubkeyDraft(
    val pubkey: HexKey,
    val role: String? = null,
) {
    fun toTag(): PubkeyRuleTag = PubkeyRuleTag(pubkey, PubkeyRuleTag.Policy.DENY, role)
}

/** Editor-side draft of a NIP-9B `wot` gate. */
@Immutable
data class WotGateDraft(
    val rootPubkey: HexKey,
    val depth: Int,
) {
    fun toTag(): WotTag = WotTag(rootPubkey, depth)
}

@Stable
class NewCommunityModel : ViewModel() {
    var account: Account? = null

    // When set, publish() reuses this d-tag so the kind-34550 replaceable event
    // is updated in place (edit flow). Null in the create flow.
    var existingDTag: String? = null

    var isPublishing by mutableStateOf(false)

    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var rules by mutableStateOf("")
    var existingImageUrl by mutableStateOf<String?>(null)

    // Image upload state - mirrors NewBadgeModel.
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)
    var selectedServer by mutableStateOf<ServerName?>(null)

    // 0 = Low, 1 = Medium, 2 = High, 3 = UNCOMPRESSED
    var mediaQualitySlider by mutableIntStateOf(CompressorQuality.defaultSliderPosition)
    var stripMetadata by mutableStateOf(true)

    val strippingFailureConfirmation = SuspendableConfirmation()

    // Moderator/Relay lists - backed by mutableStateListOf for direct Compose observation.
    val moderators = mutableStateListOf<User>()
    val relays = mutableStateListOf<CommunityRelayEntry>()

    // NIP-9B structured rules - kept separate from the freeform `rules: String` text
    // field above. When all four collections/values are empty, no kind:34551 event is
    // published, so existing communities upgrade only when an owner opts in.
    val kindRules = mutableStateListOf<KindRuleDraft>()
    val bannedPubkeys = mutableStateListOf<BannedPubkeyDraft>()
    val wotGates = mutableStateListOf<WotGateDraft>()
    var maxEventSize by mutableStateOf<Int?>(null)

    fun init(account: Account) {
        if (this.account == account) return
        this.account = account
        this.selectedServer = defaultServer()
        this.stripMetadata = account.settings.stripLocationOnUpload
    }

    /**
     * Preloads the form with the current contents of [existing] so the user can edit
     * the replaceable kind 34550 event. Keeps the original `d` tag so relays replace
     * the previous version instead of creating a new community.
     */
    fun loadFrom(existing: CommunityDefinitionEvent) {
        existingDTag = existing.dTag()
        name = existing.name().orEmpty()
        description = existing.description().orEmpty()
        rules = existing.rules().orEmpty()
        existingImageUrl = existing.image()?.imageUrl

        val ownerKey = account?.signer?.pubKey
        moderators.clear()
        existing
            .moderatorKeys()
            .asSequence()
            .filter { it != ownerKey }
            .distinct()
            .forEach { pubkey ->
                val user = account?.cache?.getOrCreateUser(pubkey) ?: return@forEach
                if (moderators.none { it.pubkeyHex == user.pubkeyHex }) {
                    moderators.add(user)
                }
            }

        relays.clear()
        existing.relays().forEach { tag ->
            if (relays.none { it.url == tag.url }) {
                relays.add(CommunityRelayEntry(tag.url, tag.marker))
            }
        }
    }

    fun isEditing(): Boolean = existingDTag != null

    fun defaultServer() = account?.settings?.defaultFileServer ?: DEFAULT_MEDIA_SERVERS[0]

    fun setPickedMedia(uris: ImmutableList<SelectedMedia>) {
        this.multiOrchestrator = if (uris.isNotEmpty()) MultiOrchestrator(uris) else null
    }

    fun hasPickedImage(): Boolean = multiOrchestrator != null

    fun addModerator(user: User) {
        if (moderators.none { it.pubkeyHex == user.pubkeyHex }) {
            moderators.add(user)
        }
    }

    fun removeModerator(user: User) {
        moderators.removeAll { it.pubkeyHex == user.pubkeyHex }
    }

    fun addRelay(url: NormalizedRelayUrl) {
        if (relays.none { it.url == url }) {
            relays.add(CommunityRelayEntry(url))
        }
    }

    fun removeRelay(entry: CommunityRelayEntry) {
        relays.removeAll { it.url == entry.url }
    }

    fun setRelayMarker(
        entry: CommunityRelayEntry,
        marker: String?,
    ) {
        val index = relays.indexOfFirst { it.url == entry.url }
        if (index >= 0) {
            relays[index] = entry.copy(marker = marker)
        }
    }

    fun canPost(): Boolean =
        !isPublishing &&
            name.isNotBlank() &&
            description.isNotBlank()

    /** Returns true when the editor has any structured NIP-9B rule worth publishing. */
    fun hasStructuredRules(): Boolean =
        kindRules.isNotEmpty() ||
            bannedPubkeys.isNotEmpty() ||
            wotGates.isNotEmpty() ||
            maxEventSize != null

    fun addKindRule(rule: KindRuleDraft) {
        if (kindRules.none { it.kind == rule.kind }) {
            kindRules.add(rule)
        }
    }

    fun updateKindRule(
        kind: Int,
        update: (KindRuleDraft) -> KindRuleDraft,
    ) {
        val index = kindRules.indexOfFirst { it.kind == kind }
        if (index >= 0) {
            kindRules[index] = update(kindRules[index])
        }
    }

    fun removeKindRule(kind: Int) {
        kindRules.removeAll { it.kind == kind }
    }

    fun addBannedPubkey(entry: BannedPubkeyDraft) {
        if (bannedPubkeys.none { it.pubkey == entry.pubkey }) {
            bannedPubkeys.add(entry)
        }
    }

    fun removeBannedPubkey(pubkey: HexKey) {
        bannedPubkeys.removeAll { it.pubkey == pubkey }
    }

    fun addWotGate(entry: WotGateDraft) {
        if (wotGates.none { it.rootPubkey == entry.rootPubkey && it.depth == entry.depth }) {
            wotGates.add(entry)
        }
    }

    fun removeWotGate(entry: WotGateDraft) {
        wotGates.removeAll { it.rootPubkey == entry.rootPubkey && it.depth == entry.depth }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun publish(
        uploader: MediaUploader,
        onSuccess: () -> Unit,
        onError: (String, String) -> Unit,
    ) {
        publishUnsafe(uploader, onSuccess, onError)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun publishUnsafe(
        uploader: MediaUploader,
        onSuccess: () -> Unit,
        onError: (String, String) -> Unit,
    ) {
        val myAccount = account ?: return

        viewModelScope.launch(Dispatchers.IO) {
            isPublishing = true
            try {
                val uploadedUrl =
                    if (multiOrchestrator != null) {
                        uploadImageIfAny(uploader, myAccount, onError) ?: return@launch
                    } else {
                        null
                    }

                val imageUrl = uploadedUrl ?: existingImageUrl

                val ownerKey = myAccount.signer.pubKey
                val moderatorTags =
                    buildList {
                        add(ModeratorTag(ownerKey, null, "moderator"))
                        moderators
                            .asSequence()
                            .filter { it.pubkeyHex != ownerKey }
                            .forEach { add(ModeratorTag(it.pubkeyHex, null, "moderator")) }
                    }

                val relayTags = relays.map { RelayTag(it.url, it.marker) }

                val dTag = existingDTag ?: Uuid.random().toString()

                val definition =
                    myAccount.sendCommunityDefinition(
                        name = name.trim(),
                        description = description.trim(),
                        moderators = moderatorTags,
                        image = imageUrl,
                        rules = rules.trim().ifBlank { null },
                        relays = relayTags.ifEmpty { null },
                        dTag = dTag,
                    )

                if (definition == null) {
                    onError(
                        loadStringRes(Res.string.read_only_user),
                        loadStringRes(Res.string.login_with_a_private_key_to_be_able_to_sign_events),
                    )
                    return@launch
                }

                // Sibling NIP-9B rules document. Strictly opt-in: only published when
                // the owner has set at least one structured rule. Reuses the same dTag
                // so the rules event replaces in place across edits.
                if (hasStructuredRules()) {
                    myAccount.sendCommunityRules(
                        communityDTag = dTag,
                        kindRules = kindRules.map { it.toTag() },
                        pubkeyRules = bannedPubkeys.map { it.toTag() },
                        wotGates = wotGates.map { it.toTag() },
                        maxEventSize = maxEventSize,
                    )
                }

                // Auto-follow only on the create flow; editing doesn't change the follow set.
                if (existingDTag == null) {
                    val communityNote = myAccount.cache.getOrCreateAddressableNote(definition.address())
                    myAccount.follow(communityNote)
                }

                selectedServer?.let { myAccount.settings.changeDefaultFileServer(it) }
                myAccount.settings.changeStripLocationOnUpload(stripMetadata)

                reset()
                onSuccess()
            } catch (_: SignerExceptions.ReadOnlyException) {
                onError(
                    loadStringRes(Res.string.read_only_user),
                    loadStringRes(Res.string.login_with_a_private_key_to_be_able_to_sign_events),
                )
            } catch (_: SignerExceptions) {
                // The user rejected it in their signer, or the signer was unavailable: nothing more
                // is published, so keep the form open instead of reporting success.
            } finally {
                isPublishing = false
            }
        }
    }

    private suspend fun uploadImageIfAny(
        uploader: MediaUploader,
        myAccount: Account,
        onError: (String, String) -> Unit,
    ): String? {
        val orch = multiOrchestrator ?: return null
        val serverToUse = selectedServer ?: defaultServer()

        val results =
            orch.upload(
                alt = name.trim().ifBlank { "Community cover" },
                contentWarningReason = null,
                mediaQuality = CompressorQuality.fromSlider(mediaQualitySlider),
                server = serverToUse,
                account = myAccount,
                uploader = uploader,
                useH265 = false,
                stripMetadata = stripMetadata,
                onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
            )

        if (!results.allGood) {
            val messages =
                results.errors
                    .map { loadStringRes(it.errorResource, *it.params) }
                    .distinct()
                    .joinToString(".\n")
            onError(loadStringRes(Res.string.failed_to_upload_media_no_details), messages)
            return null
        }

        val uploaded =
            results.successful.firstNotNullOfOrNull {
                it.result as? UploadOrchestrator.OrchestratorResult.ServerResult
            } ?: run {
                onError(
                    loadStringRes(Res.string.failed_to_upload_media_no_details),
                    loadStringRes(Res.string.upload_no_image_url_returned),
                )
                return null
            }

        return uploaded.url
    }

    fun reset() {
        name = ""
        description = ""
        rules = ""
        existingImageUrl = null
        existingDTag = null
        multiOrchestrator = null
        moderators.clear()
        relays.clear()
        kindRules.clear()
        bannedPubkeys.clear()
        wotGates.clear()
        maxEventSize = null
        selectedServer = defaultServer()
    }

    companion object {
        /**
         * Builds the `kindRules`/`pubkeyRules`/`wotGates` lists in the same order
         * the publish path uses. Pure helper for unit testing — the publish() path
         * touches an [Account] which can't be constructed in-process.
         */
        fun buildRulesPayload(
            kindRules: List<KindRuleDraft>,
            bannedPubkeys: List<BannedPubkeyDraft>,
            wotGates: List<WotGateDraft>,
            maxEventSize: Int?,
        ): RulesPayload? {
            if (kindRules.isEmpty() && bannedPubkeys.isEmpty() && wotGates.isEmpty() && maxEventSize == null) {
                return null
            }
            return RulesPayload(
                kindRules = kindRules.map { it.toTag() },
                pubkeyRules = bannedPubkeys.map { it.toTag() },
                wotGates = wotGates.map { it.toTag() },
                maxEventSize = maxEventSize,
            )
        }
    }

    @Immutable
    data class RulesPayload(
        val kindRules: List<KindRuleTag>,
        val pubkeyRules: List<PubkeyRuleTag>,
        val wotGates: List<WotTag>,
        val maxEventSize: Int?,
    )
}
