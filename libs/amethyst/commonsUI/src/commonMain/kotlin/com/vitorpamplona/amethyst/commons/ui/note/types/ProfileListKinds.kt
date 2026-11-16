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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.bolt12_offers
import com.vitorpamplona.amethyst.commons.resources.cashu_mints
import com.vitorpamplona.amethyst.commons.resources.kc_list_and_more
import com.vitorpamplona.amethyst.commons.resources.kc_list_badge_count
import com.vitorpamplona.amethyst.commons.resources.kc_list_badge_sets
import com.vitorpamplona.amethyst.commons.resources.kc_list_community_count
import com.vitorpamplona.amethyst.commons.resources.kc_list_emoji_pack_count
import com.vitorpamplona.amethyst.commons.resources.kc_list_entry_count
import com.vitorpamplona.amethyst.commons.resources.kc_list_expires
import com.vitorpamplona.amethyst.commons.resources.kc_list_external_identities
import com.vitorpamplona.amethyst.commons.resources.kc_list_favorite_follow_sets
import com.vitorpamplona.amethyst.commons.resources.kc_list_follow_set_count
import com.vitorpamplona.amethyst.commons.resources.kc_list_gallery_entry
import com.vitorpamplona.amethyst.commons.resources.kc_list_geocache_hunt
import com.vitorpamplona.amethyst.commons.resources.kc_list_link
import com.vitorpamplona.amethyst.commons.resources.kc_list_nutzap_info_off
import com.vitorpamplona.amethyst.commons.resources.kc_list_nutzap_info_on
import com.vitorpamplona.amethyst.commons.resources.kc_list_p2pk_key
import com.vitorpamplona.amethyst.commons.resources.kc_list_podcast_count
import com.vitorpamplona.amethyst.commons.resources.kc_list_relays
import com.vitorpamplona.amethyst.commons.resources.kc_list_server_count
import com.vitorpamplona.amethyst.commons.resources.kc_list_status_cleared
import com.vitorpamplona.amethyst.commons.resources.kc_list_status_expired_pill
import com.vitorpamplona.amethyst.commons.resources.kc_list_status_general
import com.vitorpamplona.amethyst.commons.resources.kc_list_status_music
import com.vitorpamplona.amethyst.commons.resources.kc_list_sticker_count
import com.vitorpamplona.amethyst.commons.resources.kc_list_sticker_pack
import com.vitorpamplona.amethyst.commons.resources.kc_list_stickers_sensitive_hidden
import com.vitorpamplona.amethyst.commons.resources.kc_list_streak_days
import com.vitorpamplona.amethyst.commons.resources.kc_list_streak_invalid
import com.vitorpamplona.amethyst.commons.resources.kc_list_streak_last_post
import com.vitorpamplona.amethyst.commons.resources.kc_list_streak_pill_ended
import com.vitorpamplona.amethyst.commons.resources.kc_list_streak_pill_live
import com.vitorpamplona.amethyst.commons.resources.kc_list_streak_started
import com.vitorpamplona.amethyst.commons.resources.kc_list_streak_title
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_active
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_background
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_background_image
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_background_video
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_body_font
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_colors
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_created_by
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_definition
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_incomplete
import com.vitorpamplona.amethyst.commons.resources.kc_list_theme_title_font
import com.vitorpamplona.amethyst.commons.resources.kc_list_top_eight
import com.vitorpamplona.amethyst.commons.resources.kind_card_private_pill
import com.vitorpamplona.amethyst.commons.resources.nests_servers_auth_label
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadUrlPreview
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.GeocacheHuntRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.gallery.toMediaContents
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.Bolt12OfferChip
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.IdentityClaimRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.PaymentTargetChip
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.badges.BadgeThumb
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.util.isWebLink
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.postingStreak.PostingStreakEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.profileTheme.active.ActiveProfileThemeEvent
import com.vitorpamplona.quartz.experimental.profileTheme.definition.ThemeDefinitionEvent
import com.vitorpamplona.quartz.experimental.profileTheme.tags.BackgroundTag
import com.vitorpamplona.quartz.experimental.profileTheme.tags.FontTag
import com.vitorpamplona.quartz.experimental.profileTheme.tags.ThemeColors
import com.vitorpamplona.quartz.experimental.topEight.TopEightEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip01Core.tags.references.HttpUrlFormatter
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiListEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.stickers.StickerPackEvent
import com.vitorpamplona.quartz.nip38UserStatus.UserStatusEvent
import com.vitorpamplona.quartz.nip39ExtIdentities.ExternalIdentitiesEvent
import com.vitorpamplona.quartz.nip39ExtIdentities.identityClaims
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip51Lists.favoriteFollowSetsList.FavoriteFollowSetsListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServersEvent
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTargetsEvent
import com.vitorpamplona.quartz.nipB0WebBookmarks.WebBookmarkEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.nipCCGeocaching.curation.GeocacheCurationListEvent
import com.vitorpamplona.quartz.nipF4Podcasts.authored.AuthoredPodcastsEvent
import com.vitorpamplona.quartz.nipF4Podcasts.favorites.FavoritePodcastsListEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.PluralStringResource

/*
 * Cards for the replaceable lists and profile extras a user keeps about themselves: the servers
 * they upload to, the ways to pay them, the badges and themes they wear, their status and streak.
 * These used to fall through to the text-note renderer, which printed an empty content (or a
 * stray description) and hid the tags that are the whole point of the event.
 *
 * Encrypted halves of NIP-51-style lists are never decrypted here: a feed card shows what the
 * author published in the clear and says when there is more it cannot show.
 */

private const val MAX_BADGE_THUMBS = 16
private const val MAX_STICKERS = 24
private const val MAX_QUOTES = 2
private const val MAX_NAME_CHIPS = 6
private const val MAX_IDENTITIES = 6
private val StickerSize = 64.dp

// ---------------------------------------------------------------------------------------------
// Shared list frame
// ---------------------------------------------------------------------------------------------

/**
 * A [KindCard] for a list: its name, how many public entries it holds (nothing when it holds
 * none), and a "Private" pill when part of it is encrypted for its author. The [body] shows a
 * short sample of the entries, never all of them: a feed card is a glance, the list screen pages.
 */
@Composable
private fun ListKindCard(
    icon: MaterialSymbol,
    title: String,
    subtitle: String?,
    hasEntries: Boolean,
    hasPrivateItems: Boolean = false,
    tone: KindTone = KindTone.Primary,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    KindCard(
        icon = icon,
        title = title,
        subtitle = subtitle,
        tone = tone,
        status = if (hasPrivateItems) stringRes(Res.string.kind_card_private_pill) else null,
        statusTone = if (hasPrivateItems) KindTone.Neutral else null,
        // An empty body still takes the frame's gap, so an empty list draws no body at all.
        body = if (hasEntries) body else null,
    )
}

/** "3 servers", "1 entry", or nothing for an empty list: an empty list says so by having no body. */
@Composable
private fun countLine(
    plural: PluralStringResource,
    count: Int,
): String? = if (count == 0) null else pluralStringRes(plural, count, count)

/**
 * The first [MAX_QUOTES] events at [addresses] as compact quotes (a missing one degrades to a
 * one-line reference), then a "+N more" line for the rest.
 */
@Composable
private fun QuotedAddresses(
    addresses: List<Address>,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (quotesLeft <= 0 || addresses.isEmpty()) return
    addresses.take(MAX_QUOTES).forEach { address ->
        key(address.toValue()) {
            KindCardQuoteAddress(address, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
    val rest = addresses.size - MAX_QUOTES
    if (rest > 0) {
        KindCardMore(rest)
    }
}

// ---------------------------------------------------------------------------------------------
// Emoji and stickers
// ---------------------------------------------------------------------------------------------

/**
 * NIP-51 kind 10030: the emoji packs this user picked. A pack's d-tag is its name, so the packs
 * show as name chips without loading nine pack events for one feed card.
 */
@Composable
fun RenderEmojiList(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? EmojiListEvent ?: return
    val packs = remember(event) { event.emojiPacks() }
    val names = remember(packs) { packs.mapNotNull { it.dTag.ifBlank { null } } }

    ListKindCard(
        icon = MaterialSymbols.EmojiEmotions,
        title = kindName(event.kind),
        subtitle = countLine(Res.plurals.kc_list_emoji_pack_count, packs.size),
        hasEntries = names.isNotEmpty(),
    ) {
        KindCardChips(names, max = MAX_NAME_CHIPS)
    }
}

/** Kind 30031: a sticker pack — its name, description, and a grid of its safe-for-work stickers. */
@Composable
fun RenderStickerPack(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? StickerPackEvent ?: return

    val stickers = remember(event) { event.stickers() }
    val safe = remember(stickers) { stickers.filter { !it.isNsfw() } }
    val hiddenCount = stickers.size - safe.size
    val description = remember(event) { event.description() }

    KindCard(
        icon = MaterialSymbols.AddReaction,
        title = event.title()?.ifBlank { null } ?: event.dTag().ifBlank { null } ?: stringRes(Res.string.kc_list_sticker_pack),
        subtitle = countLine(Res.plurals.kc_list_sticker_count, stickers.size),
    ) {
        KindCardText(baseNote, description, backgroundColor, accountViewModel, nav)

        if (accountViewModel.settings.showImages()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                safe.take(MAX_STICKERS).forEach { sticker ->
                    AsyncImage(
                        model = sticker.url,
                        contentDescription = sticker.description ?: sticker.code,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(StickerSize).clip(RoundedCornerShape(8.dp)),
                    )
                }
                if (safe.size > MAX_STICKERS) {
                    MoreCountBox(safe.size - MAX_STICKERS, StickerSize)
                }
            }
        } else {
            KindCardChips(safe.take(MAX_STICKERS).map { ":${it.code}:" })
        }

        if (hiddenCount > 0) {
            KindCardMeta(listOf(pluralStringRes(Res.plurals.kc_list_stickers_sensitive_hidden, hiddenCount, hiddenCount)))
        }
    }
}

@Composable
private fun MoreCountBox(
    count: Int,
    size: Dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringRes(Res.string.kc_list_and_more, count),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Badges
// ---------------------------------------------------------------------------------------------

/** NIP-58 kind 10008: the badges this user chose to show on their profile, as a strip of images. */
@Composable
fun RenderProfileBadges(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ProfileBadgesEvent ?: return

    val definitions = remember(event) { event.badgeAwardDefinitions() }
    val sets = remember(event) { event.badgeSets().map { it.dTag } }

    ListKindCard(
        icon = MaterialSymbols.MilitaryTech,
        title = kindName(event.kind),
        subtitle = countLine(Res.plurals.kc_list_badge_count, definitions.size),
        hasEntries = definitions.isNotEmpty() || sets.isNotEmpty(),
    ) {
        BadgeThumbStrip(definitions, accountViewModel, nav)
        if (sets.isNotEmpty()) {
            KindCardSection(stringRes(Res.string.kc_list_badge_sets))
            KindCardChips(sets.filter { it.isNotBlank() }, max = MAX_NAME_CHIPS)
        }
    }
}

/**
 * NIP-58 kind 30008: either the legacy `profile_badges` list (the old form of kind 10008) or a
 * named NIP-51 badge set with its own title and description.
 */
@Composable
fun RenderAcceptedBadgeSet(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? AcceptedBadgeSetEvent ?: return

    val definitions = remember(event) { event.badgeAwardDefinitions() }
    val title =
        if (event.isLegacyProfileBadges()) {
            kindName(event.kind)
        } else {
            event.title()?.ifBlank { null } ?: event.dTag().ifBlank { null } ?: kindName(event.kind)
        }

    KindCard(
        icon = MaterialSymbols.MilitaryTech,
        title = title,
        subtitle = countLine(Res.plurals.kc_list_badge_count, definitions.size),
    ) {
        if (!event.isLegacyProfileBadges()) {
            KindCardText(baseNote, event.description(), backgroundColor, accountViewModel, nav)
        }
        BadgeThumbStrip(definitions, accountViewModel, nav)
    }
}

/** The badge definitions at [definitions] as clickable thumbnails, capped at [MAX_BADGE_THUMBS]. */
@Composable
private fun BadgeThumbStrip(
    definitions: List<Address>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (definitions.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        definitions.take(MAX_BADGE_THUMBS).forEach { address ->
            key(address.toValue()) {
                LoadAddressableNote(address) { note ->
                    note?.let { BadgeThumb(it, accountViewModel, nav) }
                }
            }
        }
        if (definitions.size > MAX_BADGE_THUMBS) {
            Text(
                text = stringRes(Res.string.kc_list_and_more, definitions.size - MAX_BADGE_THUMBS),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Server and relay lists
// ---------------------------------------------------------------------------------------------

/** Kind 10063: the Blossom servers this user uploads media to. */
@Composable
fun RenderBlossomServers(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
) {
    val event = baseNote.event as? BlossomServersEvent ?: return
    val servers = remember(event) { event.servers().map { HttpUrlFormatter.displayUrl(it) } }
    ServerListCard(MaterialSymbols.CloudUpload, kindName(event.kind), servers)
}

/** NIP-96 kind 10096: the HTTP file storage servers this user uploads to. */
@Composable
fun RenderFileServers(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
) {
    val event = baseNote.event as? FileServersEvent ?: return
    val servers = remember(event) { event.servers().map { HttpUrlFormatter.displayUrl(it) } }
    ServerListCard(MaterialSymbols.Storage, kindName(event.kind), servers)
}

/**
 * The shape every server and relay list shares: the count under the title and the hosts as chips.
 * These lists are settings the author's apps read, so they sit in the neutral tone.
 */
@Composable
private fun ServerListCard(
    icon: MaterialSymbol,
    title: String,
    servers: List<String>,
    countPlural: PluralStringResource = Res.plurals.kc_list_server_count,
    hasPrivateItems: Boolean = false,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    ListKindCard(
        icon = icon,
        title = title,
        subtitle = countLine(countPlural, servers.size),
        hasEntries = servers.isNotEmpty(),
        hasPrivateItems = hasPrivateItems,
        tone = KindTone.Neutral,
    ) {
        KindCardChips(servers)
        extra?.invoke(this)
    }
}

/** Kind 10112: the audio-room (nests) MoQ servers this user hosts rooms on, each with its auth host. */
@Composable
fun RenderNestsServers(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
) {
    val event = baseNote.event as? NestsServersEvent ?: return
    val servers = remember(event) { event.servers() }
    val hosts = remember(servers) { servers.map { HttpUrlFormatter.displayUrl(it.relay) } }
    val authHosts = remember(servers) { servers.map { HttpUrlFormatter.displayUrl(it.auth) }.filter { it.isNotBlank() }.distinct() }

    ServerListCard(MaterialSymbols.Mic, kindName(event.kind), hosts) {
        KindCardField(stringRes(Res.string.nests_servers_auth_label), authHosts.joinToString(", "), maxLines = 2)
    }
}

// ---------------------------------------------------------------------------------------------
// Payments
// ---------------------------------------------------------------------------------------------

/** NIP-61 kind 10019: the mints, relays and locking key a user accepts nutzaps with. */
@Composable
fun RenderNutzapInfo(baseNote: Note) {
    val event = baseNote.event as? NutzapInfoEvent ?: return

    val mints = remember(event) { event.mints() }
    val mintLabels =
        remember(mints) {
            mints.map { mint ->
                val host = HttpUrlFormatter.displayUrl(mint.mintUrl)
                if (mint.units.isEmpty()) host else "$host · ${mint.units.joinToString(", ")}"
            }
        }
    val relays = remember(event) { event.relays().map { it.displayUrl() } }
    val p2pk = remember(event) { event.p2pkPubkey() }
    val accepts = mints.isNotEmpty() && p2pk != null

    KindCard(
        icon = MaterialSymbols.Paid,
        title = stringRes(if (accepts) Res.string.kc_list_nutzap_info_on else Res.string.kc_list_nutzap_info_off),
        tone = if (accepts) KindTone.Money else KindTone.Neutral,
    ) {
        if (mints.isNotEmpty()) {
            KindCardSection(stringRes(Res.string.cashu_mints))
            KindCardChips(mintLabels)
        }
        if (relays.isNotEmpty()) {
            KindCardSection(stringRes(Res.string.kc_list_relays))
            KindCardChips(relays)
        }
        KindCardField(stringRes(Res.string.kc_list_p2pk_key), p2pk?.toShortDisplay(), monospace = true, maxLines = 1)
    }
}

/** NIP-A3 kind 10133: where to pay this user, as the same chips the profile shows. */
@Composable
fun RenderPaymentTargets(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? PaymentTargetsEvent ?: return
    val author = baseNote.author ?: return
    val targets = remember(event) { event.paymentTargets() }

    ListKindCard(
        icon = MaterialSymbols.Payment,
        title = kindName(event.kind),
        subtitle = countLine(Res.plurals.kc_list_entry_count, targets.size),
        hasEntries = targets.isNotEmpty(),
        tone = KindTone.Money,
    ) {
        if (targets.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                targets.forEach { target -> PaymentTargetChip(author, target, accountViewModel, nav) }
            }
        }
    }
}

/** NIP-B1 kind 10058: the BOLT12 offers this user can be paid at; a chip opens the pay/copy dialog. */
@Composable
fun RenderBolt12OfferList(
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    val event = baseNote.event as? Bolt12OfferListEvent ?: return
    val offers = remember(event) { event.offers() }

    ListKindCard(
        icon = MaterialSymbols.Bolt,
        title = stringRes(Res.string.bolt12_offers),
        subtitle = countLine(Res.plurals.kc_list_entry_count, offers.size),
        hasEntries = offers.isNotEmpty(),
        tone = KindTone.Money,
    ) {
        if (offers.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                offers.forEach { offer -> Bolt12OfferChip(offer, accountViewModel) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Lists of things to follow
// ---------------------------------------------------------------------------------------------

/** NIP-F4 kind 10054: the podcasts this user listens to; the private half stays encrypted. */
@Composable
fun RenderFavoritePodcasts(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? FavoritePodcastsListEvent ?: return
    val podcasts = remember(event) { event.publicFavorites().map { it.pubKey } }
    PodcastListCard(kindName(event.kind), podcasts, event.content.isNotEmpty(), quotesLeft, backgroundColor, accountViewModel, nav)
}

/** NIP-F4 kind 10064: the podcasts this user says they make, confirming each show's author claim. */
@Composable
fun RenderAuthoredPodcasts(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? AuthoredPodcastsEvent ?: return
    val podcasts = remember(event) { event.authoredKeys() }
    PodcastListCard(kindName(event.kind), podcasts, false, quotesLeft, backgroundColor, accountViewModel, nav)
}

@Composable
private fun PodcastListCard(
    title: String,
    podcastKeys: List<HexKey>,
    hasPrivateItems: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val addresses = remember(podcastKeys) { podcastKeys.map { PodcastMetadataEvent.createAddress(it) } }

    ListKindCard(
        icon = MaterialSymbols.Podcasts,
        title = title,
        subtitle = countLine(Res.plurals.kc_list_podcast_count, podcastKeys.size),
        hasEntries = podcastKeys.isNotEmpty() && quotesLeft > 0,
        hasPrivateItems = hasPrivateItems,
    ) {
        QuotedAddresses(addresses, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

/** NIP-51 kind 10021: other people's (or the user's own) follow sets kept at hand. */
@Composable
fun RenderFavoriteFollowSets(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? FavoriteFollowSetsListEvent ?: return
    val sets = remember(event) { event.publicFavoriteFollowSets().map { it.address } }

    ListKindCard(
        icon = MaterialSymbols.Groups,
        title = stringRes(Res.string.kc_list_favorite_follow_sets),
        subtitle = countLine(Res.plurals.kc_list_follow_set_count, sets.size),
        hasEntries = sets.isNotEmpty() && quotesLeft > 0,
        hasPrivateItems = event.content.isNotEmpty(),
    ) {
        QuotedAddresses(sets, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

/**
 * NIP-72 kind 10004: the moderated communities this user follows. A community's d-tag is its
 * name, so they show as name chips without loading every community definition for a feed card.
 */
@Composable
fun RenderCommunityList(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CommunityListEvent ?: return
    val communities = remember(event) { event.publicCommunities() }
    val names = remember(communities) { communities.mapNotNull { it.address.dTag.ifBlank { null } } }

    ListKindCard(
        icon = MaterialSymbols.Forum,
        title = kindName(event.kind),
        subtitle = countLine(Res.plurals.kc_list_community_count, communities.size),
        hasEntries = names.isNotEmpty(),
        hasPrivateItems = event.content.isNotEmpty(),
    ) {
        KindCardChips(names, max = MAX_NAME_CHIPS)
    }
}

/** Kind 18678: the eight people this user ranks first on their profile, as chips in rank order. */
@Composable
fun RenderTopEight(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? TopEightEvent ?: return
    val ranked = remember(event) { event.rankedKeys() }

    ListKindCard(
        icon = MaterialSymbols.Star,
        title = stringRes(Res.string.kc_list_top_eight),
        subtitle = null,
        hasEntries = ranked.isNotEmpty(),
    ) {
        KindCardUsers(ranked, accountViewModel, nav, max = 8)
    }
}

// ---------------------------------------------------------------------------------------------
// Identity and presence
// ---------------------------------------------------------------------------------------------

/** NIP-39 kind 10011: accounts on other platforms this user claims, each linking to its proof. */
@Composable
fun RenderExternalIdentities(baseNote: Note) {
    val event = baseNote.event as? ExternalIdentitiesEvent ?: return
    val claims = remember(event) { event.identityClaims() }

    ListKindCard(
        icon = MaterialSymbols.AlternateEmail,
        title = stringRes(Res.string.kc_list_external_identities),
        subtitle = countLine(Res.plurals.kc_list_entry_count, claims.size),
        hasEntries = claims.isNotEmpty(),
    ) {
        claims.take(MAX_IDENTITIES).forEach { claim -> IdentityClaimRow(claim, iconSize = 16.dp, textStyle = MaterialTheme.typography.bodySmall) }
        val rest = claims.size - MAX_IDENTITIES
        if (rest > 0) {
            KindCardMore(rest)
        }
    }
}

/**
 * NIP-38 kind 30315: what the user is up to (general) or listening to (music), with the link or
 * the note, profile or address the status points at, and when it expires.
 */
@Composable
fun RenderUserStatus(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? UserStatusEvent ?: return

    val type = remember(event) { event.dTag() }
    val isCleared = remember(event) { event.isCleared() }
    val text =
        remember(event) {
            val emoji = event.statusEmoji()
            when {
                emoji == null -> event.content
                event.content.isBlank() -> emoji
                else -> "$emoji ${event.content}"
            }
        }
    val url = remember(event) { event.firstTaggedUrl()?.ifBlank { null } }
    val expiration = remember(event) { event.expiration() }
    val profile = remember(event) { event.referencedProfile() }
    val eventId = remember(event) { event.referencedEventId() }
    val address = remember(event) { event.referencedAddressId()?.let { Address.parse(it) } }

    val isExpired = rememberAtNow(event) { now -> expiration != null && expiration < now }
    val isMusic = type == UserStatusEvent.MUSIC

    val title =
        when {
            isCleared -> stringRes(Res.string.kc_list_status_cleared)
            isMusic -> stringRes(Res.string.kc_list_status_music)
            else -> stringRes(Res.string.kc_list_status_general)
        }
    // A custom status type (an app's own d-tag) names the subject, so it goes under the title.
    val subtitle = type.takeIf { it.isNotBlank() && it != UserStatusEvent.GENERAL && !isMusic }

    KindCard(
        icon = if (isMusic) MaterialSymbols.MusicNote else MaterialSymbols.Face,
        title = title,
        subtitle = subtitle,
        tone = if (isCleared || isExpired) KindTone.Neutral else KindTone.Primary,
        status = if (isExpired && !isCleared) stringRes(Res.string.kc_list_status_expired_pill) else null,
    ) {
        if (!isCleared) {
            KindCardText(baseNote, text, backgroundColor, accountViewModel, nav)
        }
        KindCardField(stringRes(Res.string.kc_list_link), url, maxLines = 1)
        if (!isExpired) {
            KindCardField(
                stringRes(Res.string.kc_list_expires),
                expiration?.let { formatDateTime(it * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT) },
            )
        }
        profile?.let { KindCardUsers(listOf(it), accountViewModel, nav) }
        when {
            eventId != null -> KindCardQuoteEvent(eventId, quotesLeft, backgroundColor, accountViewModel, nav)
            address != null -> KindCardQuoteAddress(address, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

/** Kind 13473: the author's self-reported run of daily posts, live or broken. */
@Composable
fun RenderPostingStreak(baseNote: Note) {
    val event = baseNote.event as? PostingStreakEvent ?: return

    val streak = remember(event) { event.streak() }

    if (streak == null) {
        KindCard(
            icon = MaterialSymbols.LocalFireDepartment,
            title = stringRes(Res.string.kc_list_streak_title),
            subtitle = stringRes(Res.string.kc_list_streak_invalid),
            tone = KindTone.Neutral,
        )
        return
    }

    val isLive = rememberAtNow(streak) { now -> streak.isLive(now) }
    val days = streak.days().toInt()

    KindCard(
        icon = MaterialSymbols.LocalFireDepartment,
        title = pluralStringRes(Res.plurals.kc_list_streak_days, days, days),
        // The flame stays orange while the streak burns; an ended one goes quiet.
        iconTint = if (isLive) BitcoinOrange else null,
        tone = if (isLive) KindTone.Primary else KindTone.Neutral,
        status = stringRes(if (isLive) Res.string.kc_list_streak_pill_live else Res.string.kc_list_streak_pill_ended),
    ) {
        KindCardField(
            stringRes(Res.string.kc_list_streak_started),
            formatDateTime(streak.start * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.NONE),
        )
        KindCardField(
            stringRes(Res.string.kc_list_streak_last_post),
            formatDateTime(streak.end * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT),
        )
        if (isLive) {
            KindCardField(
                stringRes(Res.string.kc_list_expires),
                formatDateTime(streak.expiresAt() * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Profile themes
// ---------------------------------------------------------------------------------------------

/** What the two Ditto theme kinds share, read once so one card draws both. */
private class ThemeInfo(
    val colors: ThemeColors?,
    val title: String?,
    val description: String?,
    val titleFont: FontTag?,
    val bodyFont: FontTag?,
    val background: BackgroundTag?,
)

/** Kind 16767: the theme this user's profile is shown in, credited to its creator when worn from someone else. */
@Composable
fun RenderActiveProfileTheme(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ActiveProfileThemeEvent ?: return
    val info =
        remember(event) {
            ThemeInfo(event.colors(), event.title(), event.description(), event.titleFont(), event.bodyFont(), event.background())
        }
    val creator = remember(event) { if (event.isWornFromAnotherUser()) event.creatorPubKey() else null }

    ThemeCard(baseNote, stringRes(Res.string.kc_list_theme_active), info, creator, backgroundColor, accountViewModel, nav)
}

/** Kind 36767: a theme someone published for others to wear. */
@Composable
fun RenderThemeDefinition(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ThemeDefinitionEvent ?: return
    val info =
        remember(event) {
            ThemeInfo(event.colors(), event.title(), event.description(), event.titleFont(), event.bodyFont(), event.background())
        }
    val creator = remember(event) { event.creator()?.pubKey?.takeIf { it != event.pubKey } }

    ThemeCard(baseNote, stringRes(Res.string.kc_list_theme_definition), info, creator, backgroundColor, accountViewModel, nav)
}

@Composable
private fun ThemeCard(
    baseNote: Note,
    kindLabel: String,
    info: ThemeInfo,
    creator: HexKey?,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val themeTitle = info.title?.ifBlank { null }

    KindCard(
        icon = MaterialSymbols.BrightnessMedium,
        title = kindLabel,
        // The swatch already draws the theme's name large, so the header only needs to say which.
        subtitle = if (info.colors == null) stringRes(Res.string.kc_list_theme_incomplete) else themeTitle,
    ) {
        info.colors?.let { ThemeSwatch(it, themeTitle ?: kindLabel) }
        KindCardText(baseNote, info.description, backgroundColor, accountViewModel, nav)
        info.colors?.let {
            KindCardField(
                stringRes(Res.string.kc_list_theme_colors),
                "${it.background.toHex()}  ${it.text.toHex()}  ${it.primary.toHex()}",
                monospace = true,
                maxLines = 1,
            )
        }
        KindCardField(stringRes(Res.string.kc_list_theme_title_font), info.titleFont?.family, maxLines = 1)
        KindCardField(stringRes(Res.string.kc_list_theme_body_font), info.bodyFont?.family, maxLines = 1)
        info.background?.let {
            KindCardField(
                stringRes(Res.string.kc_list_theme_background),
                stringRes(if (it.isVideo()) Res.string.kc_list_theme_background_video else Res.string.kc_list_theme_background_image),
            )
        }
        if (creator != null) {
            KindCardSection(stringRes(Res.string.kc_list_theme_created_by))
            KindCardUsers(listOf(creator), accountViewModel, nav)
        }
    }
}

/** A small preview of the theme: its title in the text color on the background, beside a primary-color pill. */
@Composable
private fun ThemeSwatch(
    colors: ThemeColors,
    label: String,
) {
    val shape = RoundedCornerShape(12.dp)
    val background = remember(colors) { Color(colors.background.toArgb()) }
    val text = remember(colors) { Color(colors.text.toArgb()) }
    val primary = remember(colors) { Color(colors.primary.toArgb()) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(background)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = text,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(width = 40.dp, height = 20.dp)
                .clip(RoundedCornerShape(50))
                .background(primary),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Gallery, bookmarks, hunts
// ---------------------------------------------------------------------------------------------

/**
 * Kind 1163: a picture the author pinned to their profile gallery. The picture and its caption;
 * the post it was taken from is quoted only when the entry carries no picture of its own, so the
 * same image is not drawn twice.
 */
@Composable
fun RenderProfileGalleryEntry(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ProfileGalleryEntryEvent ?: return

    val media = remember(event) { event.toMediaContents(withHash = true, uri = baseNote.toNostrUri()).toImmutableList() }
    val summary = remember(event) { event.summary() }
    val fromEvent = remember(event) { event.fromEvent() }

    KindCard(
        icon = MaterialSymbols.PhotoLibrary,
        title = stringRes(Res.string.kc_list_gallery_entry),
    ) {
        // One content-warning gate for the picture and its caption, so one tap reveals both.
        SensitivityWarning(note = baseNote, accountViewModel = accountViewModel) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                media.firstOrNull()?.let { first ->
                    ZoomableContentView(
                        content = first,
                        images = media,
                        roundedCorner = true,
                        contentScale = ContentScale.FillWidth,
                        accountViewModel = accountViewModel,
                    )
                }
                KindCardText(baseNote, summary, backgroundColor, accountViewModel, nav, gated = false)
            }
        }
        if (media.isEmpty() && fromEvent != null) {
            KindCardQuoteEvent(fromEvent, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

/** NIP-B0 kind 39701: a saved web page — its title, the link (with a preview), the note and labels. */
@Composable
fun RenderWebBookmark(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? WebBookmarkEvent ?: return

    val url = remember(event) { event.url() }
    val shownUrl = remember(url) { HttpUrlFormatter.displayUrl(url) }
    val pageTitle = remember(event) { event.title()?.ifBlank { null } }
    val hashtags = remember(event) { event.hashtags().map { "#$it" } }

    KindCard(
        icon = MaterialSymbols.Bookmark,
        title = pageTitle ?: shownUrl.ifBlank { null } ?: kindName(event.kind),
        subtitle = if (pageTitle != null) shownUrl else null,
    ) {
        if (url.isNotBlank()) {
            if (isWebLink(url) && kindCardCanPreview() && accountViewModel.settings.showUrlPreview()) {
                LoadUrlPreview(url, shownUrl, null, accountViewModel, nav)
            } else {
                ClickableUrl(url = url, displayText = shownUrl, style = MaterialTheme.typography.bodySmall)
            }
        }
        KindCardText(baseNote, event.description(), backgroundColor, accountViewModel, nav)
        KindCardChips(hashtags)
    }
}

/** NIP-CC kind 37517: a geocache hunt, drawn as the same row the Hunts tab uses, with progress. */
@Composable
fun RenderGeocacheCurationList(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GeocacheCurationListEvent ?: return

    KindCard(
        icon = MaterialSymbols.Hiking,
        title = stringRes(Res.string.kc_list_geocache_hunt),
    ) {
        GeocacheHuntRow(baseNote, event, accountViewModel, nav)
    }
}
