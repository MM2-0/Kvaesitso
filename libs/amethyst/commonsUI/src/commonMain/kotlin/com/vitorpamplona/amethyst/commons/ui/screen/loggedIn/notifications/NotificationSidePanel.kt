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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.ui.CHAT_UNREAD_CAP
import com.vitorpamplona.amethyst.commons.feeds.Card
import com.vitorpamplona.amethyst.commons.feeds.CardFeedState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.collapse
import com.vitorpamplona.amethyst.commons.resources.notification_panel_folded_new
import com.vitorpamplona.amethyst.commons.resources.refresh
import com.vitorpamplona.amethyst.commons.resources.route_notifications
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.layouts.NotificationPanelCollapsedWidth
import com.vitorpamplona.amethyst.commons.ui.layouts.NotificationPanelWidth
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Size12dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size16dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size24Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The docked notification feed shown on very wide windows, to the right of the center pane.
 * It renders the same card feed body as the Notifications screen (same last-read marking —
 * cards mark themselves read as they become visible here, exactly as they would on the
 * screen, so the new-item dot only lights for items the panel hasn't displayed). When the
 * user has split notifications enabled, the panel shows the Following feed to match the
 * screen's default tab. Under the header it shows today's counts, which expand into the
 * 7-day chart when tapped. Tapping the header opens the full screen, which adds the
 * Following/Everyone tabs; its refresh button reloads and regroups the cards.
 *
 * The fold rides the window's width, not the platform: wherever the panel docks, the shell hands
 * [onCollapse] down and the header carries the fold button beside the refresh one.
 */
@Composable
fun NotificationSidePanel(
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
    onCollapse: (() -> Unit)? = null,
) {
    val split by accountViewModel.account.settings.splitNotificationsEnabled
        .collectAsStateWithLifecycle()

    val notifFeedContentState =
        if (split) {
            accountViewModel.feedStates.notificationsFollowing
        } else {
            accountViewModel.feedStates.notifications
        }
    val scrollStateKey =
        if (split) {
            ScrollStateKeys.NOTIFICATION_SIDE_PANEL_FOLLOWING
        } else {
            ScrollStateKeys.NOTIFICATION_SIDE_PANEL
        }

    WatchAccountForNotifications(notifFeedContentState, accountViewModel)

    // The Surface provides the Material container color + onBackground as LocalContentColor;
    // in a bare Row the default text color falls back to Color.Black and is invisible on the
    // dark theme (same reason DisappearingScaffold roots itself in a Surface).
    Surface(
        modifier = modifier.width(NotificationPanelWidth).fillMaxHeight(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier.windowInsetsPadding(
                WindowInsets.systemBars.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End,
                ),
            ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { nav.nav(Route.Notification()) }
                        .padding(horizontal = Size16dp, vertical = Size12dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    symbol = MaterialSymbols.Notifications,
                    contentDescription = null,
                    modifier = Size22Modifier,
                    tint = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = StdHorzSpacer)
                Text(
                    text = stringRes(Res.string.route_notifications),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                // What tapping Notifications in the bottom bar does on a phone that is already
                // there: reload and regroup the cards, back at the top.
                IconButton(
                    onClick = { notifFeedContentState.invalidateDataAndSendToTop(true) },
                    modifier = Size24Modifier,
                ) {
                    Icon(
                        symbol = MaterialSymbols.Refresh,
                        contentDescription = stringRes(Res.string.refresh),
                        modifier = Size20Modifier,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The panel docks wherever the window has the width, so the fold button is
                // always there beside the refresh one.
                if (onCollapse != null) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Size24Modifier,
                    ) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.KeyboardArrowRight,
                            contentDescription = stringRes(Res.string.collapse),
                            modifier = Size20Modifier,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Today's counts; tapping them expands the 7-day chart, as on the full screen.
            SummaryBar(state = accountViewModel.feedStates.notificationSummary)

            HorizontalDivider(thickness = DividerThickness)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                SingleNotificationsBody(
                    notifFeedContentState = notifFeedContentState,
                    notifPolls = accountViewModel.feedStates.notificationsOpenPolls,
                    scrollToEventId = null,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    scrollStateKey = scrollStateKey,
                )
            }
        }
    }
}

/**
 * The folded form of [NotificationSidePanel]: just the bell, badged with how many notification
 * cards landed since the panel was folded ([collapsedAt]; null — expanded, or the saved fold not
 * loaded yet — reads as zero). Tapping the bell unfolds the panel, which resets the count. The
 * feed the panel would show is watched here too, so the badge ticks as cards arrive while folded.
 */
@Composable
fun CollapsedNotificationPanel(
    accountViewModel: AccountViewModel,
    collapsedAt: Long?,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val split by accountViewModel.account.settings.splitNotificationsEnabled
        .collectAsStateWithLifecycle()

    val notifFeedContentState =
        if (split) {
            accountViewModel.feedStates.notificationsFollowing
        } else {
            accountViewModel.feedStates.notifications
        }

    WatchAccountForNotifications(notifFeedContentState, accountViewModel)

    val feedState by notifFeedContentState.feedContent.collectAsStateWithLifecycle()
    val loadedFeed = (feedState as? CardFeedState.Loaded)?.feed?.collectAsStateWithLifecycle()?.value
    val missed = countNewerThan(loadedFeed?.list, collapsedAt)

    // The bell is the strip's only control: name what it opens, plus the missed count when there
    // is one, rather than the bare action ("Expand"), which reads as nonsense out of context.
    val bellDescription =
        if (missed > 0) {
            pluralStringRes(Res.plurals.notification_panel_folded_new, missed, missed)
        } else {
            stringRes(Res.string.route_notifications)
        }

    // The Surface provides the Material container color + onBackground as LocalContentColor, as
    // in the expanded panel above.
    Surface(
        modifier = modifier.width(NotificationPanelCollapsedWidth).fillMaxHeight(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier.windowInsetsPadding(
                WindowInsets.systemBars.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End,
                ),
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A 48 dp button with no top padding centres the bell 24 dp down, on the same line as
            // the expanded header (12 dp padding around a 24 dp row) and the centre pane's top bar.
            IconButton(onClick = onExpand) {
                BadgedBox(
                    badge = {
                        if (missed > 0) Badge { Text(missedBadgeLabel(missed)) }
                    },
                ) {
                    Icon(
                        symbol = MaterialSymbols.Notifications,
                        contentDescription = bellDescription,
                        modifier = Size22Modifier,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

/** The cards newer than [sinceSeconds]; null — no fold yet or no feed yet — reads as nothing missed. */
internal fun countNewerThan(
    cards: List<Card>?,
    sinceSeconds: Long?,
): Int = if (cards == null || sinceSeconds == null) 0 else cards.count { it.createdAt() > sinceSeconds }

/** The collapsed bell's badge text, capped at [CHAT_UNREAD_CAP]+ like every other unread pill. */
internal fun missedBadgeLabel(missed: Int): String = if (missed > CHAT_UNREAD_CAP) "$CHAT_UNREAD_CAP+" else missed.toString()
