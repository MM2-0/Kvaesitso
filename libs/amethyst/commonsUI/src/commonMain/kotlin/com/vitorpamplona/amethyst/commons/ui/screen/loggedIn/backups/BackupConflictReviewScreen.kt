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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.backups.BackupEventType
import com.vitorpamplona.amethyst.commons.model.backups.ReplaceableBackupConflict
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.backup_action_block_again
import com.vitorpamplona.amethyst.commons.resources.backup_action_keep_count
import com.vitorpamplona.amethyst.commons.resources.backup_action_rejoin
import com.vitorpamplona.amethyst.commons.resources.backup_action_remute
import com.vitorpamplona.amethyst.commons.resources.backup_action_restore_count
import com.vitorpamplona.amethyst.commons.resources.backup_action_restore_feeds
import com.vitorpamplona.amethyst.commons.resources.backup_action_restore_places
import com.vitorpamplona.amethyst.commons.resources.backup_action_restore_profile
import com.vitorpamplona.amethyst.commons.resources.backup_action_restore_providers
import com.vitorpamplona.amethyst.commons.resources.backup_action_restore_relays
import com.vitorpamplona.amethyst.commons.resources.backup_action_restore_topics
import com.vitorpamplona.amethyst.commons.resources.backup_action_restore_wallet
import com.vitorpamplona.amethyst.commons.resources.backup_conflict_added
import com.vitorpamplona.amethyst.commons.resources.backup_conflict_changed
import com.vitorpamplona.amethyst.commons.resources.backup_conflict_keep_new
import com.vitorpamplona.amethyst.commons.resources.backup_conflict_private_added
import com.vitorpamplona.amethyst.commons.resources.backup_conflict_private_changed
import com.vitorpamplona.amethyst.commons.resources.backup_conflict_private_cleared
import com.vitorpamplona.amethyst.commons.resources.backup_conflict_removed
import com.vitorpamplona.amethyst.commons.resources.backup_conflict_restore_mine
import com.vitorpamplona.amethyst.commons.resources.backup_review_changed_by_other_app
import com.vitorpamplona.amethyst.commons.resources.backup_review_title_cleared
import com.vitorpamplona.amethyst.commons.resources.backup_review_title_updated
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.LoadPublicChatChannel
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListDiff
import com.vitorpamplona.quartz.nip01Core.diff.ContentChange
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataDiff
import com.vitorpamplona.quartz.nip02FollowList.ContactListDiff
import com.vitorpamplona.quartz.nip28PublicChat.list.PublicChatListDiff
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListDiff
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListDiff
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListDiff
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListDiff
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayListDiff
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListDiff
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletDiff
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListDiff
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListDiff
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListDiff
import org.jetbrains.compose.resources.StringResource

/** A row of the review list, flattened so hundreds of entries scroll lazily. */
@Immutable
internal sealed interface ReviewRow {
    class Section(
        val titleRes: StringResource,
        val count: Int,
        val tone: Tone,
    ) : ReviewRow

    class Note(
        val textRes: StringResource,
    ) : ReviewRow

    class Group(
        val labelRes: StringResource,
    ) : ReviewRow

    class Entry(
        val item: ReviewItem,
    ) : ReviewRow
}

internal enum class Tone { REMOVED, ADDED, CHANGED }

internal fun buildRows(
    presentation: DiffPresentation,
    skipGroups: Set<StringResource> = emptySet(),
): List<ReviewRow> {
    val rows = mutableListOf<ReviewRow>()

    fun section(
        titleRes: StringResource,
        count: Int,
        tone: Tone,
        contentNote: StringResource?,
        items: (DiffGroup) -> List<ReviewItem>,
    ) {
        if (count == 0 && contentNote == null) return
        rows.add(ReviewRow.Section(titleRes, count, tone))
        contentNote?.let { rows.add(ReviewRow.Note(it)) }
        presentation.groups.forEach { group ->
            if (group.label in skipGroups) return@forEach
            val entries = items(group)
            if (entries.isNotEmpty()) {
                rows.add(ReviewRow.Group(group.label))
                entries.forEach { rows.add(ReviewRow.Entry(it)) }
            }
        }
    }

    val content = presentation.content
    section(Res.string.backup_conflict_removed, presentation.removedCount, Tone.REMOVED, Res.string.backup_conflict_private_cleared.takeIf { content == ContentChange.CLEARED }) { it.removed }
    section(Res.string.backup_conflict_added, presentation.addedCount, Tone.ADDED, Res.string.backup_conflict_private_added.takeIf { content == ContentChange.ADDED }) { it.added }
    section(Res.string.backup_conflict_changed, presentation.changedCount, Tone.CHANGED, Res.string.backup_conflict_private_changed.takeIf { content == ContentChange.CHANGED }) { it.changed }
    return rows
}

/**
 * Every entry of a backup conflict, removed / added / changed, in a lazy list: people,
 * notes, communities, feeds, chats and relays are loaded from relays and open their own
 * screens when tapped, so the user can investigate the change before deciding. Opened from
 * the Home cards ([BackupConflictCards]); deciding resolves the conflict and removes its card.
 * Leaving without deciding keeps the card and the frozen backup.
 */
@Composable
fun BackupConflictReviewScreen(
    slot: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val settings = accountViewModel.account.settings
    val conflicts by settings.backupConflicts.collectAsStateWithLifecycle()
    val conflict = conflicts[slot]

    // The only way out after a decision: resolving (from here or elsewhere) removes the
    // conflict, and this pops the screen exactly once. The buttons don't pop themselves,
    // which would pop a second screen, and a stale conflict (replaced by a newer one) is
    // simply shown in its place.
    val popped = remember { mutableStateOf(false) }
    if (conflict == null) {
        LaunchedEffect(Unit) {
            if (!popped.value) {
                popped.value = true
                nav.popBack()
            }
        }
        return
    }

    BackupConflictReview(
        conflict = conflict,
        accountViewModel = accountViewModel,
        nav = nav,
        // A read-only account can't sign the saved version again, so it can only keep the new one.
        onRestore = if (accountViewModel.isWriteable()) ({ accountViewModel.launchSigner { accountViewModel.account.restoreBackupOver(conflict) } }) else null,
        onKeepNew = { accountViewModel.acceptExternalBackupVersion(conflict) },
    )
}

@Composable
private fun BackupConflictReview(
    conflict: ReplaceableBackupConflict,
    accountViewModel: AccountViewModel,
    nav: INav,
    onRestore: (() -> Unit)?,
    onKeepNew: () -> Unit,
) {
    val presentation = rememberPresentation(conflict.diff)
    val ui = remember(conflict.slot) { ReviewUiState() }
    val (keepLabel, restoreLabel) = actionLabels(conflict)

    Scaffold(
        topBar = {
            TopBarWithBackButton(eventTitle(conflict), nav)
        },
        bottomBar = { ReviewActions(keepLabel, restoreLabel, onRestore, onKeepNew) },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            // Scaffold hands the content its insets (status bar, the bottom action bar and,
            // in landscape, a side 3-button navigation bar); consume them so the IME padding
            // below only adds what the keyboard covers beyond them.
            modifier =
                Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(padding)
                    .imePaddingSafe(),
            contentPadding =
                PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(layoutDirection),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
        ) {
            item(key = "intro", contentType = "intro") {
                BackupConflictIntro(conflict, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            eventDiffItems(conflict, presentation, ui, accountViewModel, nav)
        }
    }
}

/** The default layout: removed / added / changed sections of labelled, typed entries. */
internal fun LazyListScope.genericDiffItems(
    rows: List<ReviewRow>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    itemsIndexed(
        rows,
        contentType = { _, row ->
            when (row) {
                is ReviewRow.Section -> "section"
                is ReviewRow.Note -> "note"
                is ReviewRow.Group -> "group"
                is ReviewRow.Entry -> row.item::class.simpleName
            }
        },
    ) { _, row ->
        when (row) {
            is ReviewRow.Section -> SectionRow(row)
            is ReviewRow.Note -> DetailText("• " + stringRes(row.textRes), Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            is ReviewRow.Group -> GroupLabel(row.labelRes)
            is ReviewRow.Entry -> ReviewEntry(row.item, accountViewModel, nav)
        }
    }
}

@Composable
internal fun GroupLabel(labelRes: StringResource) {
    Text(
        text = stringRes(labelRes),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
internal fun SectionRow(row: ReviewRow.Section) {
    val color =
        when (row.tone) {
            Tone.REMOVED -> MaterialTheme.colorScheme.error
            Tone.ADDED -> MaterialTheme.colorScheme.primary
            Tone.CHANGED -> MaterialTheme.colorScheme.tertiary
        }
    HorizontalDivider()
    Text(
        text = stringRes(row.titleRes, row.count.toString()),
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ReviewActions(
    keepLabel: String,
    restoreLabel: String,
    onRestore: (() -> Unit)?,
    onKeepNew: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            // Scaffold only insets its content slot; a bottomBar handles its own. The padding
            // goes on the content, not the Surface, so the bar's background still reaches the
            // screen edge while the buttons sit above the 3-button navigation (and above the
            // keyboard, if one is up) instead of underneath it.
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePaddingSafe()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Keeping the new version is the default: it's what is on relays already. Restoring
            // always reverts another app's change, so it is the red, secondary choice.
            if (onRestore != null) {
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                ) {
                    Text(restoreLabel, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                }
            }
            Button(onClick = onKeepNew, modifier = Modifier.weight(1f).height(52.dp)) {
                Text(keepLabel, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** "Follow list updated", "Mute list cleared"…: the event and what happened to it, as a title. */
@Composable
internal fun eventTitle(conflict: ReplaceableBackupConflict): String {
    val name = stringRes(eventTypeName(conflict.eventType)).replaceFirstChar { it.uppercase() }
    // Says what happened to it, not just what it is: "Community list updated".
    val emptied = conflict.incoming.tags.isEmpty() && conflict.incoming.content.isEmpty()
    return stringRes(if (emptied) Res.string.backup_review_title_cleared else Res.string.backup_review_title_updated, name)
}

/** The two choices, worded for what they do to this event: "Keep 120" / "Restore 523". */
@Composable
private fun actionLabels(conflict: ReplaceableBackupConflict): Pair<String, String> =
    when (val diff = conflict.diff) {
        is ContactListDiff -> {
            val (saved, new) = conflict.followCounts
            stringRes(Res.string.backup_action_keep_count, new.toString()) to stringRes(Res.string.backup_action_restore_count, saved.toString())
        }
        is MuteListDiff -> {
            val removed = diff.publicMutes.removed.size
            stringRes(Res.string.backup_conflict_keep_new) to
                if (removed > 0) stringRes(Res.string.backup_action_remute, removed.toString()) else stringRes(Res.string.backup_conflict_restore_mine)
        }
        is SimpleGroupListDiff -> {
            val removed = diff.groups.removed.size
            stringRes(Res.string.backup_conflict_keep_new) to
                if (removed > 0) stringRes(Res.string.backup_action_rejoin, removed.toString()) else stringRes(Res.string.backup_conflict_restore_mine)
        }
        is MetadataDiff -> stringRes(Res.string.backup_conflict_keep_new) to stringRes(Res.string.backup_action_restore_profile)
        is RelayListDiff ->
            if (conflict.eventType == BackupEventType.BLOCKED_RELAYS && diff.relays.removed.isNotEmpty()) {
                stringRes(Res.string.backup_conflict_keep_new) to
                    stringRes(
                        Res.string.backup_action_block_again,
                        diff.relays.removed.size
                            .toString(),
                    )
            } else {
                stringRes(Res.string.backup_conflict_keep_new) to stringRes(Res.string.backup_action_restore_relays)
            }
        is AdvertisedRelayListDiff -> stringRes(Res.string.backup_conflict_keep_new) to stringRes(Res.string.backup_action_restore_relays)
        is PublicChatListDiff -> rejoinLabels(diff.channels.removed.size)
        is CommunityListDiff -> rejoinLabels(diff.communities.removed.size)
        is EphemeralChatListDiff -> rejoinLabels(diff.rooms.removed.size)
        is FavoriteAlgoFeedsListDiff -> stringRes(Res.string.backup_conflict_keep_new) to stringRes(Res.string.backup_action_restore_feeds)
        is InterestListDiff -> stringRes(Res.string.backup_conflict_keep_new) to stringRes(Res.string.backup_action_restore_topics)
        // Geohashes are places, not topics: the screen counts "places gone" under "Followed
        // locations", so the button has to agree with the page it sits on.
        is GeohashListDiff -> stringRes(Res.string.backup_conflict_keep_new) to stringRes(Res.string.backup_action_restore_places)
        is TrustProviderListDiff -> stringRes(Res.string.backup_conflict_keep_new) to stringRes(Res.string.backup_action_restore_providers)
        is CashuWalletDiff -> stringRes(Res.string.backup_conflict_keep_new) to stringRes(Res.string.backup_action_restore_wallet)
        else -> stringRes(Res.string.backup_conflict_keep_new) to stringRes(Res.string.backup_conflict_restore_mine)
    }

@Composable
private fun rejoinLabels(removed: Int): Pair<String, String> =
    stringRes(Res.string.backup_conflict_keep_new) to
        if (removed > 0) stringRes(Res.string.backup_action_rejoin, removed.toString()) else stringRes(Res.string.backup_conflict_restore_mine)

/** When the other app changed it, and what the event is for. */
@Composable
private fun BackupConflictIntro(
    conflict: ReplaceableBackupConflict,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringRes(Res.string.backup_review_changed_by_other_app, timeAgoNoDot(conflict.cause.createdAt)),
            style = MaterialTheme.typography.labelLarge,
            color = conflictTones().removed,
        )
        Text(
            text = stringRes(eventTypeExplainer(conflict.eventType)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.placeholderText,
        )
    }
}

internal val EntryPadding = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)

@Composable
internal fun ReviewEntry(
    item: ReviewItem,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (item) {
        is ReviewItem.Person -> PersonEntry(item, accountViewModel, nav)
        is ReviewItem.Thread ->
            LoadNote(item.eventId) { note ->
                if (note != null) {
                    Column(EntryPadding) {
                        NoteCompose(note, makeItShort = true, quotesLeft = 1, accountViewModel = accountViewModel, nav = nav)
                        item.detail?.let { DetailText(it) }
                    }
                } else {
                    TextEntry(item.eventId.toShortDisplay(), item.detail)
                }
            }
        is ReviewItem.Addressable ->
            LoadAddressableNote(item.address) { note ->
                if (note != null) {
                    Column(EntryPadding) {
                        NoteCompose(note, makeItShort = true, quotesLeft = 1, accountViewModel = accountViewModel, nav = nav)
                        item.detail?.let { DetailText(it) }
                    }
                } else {
                    TextEntry(item.address.dTag.ifBlank { item.address.toValue() }, item.detail)
                }
            }
        is ReviewItem.PublicChat -> PublicChatEntry(item, accountViewModel, nav)
        is ReviewItem.ChatRoom ->
            TextEntry(item.roomId.id + " @ " + item.roomId.relayUrl.url, item.detail) { nav.nav(routeFor(item.roomId)) }
        is ReviewItem.Relay -> TextEntry(item.url.url, item.detail) { nav.nav(Route.RelayInfo(item.url.url)) }
        is ReviewItem.Text -> TextEntry(item.text, item.detail)
    }
}

@Composable
private fun PersonEntry(
    item: ReviewItem.Person,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadUser(item.pubKey) { user ->
        if (user == null) {
            TextEntry(item.pubKey.toShortDisplay(), item.detail)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { nav.nav(routeFor(user)) }.then(EntryPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserPicture(user, Size35dp, accountViewModel = accountViewModel, nav = nav)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    // Subscribes to the user's metadata on relays, so unknown people resolve.
                    UsernameDisplay(user, accountViewModel = accountViewModel)
                    item.detail?.let { DetailText(it) }
                }
            }
        }
    }
}

@Composable
private fun PublicChatEntry(
    item: ReviewItem.PublicChat,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadPublicChatChannel(item.channelId) { channel ->
        // Subscribes to the channel's metadata on relays and recomposes when it arrives.
        val state by observeChannel(channel, accountViewModel)
        val name = state?.channel?.toBestDisplayName() ?: channel.toBestDisplayName()
        TextEntry(name, item.detail) { nav.nav(routeFor(channel)) }
    }
}

@Composable
internal fun TextEntry(
    text: String,
    detail: String?,
    onClick: (() -> Unit)? = null,
) {
    val modifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
    Column(modifier.then(EntryPadding)) {
        Text(
            text = text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        detail?.let { DetailText(it) }
    }
}

@Composable
internal fun DetailText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
