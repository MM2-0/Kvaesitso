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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.drss.DrssShowSettings
import com.vitorpamplona.amethyst.commons.drss.DrssShowSlot
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeForUser
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relayClient.drss.DrssShowEpisodesQueryState
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drss_add_to_my_lists
import com.vitorpamplona.amethyst.commons.resources.drss_badge_read
import com.vitorpamplona.amethyst.commons.resources.drss_badge_write
import com.vitorpamplona.amethyst.commons.resources.drss_brand
import com.vitorpamplona.amethyst.commons.resources.drss_copy_feeds
import com.vitorpamplona.amethyst.commons.resources.drss_edit
import com.vitorpamplona.amethyst.commons.resources.drss_event_list
import com.vitorpamplona.amethyst.commons.resources.drss_feed_count
import com.vitorpamplona.amethyst.commons.resources.drss_feed_list
import com.vitorpamplona.amethyst.commons.resources.drss_item_count
import com.vitorpamplona.amethyst.commons.resources.drss_list_count
import com.vitorpamplona.amethyst.commons.resources.drss_list_folder
import com.vitorpamplona.amethyst.commons.resources.drss_listen_finished
import com.vitorpamplona.amethyst.commons.resources.drss_listen_loading_episode
import com.vitorpamplona.amethyst.commons.resources.drss_listen_plays
import com.vitorpamplona.amethyst.commons.resources.drss_listen_time_left
import com.vitorpamplona.amethyst.commons.resources.drss_listen_time_of
import com.vitorpamplona.amethyst.commons.resources.drss_listen_title
import com.vitorpamplona.amethyst.commons.resources.drss_listening
import com.vitorpamplona.amethyst.commons.resources.drss_lists_title
import com.vitorpamplona.amethyst.commons.resources.drss_loading
import com.vitorpamplona.amethyst.commons.resources.drss_open_history
import com.vitorpamplona.amethyst.commons.resources.drss_organize
import com.vitorpamplona.amethyst.commons.resources.drss_provenance
import com.vitorpamplona.amethyst.commons.resources.drss_provenance_count
import com.vitorpamplona.amethyst.commons.resources.drss_provenance_folder
import com.vitorpamplona.amethyst.commons.resources.drss_provenance_through
import com.vitorpamplona.amethyst.commons.resources.drss_provenance_via
import com.vitorpamplona.amethyst.commons.resources.drss_read_as_feed
import com.vitorpamplona.amethyst.commons.resources.drss_read_list
import com.vitorpamplona.amethyst.commons.resources.drss_show
import com.vitorpamplona.amethyst.commons.resources.drss_show_keeps_days
import com.vitorpamplona.amethyst.commons.resources.drss_show_keeps_hours
import com.vitorpamplona.amethyst.commons.resources.drss_show_latest
import com.vitorpamplona.amethyst.commons.resources.drss_show_makes
import com.vitorpamplona.amethyst.commons.resources.drss_show_off_air
import com.vitorpamplona.amethyst.commons.resources.drss_show_on_air
import com.vitorpamplona.amethyst.commons.resources.drss_show_publishes_as
import com.vitorpamplona.amethyst.commons.resources.drss_show_voice
import com.vitorpamplona.amethyst.commons.resources.drss_untitled
import com.vitorpamplona.amethyst.commons.resources.drss_write_list
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.drss.eventList.DrssEventListEvent
import com.vitorpamplona.quartz.drss.feedList.DrssFeedListEvent
import com.vitorpamplona.quartz.drss.feedList.tags.FeedTag
import com.vitorpamplona.quartz.drss.listFolder.DrssListFolderEvent
import com.vitorpamplona.quartz.drss.listenProgress.DrssListenProgress
import com.vitorpamplona.quartz.drss.provenance.DrssProvenanceEvent
import com.vitorpamplona.quartz.drss.provenance.DrssProvenanceFolderEvent
import com.vitorpamplona.quartz.drss.readWriteLists.DrssReadWriteListsEvent
import com.vitorpamplona.quartz.drss.show.DrssShowEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip51Lists.articleCurationSet.ArticleCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.BookmarkSetEvent
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag

/*
 * Cards for drss.io's reader (RSS and podcasts bridged to Nostr): what a listener has heard, the
 * shows drss generates, and the lists, folders and provenance notes a reader keeps. They follow
 * drss's own look: a rounded surface with a hairline border, a small accent overline naming what
 * the card is, outlined pill chips for the things inside it, and covers and avatars up front.
 */

private val DrssShape = RoundedCornerShape(16.dp)
private val DrssInnerShape = RoundedCornerShape(12.dp)
private const val MAX_ROWS = 5
private const val MAX_AVATARS = 8

/** A folder's chips: each one loads and observes its list, so a folder of hundreds stops here. */
private const val MAX_CHIPS = 12

/** The frame every drss card shares; the whole card opens [onClick] when given. */
@Composable
private fun DrssSurface(
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(DrssShape)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), DrssShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** The small accent line that opens a card: the kind's glyph and name, and drss's mark at the end. */
@Composable
private fun DrssOverline(
    icon: MaterialSymbol,
    label: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(symbol = icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            trailing()
        } else {
            DrssMark()
        }
    }
}

@Composable
private fun DrssMark() {
    Text(
        text = stringRes(Res.string.drss_brand),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.grayText,
        modifier =
            Modifier
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), CircleShape)
                .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

/** A card's title, with the count of what it holds under it. */
@Composable
private fun DrssTitle(
    title: String,
    meta: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        meta?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = 1)
        }
    }
}

/** A list's [title] beside its [picture], when it has one. */
@Composable
private fun DrssTitleWithPicture(
    picture: String?,
    fallback: MaterialSymbol,
    accountViewModel: AccountViewModel,
    title: @Composable () -> Unit,
) {
    if (picture == null) {
        title()
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DrssCover(picture, 56.dp, fallback, accountViewModel)
            Box(Modifier.weight(1f)) { title() }
        }
    }
}

/** drss's outlined pill: a sub-list, a tag, a schedule slot. */
@Composable
internal fun DrssChip(
    label: String,
    icon: MaterialSymbol? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier =
            Modifier
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), CircleShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        icon?.let { Icon(symbol = it, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(14.dp)) }
        Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A square cover, or the [fallback] glyph on an accent wash while there is none. */
@Composable
internal fun DrssCover(
    url: String?,
    size: Dp,
    fallback: MaterialSymbol,
    accountViewModel: AccountViewModel,
) {
    val modifier = Modifier.size(size).clip(DrssInnerShape)
    val placeholder: @Composable () -> Unit = { DrssCoverPlaceholder(size, fallback) }
    if (url == null) {
        placeholder()
    } else {
        MyAsyncImage(
            imageUrl = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            mainImageModifier = modifier,
            loadedImageModifier = modifier,
            accountViewModel = accountViewModel,
            onLoadingBackground = placeholder,
            onError = placeholder,
        )
    }
}

@Composable
private fun DrssCoverPlaceholder(
    size: Dp,
    icon: MaterialSymbol,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(DrssInnerShape)
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.35f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(symbol = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size / 2.5f))
    }
}

/** A user's avatar and name on one line; tapping it opens the profile. */
@Composable
private fun DrssUserRow(
    pubKey: String,
    label: String?,
    trailing: String?,
    avatarSize: Dp,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(DrssInnerShape)
                .clickable { nav.nav(routeForUser(pubKey)) }
                .padding(vertical = 4.dp),
    ) {
        UserPicture(pubKey, avatarSize, accountViewModel = accountViewModel, nav = nav)
        Column(Modifier.weight(1f)) {
            if (label != null) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                LoadUser(pubKey) { user ->
                    if (user != null) UsernameDisplay(user, fontWeight = FontWeight.SemiBold, accountViewModel = accountViewModel)
                }
            }
            trailing?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun ATag.address() = Address(kind, pubKeyHex, dTag)

/** `1:02:03` for an hour or more, `12:03` below. */
private fun formatClock(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
}

/** The host of a URL (`blog.senko.net`), or [source] itself when it is not one. */
private fun sourceHost(source: String): String =
    if (source.startsWith("https://") || source.startsWith("http://")) {
        source.substringAfter("://").substringBefore('/').removePrefix("www.")
    } else {
        source
    }

// ---- listening progress (30078, and the older 30054) ----

/**
 * drss.io's listening progress: the episode's cover and title, the feed it comes from, and how far
 * the listener got, as a bar. Tapping it opens the episode.
 */
@Composable
fun RenderDrssListenProgress(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<Event>(note, accountViewModel)
    val progress = event as? DrssListenProgress ?: return
    val episode = remember(progress) { progress.episode() }

    if (episode == null) {
        DrssSurface {
            DrssOverline(MaterialSymbols.Headphones, stringRes(Res.string.drss_listening))
            DrssListenBar(progress)
        }
        return
    }

    LoadAddressableNote(episode.address()) { episodeNote ->
        DrssSurface(onClick = episodeNote?.let { { routeFor(it, accountViewModel.account)?.let(nav::nav) } }) {
            DrssOverline(MaterialSymbols.Headphones, stringRes(Res.string.drss_listening))
            if (episodeNote != null) {
                DrssEpisodeRow(episodeNote, episode.pubKeyHex, accountViewModel)
            } else {
                Text(stringRes(Res.string.drss_listen_loading_episode), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.grayText)
            }
            DrssListenBar(progress)
            if (progress.pubKey == accountViewModel.account.pubKey) DrssListenLinks(nav)
        }
    }
}

/** Ways from one of the user's listening records to the queue and the history. */
@Composable
private fun DrssListenLinks(nav: INav) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DrssChip(stringRes(Res.string.drss_listen_title), MaterialSymbols.Headphones) { nav.nav(Route.DrssListen) }
        DrssChip(stringRes(Res.string.drss_open_history), MaterialSymbols.History) { nav.nav(Route.DrssHistory) }
    }
}

@Composable
private fun DrssEpisodeRow(
    episodeNote: Note,
    feedPubKey: String,
    accountViewModel: AccountViewModel,
) {
    // drss points progress at its 30023 articles, but the address may name any kind: read the
    // title from whatever loads rather than waiting for an article that will never come.
    val episode by observeNoteEvent<Event>(episodeNote, accountViewModel)
    val title = remember(episode) { episode?.let(::itemLabel) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DrssCover((episode as? LongFormContentEvent)?.image(), 64.dp, MaterialSymbols.Podcasts, accountViewModel)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LoadUser(feedPubKey) { user ->
                if (user != null) {
                    UsernameDisplay(user, fontWeight = FontWeight.Normal, textColor = MaterialTheme.colorScheme.grayText, accountViewModel = accountViewModel)
                }
            }
            Text(
                text = title ?: stringRes(if (episode == null) Res.string.drss_listen_loading_episode else Res.string.drss_untitled),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** How far the listener got: a bar, the time heard and left, or a finished check and the play count. */
@Composable
private fun DrssListenBar(progress: DrssListenProgress) {
    val position = progress.position()
    val duration = progress.durationInSeconds()?.takeIf { it > 0 }
    val finished = progress.completedAt() != null && position == null
    val fraction =
        when {
            finished -> 1f
            position != null && duration != null -> (position.toFloat() / duration).coerceIn(0f, 1f)
            else -> null
        }
    val accent = if (finished) MaterialTheme.colorScheme.allGoodColor else MaterialTheme.colorScheme.primary

    if (fraction != null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.7f), accent))),
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (finished) {
            Icon(symbol = MaterialSymbols.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Text(stringRes(Res.string.drss_listen_finished), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = accent)
        } else if (position != null) {
            Text(
                text = if (duration != null) stringRes(Res.string.drss_listen_time_of, formatClock(position), formatClock(duration)) else formatClock(position),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        } else if (duration != null) {
            Text(formatClock(duration), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        val plays = progress.plays()?.toInt()
        if (plays != null && plays > 0) {
            DrssChip(pluralStringRes(Res.plurals.drss_listen_plays, plays, plays), MaterialSymbols.Replay)
        } else if (!finished && position != null && duration != null && duration > position) {
            Text(
                text = stringRes(Res.string.drss_listen_time_left, formatClock(duration - position)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}

// ---- 31341 show ----

/**
 * A podcast drss.io generates: its cover (or an accent wash) with the name and whether it is on
 * air, the bot it publishes from, when it publishes, and what each episode is made of.
 */
@Composable
fun RenderDrssShow(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<DrssShowEvent>(note, accountViewModel)
    val show = event ?: return
    val settings = remember(show) { DrssShowSettings.of(show) }
    val name = settings.name ?: show.showName() ?: show.dTag()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(DrssShape)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), DrssShape),
    ) {
        DrssShowHero(name, settings.picture, show.isActive(), accountViewModel)
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            show.showBotKey()?.let { bot ->
                DrssUserRow(bot, null, stringRes(Res.string.drss_show_publishes_as), 32.dp, accountViewModel, nav)
            }
            val facts =
                buildList {
                    settings.schedule.forEach { add(slotLabel(it) to MaterialSymbols.Schedule) }
                    settings.language?.let { add(it.uppercase() to MaterialSymbols.Language) }
                    settings.voice?.let { add(stringRes(Res.string.drss_show_voice, it) to MaterialSymbols.Mic) }
                }
            if (facts.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    facts.forEach { (label, icon) -> DrssChip(label, icon) }
                }
            }
            if (settings.products.isNotEmpty()) {
                Text(stringRes(Res.string.drss_show_makes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.grayText)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    settings.products.forEach { DrssChip(it.replaceFirstChar(Char::uppercase)) }
                }
            }
            if (settings.tags.isNotEmpty()) {
                Text(
                    text = settings.tags.joinToString("  ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            settings.retentionHours?.let { hours ->
                Text(
                    text =
                        if (hours % 24 == 0) {
                            pluralStringRes(Res.plurals.drss_show_keeps_days, hours / 24, hours / 24)
                        } else {
                            pluralStringRes(Res.plurals.drss_show_keeps_hours, hours, hours)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
            show.showBotKey()?.let { DrssShowEpisodes(it, accountViewModel, nav) }
            if (show.pubKey == accountViewModel.account.signer.pubKey) {
                DrssChip(stringRes(Res.string.drss_edit), MaterialSymbols.Edit) { nav.nav(Route.DrssShowEdit(show.address().toValue())) }
            }
        }
    }
}

/** The show's latest episodes, newest first, each opening the episode; nothing until one loads. */
@Composable
private fun DrssShowEpisodes(
    bot: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadUser(bot) { user -> user?.let { DrssShowEpisodesSubscription(it, accountViewModel) } }
    val episodes by remember(bot) {
        LocalCache.observeNotes(Filter(kinds = listOf(LongFormContentEvent.KIND), authors = listOf(bot), limit = MAX_EPISODES))
    }.collectAsStateWithLifecycle(emptyList())
    if (episodes.isEmpty()) return

    Text(stringRes(Res.string.drss_show_latest), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.grayText)
    Column(Modifier.clip(DrssInnerShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))) {
        episodes.take(MAX_EPISODES).forEach { DrssShowEpisodeRow(it, accountViewModel, nav) }
    }
}

@Composable
private fun DrssShowEpisodesSubscription(
    bot: User,
    accountViewModel: AccountViewModel,
) {
    val state = remember(bot) { DrssShowEpisodesQueryState(bot) }
    LifecycleAwareKeyDataSourceSubscription(state, accountViewModel.dataSources().drssShowEpisodes)
}

@Composable
private fun DrssShowEpisodeRow(
    episodeNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val episode by observeNoteEvent<LongFormContentEvent>(episodeNote, accountViewModel)
    val article = episode ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { routeFor(episodeNote, accountViewModel.account)?.let(nav::nav) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        DrssCover(article.image(), 40.dp, MaterialSymbols.Podcasts, accountViewModel)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = article.title() ?: stringRes(Res.string.drss_untitled),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            (episodeNote as? AddressableNote)?.let { DrssHeardBadge(it, article, accountViewModel) }
        }
    }
}

private const val MAX_EPISODES = 3

// ---- 30078 read-and-write lists ----

/** A drss.io reader's read and write lists: the folder their river reads, and where follows and saves go. */
@Composable
fun RenderDrssReadWriteLists(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<DrssReadWriteListsEvent>(note, accountViewModel)
    val preference = event ?: return
    val read = preference.readList()
    val write = preference.writeList()

    DrssSurface {
        DrssOverline(MaterialSymbols.Folder, stringRes(Res.string.drss_lists_title))
        read?.let { DrssRoleLine(stringRes(Res.string.drss_read_list), it, accountViewModel, nav) }
        write?.takeIf { it.toTag() != read?.toTag() }?.let { DrssRoleLine(stringRes(Res.string.drss_write_list), it, accountViewModel, nav) }
    }
}

@Composable
private fun DrssRoleLine(
    label: String,
    list: ATag,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.grayText)
        LoadAddressableNote(list.address()) { listNote ->
            DrssListChip(list, listNote, accountViewModel, nav)
        }
    }
}

/**
 * What the reader can do with a folder or feed list: read it as the home feed; for their own,
 * see where it is their river or write list and open the organizer; for someone else's, put it in
 * their lists or, for a feed list, copy its feeds into a follow set of their own.
 */
@Composable
private fun DrssListActions(
    list: BaseAddressableEvent,
    feedsToCopy: List<FeedTag>?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account
    val drss = account.drss
    val read by drss.readList.collectAsStateWithLifecycle()
    val write by drss.writeList.collectAsStateWithLifecycle()
    val address = remember(list) { list.address() }
    val isMine = list.pubKey == account.signer.pubKey

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DrssChip(stringRes(Res.string.drss_read_as_feed), MaterialSymbols.AutoMirrored.Feed) {
            account.settings.changeDefaultHomeFollowList(TopFilter.PeopleList(address))
            nav.newStack(Route.Home)
        }
        if (isMine) {
            if (address == read) {
                DrssChip(stringRes(Res.string.drss_badge_read), MaterialSymbols.CheckCircle)
                DrssChip(stringRes(Res.string.drss_listen_title), MaterialSymbols.Headphones) { nav.nav(Route.DrssListen) }
            }
            if (address == write) DrssChip(stringRes(Res.string.drss_badge_write), MaterialSymbols.CheckCircle)
            if (drss.preference.value != null || list is DrssListFolderEvent) {
                DrssChip(stringRes(Res.string.drss_organize), MaterialSymbols.Folder) { nav.nav(Route.DrssLists) }
            }
        } else if (write != null && account.isWriteable()) {
            DrssChip(stringRes(Res.string.drss_add_to_my_lists), MaterialSymbols.Add) {
                accountViewModel.launchSigner { drss.followList(address, account) }
            }
            if (!feedsToCopy.isNullOrEmpty()) {
                val title = (list as? DrssFeedListEvent)?.title() ?: address.dTag.substringAfterLast('/')
                DrssChip(stringRes(Res.string.drss_copy_feeds), MaterialSymbols.ContentCopy) {
                    accountViewModel.launchSigner { drss.newFollowSet(title, feedsToCopy.map { UserTag(it.pubKey, it.relayHint) }, account) }
                }
            }
        }
    }
}

private fun slotLabel(slot: DrssShowSlot): String {
    val part = slot.part?.replaceFirstChar(Char::uppercase)
    val zone = slot.zone?.substringAfterLast('/')?.replace('_', ' ')
    return listOfNotNull(part, slot.time, zone).joinToString(" · ")
}

@Composable
private fun DrssShowHero(
    name: String,
    picture: String?,
    onAir: Boolean,
    accountViewModel: AccountViewModel,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)))),
    ) {
        if (picture != null) {
            MyAsyncImage(
                imageUrl = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.fillMaxSize(),
                loadedImageModifier = Modifier.fillMaxSize(),
                accountViewModel = accountViewModel,
                onLoadingBackground = null,
                onError = null,
            )
        }
        // Keeps the name readable on any cover.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))))
        DrssOnAirPill(onAir, Modifier.align(Alignment.TopEnd).padding(12.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(symbol = MaterialSymbols.Podcasts, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(
                    text = stringRes(Res.string.drss_show).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DrssOnAirPill(
    onAir: Boolean,
    modifier: Modifier,
) {
    val dot = if (onAir) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.5f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(
            text = stringRes(if (onAir) Res.string.drss_show_on_air else Res.string.drss_show_off_air),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

// ---- 30101 list folder ----

/** A drss.io folder of lists: its title and one chip per list in it, each opening that list. */
@Composable
fun RenderDrssListFolder(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<DrssListFolderEvent>(note, accountViewModel)
    val folder = event ?: return
    val children = remember(folder) { folder.children() }

    DrssSurface {
        DrssOverline(MaterialSymbols.Folder, stringRes(Res.string.drss_list_folder))
        DrssTitleWithPicture(folder.thumb()?.imageUrl ?: folder.image(), MaterialSymbols.Folder, accountViewModel) {
            DrssTitle(folder.title() ?: folder.dTag(), pluralStringRes(Res.plurals.drss_list_count, children.size, children.size))
        }
        if (children.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                children.take(MAX_CHIPS).forEach { child ->
                    LoadAddressableNote(child.address()) { childNote ->
                        DrssListChip(child, childNote, accountViewModel, nav)
                    }
                }
            }
            DrssMoreRows(children.size - MAX_CHIPS)
        }
        DrssListActions(folder, null, accountViewModel, nav)
    }
}

@Composable
private fun DrssListChip(
    child: ATag,
    childNote: Note?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = childNote?.let { observeNoteEvent<Event>(it, accountViewModel).value }
    val title = remember(event) { event?.let(::listTitle) }
    DrssChip(
        label = title ?: child.dTag.ifBlank { stringRes(Res.string.drss_untitled) },
        icon = listIcon(child.kind),
        onClick = childNote?.let { { routeFor(it, accountViewModel.account)?.let(nav::nav) } },
    )
}

/** The title a list or folder gives itself. */
private fun listTitle(event: Event): String? =
    when (event) {
        is DrssListFolderEvent -> event.title()
        is DrssFeedListEvent -> event.title()
        is DrssEventListEvent -> event.title()
        is DrssProvenanceFolderEvent -> event.title()
        is FollowSetEvent -> event.titleOrName()
        is BookmarkSetEvent -> event.titleOrName()
        is ArticleCurationSetEvent -> event.title()
        else -> null
    }?.ifBlank { null }

private fun listIcon(kind: Int): MaterialSymbol =
    when (kind) {
        DrssListFolderEvent.KIND -> MaterialSymbols.Folder
        DrssProvenanceFolderEvent.KIND -> MaterialSymbols.TravelExplore
        DrssEventListEvent.KIND -> MaterialSymbols.Collections
        ArticleCurationSetEvent.KIND -> MaterialSymbols.AutoMirrored.Article
        BookmarkSetEvent.KIND -> MaterialSymbols.Bookmark
        else -> MaterialSymbols.AutoMirrored.Feed
    }

// ---- 30100 feed list ----

/** A drss.io feed list: the feeds' avatars stacked, then a row per feed with its title. */
@Composable
fun RenderDrssFeedList(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<DrssFeedListEvent>(note, accountViewModel)
    val list = event ?: return
    val feeds = remember(list) { list.feeds() }
    val feedKeys = remember(feeds) { feeds.map { it.pubKey } }

    DrssSurface {
        DrssOverline(MaterialSymbols.AutoMirrored.Feed, stringRes(Res.string.drss_feed_list))
        DrssTitle(list.title() ?: list.dTag().substringAfterLast('/'), pluralStringRes(Res.plurals.drss_feed_count, feeds.size, feeds.size))
        DrssAvatarStack(feedKeys, accountViewModel, nav)
        feeds.take(MAX_ROWS).forEach { feed: FeedTag ->
            DrssUserRow(feed.pubKey, feed.title, null, 32.dp, accountViewModel, nav)
        }
        DrssMoreRows(feeds.size - MAX_ROWS)
        DrssListActions(list, feeds, accountViewModel, nav)
    }
}

@Composable
private fun DrssAvatarStack(
    pubKeys: List<String>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (pubKeys.size < 2) return
    Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
        pubKeys.take(MAX_AVATARS).forEach { key ->
            Box(Modifier.clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.background, CircleShape)) {
                UserPicture(key, 30.dp, accountViewModel = accountViewModel, nav = nav)
            }
        }
    }
}

@Composable
private fun DrssMoreRows(count: Int) {
    if (count <= 0) return
    KindCardMore(count)
}

// ---- 30102 event list ----

/** A drss.io collection of events: its title, then a row per item with what it is and says. */
@Composable
fun RenderDrssEventList(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<DrssEventListEvent>(note, accountViewModel)
    val list = event ?: return
    val items = remember(list) { list.itemIds() }

    DrssSurface {
        DrssOverline(MaterialSymbols.Collections, stringRes(Res.string.drss_event_list))
        DrssTitle(list.title() ?: list.dTag(), pluralStringRes(Res.plurals.drss_item_count, items.size, items.size))
        Column(Modifier.clip(DrssInnerShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))) {
            items.take(MAX_ROWS).forEach { id ->
                LoadNote(id) { itemNote -> DrssItemRow(itemNote, accountViewModel, nav) }
            }
        }
        DrssMoreRows(items.size - MAX_ROWS)
    }
}

@Composable
private fun DrssItemRow(
    itemNote: Note?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = itemNote?.let { observeNoteEvent<Event>(it, accountViewModel).value }
    val label = remember(event) { event?.let(::itemLabel) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (itemNote != null) Modifier.clickable { routeFor(itemNote, accountViewModel.account)?.let(nav::nav) } else Modifier)
                .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Icon(symbol = MaterialSymbols.Description, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label ?: stringRes(if (event == null) Res.string.drss_loading else Res.string.drss_untitled),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            event?.let {
                Text(text = kindName(it.kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = 1)
            }
        }
    }
}

/** What an item calls itself: its title, its `alt`, or its first line. */
private fun itemLabel(event: Event): String? =
    (
        (event as? LongFormContentEvent)?.title()
            ?: event.tags.fastFirstNotNullOfOrNull(TitleTag::parse)
            ?: event.alt()
            ?: event.content.lineSequence().firstOrNull { it.isNotBlank() }
    )?.trim()?.take(120)

// ---- 31985 provenance ----

/**
 * A feed a drss.io reader found: the feed's avatar and name, where it was found, the reader's note
 * about it, and the article that led there.
 */
@Composable
fun RenderDrssProvenance(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<DrssProvenanceEvent>(note, accountViewModel)
    val record = event ?: return
    val uriHandler = LocalUriHandler.current

    DrssSurface {
        DrssOverline(MaterialSymbols.TravelExplore, stringRes(Res.string.drss_provenance))
        record.feedKey()?.let { feed ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                UserPicture(feed, 48.dp, accountViewModel = accountViewModel, nav = nav)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LoadUser(feed) { user ->
                        if (user != null) UsernameDisplay(user, fontWeight = FontWeight.Bold, accountViewModel = accountViewModel)
                    }
                    record.source()?.let { source ->
                        val isUrl = source.startsWith("https://") || source.startsWith("http://")
                        DrssChip(
                            label = stringRes(Res.string.drss_provenance_via, sourceHost(source)),
                            icon = MaterialSymbols.Link,
                            onClick = if (isUrl) ({ runCatching { uriHandler.openUri(source) } }) else null,
                        )
                    }
                }
            }
        }
        record.note().takeIf { it.isNotBlank() }?.let { text ->
            DrssQuote {
                TranslatableRichTextViewer(
                    content = text,
                    canPreview = false,
                    quotesLeft = 0,
                    tags = remember(record) { record.tags.toImmutableListOfLists() },
                    backgroundColor = backgroundColor,
                    id = note.idHex + "-drss-note",
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
        record.foundThrough()?.let { article ->
            LoadAddressableNote(article.address()) { articleNote ->
                articleNote?.let { DrssFoundThrough(it, accountViewModel, nav) }
            }
        }
    }
}

@Composable
private fun DrssQuote(content: @Composable BoxScope.() -> Unit) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Box(Modifier.padding(start = 12.dp), content = content)
    }
}

@Composable
private fun DrssFoundThrough(
    articleNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val article by observeNoteEvent<LongFormContentEvent>(articleNote, accountViewModel)
    val title = article?.title() ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(DrssInnerShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                .clickable { routeFor(articleNote, accountViewModel.account)?.let(nav::nav) }
                .padding(10.dp),
    ) {
        DrssCover(article?.image(), 40.dp, MaterialSymbols.AutoMirrored.Article, accountViewModel)
        Column(Modifier.weight(1f)) {
            Text(stringRes(Res.string.drss_provenance_through), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.grayText)
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---- 30104 provenance folder ----

/** A drss.io folder of provenance notes: a row per feed found, with where it was found. */
@Composable
fun RenderDrssProvenanceFolder(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<DrssProvenanceFolderEvent>(note, accountViewModel)
    val folder = event ?: return
    val children = remember(folder) { folder.children() }
    val records = remember(children) { children.filter { it.kind == DrssProvenanceEvent.KIND } }
    val subFolders = remember(children) { children.filter { it.kind != DrssProvenanceEvent.KIND } }

    DrssSurface {
        DrssOverline(MaterialSymbols.TravelExplore, stringRes(Res.string.drss_provenance_folder))
        DrssTitle(folder.title() ?: folder.dTag(), pluralStringRes(Res.plurals.drss_provenance_count, records.size, records.size))
        records.take(MAX_ROWS).forEach { child ->
            LoadAddressableNote(child.address()) { recordNote ->
                recordNote?.let { DrssProvenanceRow(it, accountViewModel, nav) }
            }
        }
        DrssMoreRows(records.size - MAX_ROWS)
        if (subFolders.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                subFolders.take(MAX_CHIPS).forEach { child ->
                    LoadAddressableNote(child.address()) { childNote -> DrssListChip(child, childNote, accountViewModel, nav) }
                }
            }
            DrssMoreRows(subFolders.size - MAX_CHIPS)
        }
    }
}

@Composable
private fun DrssProvenanceRow(
    recordNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val record by observeNoteEvent<DrssProvenanceEvent>(recordNote, accountViewModel)
    val feed = record?.feedKey() ?: return
    DrssUserRow(feed, null, record?.source()?.let(::sourceHost), 32.dp, accountViewModel, nav)
}
