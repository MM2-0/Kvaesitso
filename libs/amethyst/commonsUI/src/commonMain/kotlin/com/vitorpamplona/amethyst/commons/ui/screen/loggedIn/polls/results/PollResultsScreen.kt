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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.polls.results

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.rooms.roomFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.relayClient.polls.results.RelayPollResponseLoader
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.poll_multiple_choice
import com.vitorpamplona.amethyst.commons.resources.poll_results_all_options
import com.vitorpamplona.amethyst.commons.resources.poll_results_backdated_votes
import com.vitorpamplona.amethyst.commons.resources.poll_results_checking_completeness
import com.vitorpamplona.amethyst.commons.resources.poll_results_closes_in
import com.vitorpamplona.amethyst.commons.resources.poll_results_ended
import com.vitorpamplona.amethyst.commons.resources.poll_results_hidden_voters
import com.vitorpamplona.amethyst.commons.resources.poll_results_ignored_votes
import com.vitorpamplona.amethyst.commons.resources.poll_results_late_votes
import com.vitorpamplona.amethyst.commons.resources.poll_results_loading
import com.vitorpamplona.amethyst.commons.resources.poll_results_no_votes
import com.vitorpamplona.amethyst.commons.resources.poll_results_open
import com.vitorpamplona.amethyst.commons.resources.poll_results_option_count
import com.vitorpamplona.amethyst.commons.resources.poll_results_partial
import com.vitorpamplona.amethyst.commons.resources.poll_results_partial_approx
import com.vitorpamplona.amethyst.commons.resources.poll_results_selections
import com.vitorpamplona.amethyst.commons.resources.poll_results_title
import com.vitorpamplona.amethyst.commons.resources.poll_results_your_pick
import com.vitorpamplona.amethyst.commons.resources.poll_single_choice
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserLine
import com.vitorpamplona.amethyst.commons.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgoStyle
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.note.timeAheadNoDot
import com.vitorpamplona.amethyst.commons.ui.note.types.UserGallery
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.polls.results.datasources.PollResponsesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.ui.theme.SmallishBorder
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.nip88Polls.PollLoadReport
import com.vitorpamplona.amethyst.commons.viewmodels.nip88Polls.PollOptionResult
import com.vitorpamplona.amethyst.commons.viewmodels.nip88Polls.PollResponseLoader
import com.vitorpamplona.amethyst.commons.viewmodels.nip88Polls.PollResultsUiState
import com.vitorpamplona.amethyst.commons.viewmodels.nip88Polls.PollResultsViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.nip88Polls.PollVoterRow
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.poll.tags.PollType
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.map

private val VoteColumnMaxWidth = 140.dp

/**
 * The full results of a NIP-88 poll: how many votes each option got, and who voted for what.
 *
 * Everything here reads the tally already hanging off the poll [Note] — the vote counts on the feed
 * card and the numbers on this screen are the same object, so they can never disagree.
 */
@Composable
fun PollResultsScreen(
    noteId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(baseNoteHex = noteId) { note ->
        if (note == null) {
            PollResultsScaffold(nav) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringRes(Res.string.poll_results_loading),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                }
            }
        } else {
            PollResults(note, accountViewModel, nav)
        }
    }
}

@Composable
private fun PollResults(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account

    // The poll's own event and engagement, exactly as any note on any screen gets them.
    val noteState by observeNote(note, accountViewModel)

    // A room's poll has every vote it will ever have already: its room delivered them, and asking
    // relays about it would name the room's private message outside the room.
    val inRoom = remember(note) { note.roomFor(account) != null }

    // ...plus this screen's own claim: every vote, from the relays the poll nominates. Lifecycle-aware
    // and deduplicated like every other current-screen data source, so leaving the screen closes it.
    if (!inRoom) PollResponsesFilterAssemblerSubscription(note.idHex, accountViewModel)

    // Opening this screen is the opt-in, exactly like the card's "View results" link. Keyed on the
    // note state rather than the id: arriving by deep link, the poll event lands *after* the first
    // composition, and an id-keyed effect would never run again to record it.
    LaunchedEffect(noteState) {
        (note.event as? PollEvent)?.let { accountViewModel.markPollResultsViewed(it.id, it.endsAt()) }
    }

    // Remembered: the view model store only consults the factory once, but building it inline allocated a
    // loader, two lambdas and a mapped Flow on every recomposition.
    val factory =
        remember(note, account, inRoom) {
            PollResultsViewModel.Factory(
                pollNote = note,
                forKey = account.pubKey,
                isHidden = { account.isHidden(it) },
                follows = account.allFollows.flow.map { it.authors },
                hiddenChanges = account.hiddenUsers.flow,
                loader = if (inRoom) PollResponseLoader { PollLoadReport(null, approximate = false, relaysAsked = 0, relaysAnswered = 0) } else RelayPollResponseLoader(account.client, note),
            )
        }

    val viewModel: PollResultsViewModel = rememberViewModel(key = "PollResults-${note.idHex}", factory = factory)

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selected by viewModel.selectedOption.collectAsStateWithLifecycle()

    PollResultsScaffold(nav) {
        // The summary scrolls with the voters rather than being pinned: on a phone a pinned summary
        // eats most of the screen and leaves a sliver of list to scroll. Its cost when it scrolls
        // back into view is paid down in OptionBar instead — see the note on the bar animation.
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item("header") {
                PollHeader(note, state, accountViewModel, nav)
            }

            item("options") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.options.forEach { option ->
                        key(option.code) {
                            OptionBar(
                                option = option,
                                isSelected = option.code == selected,
                                isMyPick = option.code in state.myVote,
                                accountViewModel = accountViewModel,
                                nav = nav,
                                onClick = { viewModel.selectOption(option.code) },
                            )
                        }
                    }
                }
            }

            if (state.options.size > 1) {
                item("chips") {
                    OptionFilterRow(state, selected, onSelect = viewModel::selectOption)
                }
            }

            item("voters-divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp),
                    thickness = DividerThickness,
                )
            }

            items(state.voters, key = { it.user.pubkeyHex }) { voter ->
                VoterRow(voter, accountViewModel, nav)
                HorizontalDivider(thickness = DividerThickness)
            }

            item("footer") {
                ResultsFooter(state)
            }
        }
    }
}

@Composable
private fun PollResultsScaffold(
    nav: INav,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = { TopBarWithBackButton(caption = stringRes(Res.string.poll_results_title), nav = nav) },
    ) { padding ->
        Box(Modifier.padding(padding)) { content() }
    }
}

// -------------------------------------------------------------------------------------------
// Header: who asked, what they asked, and the totals in one glance.
// -------------------------------------------------------------------------------------------

@Composable
private fun PollHeader(
    note: Note,
    state: PollResultsUiState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? PollEvent

    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        note.author?.let { author ->
            // Same shape as every other note header in the app: name on the left, and the time plus
            // the options menu pinned to the right edge rather than trailing the name.
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserPicture(author, Size25dp, accountViewModel = accountViewModel, nav = nav)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    UsernameDisplay(author, accountViewModel = accountViewModel)
                }
                TimeAgo(note.createdAt() ?: 0L, TimeAgoStyle.Dotted)
                Spacer(Modifier.width(6.dp))
                // The app's own note menu — share, copy, report, bookmark — rather than a
                // poll-specific subset that would drift from every other note header.
                MoreOptionsButton(note, accountViewModel = accountViewModel, nav = nav)
            }
        }

        event?.content?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PollStatusChip(state.endsAt)
            PollTypeChip(state)
        }
    }
}

@Composable
private fun PollStatusChip(endsAt: Long?) {
    val hasEnded = endsAt != null && endsAt < TimeUtils.now()
    val tint = if (hasEnded) MaterialTheme.colorScheme.grayText else MaterialTheme.colorScheme.allGoodColor

    Chip(tint) {
        // A slow breath on the dot: the tally really is live, and this is the only thing on the
        // screen that says so without adding a control nobody asked for.
        val alpha =
            if (hasEnded) {
                1f
            } else {
                val transition = rememberInfiniteTransition(label = "pollLive")
                transition
                    .animateFloat(
                        initialValue = 1f,
                        targetValue = 0.25f,
                        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
                        label = "pollLiveDot",
                    ).value
            }

        Box(
            Modifier
                .size(7.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(tint),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text =
                if (endsAt == null) {
                    stringRes(Res.string.poll_results_open)
                } else if (hasEnded) {
                    stringRes(Res.string.poll_results_ended, timeAgoNoDot(endsAt))
                } else {
                    // Ahead, not ago: timeAgoNoDot on a future stamp collapses to "now".
                    stringRes(Res.string.poll_results_closes_in, timeAheadNoDot(endsAt))
                },
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}

@Composable
private fun PollTypeChip(state: PollResultsUiState) {
    val gray = MaterialTheme.colorScheme.grayText
    Chip(gray) {
        val type =
            stringRes(
                if (state.type == PollType.MULTI_CHOICE) {
                    Res.string.poll_multiple_choice
                } else {
                    Res.string.poll_single_choice
                },
            )

        Text(
            // Selections only differ from voters when people can tick more than one box, and this
            // chip is what explains why the bars below may sum past 100% — so it carries the count.
            text =
                if (state.totalSelections != state.totalVoters) {
                    type + " " + pluralStringRes(Res.plurals.poll_results_selections, state.totalSelections, state.totalSelections)
                } else {
                    type
                },
            style = MaterialTheme.typography.labelMedium,
            color = gray,
        )
    }
}

@Composable
private fun Chip(
    tint: Color,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(CircleShape)
                .border(1.dp, tint.copy(alpha = 0.4f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

// -------------------------------------------------------------------------------------------
// Option bars.
// -------------------------------------------------------------------------------------------

@Composable
private fun OptionBar(
    option: PollOptionResult,
    isSelected: Boolean,
    isMyPick: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    onClick: () -> Unit,
) {
    val winner = MaterialTheme.colorScheme.allGoodColor
    val accent = MaterialTheme.colorScheme.primary
    val barColor = if (option.isWinning) winner else accent

    val borderColor by animateColorAsState(
        targetValue =
            when {
                isSelected -> accent
                option.isWinning -> winner.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.grayText.copy(alpha = 0.4f)
            },
        label = "pollOptionBorder",
    )

    // Same 800ms tween the feed card uses, so a vote cast there and read here moves identically —
    // but animating *to* the value rather than growing from zero on every composition.
    //
    // A LazyColumn drops an item's state when it scrolls out of view, so a zero-start meant the
    // bars replayed their full 800ms every time the summary came back: 800ms of recomposition and
    // redraw for a flourish nobody asked to see twice. Starting at the target costs the intro on
    // first paint and keeps the animation for what it is actually for — a vote landing while you
    // are reading.
    val progress by animateFloatAsState(
        targetValue = option.percent,
        animationSpec = tween(durationMillis = 800),
        label = "pollOptionBar",
    )

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(SmallishBorder)
                .border(if (isSelected) 2.dp else 1.dp, borderColor, SmallishBorder)
                .background(
                    if (option.isWinning) winner.copy(alpha = 0.12f) else MaterialTheme.colorScheme.subtleBorder,
                ).clickable(onClick = onClick),
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .alpha(0.32f)
                    .drawWithContent {
                        clipRect(right = size.width * progress) { drawRect(barColor) }
                        drawContent()
                    },
        )

        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isMyPick) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringRes(Res.string.poll_results_your_pick),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    text = pluralStringRes(Res.plurals.poll_results_option_count, option.voters, option.voters),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            }

            Spacer(Modifier.width(10.dp))
            AvatarStack(option, accountViewModel, nav)
            Spacer(Modifier.width(10.dp))

            Text(
                text = "${(option.percent * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                color = if (option.isWinning) winner else Color.Unspecified,
            )
        }
    }
}

@Composable
private fun AvatarStack(
    option: PollOptionResult,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (option.topVoters.isEmpty()) return

    UserGallery(option.topVoters, option.voters) { user ->
        UserPicture(user, Size25dp, accountViewModel = accountViewModel, nav = nav)
    }
}

// -------------------------------------------------------------------------------------------
// Voter list.
// -------------------------------------------------------------------------------------------

@Composable
private fun OptionFilterRow(
    state: PollResultsUiState,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Carries the poll's total the way each option chip carries its own, so the count lives in
        // the same row as the numbers it totals instead of in a headline above them.
        FilterChip(
            label = "${stringRes(Res.string.poll_results_all_options)} · ${state.totalVoters}",
            isSelected = selected == null,
            onClick = { onSelect(null) },
        )
        state.options.forEach { option ->
            key(option.code) {
                FilterChip(
                    label = "${option.label} · ${option.voters}",
                    isSelected = option.code == selected,
                    onClick = { onSelect(option.code) },
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(if (isSelected) accent.copy(alpha = 0.16f) else Color.Transparent)
                .border(1.dp, if (isSelected) accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.grayText.copy(alpha = 0.4f), CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) accent else MaterialTheme.colorScheme.grayText,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/**
 * A voter is a user, so this is [UserLine] — the app's own row — with the vote handed to the
 * trailing slot it already exposes, in place of the follow buttons.
 */
@Composable
private fun VoterRow(
    voter: PollVoterRow,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    UserLine(
        baseUser = voter.user,
        accountViewModel = accountViewModel,
        trailingContent = { VoteChoice(voter) },
        onClick = { nav.nav(routeFor(voter.user)) },
    )
}

@Composable
private fun VoteChoice(voter: PollVoterRow) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.widthIn(max = VoteColumnMaxWidth),
    ) {
        Text(
            text = voter.labels.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TimeAgo(voter.createdAt, TimeAgoStyle.Short)
    }
}

// -------------------------------------------------------------------------------------------
// Footer: what the numbers above do not include.
// -------------------------------------------------------------------------------------------

@Composable
private fun ResultsFooter(state: PollResultsUiState) {
    val notes =
        remember(state) {
            buildList {
                if (state.lateVotes > 0) add(Res.plurals.poll_results_late_votes to state.lateVotes)
                if (state.backdatedVotes > 0) add(Res.plurals.poll_results_backdated_votes to state.backdatedVotes)
                if (state.ignoredVotes > 0) add(Res.plurals.poll_results_ignored_votes to state.ignoredVotes)
                if (state.hiddenVoters > 0) add(Res.plurals.poll_results_hidden_voters to state.hiddenVoters)
            }
        }

    if (state.hasTally && state.totalVoters == 0 && !state.isCheckingCompleteness) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringRes(Res.string.poll_results_no_votes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Completeness(state)

        notes.forEach { (res, count) ->
            Text(
                text = pluralStringRes(res, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        }
    }
}

/**
 * Says how complete the tally is, and only when there is something to say. Silence means the relays
 * either agreed with what we hold or could not answer — claiming completeness we cannot prove would
 * be the same mistake as presenting a truncated tally as final.
 */
@Composable
private fun Completeness(state: PollResultsUiState) {
    if (state.isCheckingCompleteness) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.placeholderText,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringRes(Res.string.poll_results_checking_completeness),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        }
        return
    }

    val reported = state.reportedResponses ?: return
    if (!state.isIncomplete) return

    Text(
        text =
            stringRes(
                if (state.reportIsApproximate) {
                    Res.string.poll_results_partial_approx
                } else {
                    Res.string.poll_results_partial
                },
                state.loadedResponses,
                reported,
                state.relaysAnswered,
            ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.warningColor,
    )
}
