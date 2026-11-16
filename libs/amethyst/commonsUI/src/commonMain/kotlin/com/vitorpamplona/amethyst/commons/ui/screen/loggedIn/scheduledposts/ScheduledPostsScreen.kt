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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.scheduledposts

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.new_post
import com.vitorpamplona.amethyst.commons.resources.quick_action_copy_note_id
import com.vitorpamplona.amethyst.commons.resources.quick_action_delete
import com.vitorpamplona.amethyst.commons.resources.relays
import com.vitorpamplona.amethyst.commons.resources.retry
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_action_send_now
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_at_time
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_at_time_past
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_day_tomorrow
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_empty_hint
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_empty_title
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_error_prefix
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_event_id_copied
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_relay_count
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_status_cancelled
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_status_failed
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_status_pending
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_status_publishing
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_status_sent
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_subtitle_due_suffix
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_subtitle_queued
import com.vitorpamplona.amethyst.commons.resources.today
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPost
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStatus
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostsViewModel
import com.vitorpamplona.amethyst.commons.scheduledposts.extractContentPreview
import com.vitorpamplona.amethyst.commons.scheduledposts.extractEventId
import com.vitorpamplona.amethyst.commons.scheduledposts.extractFirstMediaUrl
import com.vitorpamplona.amethyst.commons.scheduledposts.ui.MediaThumbnail
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.ui.components.SwipeToDeleteWithConfirmation
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarSize
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.note.DateSkeletonFormatter
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.note.timeAheadNoDot
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduledPostsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val accountPubkey = accountViewModel.account.signer.pubKey
    // No store means the platform cannot schedule posts at all: keep the bar so the user can
    // leave, with nothing to list under it.
    val store = accountViewModel.host.scheduledPostStore
    if (store == null) {
        Scaffold(topBar = { TopBarWithBackButton(stringRes(Res.string.scheduled_posts), nav) }) { }
        return
    }
    val viewModel: ScheduledPostsViewModel =
        rememberViewModel(key = "scheduled-posts-$accountPubkey") {
            ScheduledPostsViewModel.create(store, accountPubkey)
        }
    val groups by viewModel.groupedPosts.collectAsStateWithLifecycle()
    val totalActive by viewModel.totalActive.collectAsStateWithLifecycle()
    var expandedId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Tick once per minute so relative-time strings ("publishes in 2h 13m")
    // refresh on a long-open list instead of being frozen at first composition.
    val nowSec by produceState(initialValue = TimeUtils.now()) {
        while (true) {
            delay(60_000)
            value = TimeUtils.now()
        }
    }

    val dueSoonCount by remember {
        derivedStateOf {
            groups
                .flatMap { it.posts }
                .count { it.publishAtSec - nowSec in 1..URGENT_THRESHOLD_SEC }
        }
    }

    val barHeight = if (totalActive > 0) 64.dp else TopBarSize

    Scaffold(
        topBar = {
            ShorterTopAppBar(
                expandedHeight = barHeight,
                title = {
                    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        Text(stringRes(Res.string.scheduled_posts))
                        if (totalActive > 0) {
                            val queuedText =
                                pluralStringRes(
                                    id = Res.plurals.scheduled_posts_subtitle_queued,
                                    count = totalActive,
                                    totalActive,
                                )
                            val dueText =
                                if (dueSoonCount > 0) {
                                    pluralStringRes(
                                        id = Res.plurals.scheduled_posts_subtitle_due_suffix,
                                        count = dueSoonCount,
                                        dueSoonCount,
                                    )
                                } else {
                                    ""
                                }
                            Text(
                                text = queuedText + dueText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    // Suppress the back arrow when this is the bottom of the back stack
                    // (i.e. the user landed here via the bottom nav, which clears the stack).
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) { ArrowBackIcon() }
                    }
                },
            )
        },
        bottomBar = {
            AppBottomBar(Route.ScheduledPosts, nav, accountViewModel) { route ->
                if (route == Route.ScheduledPosts) {
                    scope.launch { listState.animateScrollToItem(0) }
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyState(onCompose = { nav.nav(Route.NewShortNote()) }, modifier = Modifier.padding(padding))
        } else {
            val today = remember(nowSec) { LocalClock.today() }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { group ->
                    // Lazy keys must be Bundle-storable on Android: a data class key crashes there.
                    stickyHeader(key = "day-${group.day.ymd()}") {
                        DayHeader(group.day, today)
                    }
                    items(group.posts, key = { it.id }) { post ->
                        val isExpanded = expandedId == post.id
                        val rowAlpha by animateFloatAsState(
                            targetValue = if (expandedId != null && !isExpanded) 0.65f else 1f,
                            label = "row-alpha",
                        )
                        Box(modifier = Modifier.alpha(rowAlpha)) {
                            if (isExpanded) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .animateContentSize(),
                                ) {
                                    ScheduledPostCardCollapsed(
                                        post = post,
                                        nowSec = nowSec,
                                        onClick = { expandedId = null },
                                    )
                                    ScheduledPostCardExpandedPanel(
                                        post = post,
                                        onPublishNow = {
                                            viewModel.publishNow(post.id)
                                            accountViewModel.host.requestScheduledPostCatchUp()
                                            expandedId = null
                                        },
                                        onDelete = {
                                            viewModel.cancel(post.id)
                                            expandedId = null
                                        },
                                    )
                                }
                            } else {
                                SwipeToDeleteWithConfirmation(
                                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                                    onDelete = { viewModel.cancel(post.id) },
                                    confirmLabelRes = Res.string.quick_action_delete,
                                ) {
                                    ScheduledPostCardCollapsed(
                                        post = post,
                                        nowSec = nowSec,
                                        onClick = { expandedId = post.id },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val URGENT_THRESHOLD_SEC = 3600L

// Tailwind amber-400. Material 3 has no amber slot, but the publishing-pulse
// reads better than `tertiary` against a violet card.
private val PublishingAmber = Color(0xFFFBBF24)

@Composable
private fun Modifier.urgentEdge(enabled: Boolean): Modifier {
    if (!enabled) return this
    val gradientStart = MaterialTheme.colorScheme.primary
    val gradientEnd = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val brush =
        remember(gradientStart, gradientEnd) {
            Brush.verticalGradient(listOf(gradientStart, gradientEnd))
        }
    return this.drawWithContent {
        drawContent()
        drawRect(
            brush = brush,
            topLeft = Offset(0f, 8.dp.toPx()),
            size = Size(3.dp.toPx(), size.height - 16.dp.toPx()),
        )
    }
}

@Composable
private fun ScheduledPostCardCollapsed(
    post: ScheduledPost,
    nowSec: Long,
    onClick: () -> Unit,
) {
    val preview = remember(post.id) { extractContentPreview(post, 200) }
    val media = remember(post.id) { extractFirstMediaUrl(post) }
    val relayCountText =
        pluralStringRes(
            id = Res.plurals.scheduled_posts_relay_count,
            count = post.relayUrls.size,
            post.relayUrls.size,
        )

    val isFailed = post.status == ScheduledPostStatus.FAILED
    // Composite the tint over surface so the card is opaque — otherwise the
    // SwipeToDismissBox background ("Delete" / "Cancel") bleeds through at rest.
    val surface = MaterialTheme.colorScheme.surface
    val containerColor =
        if (isFailed) {
            MaterialTheme.colorScheme.error
                .copy(alpha = 0.06f)
                .compositeOver(surface)
        } else {
            MaterialTheme.colorScheme.primary
                .copy(alpha = 0.06f)
                .compositeOver(surface)
        }
    val borderColor =
        if (isFailed) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        }

    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .urgentEdge(!isFailed && post.publishAtSec - nowSec in 1..URGENT_THRESHOLD_SEC),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatusPill(post.status)
                Text(
                    text = formatAtTime(post.publishAtSec, nowSec),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (media != null) {
                    MediaThumbnail(media)
                }
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = relayCountText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScheduledPostCardExpandedPanel(
    post: ScheduledPost,
    onPublishNow: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheduledPostsEventIdCopiedStr = stringRes(Res.string.scheduled_posts_event_id_copied)
    val shortNotice = rememberShortNotice()
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val eventId = remember(post.id) { extractEventId(post) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

        Column {
            SectionLabel(stringRes(Res.string.relays))
            post.relayUrls.forEach { url ->
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }

        if (eventId != null) {
            Column {
                SectionLabel(stringRes(Res.string.quick_action_copy_note_id))
                Text(
                    text = eventId,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    scope.launch { clipboardManager.setText(eventId) }
                                    shortNotice.show(scheduledPostsEventIdCopiedStr)
                                },
                            ),
                )
            }
        }

        val err = post.lastError
        if (post.status == ScheduledPostStatus.FAILED && !err.isNullOrBlank()) {
            Text(
                text = stringRes(Res.string.scheduled_posts_error_prefix, err),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onPublishNow,
                enabled = post.status != ScheduledPostStatus.PUBLISHING,
                modifier = Modifier.weight(1f),
            ) {
                val labelRes =
                    when (post.status) {
                        ScheduledPostStatus.FAILED -> Res.string.retry
                        ScheduledPostStatus.PUBLISHING -> Res.string.scheduled_posts_status_publishing
                        else -> Res.string.scheduled_posts_action_send_now
                    }
                Text(stringRes(labelRes))
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(stringRes(Res.string.quick_action_delete))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.toUpperCase(Locale.current),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun DayHeader(
    day: SearchDate,
    today: SearchDate,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = formatDayHeader(day, today),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun StatusPill(status: ScheduledPostStatus) {
    val (labelRes, color, pulse) =
        when (status) {
            ScheduledPostStatus.PENDING -> {
                Triple(Res.string.scheduled_posts_status_pending, MaterialTheme.colorScheme.primary, false)
            }

            ScheduledPostStatus.PUBLISHING -> {
                Triple(Res.string.scheduled_posts_status_publishing, PublishingAmber, true)
            }

            ScheduledPostStatus.FAILED -> {
                Triple(Res.string.scheduled_posts_status_failed, MaterialTheme.colorScheme.error, false)
            }

            ScheduledPostStatus.SENT -> {
                Triple(Res.string.scheduled_posts_status_sent, MaterialTheme.colorScheme.tertiary, false)
            }

            ScheduledPostStatus.CANCELLED -> {
                Triple(Res.string.scheduled_posts_status_cancelled, MaterialTheme.colorScheme.onSurfaceVariant, false)
            }
        }
    val dotAlpha =
        if (pulse) {
            val transition = rememberInfiniteTransition(label = "publishing-pulse")
            transition
                .animateFloat(
                    initialValue = 1f,
                    targetValue = 0.35f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 1400, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "publishing-pulse-alpha",
                ).value
        } else {
            1f
        }

    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.18f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = dotAlpha)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringRes(labelRes),
                color = color,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun EmptyState(
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = MaterialSymbols.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringRes(Res.string.scheduled_posts_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringRes(Res.string.scheduled_posts_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onCompose) {
                Text(stringRes(Res.string.new_post))
            }
        }
    }
}

private val fullDateFormatter = DateSkeletonFormatter("yMMMMEEEEd")

// The locale's own hour cycle, like the FormatStyle.SHORT formatter this replaced; cached per locale.
private val shortTimeFormatter = DateSkeletonFormatter("jm")

@Composable
private fun formatAtTime(
    publishAtSec: Long,
    nowSec: Long,
): String {
    val absolute = shortTimeFormatter.format(publishAtSec * 1000)
    return if (publishAtSec > nowSec) {
        stringRes(Res.string.scheduled_posts_at_time, absolute, timeAheadNoDot(publishAtSec))
    } else {
        stringRes(Res.string.scheduled_posts_at_time_past, absolute, timeAgoNoDot(publishAtSec).trim())
    }
}

@Composable
private fun formatDayHeader(
    day: SearchDate,
    today: SearchDate,
): String =
    when (day) {
        today -> stringRes(Res.string.today)
        today.plusDays(1) -> stringRes(Res.string.scheduled_posts_day_tomorrow)
        else -> fullDateFormatter.format(LocalClock.startOfDay(day) * 1000)
    }
