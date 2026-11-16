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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.backups

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.backups.BackupEventType
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.backup_conflict_relay_read_only
import com.vitorpamplona.amethyst.commons.resources.backup_conflict_relay_read_write
import com.vitorpamplona.amethyst.commons.resources.backup_conflict_relay_write_only
import com.vitorpamplona.amethyst.commons.resources.backup_entry_algo_feed
import com.vitorpamplona.amethyst.commons.resources.backup_entry_chat_room
import com.vitorpamplona.amethyst.commons.resources.backup_entry_community
import com.vitorpamplona.amethyst.commons.resources.backup_entry_hashtag
import com.vitorpamplona.amethyst.commons.resources.backup_entry_identity_claim
import com.vitorpamplona.amethyst.commons.resources.backup_entry_location
import com.vitorpamplona.amethyst.commons.resources.backup_entry_mint
import com.vitorpamplona.amethyst.commons.resources.backup_entry_nutzap_key
import com.vitorpamplona.amethyst.commons.resources.backup_entry_offer
import com.vitorpamplona.amethyst.commons.resources.backup_entry_other
import com.vitorpamplona.amethyst.commons.resources.backup_entry_payment_target
import com.vitorpamplona.amethyst.commons.resources.backup_entry_person
import com.vitorpamplona.amethyst.commons.resources.backup_entry_profile_field
import com.vitorpamplona.amethyst.commons.resources.backup_entry_public_chat
import com.vitorpamplona.amethyst.commons.resources.backup_entry_relay
import com.vitorpamplona.amethyst.commons.resources.backup_entry_relay_group
import com.vitorpamplona.amethyst.commons.resources.backup_entry_thread
import com.vitorpamplona.amethyst.commons.resources.backup_entry_trust_provider
import com.vitorpamplona.amethyst.commons.resources.backup_entry_word
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_about
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_banner
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_birthday
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_bot
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_clink_offer
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_display_name
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_lud06
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_lud16
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_name
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_nip05
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_picture
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_pronouns
import com.vitorpamplona.amethyst.commons.resources.backup_profile_field_website
import com.vitorpamplona.amethyst.commons.resources.backup_type_app_settings
import com.vitorpamplona.amethyst.commons.resources.backup_type_app_settings_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_blocked_relays
import com.vitorpamplona.amethyst.commons.resources.backup_type_blocked_relays_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_bolt12_offers
import com.vitorpamplona.amethyst.commons.resources.backup_type_bolt12_offers_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_cashu_wallet
import com.vitorpamplona.amethyst.commons.resources.backup_type_cashu_wallet_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_communities
import com.vitorpamplona.amethyst.commons.resources.backup_type_communities_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_concord_communities
import com.vitorpamplona.amethyst.commons.resources.backup_type_concord_communities_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_dm_relays
import com.vitorpamplona.amethyst.commons.resources.backup_type_dm_relays_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_ephemeral_chats
import com.vitorpamplona.amethyst.commons.resources.backup_type_ephemeral_chats_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_favorite_algo_feeds
import com.vitorpamplona.amethyst.commons.resources.backup_type_favorite_algo_feeds_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_follow_list
import com.vitorpamplona.amethyst.commons.resources.backup_type_follow_list_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_geohashes
import com.vitorpamplona.amethyst.commons.resources.backup_type_geohashes_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_hashtags
import com.vitorpamplona.amethyst.commons.resources.backup_type_hashtags_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_indexer_relays
import com.vitorpamplona.amethyst.commons.resources.backup_type_indexer_relays_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_key_package_relays
import com.vitorpamplona.amethyst.commons.resources.backup_type_key_package_relays_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_mute_list
import com.vitorpamplona.amethyst.commons.resources.backup_type_mute_list_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_nutzap_info
import com.vitorpamplona.amethyst.commons.resources.backup_type_nutzap_info_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_other
import com.vitorpamplona.amethyst.commons.resources.backup_type_other_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_outbox_inbox_relays
import com.vitorpamplona.amethyst.commons.resources.backup_type_outbox_inbox_relays_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_payment_targets
import com.vitorpamplona.amethyst.commons.resources.backup_type_payment_targets_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_private_outbox_relays
import com.vitorpamplona.amethyst.commons.resources.backup_type_private_outbox_relays_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_profile
import com.vitorpamplona.amethyst.commons.resources.backup_type_profile_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_public_chats
import com.vitorpamplona.amethyst.commons.resources.backup_type_public_chats_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_relay_feeds
import com.vitorpamplona.amethyst.commons.resources.backup_type_relay_feeds_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_relay_groups
import com.vitorpamplona.amethyst.commons.resources.backup_type_relay_groups_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_search_relays
import com.vitorpamplona.amethyst.commons.resources.backup_type_search_relays_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_trust_providers
import com.vitorpamplona.amethyst.commons.resources.backup_type_trust_providers_explainer
import com.vitorpamplona.amethyst.commons.resources.backup_type_trusted_relays
import com.vitorpamplona.amethyst.commons.resources.backup_type_trusted_relays_explainer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListDiff
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListDiff
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.diff.ContentChange
import com.vitorpamplona.quartz.nip01Core.diff.EventDiff
import com.vitorpamplona.quartz.nip01Core.diff.ItemChange
import com.vitorpamplona.quartz.nip01Core.diff.ListDiff
import com.vitorpamplona.quartz.nip01Core.diff.ValueChange
import com.vitorpamplona.quartz.nip01Core.metadata.Birthday
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataDiff
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip02FollowList.ContactListDiff
import com.vitorpamplona.quartz.nip28PublicChat.list.PublicChatListDiff
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListDiff
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListDiff
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListDiff
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListDiff
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.EventTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.HashtagTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.MuteTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.WordTag
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayListDiff
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListDiff
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletDiff
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoDiff
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListDiff
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListDiff
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataDiff
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListDiff
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTargetsDiff
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListDiff
import org.jetbrains.compose.resources.StringResource

private const val MAX_VALUE_LENGTH = 80

/** Items lost, gained and changed in a conflict, counting encrypted content as one item. */
@Immutable
class ConflictCounts(
    val removed: Int,
    val added: Int,
    val changed: Int,
)

private fun ContentChange.asCounts() =
    ConflictCounts(
        removed = if (this == ContentChange.CLEARED) 1 else 0,
        added = if (this == ContentChange.ADDED) 1 else 0,
        changed = if (this == ContentChange.CHANGED) 1 else 0,
    )

private operator fun ConflictCounts.plus(other: ConflictCounts) = ConflictCounts(removed + other.removed, added + other.added, changed + other.changed)

private fun ListDiff<*>.asCounts() = ConflictCounts(removed.size, added.size, changed.size)

private fun ValueChange<*>?.asCounts() =
    when {
        this == null -> ConflictCounts(0, 0, 0)
        after == null -> ConflictCounts(1, 0, 0)
        before == null -> ConflictCounts(0, 1, 0)
        else -> ConflictCounts(0, 0, 1)
    }

/**
 * The counts straight from a diff's own lists, without building display items: what the
 * Home cards need, however large the conflict.
 */
fun countsOf(diff: EventDiff): ConflictCounts =
    when (diff) {
        is MetadataDiff ->
            listOf(diff.name, diff.displayName, diff.picture, diff.banner, diff.website, diff.about, diff.pronouns, diff.nip05, diff.lud06, diff.lud16, diff.clinkOffer, diff.bot, diff.birthday)
                .fold(diff.otherFields.asCounts() + diff.identityClaims.asCounts()) { acc, change -> acc + change.asCounts() }
        is ContactListDiff -> diff.follows.asCounts()
        is MuteListDiff -> diff.publicMutes.asCounts() + diff.privateItems.asCounts()
        is AdvertisedRelayListDiff -> diff.relays.asCounts()
        is RelayListDiff -> diff.relays.asCounts() + diff.privateRelays.asCounts()
        is PublicChatListDiff -> diff.channels.asCounts() + diff.privateItems.asCounts()
        is CommunityListDiff -> diff.communities.asCounts() + diff.privateItems.asCounts()
        is InterestListDiff -> diff.hashtags.asCounts() + diff.privateItems.asCounts()
        is GeohashListDiff -> diff.geohashes.asCounts() + diff.privateItems.asCounts()
        is FavoriteAlgoFeedsListDiff -> diff.feeds.asCounts() + diff.privateItems.asCounts()
        is EphemeralChatListDiff -> diff.rooms.asCounts() + diff.privateItems.asCounts()
        is SimpleGroupListDiff -> diff.groups.asCounts() + diff.privateItems.asCounts()
        is TrustProviderListDiff -> diff.providers.asCounts() + diff.privateItems.asCounts()
        is NutzapInfoDiff -> diff.mints.asCounts() + diff.relays.asCounts() + diff.p2pkPubkey.asCounts()
        is PaymentTargetsDiff -> diff.targets.asCounts()
        is Bolt12OfferListDiff -> diff.offers.asCounts()
        is CashuWalletDiff -> diff.wallet.asCounts()
        is ConcordCommunityListDiff -> diff.communities.asCounts()
        is AppSpecificDataDiff -> diff.data.asCounts()
        else -> ConflictCounts(0, 0, 0)
    }

/**
 * One entry of a conflict, typed by what it points to so the review screen can load it from
 * relays and link to it. [detail] is extra text: a petname, a relay marker, before → after.
 */
@Immutable
sealed interface ReviewItem {
    val detail: String?

    class Person(
        val pubKey: HexKey,
        override val detail: String? = null,
    ) : ReviewItem

    class Thread(
        val eventId: HexKey,
        override val detail: String? = null,
    ) : ReviewItem

    class PublicChat(
        val channelId: HexKey,
        override val detail: String? = null,
    ) : ReviewItem

    class Addressable(
        val address: Address,
        override val detail: String? = null,
    ) : ReviewItem

    class ChatRoom(
        val roomId: RoomId,
        override val detail: String? = null,
    ) : ReviewItem

    class Relay(
        val url: NormalizedRelayUrl,
        override val detail: String? = null,
    ) : ReviewItem

    class Text(
        val text: String,
        override val detail: String? = null,
    ) : ReviewItem
}

/** One labelled group of differences (people, relays, profile fields…) of a conflict. */
@Immutable
class DiffGroup(
    val label: StringResource,
    val removed: List<ReviewItem>,
    val added: List<ReviewItem>,
    val changed: List<ReviewItem>,
) {
    fun isEmpty() = removed.isEmpty() && added.isEmpty() && changed.isEmpty()
}

/** Everything a conflict shows: its non-empty groups and the change to its encrypted content. */
@Immutable
class DiffPresentation(
    val groups: List<DiffGroup>,
    val content: ContentChange,
) {
    val removedCount = groups.sumOf { it.removed.size }
    val addedCount = groups.sumOf { it.added.size }
    val changedCount = groups.sumOf { it.changed.size }
}

/** A group from a [ListDiff] of an event's parsed items. */
private fun <T> listGroup(
    label: StringResource,
    diff: ListDiff<T>,
    item: (T) -> ReviewItem,
    change: (ItemChange<T>) -> ReviewItem,
) = DiffGroup(label, diff.removed.map(item), diff.added.map(item), diff.changed.map(change))

/** A group whose items can't change, only be added or removed (hashtags, plain relays…). */
private fun <T> listGroup(
    label: StringResource,
    diff: ListDiff<T>,
    item: (T) -> ReviewItem,
) = listGroup(label, diff, item) { item(it.after) }

/** Keeps only the items of [T] from a list diff of a sealed family (e.g. one kind of [MuteTag]). */
private inline fun <reified T> ListDiff<*>.only() =
    ListDiff(
        removed.filterIsInstance<T>(),
        added.filterIsInstance<T>(),
        changed.filter { it.before is T }.map { ItemChange(it.before as T, it.after as T) },
    )

/** A group from single-valued fields: a field set only after is added, only before is removed. */
private fun fieldGroup(
    label: StringResource,
    fields: List<Pair<String, ValueChange<String>?>>,
): DiffGroup {
    val removed = mutableListOf<ReviewItem>()
    val added = mutableListOf<ReviewItem>()
    val changed = mutableListOf<ReviewItem>()
    fields.forEach { (name, change) ->
        if (change == null) return@forEach
        val before = change.before
        val after = change.after
        when {
            before != null && after == null -> removed.add(ReviewItem.Text(name, clip(before)))
            before == null && after != null -> added.add(ReviewItem.Text(name, clip(after)))
            else -> changed.add(ReviewItem.Text(name, clip(before) + " → " + clip(after)))
        }
    }
    return DiffGroup(label, removed, added, changed)
}

private fun arrow(
    before: String?,
    after: String?,
) = (before ?: "∅") + " → " + (after ?: "∅")

/**
 * Each event's own diff as typed, labelled groups, built once per diff: it maps every item,
 * which for a large list is too much work to redo on each recomposition.
 */
@Composable
fun rememberPresentation(diff: EventDiff): DiffPresentation {
    // Compose resources resolve only in composition (or suspended), so the labels
    // [presentationOf] writes into item text are resolved here and handed in. Only the
    // diff types that write labels get any, so the rest pay for no resource reads. The
    // map compares by value: a recomposition with the same locale keeps the cached result.
    val labels = labelsWrittenBy(diff).associateWith { stringRes(it) }
    return remember(diff, labels) { presentationOf(diff) { labels.getValue(it) } }
}

/** Every label [presentationOf] resolves through its `str` parameter for [diff]. */
fun labelsWrittenBy(diff: EventDiff): List<StringResource> =
    when (diff) {
        is MetadataDiff -> PROFILE_FIELD_LABELS
        is AdvertisedRelayListDiff -> RELAY_MARKER_LABELS
        else -> emptyList()
    }

private val PROFILE_FIELD_LABELS =
    listOf(
        Res.string.backup_profile_field_name,
        Res.string.backup_profile_field_display_name,
        Res.string.backup_profile_field_about,
        Res.string.backup_profile_field_picture,
        Res.string.backup_profile_field_banner,
        Res.string.backup_profile_field_website,
        Res.string.backup_profile_field_nip05,
        Res.string.backup_profile_field_lud16,
        Res.string.backup_profile_field_lud06,
        Res.string.backup_profile_field_clink_offer,
        Res.string.backup_profile_field_pronouns,
        Res.string.backup_profile_field_birthday,
        Res.string.backup_profile_field_bot,
    )

private val RELAY_MARKER_LABELS =
    listOf(
        Res.string.backup_conflict_relay_read_write,
        Res.string.backup_conflict_relay_read_only,
        Res.string.backup_conflict_relay_write_only,
    )

/**
 * Turns each event's own diff into typed, labelled groups. [str] resolves the labels; a
 * label it asks for must be in [labelsWrittenBy] for that diff type.
 */
fun presentationOf(
    diff: EventDiff,
    str: (StringResource) -> String,
): DiffPresentation {
    val (groups, content) =
        when (diff) {
            is MetadataDiff -> {
                val birthday = diff.birthday?.let { ValueChange(it.before?.let(::birthdayText), it.after?.let(::birthdayText)) }
                val bot = diff.bot?.let { ValueChange(it.before?.toString(), it.after?.toString()) }
                listOf(
                    fieldGroup(
                        Res.string.backup_entry_profile_field,
                        listOf(
                            str(Res.string.backup_profile_field_name) to diff.name,
                            str(Res.string.backup_profile_field_display_name) to diff.displayName,
                            str(Res.string.backup_profile_field_about) to diff.about,
                            str(Res.string.backup_profile_field_picture) to diff.picture,
                            str(Res.string.backup_profile_field_banner) to diff.banner,
                            str(Res.string.backup_profile_field_website) to diff.website,
                            str(Res.string.backup_profile_field_nip05) to diff.nip05,
                            str(Res.string.backup_profile_field_lud16) to diff.lud16,
                            str(Res.string.backup_profile_field_lud06) to diff.lud06,
                            str(Res.string.backup_profile_field_clink_offer) to diff.clinkOffer,
                            str(Res.string.backup_profile_field_pronouns) to diff.pronouns,
                            str(Res.string.backup_profile_field_birthday) to birthday,
                            str(Res.string.backup_profile_field_bot) to bot,
                        ),
                    ),
                    listGroup(
                        Res.string.backup_entry_identity_claim,
                        diff.identityClaims,
                        { ReviewItem.Text(it.platformIdentity()) },
                        { ReviewItem.Text(it.after.platformIdentity(), arrow(it.before.proof, it.after.proof)) },
                    ),
                    listGroup(
                        Res.string.backup_entry_other,
                        diff.otherFields,
                        { ReviewItem.Text(it.first, clip(it.second)) },
                        { ReviewItem.Text(it.after.first, clip(it.before.second) + " → " + clip(it.after.second)) },
                    ),
                ) to ContentChange.NONE
            }
            is ContactListDiff ->
                listOf(
                    listGroup(
                        Res.string.backup_entry_person,
                        diff.follows,
                        { ReviewItem.Person(it.pubKey, it.petname) },
                        {
                            ReviewItem.Person(
                                it.after.pubKey,
                                arrow(
                                    listOfNotNull(it.before.petname, it.before.relayUri?.url).joinToString().ifEmpty { null },
                                    listOfNotNull(it.after.petname, it.after.relayUri?.url).joinToString().ifEmpty { null },
                                ),
                            )
                        },
                    ),
                ) to ContentChange.NONE
            is MuteListDiff ->
                listOf(
                    listGroup(Res.string.backup_entry_person, diff.publicMutes.only<UserTag>(), { ReviewItem.Person(it.pubKey) }),
                    listGroup(Res.string.backup_entry_word, diff.publicMutes.only<WordTag>(), { ReviewItem.Text("\"" + it.word + "\"") }),
                    listGroup(Res.string.backup_entry_hashtag, diff.publicMutes.only<HashtagTag>(), { ReviewItem.Text("#" + it.hashtag) }),
                    listGroup(Res.string.backup_entry_thread, diff.publicMutes.only<EventTag>(), { ReviewItem.Thread(it.eventId) }),
                ) to diff.privateItems
            is AdvertisedRelayListDiff -> {
                val types =
                    mapOf(
                        AdvertisedRelayType.BOTH to str(Res.string.backup_conflict_relay_read_write),
                        AdvertisedRelayType.READ to str(Res.string.backup_conflict_relay_read_only),
                        AdvertisedRelayType.WRITE to str(Res.string.backup_conflict_relay_write_only),
                    )
                listOf(
                    listGroup(
                        Res.string.backup_entry_relay,
                        diff.relays,
                        { ReviewItem.Relay(it.relayUrl, types[it.type]) },
                        { ReviewItem.Relay(it.after.relayUrl, arrow(types[it.before.type], types[it.after.type])) },
                    ),
                ) to ContentChange.NONE
            }
            is RelayListDiff -> listOf(listGroup(Res.string.backup_entry_relay, diff.relays, { ReviewItem.Relay(it) })) to diff.privateRelays
            is PublicChatListDiff ->
                listOf(
                    listGroup(
                        Res.string.backup_entry_public_chat,
                        diff.channels,
                        { ReviewItem.PublicChat(it.eventId) },
                        { ReviewItem.PublicChat(it.after.eventId, arrow(it.before.relay?.url, it.after.relay?.url)) },
                    ),
                ) to diff.privateItems
            is CommunityListDiff ->
                listOf(
                    listGroup(
                        Res.string.backup_entry_community,
                        diff.communities,
                        { ReviewItem.Addressable(it.address) },
                        { ReviewItem.Addressable(it.after.address, arrow(it.before.relayHint?.url, it.after.relayHint?.url)) },
                    ),
                ) to diff.privateItems
            is InterestListDiff -> listOf(listGroup(Res.string.backup_entry_hashtag, diff.hashtags, { ReviewItem.Text("#$it") })) to diff.privateItems
            is GeohashListDiff -> listOf(listGroup(Res.string.backup_entry_location, diff.geohashes, { ReviewItem.Text(it) })) to diff.privateItems
            is FavoriteAlgoFeedsListDiff ->
                listOf(
                    listGroup(
                        Res.string.backup_entry_algo_feed,
                        diff.feeds,
                        { ReviewItem.Addressable(it.address) },
                        { ReviewItem.Addressable(it.after.address, arrow(it.before.relayHint?.url, it.after.relayHint?.url)) },
                    ),
                ) to diff.privateItems
            is EphemeralChatListDiff -> listOf(listGroup(Res.string.backup_entry_chat_room, diff.rooms, { ReviewItem.ChatRoom(it) })) to diff.privateItems
            is SimpleGroupListDiff ->
                listOf(
                    listGroup(
                        Res.string.backup_entry_relay_group,
                        diff.groups,
                        { ReviewItem.Text(it.name ?: it.groupId, it.relayUrl) },
                        { ReviewItem.Text(it.after.name ?: it.after.groupId, arrow(it.before.name, it.after.name)) },
                    ),
                ) to diff.privateItems
            is TrustProviderListDiff ->
                listOf(
                    listGroup(
                        Res.string.backup_entry_trust_provider,
                        diff.providers,
                        { ReviewItem.Person(it.pubkey, it.service.type) },
                        { ReviewItem.Person(it.after.pubkey, it.after.service.type + ": " + arrow(it.before.relayUrl.url, it.after.relayUrl.url)) },
                    ),
                ) to diff.privateItems
            is NutzapInfoDiff ->
                listOf(
                    listGroup(
                        Res.string.backup_entry_mint,
                        diff.mints,
                        { ReviewItem.Text(it.mintUrl, it.units.joinToString().ifEmpty { null }) },
                        { ReviewItem.Text(it.after.mintUrl, arrow(it.before.units.joinToString(), it.after.units.joinToString())) },
                    ),
                    listGroup(Res.string.backup_entry_relay, diff.relays, { ReviewItem.Relay(it) }),
                    nutzapKeyGroup(diff.p2pkPubkey),
                ) to ContentChange.NONE
            is PaymentTargetsDiff ->
                listOf(
                    listGroup(Res.string.backup_entry_payment_target, diff.targets, { ReviewItem.Text(it.type, clip(it.authority)) }),
                ) to ContentChange.NONE
            is Bolt12OfferListDiff -> listOf(listGroup(Res.string.backup_entry_offer, diff.offers, { ReviewItem.Text(clip(it)) })) to ContentChange.NONE
            is CashuWalletDiff -> emptyList<DiffGroup>() to diff.wallet
            is ConcordCommunityListDiff -> emptyList<DiffGroup>() to diff.communities
            is AppSpecificDataDiff -> emptyList<DiffGroup>() to diff.data
            else -> emptyList<DiffGroup>() to ContentChange.NONE
        }
    return DiffPresentation(groups.filterNot { it.isEmpty() }, content)
}

private fun nutzapKeyGroup(change: ValueChange<HexKey>?): DiffGroup {
    val none = emptyList<ReviewItem>()
    if (change == null) return DiffGroup(Res.string.backup_entry_nutzap_key, none, none, none)
    val before = change.before
    val after = change.after
    return when {
        before != null && after == null -> DiffGroup(Res.string.backup_entry_nutzap_key, listOf(ReviewItem.Text(before)), none, none)
        before == null && after != null -> DiffGroup(Res.string.backup_entry_nutzap_key, none, listOf(ReviewItem.Text(after)), none)
        else -> DiffGroup(Res.string.backup_entry_nutzap_key, none, none, listOf(ReviewItem.Text(arrow(before, after))))
    }
}

private fun birthdayText(birthday: Birthday): String = listOfNotNull(birthday.year, birthday.month, birthday.day).joinToString("-")

fun clip(text: String?): String {
    if (text == null) return ""
    val oneLine = text.replace('\n', ' ')
    return if (oneLine.length > MAX_VALUE_LENGTH) oneLine.take(MAX_VALUE_LENGTH) + "…" else oneLine
}

fun eventTypeName(type: BackupEventType): StringResource =
    when (type) {
        BackupEventType.PROFILE -> Res.string.backup_type_profile
        BackupEventType.FOLLOW_LIST -> Res.string.backup_type_follow_list
        BackupEventType.MUTE_LIST -> Res.string.backup_type_mute_list
        BackupEventType.OUTBOX_INBOX_RELAYS -> Res.string.backup_type_outbox_inbox_relays
        BackupEventType.DM_RELAYS -> Res.string.backup_type_dm_relays
        BackupEventType.KEY_PACKAGE_RELAYS -> Res.string.backup_type_key_package_relays
        BackupEventType.SEARCH_RELAYS -> Res.string.backup_type_search_relays
        BackupEventType.INDEXER_RELAYS -> Res.string.backup_type_indexer_relays
        BackupEventType.RELAY_FEEDS -> Res.string.backup_type_relay_feeds
        BackupEventType.BLOCKED_RELAYS -> Res.string.backup_type_blocked_relays
        BackupEventType.TRUSTED_RELAYS -> Res.string.backup_type_trusted_relays
        BackupEventType.PRIVATE_OUTBOX_RELAYS -> Res.string.backup_type_private_outbox_relays
        BackupEventType.APP_SETTINGS -> Res.string.backup_type_app_settings
        BackupEventType.PUBLIC_CHATS -> Res.string.backup_type_public_chats
        BackupEventType.COMMUNITIES -> Res.string.backup_type_communities
        BackupEventType.HASHTAGS -> Res.string.backup_type_hashtags
        BackupEventType.GEOHASHES -> Res.string.backup_type_geohashes
        BackupEventType.FAVORITE_ALGO_FEEDS -> Res.string.backup_type_favorite_algo_feeds
        BackupEventType.EPHEMERAL_CHATS -> Res.string.backup_type_ephemeral_chats
        BackupEventType.RELAY_GROUPS -> Res.string.backup_type_relay_groups
        BackupEventType.CONCORD_COMMUNITIES -> Res.string.backup_type_concord_communities
        BackupEventType.TRUST_PROVIDERS -> Res.string.backup_type_trust_providers
        BackupEventType.CASHU_WALLET -> Res.string.backup_type_cashu_wallet
        BackupEventType.NUTZAP_INFO -> Res.string.backup_type_nutzap_info
        BackupEventType.PAYMENT_TARGETS -> Res.string.backup_type_payment_targets
        BackupEventType.BOLT12_OFFERS -> Res.string.backup_type_bolt12_offers
        BackupEventType.OTHER -> Res.string.backup_type_other
    }

fun eventTypeExplainer(type: BackupEventType): StringResource =
    when (type) {
        BackupEventType.PROFILE -> Res.string.backup_type_profile_explainer
        BackupEventType.FOLLOW_LIST -> Res.string.backup_type_follow_list_explainer
        BackupEventType.MUTE_LIST -> Res.string.backup_type_mute_list_explainer
        BackupEventType.OUTBOX_INBOX_RELAYS -> Res.string.backup_type_outbox_inbox_relays_explainer
        BackupEventType.DM_RELAYS -> Res.string.backup_type_dm_relays_explainer
        BackupEventType.KEY_PACKAGE_RELAYS -> Res.string.backup_type_key_package_relays_explainer
        BackupEventType.SEARCH_RELAYS -> Res.string.backup_type_search_relays_explainer
        BackupEventType.INDEXER_RELAYS -> Res.string.backup_type_indexer_relays_explainer
        BackupEventType.RELAY_FEEDS -> Res.string.backup_type_relay_feeds_explainer
        BackupEventType.BLOCKED_RELAYS -> Res.string.backup_type_blocked_relays_explainer
        BackupEventType.TRUSTED_RELAYS -> Res.string.backup_type_trusted_relays_explainer
        BackupEventType.PRIVATE_OUTBOX_RELAYS -> Res.string.backup_type_private_outbox_relays_explainer
        BackupEventType.APP_SETTINGS -> Res.string.backup_type_app_settings_explainer
        BackupEventType.PUBLIC_CHATS -> Res.string.backup_type_public_chats_explainer
        BackupEventType.COMMUNITIES -> Res.string.backup_type_communities_explainer
        BackupEventType.HASHTAGS -> Res.string.backup_type_hashtags_explainer
        BackupEventType.GEOHASHES -> Res.string.backup_type_geohashes_explainer
        BackupEventType.FAVORITE_ALGO_FEEDS -> Res.string.backup_type_favorite_algo_feeds_explainer
        BackupEventType.EPHEMERAL_CHATS -> Res.string.backup_type_ephemeral_chats_explainer
        BackupEventType.RELAY_GROUPS -> Res.string.backup_type_relay_groups_explainer
        BackupEventType.CONCORD_COMMUNITIES -> Res.string.backup_type_concord_communities_explainer
        BackupEventType.TRUST_PROVIDERS -> Res.string.backup_type_trust_providers_explainer
        BackupEventType.CASHU_WALLET -> Res.string.backup_type_cashu_wallet_explainer
        BackupEventType.NUTZAP_INFO -> Res.string.backup_type_nutzap_info_explainer
        BackupEventType.PAYMENT_TARGETS -> Res.string.backup_type_payment_targets_explainer
        BackupEventType.BOLT12_OFFERS -> Res.string.backup_type_bolt12_offers_explainer
        BackupEventType.OTHER -> Res.string.backup_type_other_explainer
    }
