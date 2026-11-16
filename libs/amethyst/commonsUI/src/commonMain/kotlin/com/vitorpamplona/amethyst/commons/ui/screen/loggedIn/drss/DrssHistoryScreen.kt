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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.drss

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.drss.DrssFeedHeard
import com.vitorpamplona.amethyst.commons.drss.DrssHistory
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drss_history_empty
import com.vitorpamplona.amethyst.commons.resources.drss_history_feeds
import com.vitorpamplona.amethyst.commons.resources.drss_history_finished
import com.vitorpamplona.amethyst.commons.resources.drss_history_title
import com.vitorpamplona.amethyst.commons.resources.drss_history_today
import com.vitorpamplona.amethyst.commons.resources.drss_history_yesterday
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateSkeletonFormatter
import com.vitorpamplona.amethyst.commons.ui.note.calendarYearAndDay
import com.vitorpamplona.amethyst.commons.ui.note.types.formatListenClock
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.drss.listenProgress.DrssListenProgress
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.utils.TimeUtils

/** How many feeds the "Feeds heard" chart ranks. */
private const val TOP_FEEDS = 10

private val BarShape = RoundedCornerShape(4.dp)

/**
 * drss.io's history: what the user listened to, by the day they last saved their place in it, and
 * the feeds they heard most. Everything comes from their listening records (kind 30078), so it
 * matches what drss shows.
 */
@Composable
fun DrssHistoryScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val drss = accountViewModel.account.drss
    val progressVersion by drss.progressVersion.collectAsStateWithLifecycle()
    val records = remember(progressVersion) { drss.myProgress() }
    val feeds = remember(records) { DrssHistory.feedsHeard(records).take(TOP_FEEDS) }
    val days = remember(records) { records.groupBy { calendarYearAndDay(it.createdAt * 1000) }.toList() }
    val dayFormatter = remember { DateSkeletonFormatter("yMMMEd") }
    val today = remember { calendarYearAndDay(TimeUtils.nowMillis()) }
    val yesterday = remember { calendarYearAndDay(TimeUtils.nowMillis() - TimeUtils.ONE_DAY * 1000) }

    DrssScreenFrame(stringRes(Res.string.drss_history_title), nav) {
        LazyColumn(Modifier.fillMaxWidth()) {
            if (records.isEmpty()) {
                item(key = "empty") { DrssHint(stringRes(Res.string.drss_history_empty)) }
            }
            if (feeds.isNotEmpty()) {
                item(key = "feeds") { DrssFeedsHeard(feeds, accountViewModel) }
            }
            days.forEach { (day, dayRecords) ->
                item(key = "day$day") {
                    val label =
                        when (day) {
                            today -> stringRes(Res.string.drss_history_today)
                            yesterday -> stringRes(Res.string.drss_history_yesterday)
                            else -> dayFormatter.format(dayRecords.first().createdAt * 1000)
                        }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                    )
                }
                items(dayRecords, key = { "r" + it.dTag() }) { record -> DrssHistoryRow(record, accountViewModel, nav) }
            }
        }
    }
}

@Composable
private fun DrssHistoryRow(
    record: DrssListenProgress,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val episode = remember(record) { record.episode()?.let { LocalCache.getOrCreateAddressableNote(Address(it.kind, it.pubKeyHex, it.dTag)) } } ?: return
    DrssQueueRow(episode, isCurrent = false, accountViewModel) { routeFor(episode, accountViewModel.account)?.let(nav::nav) }
}

/** A bar per feed, as long as the time heard next to the most heard one's. */
@Composable
private fun DrssFeedsHeard(
    feeds: List<DrssFeedHeard>,
    accountViewModel: AccountViewModel,
) {
    val most = feeds.maxOf { it.heardSeconds }.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringRes(Res.string.drss_history_feeds), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        feeds.forEach { feed ->
            val show = remember(feed.feed) { LocalCache.getOrCreateUser(feed.feed) }
            val name by observeUserName(show, accountViewModel)
            val finished = pluralStringRes(Res.plurals.drss_history_finished, feed.finished, feed.finished)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(120.dp))
                Box(Modifier.weight(1f).height(14.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth((feed.heardSeconds.toFloat() / most).coerceIn(0.02f, 1f))
                            .height(14.dp)
                            .clip(BarShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Column(Modifier.width(72.dp), horizontalAlignment = Alignment.End) {
                    Text(formatListenClock(feed.heardSeconds), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                    Text(finished, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.grayText, textAlign = TextAlign.End, maxLines = 1)
                }
            }
        }
    }
}
