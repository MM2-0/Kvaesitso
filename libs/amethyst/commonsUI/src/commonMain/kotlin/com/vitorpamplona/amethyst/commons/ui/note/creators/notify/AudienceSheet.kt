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
package com.vitorpamplona.amethyst.commons.ui.note.creators.notify

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.composer.AudienceCap
import com.vitorpamplona.amethyst.commons.model.composer.AudienceList
import com.vitorpamplona.amethyst.commons.model.composer.AudienceListKind
import com.vitorpamplona.amethyst.commons.model.composer.AudienceMember
import com.vitorpamplona.amethyst.commons.model.composer.AudienceSelection
import com.vitorpamplona.amethyst.commons.model.nip51Lists.followSets.PeopleList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.audience_add_anyway
import com.vitorpamplona.amethyst.commons.resources.audience_add_people
import com.vitorpamplona.amethyst.commons.resources.audience_badge_already_added
import com.vitorpamplona.amethyst.commons.resources.audience_badge_muted
import com.vitorpamplona.amethyst.commons.resources.audience_badge_private_member
import com.vitorpamplona.amethyst.commons.resources.audience_count_with_private
import com.vitorpamplona.amethyst.commons.resources.audience_hard_cap
import com.vitorpamplona.amethyst.commons.resources.audience_over_limit
import com.vitorpamplona.amethyst.commons.resources.audience_recipients_are_visible
import com.vitorpamplona.amethyst.commons.resources.audience_sheet_no_lists
import com.vitorpamplona.amethyst.commons.resources.audience_sheet_search
import com.vitorpamplona.amethyst.commons.resources.audience_sheet_title
import com.vitorpamplona.amethyst.commons.resources.audience_soft_cap_private
import com.vitorpamplona.amethyst.commons.resources.audience_soft_cap_public
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.discover_follows
import com.vitorpamplona.amethyst.commons.resources.follow_sets
import com.vitorpamplona.amethyst.commons.resources.num_selected
import com.vitorpamplona.amethyst.commons.resources.select_all
import com.vitorpamplona.amethyst.commons.ui.components.OutlinedThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Size24dp
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import org.jetbrains.compose.resources.StringResource

/**
 * The one place the composer's audience is edited: search for a person, pick a
 * whole people list or follow pack, review who that would actually add.
 *
 * This replaces the two competing "Add" / "Add list" chips that would otherwise
 * sit next to the people they act on. Nothing here mutates the ViewModel
 * directly — the callbacks do, so the selection rules stay testable in
 * [AudienceSelection].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudienceSheet(
    audience: ImmutableList<User>,
    mutedNotifies: ImmutableSet<HexKey>,
    isPrivate: Boolean,
    searchState: TextFieldState,
    onSearchChanged: () -> Unit,
    userSuggestions: UserSuggestionState?,
    accountViewModel: AccountViewModel,
    onAddUser: (User) -> Unit,
    onAddList: (AudienceList, List<User>) -> Unit,
    onDismiss: () -> Unit,
) {
    var reviewing by remember { mutableStateOf<AudienceList?>(null) }

    // Muted people are in pTags but not in the audience, so a list that
    // contains them is offering something real: re-adding un-mutes them.
    val alreadyInAudience =
        remember(audience, mutedNotifies) {
            audience.mapNotNullTo(mutableSetOf()) { it.pubkeyHex.takeIf { hex -> hex !in mutedNotifies } }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            val list = reviewing
            if (list == null) {
                AudienceCatalog(
                    searchState = searchState,
                    onSearchChanged = onSearchChanged,
                    userSuggestions = userSuggestions,
                    accountViewModel = accountViewModel,
                    onAddUser = onAddUser,
                    onPickList = { reviewing = it },
                )
            } else {
                AudienceReview(
                    list = list,
                    alreadyInAudience = alreadyInAudience,
                    activeAudienceSize = alreadyInAudience.size,
                    isPrivate = isPrivate,
                    accountViewModel = accountViewModel,
                    onBack = { reviewing = null },
                    onConfirm = { users ->
                        onAddList(list, users)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun AudienceCatalog(
    searchState: TextFieldState,
    onSearchChanged: () -> Unit,
    userSuggestions: UserSuggestionState?,
    accountViewModel: AccountViewModel,
    onAddUser: (User) -> Unit,
    onPickList: (AudienceList) -> Unit,
) {
    val allLists = rememberAudienceLists(accountViewModel)
    // Fixed dp caps (220 + 400) overflow a short screen or a large font scale,
    // and a bottom sheet clips rather than scrolls — the confirm button would be
    // unreachable. Budgeting against the actual screen keeps it in view.
    val screenHeight = windowHeightDp()
    val query = searchState.text.toString()

    val sets =
        remember(allLists, query) {
            allLists.filter { it.kind == AudienceListKind.PEOPLE_LIST && it.matches(query) }
        }
    val packs =
        remember(allLists, query) {
            allLists.filter { it.kind == AudienceListKind.FOLLOW_PACK && it.matches(query) }
        }

    Text(
        text = stringRes(Res.string.audience_sheet_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )

    Spacer(Modifier.height(12.dp))

    OutlinedThinPaddingTextField(
        state = searchState,
        onTextChanged = onSearchChanged,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        leadingIcon = {
            Icon(
                symbol = MaterialSymbols.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        placeholder = {
            Text(
                text = stringRes(Res.string.audience_sheet_search),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
    )

    Spacer(Modifier.height(12.dp))

    // People matching the query come from the same suggestion machinery the
    // @-mention autocomplete uses, so NIP-05 and npub input keep working here.
    if (query.isNotBlank() && userSuggestions != null) {
        ShowUserSuggestionList(
            userSuggestions = userSuggestions,
            onSelect = onAddUser,
            accountViewModel = accountViewModel,
            modifier = Modifier.heightIn(max = (screenHeight * 0.28f).dp),
            itemColors = ListItemDefaults.colors(containerColor = Color.Transparent),
            showDividers = false,
        )
    }

    LazyColumn(
        modifier = Modifier.heightIn(max = (screenHeight * 0.45f).dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (sets.isNotEmpty()) {
            item { SectionHeader(stringRes(Res.string.follow_sets)) }
            items(sets, key = { "set-" + it.id }) { AudienceListRow(it) { onPickList(it) } }
        }
        if (packs.isNotEmpty()) {
            item { SectionHeader(stringRes(Res.string.discover_follows)) }
            items(packs, key = { "pack-" + it.id }) { AudienceListRow(it) { onPickList(it) } }
        }
        if (sets.isEmpty() && packs.isEmpty()) {
            item {
                Text(
                    text = stringRes(Res.string.audience_sheet_no_lists),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun AudienceListRow(
    list: AudienceList,
    onClick: () -> Unit,
) {
    val overHard = list.memberCount > AudienceSelection.HARD_CAP

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Groups,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = list.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (overHard) {
            Text(
                text = stringRes(Res.string.audience_over_limit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.warningColor,
            )
        }
        Text(
            text =
                if (list.privateMembers.isEmpty()) {
                    list.memberCount.toString()
                } else {
                    stringRes(Res.string.audience_count_with_private, list.memberCount, list.privateMembers.size)
                },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.placeholderText,
        )
    }
}

@Composable
private fun AudienceReview(
    list: AudienceList,
    alreadyInAudience: Set<HexKey>,
    activeAudienceSize: Int,
    isPrivate: Boolean,
    accountViewModel: AccountViewModel,
    onBack: () -> Unit,
    onConfirm: (List<User>) -> Unit,
) {
    val hidden by accountViewModel.account.hiddenUsers.flow
        .collectAsStateWithLifecycle()

    // Leaves room for the header, the select-all row, the cap notes and the
    // confirm button, which all have to stay on screen for the sheet to work.
    val listMaxHeight = (windowHeightDp() * 0.42f).dp

    val members =
        remember(list, alreadyInAudience, hidden) {
            AudienceSelection.buildMembers(
                list = list,
                alreadyInAudience = alreadyInAudience,
                hiddenUsers = hidden.hiddenUsers + hidden.spammers,
            )
        }

    var selected by remember(members) { mutableStateOf(AudienceSelection.defaultSelection(members, activeAudienceSize)) }

    val additions = remember(members, selected) { AudienceSelection.pendingAdditions(members, selected) }
    val cap = AudienceSelection.capFor(activeAudienceSize, additions.size)
    val toggleable = remember(members) { AudienceSelection.toggleableIds(members) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.minimumInteractiveComponentSize().size(32.dp).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                contentDescription = stringRes(Res.string.back),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = list.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringRes(Res.string.num_selected, selected.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    Spacer(Modifier.height(4.dp))

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    val allOn = toggleable.all { it in selected }
                    selected = if (allOn) selected - toggleable else selected + toggleable
                }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = toggleable.isNotEmpty() && toggleable.all { it in selected },
            onCheckedChange = null,
        )
        Spacer(Modifier.size(8.dp))
        Text(stringRes(Res.string.select_all), style = MaterialTheme.typography.bodyMedium)
    }

    HorizontalDivider(thickness = DividerThickness)

    LazyColumn(modifier = Modifier.heightIn(max = listMaxHeight)) {
        items(members, key = { it.pubkeyHex }) { member ->
            AudienceMemberRow(
                member = member,
                isSelected = member.pubkeyHex in selected,
                accountViewModel = accountViewModel,
                onToggle = {
                    if (member.isAlreadyInAudience) return@AudienceMemberRow
                    selected =
                        if (member.pubkeyHex in selected) {
                            selected - member.pubkeyHex
                        } else {
                            selected + member.pubkeyHex
                        }
                },
            )
        }
    }

    HorizontalDivider(thickness = DividerThickness)

    Spacer(Modifier.height(10.dp))

    if (isPrivate) {
        NoteLine(
            text = stringRes(Res.string.audience_recipients_are_visible),
            color = MaterialTheme.colorScheme.placeholderText,
            symbol = MaterialSymbols.Info,
        )
    }

    when (cap) {
        is AudienceCap.OverHard ->
            NoteLine(
                text = pluralStringRes(Res.plurals.audience_hard_cap, cap.total, cap.total, AudienceSelection.HARD_CAP),
                color = MaterialTheme.colorScheme.error,
                symbol = MaterialSymbols.Warning,
            )
        is AudienceCap.OverSoft ->
            NoteLine(
                text =
                    if (isPrivate) {
                        pluralStringRes(Res.plurals.audience_soft_cap_private, cap.total, cap.total)
                    } else {
                        pluralStringRes(Res.plurals.audience_soft_cap_public, cap.total, cap.total)
                    },
                color = MaterialTheme.colorScheme.warningColor,
                symbol = MaterialSymbols.Warning,
            )
        AudienceCap.Fine -> Unit
    }

    Spacer(Modifier.height(10.dp))

    Button(
        onClick = { onConfirm(additions) },
        enabled = additions.isNotEmpty() && cap !is AudienceCap.OverHard,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text =
                if (cap is AudienceCap.OverSoft) {
                    pluralStringRes(Res.plurals.audience_add_anyway, additions.size, additions.size)
                } else {
                    pluralStringRes(Res.plurals.audience_add_people, additions.size, additions.size)
                },
        )
    }
}

@Composable
private fun NoteLine(
    text: String,
    color: Color,
    symbol: MaterialSymbol,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color,
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun AudienceMemberRow(
    member: AudienceMember,
    isSelected: Boolean,
    accountViewModel: AccountViewModel,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = null,
            enabled = !member.isAlreadyInAudience,
        )
        BaseUserPicture(member.user, Size24dp, accountViewModel)
        Box(Modifier.weight(1f)) {
            UsernameDisplay(member.user, accountViewModel = accountViewModel)
        }

        when {
            member.isAlreadyInAudience -> MemberBadge(Res.string.audience_badge_already_added, MaterialTheme.colorScheme.placeholderText)
            member.isHidden -> MemberBadge(Res.string.audience_badge_muted, MaterialTheme.colorScheme.placeholderText)
            member.isPrivateMember -> MemberBadge(Res.string.audience_badge_private_member, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MemberBadge(
    textRes: StringResource,
    color: Color,
) {
    Text(
        text = stringRes(textRes),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
    )
}

/**
 * The account's people lists and follow packs as one catalog. Shared by the
 * sheet and by the composer, which needs the titles to label the group chips.
 */
@Composable
fun rememberAudienceLists(accountViewModel: AccountViewModel): List<AudienceList> {
    val peopleLists by accountViewModel.account.followSets.uiListFlow
        .collectAsStateWithLifecycle()
    val followPacks by accountViewModel.account.starterPacks.uiListFlow
        .collectAsStateWithLifecycle()

    return remember(peopleLists, followPacks) {
        peopleLists.map { it.toAudienceList(AudienceListKind.PEOPLE_LIST) } +
            followPacks.map { it.toAudienceList(AudienceListKind.FOLLOW_PACK) }
    }
}

private fun PeopleList.toAudienceList(kind: AudienceListKind) =
    AudienceList(
        // Qualified by kind: a people list and a follow pack are free to share a
        // d tag, and provenance keys on this string — an unqualified id would let
        // one list's chip carry the other's title and remove both batches at once.
        id = kind.name + ":" + identifierTag,
        kind = kind,
        title = title,
        publicMembers = publicMembersList,
        privateMembers = privateMembersList,
    )

/** The window's height in dp, which the sheet budgets its lists against. */
@Composable
private fun windowHeightDp(): Int =
    with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height
            .toDp()
            .value
            .toInt()
    }
