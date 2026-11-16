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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.subscriptions

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.aroundMe.LocationTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.favoriteAlgoFeeds.FavoriteAlgoFeedTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurposeGroup
import com.vitorpamplona.amethyst.commons.relays.subscriptions.ActiveSubscriptionsState
import com.vitorpamplona.amethyst.commons.relays.subscriptions.ActiveSubscriptionsViewModel
import com.vitorpamplona.amethyst.commons.relays.subscriptions.SubscriptionAccountRow
import com.vitorpamplona.amethyst.commons.relays.subscriptions.SubscriptionEntityRow
import com.vitorpamplona.amethyst.commons.relays.subscriptions.SubscriptionPurposeRow
import com.vitorpamplona.amethyst.commons.relays.ui.SubPurposeLabels
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.active_subs_filters
import com.vitorpamplona.amethyst.commons.resources.active_subs_groups
import com.vitorpamplona.amethyst.commons.resources.active_subs_no_entity
import com.vitorpamplona.amethyst.commons.resources.active_subs_relays
import com.vitorpamplona.amethyst.commons.resources.active_subs_scope_algo
import com.vitorpamplona.amethyst.commons.resources.active_subs_scope_all_communities
import com.vitorpamplona.amethyst.commons.resources.active_subs_scope_authors
import com.vitorpamplona.amethyst.commons.resources.active_subs_scope_follows
import com.vitorpamplona.amethyst.commons.resources.active_subs_scope_global
import com.vitorpamplona.amethyst.commons.resources.active_subs_scope_muted
import com.vitorpamplona.amethyst.commons.resources.active_subs_share
import com.vitorpamplona.amethyst.commons.resources.active_subs_title
import com.vitorpamplona.amethyst.commons.resources.active_subs_unattributed
import com.vitorpamplona.amethyst.commons.resources.active_subs_untagged
import com.vitorpamplona.amethyst.commons.resources.marmot_group_fallback_name
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadCityName
import com.vitorpamplona.amethyst.commons.ui.platform.rememberConcordImageModel
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.loadMarmotRelayIcon
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.rememberMarmotGroupAvatarUrl
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.RelayIconFilter
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlin.math.roundToInt

/**
 * Explains why the app is holding the number of subscriptions it currently holds.
 *
 * Pivoted on purpose rather than relay: the relay-shaped view hides exactly the thing worth finding,
 * because a probe holding hundreds of filters across hundreds of relays looks like one ordinary row
 * repeated hundreds of times. Here it is one card whose share bar runs the full width while
 * everything under it is a stub.
 *
 * Account is the outer grouping — several are normally logged in, they do not share relay sets, and
 * a mixed total cannot be acted on.
 */
@Composable
fun ActiveSubscriptionsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
    viewModel: ActiveSubscriptionsViewModel = rememberViewModel { ActiveSubscriptionsViewModel() },
) {
    LaunchedEffect(Unit) { viewModel.pollWhileShown(accountViewModel.account.client) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.active_subs_title), nav) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { TotalsHeader(state) }

            state.accounts.forEach { account ->
                item(key = "acct-${account.accountPubKey ?: "none"}") { AccountHeader(account) }

                items(account.purposes, key = { "${account.accountPubKey}-${it.purpose.name}" }) { purpose ->
                    PurposeCard(purpose, state.attributedFilters, accountViewModel, nav)
                }
            }
        }
    }
}

@Composable
private fun TotalsHeader(state: ActiveSubscriptionsState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = pluralStringRes(Res.plurals.active_subs_filters, state.totalFilters, state.totalFilters),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = pluralStringRes(Res.plurals.active_subs_relays, state.totalRelays, state.totalRelays),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.untaggedFilters > 0) {
                // Stated rather than hidden: an untagged filter is a subscription this screen cannot
                // explain, and a total that claims to be fully attributed would defeat the point.
                Spacer(Modifier.height(6.dp))
                Text(
                    text = pluralStringRes(Res.plurals.active_subs_untagged, state.untaggedFilters, state.untaggedFilters),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            }
        }
    }
}

@Composable
private fun AccountHeader(account: SubscriptionAccountRow) {
    val name = account.accountPubKey?.let { displayNameOf(it) } ?: stringRes(Res.string.active_subs_unattributed)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 2.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = pluralStringRes(Res.plurals.active_subs_relays, account.relays.size, account.relays.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.placeholderText,
        )
    }
}

@Composable
private fun PurposeCard(
    row: SubscriptionPurposeRow,
    /**
     * The sum of the per-account counts, not the wire total: a merged filter serving four accounts
     * contributes to four cards, so dividing by the wire total would overstate every one of them.
     */
    attributedFilters: Int,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var expanded by rememberSaveable(row.purpose) { mutableStateOf(false) }
    val accent = colorOf(row.purpose.group)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .animateContentSize(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringRes(SubPurposeLabels.labelOf(row.purpose)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = pluralStringRes(Res.plurals.active_subs_filters, row.filterCount, row.filterCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Share of ALL subscriptions, not of the biggest one: measuring against the biggest
            // makes one bar permanently full and says nothing about how much of the app's traffic a
            // purpose actually accounts for.
            val share = if (attributedFilters > 0) row.filterCount / attributedFilters.toFloat() else 0f
            ShareBar(fraction = share, color = accent)

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = pluralStringRes(Res.plurals.active_subs_relays, row.relays.size, row.relays.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringRes(Res.string.active_subs_share, (share * 100).roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            }

            if (expanded) {
                SubPurposeLabels.explainerOf(row.purpose)?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringRes(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Relay-hosted purposes pivot on the relay instead of listing every entity flat: a
                // NIP-29 group is keyed by (id, host relay), and one community relay routinely hosts
                // many of them — measured here, 13 groups across 5 relays, with one hostname repeated
                // seven times. Grouping folds that repetition away and answers the question the screen
                // exists for: *this* relay is connected because of *these* groups.
                if (row.purpose == SubPurpose.RELAY_GROUPS) {
                    Spacer(Modifier.height(4.dp))
                    RelayGroupedEntities(row.entities, accountViewModel, nav)
                } else {
                    // Community Chats drops its unnamed row: Concord filters that name no community
                    // are the plane-level machinery (control, guestbook, rekey), and rendering them as
                    // a nameless "All" alongside real communities read as an unexplained extra rather
                    // than as detail. The card's own filter count still includes them, so nothing is
                    // lost from the totals — only from the per-entity list.
                    val shown =
                        if (row.purpose == SubPurpose.COMMUNITY_CHATS) {
                            row.entities.filter { it.entityId != null }
                        } else {
                            row.entities
                        }

                    shown.forEach { entity ->
                        Spacer(Modifier.height(12.dp))
                        EntityBlock(entity, accountViewModel, nav)
                    }
                }
            }
        }
    }
}

/**
 * The feed selection a discovery filter is searching within, in the app's own words.
 *
 * Hashtags and geohashes read out their own values — they are short, and the whole point is *which*
 * hashtag. The author-based selections do not: a follow list is thousands of keys, and per relay it
 * is a different slice of them, so naming the kind is the honest summary.
 */
@Composable
private fun scopeLabel(scope: IFeedTopNavPerRelayFilter): String? =
    when (scope) {
        is GlobalTopNavPerRelayFilter -> stringRes(Res.string.active_subs_scope_global)
        is AllFollowsTopNavPerRelayFilter -> stringRes(Res.string.active_subs_scope_follows)
        is AuthorsTopNavPerRelayFilter -> stringRes(Res.string.active_subs_scope_authors)
        is MutedAuthorsTopNavPerRelayFilter -> stringRes(Res.string.active_subs_scope_muted)
        is AllCommunitiesTopNavPerRelayFilter -> stringRes(Res.string.active_subs_scope_all_communities)
        is FavoriteAlgoFeedTopNavPerRelayFilter -> stringRes(Res.string.active_subs_scope_algo)
        is HashtagTopNavPerRelayFilter -> scope.hashtags.sorted().joinToString(", ") { "#$it" }
        is LocationTopNavPerRelayFilter -> scope.geotags.sorted().joinToString(", ")
        // The community and the relay already name themselves — the community through its own
        // entity row, the relay through the row's relay list — so repeating it here would be noise.
        else -> null
    }

/** The entity's name, coloured as a link only when tapping it actually goes somewhere. */
@Composable
private fun EntityLabelText(
    text: String,
    route: Route?,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = if (route != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * One row per host relay, expandable to the groups it carries.
 *
 * Collapsed by default because the relay is the thing that costs a connection — the question "why am
 * I talking to this host" is answered by the header alone, and the group list is the follow-up only
 * some people want. The count sits on the header so a busy relay is obvious without opening it.
 */
@Composable
private fun RelayGroupedEntities(
    entities: List<SubscriptionEntityRow>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // An entity can name several relays in principle; each pairing is its own row under that relay.
    val byRelay =
        remember(entities) {
            entities
                .flatMap { entity -> entity.relays.map { relay -> relay to entity } }
                .groupBy({ it.first }, { it.second })
                .toList()
                .sortedWith(compareByDescending<Pair<NormalizedRelayUrl, List<SubscriptionEntityRow>>> { it.second.size }.thenBy { it.first.url })
        }

    byRelay.forEach { (relay, hosted) ->
        var open by rememberSaveable(relay.url) { mutableStateOf(false) }

        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
        ) {
            RelayLine(relay, accountViewModel, modifier = Modifier.weight(1f))
            Text(
                text = pluralStringRes(Res.plurals.active_subs_groups, hosted.size, hosted.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        }

        if (open) {
            hosted.forEach { entity ->
                Spacer(Modifier.height(6.dp))
                // Indented, and without the relay repeated — it is the header directly above.
                Column(Modifier.padding(start = 28.dp)) {
                    EntityBlock(entity, accountViewModel, nav, showRelays = false)
                }
            }
        }
    }
}

@Composable
private fun ShareBar(
    fraction: Float,
    color: Color,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** One colour per family, so the eye groups Messages/Account/Feeds without reading labels. */
@Composable
private fun colorOf(group: SubPurposeGroup): Color =
    when (group) {
        SubPurposeGroup.MESSAGES -> MaterialTheme.colorScheme.primary
        SubPurposeGroup.ACCOUNT -> MaterialTheme.colorScheme.allGoodColor
        SubPurposeGroup.FEEDS -> MaterialTheme.colorScheme.tertiary
        SubPurposeGroup.CURRENT_SCREEN -> MaterialTheme.colorScheme.warningColor
        SubPurposeGroup.OTHER -> MaterialTheme.colorScheme.placeholderText
    }

@Composable
private fun EntityBlock(
    entity: SubscriptionEntityRow,
    accountViewModel: AccountViewModel,
    nav: INav,
    showRelays: Boolean = true,
) {
    // Marmot groups are resolved first and separately: their name and avatar live in per-account
    // StateFlows (and the avatar is an encrypted Blossom blob needing a cipher registration), so
    // they cannot come from the plain LocalCache lookup that serves every other entity type.
    val marmot = entity.entityId?.let { id -> rememberMarmotEntity(id, accountViewModel) }
    val concord = entity.entityId?.let { id -> rememberConcordEntity(id, accountViewModel) }
    val resolved = marmot ?: concord ?: entity.entityId?.let { id -> remember(id, entity.relays) { resolveEntity(id, entity.relays) } }
    // Deliberately not falling back to `entity.detail`: that field is a developer breadcrumb set in
    // `commons`, where Android string resources do not exist, so it is hardcoded English and could
    // never be translated. A localized "no entity" line is better than an untranslatable one.
    //
    // A discovery filter has no entity by nature — it searches for chats and articles rather than
    // serving ones already named — but it does carry the selection it searches within, which is a
    // far better answer than "no entity". The scope arrives as a typed value for exactly this
    // reason: the wording is chosen here, where translations exist.
    val label = resolved?.name ?: entity.scope?.let { scopeLabel(it) } ?: stringRes(Res.string.active_subs_no_entity)

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        // Only tappable when we know where it goes; a dead tap target is worse than none.
                        resolved?.route?.let { route -> Modifier.clickable { nav.nav(route) } } ?: Modifier,
                    ).padding(vertical = 2.dp),
        ) {
            if (resolved?.picture != null) {
                RobohashFallbackAsyncImage(
                    robot = entity.entityId ?: label,
                    model = resolved.picture,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp).clip(CircleShape),
                    loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                    loadRobohash = false,
                )
                Spacer(Modifier.width(8.dp))
            }
            // A geohash cell reads as a place everywhere else in the app, so it does here too. Same
            // helper the note headers and the geohash screen use, which means the same reverse-geocode
            // cache and the same graceful fallback to the raw cell on devices with no Geocoder backend.
            val cell = (resolved?.route as? Route.GeohashChat)?.geohash
            if (cell != null) {
                LoadCityName(
                    geohashStr = cell,
                    onLoading = { EntityLabelText(label, resolved.route) },
                ) { city ->
                    EntityLabelText("#" + city, resolved.route)
                }
            } else {
                EntityLabelText(label, resolved?.route)
            }
        }
        if (showRelays) {
            entity.relays.forEach { relay -> RelayLine(relay, accountViewModel) }
        }
    }
}

/** A relay with its real icon and the app's own relay-name colour, not a bare URL string. */
@Composable
private fun RelayLine(
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val info by loadRelayInfo(relay)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = 6.dp, start = 2.dp),
    ) {
        // Plain favicon, not RenderRelayIcon: that one overlays a ping-count badge which, at this
        // size, covers the icon entirely and reads as a random number next to every relay.
        RobohashFallbackAsyncImage(
            robot = relay.displayUrl(),
            model = info.icon,
            contentDescription = relay.displayUrl(),
            colorFilter = RelayIconFilter,
            modifier = Modifier.size(20.dp).clip(CircleShape),
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            loadRobohash = false,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = relay.displayUrl(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * What an entity id resolves to right now: a name, a picture, and where tapping it goes.
 *
 * Resolved at render time rather than baked into the filter — names and pictures arrive *after* the
 * subscription that needed them, so anything captured at filter-construction would usually be empty
 * and would go stale on rename.
 *
 * The relays matter: a NIP-29 group is keyed by [GroupId] (id **and** host relay), so the id alone
 * cannot find it. Those are exactly the rows that used to render as bare hex.
 */
private class ResolvedEntity(
    val name: String,
    val picture: String?,
    val route: Route?,
)

private fun resolveEntity(
    id: HexKey,
    relays: List<NormalizedRelayUrl>,
): ResolvedEntity {
    relays.forEach { relay ->
        LocalCache.getRelayGroupChannelIfExists(GroupId(id, relay))?.let {
            return ResolvedEntity(it.toBestDisplayName(), it.profilePicture(), Route.RelayGroup(id, relay.url))
        }
    }
    LocalCache.getPublicChatChannelIfExists(id)?.let {
        return ResolvedEntity(it.toBestDisplayName(), it.profilePicture(), Route.PublicChatChannel(id))
    }
    // Geohash cells: the id IS the cell, and it renders as "#cell" everywhere else in the app.
    LocalCache.getGeohashChannelIfExists(id)?.let {
        return ResolvedEntity(it.toBestDisplayName(), null, Route.GeohashChat(id))
    }
    LocalCache.getUserIfExists(id)?.let {
        return ResolvedEntity(it.toBestDisplayName(), it.profilePicture(), Route.Profile(id))
    }
    // Known-but-unresolvable (a Concord community, a mint) still shows something stable rather than
    // disappearing — a short id is honest about what we have.
    return ResolvedEntity(id.take(8), null, null)
}

/**
 * A Marmot (MLS) group, or null when [id] names no group this account knows.
 *
 * Kept apart from [resolveEntity] because none of it is a plain cache read: the name and avatar are
 * `StateFlow`s that fill in after the subscription starts, and the avatar is an encrypted Blossom
 * blob whose decryption cipher `rememberMarmotGroupIconUrl` registers as a side effect. Looking the
 * room up with `rooms.get` rather than `getOrCreateGroup` keeps an unknown id from minting an empty
 * room just because a diagnostics screen asked about it.
 */
@Composable
private fun rememberMarmotEntity(
    id: HexKey,
    accountViewModel: AccountViewModel,
): ResolvedEntity? {
    val chatroom =
        remember(id) {
            accountViewModel.account.marmotGroupList.rooms
                .get(id)
        } ?: return null

    val displayName by chatroom.displayName.collectAsStateWithLifecycle()
    val image by chatroom.image.collectAsStateWithLifecycle()
    val avatarUrl by chatroom.avatarUrl.collectAsStateWithLifecycle()
    val relays by chatroom.relays.collectAsStateWithLifecycle()
    val adminPubkeys by chatroom.adminPubkeys.collectAsStateWithLifecycle()

    // Same name/icon precedence the chat-rooms list uses, so a group reads identically in both places.
    val name = displayName?.takeIf { it.isNotBlank() } ?: stringRes(Res.string.marmot_group_fallback_name, id.take(8))
    val picture =
        if (avatarUrl != null || image != null) {
            rememberMarmotGroupAvatarUrl(avatarUrl, image, accountViewModel, adminPubkeys)
        } else {
            loadMarmotRelayIcon(relays)
        }

    return ResolvedEntity(name, picture, Route.MarmotGroupChat(id))
}

/**
 * A Concord community, or null when [id] names none this account has folded.
 *
 * Also not a cache read: the name and icon come from the folded Control Plane, and a fold is
 * announced by `concordSessions.revision` rather than by the session's own state, so the read has to
 * be keyed on that counter or the row keeps whatever it saw first. The icon is a CORD-02
 * [ImagePointer] — usually an encrypted blob — so it goes through `rememberConcordImageModel` rather
 * than being handed to Coil as a URL.
 *
 * Falls back to the joined-list entry's name, which is present before the first fold completes, and
 * returns null when neither knows the community so the caller can keep looking.
 */
@Composable
private fun rememberConcordEntity(
    id: HexKey,
    accountViewModel: AccountViewModel,
): ResolvedEntity? {
    val account = accountViewModel.account
    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    val communities by account.concordChannelList.liveCommunities.collectAsStateWithLifecycle()

    val metadata =
        remember(id, revision) {
            account.concordSessions
                .sessionFor(id)
                ?.state
                ?.value
                ?.metadata
        }
    val fallbackName = remember(communities, id) { communities.firstOrNull { it.id == id }?.name?.ifBlank { null } }

    // Resolved unconditionally: bailing out early would make the number of composable calls depend on
    // whether the community happens to be known yet, which changes as folds land.
    val model = rememberConcordImageModel(metadata?.icon, accountViewModel)
    val name = metadata?.name?.takeIf { it.isNotBlank() } ?: fallbackName

    return name?.let { ResolvedEntity(it, model, Route.ConcordServer(id)) }
}

@Composable
private fun displayNameOf(id: HexKey): String = remember(id) { resolveEntity(id, emptyList()).name }
