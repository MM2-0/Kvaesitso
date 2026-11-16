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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.softwareapps

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.softwareapps.SoftwareReleasesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.loading_feed
import com.vitorpamplona.amethyst.commons.resources.nip82_no_comments
import com.vitorpamplona.amethyst.commons.resources.nip82_older_releases_hide
import com.vitorpamplona.amethyst.commons.resources.nip82_older_releases_show
import com.vitorpamplona.amethyst.commons.resources.nip82_prereleases_hide
import com.vitorpamplona.amethyst.commons.resources.nip82_prereleases_show
import com.vitorpamplona.amethyst.commons.resources.nip82_section_about
import com.vitorpamplona.amethyst.commons.resources.nip82_section_latest_release
import com.vitorpamplona.amethyst.commons.resources.nip82_section_license
import com.vitorpamplona.amethyst.commons.resources.nip82_section_links
import com.vitorpamplona.amethyst.commons.resources.nip82_section_platforms
import com.vitorpamplona.amethyst.commons.resources.nip82_section_prereleases
import com.vitorpamplona.amethyst.commons.resources.nip82_section_topics
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareReleases
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.commons.ui.note.types.AppAuthorLine
import com.vitorpamplona.amethyst.commons.ui.note.types.AppIcon
import com.vitorpamplona.amethyst.commons.ui.note.types.AppLinksColumn
import com.vitorpamplona.amethyst.commons.ui.note.types.Chip
import com.vitorpamplona.amethyst.commons.ui.note.types.LicenseChip
import com.vitorpamplona.amethyst.commons.ui.note.types.PlatformChips
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderSoftwareReleaseBody
import com.vitorpamplona.amethyst.commons.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.commons.ui.note.types.ScreenshotsStrip
import com.vitorpamplona.amethyst.commons.ui.note.types.TopicChipFlow
import com.vitorpamplona.amethyst.commons.ui.note.types.VersionChip
import com.vitorpamplona.amethyst.commons.ui.note.types.produceNip82Releases
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.threadview.dal.ThreadFeedViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.threadview.datasources.ThreadFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.PaddingHorizontal12Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.ui.thread.drawReplyLevel
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource

@Composable
fun SoftwareAppDetailScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address) { note ->
        note?.let {
            SoftwareAppDetailScreenContent(
                note = it,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun SoftwareAppDetailScreenContent(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<SoftwareApplicationEvent>(note, accountViewModel)

    // Drive the comments thread off the app's address tag — ThreadAssembler
    // resolves that to the application note and then walks replies.
    val addressTag = note.idHex
    val threadViewModel: ThreadFeedViewModel =
        rememberViewModel(
            key = addressTag + "SoftwareAppDetailThread",
            factory = ThreadFeedViewModel.Factory(accountViewModel.account, addressTag),
        )
    WatchLifecycleAndUpdateModel(threadViewModel)
    ThreadFilterAssemblerSubscription(addressTag, accountViewModel)
    EventFinderFilterAssemblerSubscription(note, accountViewModel)
    SoftwareReleasesFilterAssemblerSubscription(note, accountViewModel.dataSources().softwareReleases)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarWithBackButton(
                caption = event?.name() ?: event?.appId() ?: note.dTag(),
                nav = nav,
            )
        },
        accountViewModel = accountViewModel,
    ) {
        val current = event
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringRes(Res.string.loading_feed),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            }
        } else {
            SoftwareAppDetailBody(
                note = note,
                event = current,
                threadViewModel = threadViewModel,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun SoftwareAppDetailBody(
    note: AddressableNote,
    event: SoftwareApplicationEvent,
    threadViewModel: ThreadFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val icon = remember(event) { event.icon() }
    val name = remember(event) { event.name() ?: event.appId().orEmpty() }
    val summary = remember(event) { event.summary() }
    val description = remember(event) { event.content.trim() }
    val images = remember(event) { event.images() }
    val topics = remember(event) { event.topics().toImmutableList() }
    val platforms = remember(event) { event.platforms() }
    val license = remember(event) { event.license() }
    val website = remember(event) { event.url() }
    val repo = remember(event) { event.repository() }
    val gitRepo = remember(event) { event.gitRepository()?.let { Address(it.kind, it.pubKeyHex, it.dTag) } }

    val releases by produceNip82Releases(event)
    val arranged = remember(releases) { SoftwareReleases.arrange(releases) }
    val latestRelease = arranged.latest
    val preReleases = arranged.preReleases
    val olderReleases = arranged.older

    val background = MaterialTheme.colorScheme.background
    val backgroundColor = remember(background) { mutableStateOf(background) }

    val threadState by threadViewModel.feedState.feedContent.collectAsStateWithLifecycle()
    val comments: List<Note> =
        when (val s = threadState) {
            is FeedState.Loaded ->
                s.feed
                    .collectAsStateWithLifecycle()
                    .value.list
                    .filter { it.idHex != note.idHex }
            else -> emptyList()
        }

    var showOlder by rememberSaveable(event.id) { mutableStateOf(false) }
    var showEarlierPreReleases by rememberSaveable(event.id) { mutableStateOf(false) }

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = threadViewModel.llState,
    ) {
        item(key = "header") {
            AppDetailHeader(
                note = note,
                icon = icon,
                name = name,
                summary = summary,
                latestVersion = latestRelease?.version(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        if (images.isNotEmpty()) {
            item(key = "screenshots") {
                Spacer(Modifier.height(12.dp))
                ScreenshotsStrip(images, accountViewModel, contentPadding = PaddingValues(horizontal = 12.dp))
            }
        }

        if (description.isNotBlank()) {
            item(key = "about") {
                Spacer(Modifier.height(12.dp))
                Section(title = stringRes(Res.string.nip82_section_about)) {
                    val tags = remember(event) { event.tags.toImmutableListOfLists() }
                    TranslatableRichTextViewer(
                        content = description,
                        canPreview = true,
                        quotesLeft = 1,
                        modifier = Modifier.fillMaxWidth(),
                        tags = tags,
                        backgroundColor = backgroundColor,
                        id = note.idHex,
                        callbackUri = note.toNostrUri(),
                        authorPubKey = event.pubKey,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }

        if (platforms.isNotEmpty()) {
            item(key = "platforms") {
                Spacer(Modifier.height(12.dp))
                Section(title = stringRes(Res.string.nip82_section_platforms)) {
                    PlatformChips(platforms)
                }
            }
        }

        if (license != null) {
            item(key = "license") {
                Spacer(Modifier.height(12.dp))
                Section(title = stringRes(Res.string.nip82_section_license)) {
                    LicenseChip(license)
                }
            }
        }

        if (topics.isNotEmpty()) {
            item(key = "topics") {
                Spacer(Modifier.height(12.dp))
                Section(title = stringRes(Res.string.nip82_section_topics)) {
                    TopicChipFlow(topics = topics, nav = nav)
                }
            }
        }

        if (website != null || repo != null || gitRepo != null) {
            item(key = "links") {
                Spacer(Modifier.height(12.dp))
                Section(title = stringRes(Res.string.nip82_section_links)) {
                    AppLinksColumn(website = website, repository = repo, gitRepository = gitRepo, nav = nav)
                }
            }
        }

        if (latestRelease != null) {
            item(key = "latest-release") {
                Spacer(Modifier.height(12.dp))
                Column(PaddingHorizontal12Modifier) {
                    SectionLabel(stringRes(Res.string.nip82_section_latest_release))
                    RenderSoftwareReleaseBody(
                        event = latestRelease,
                        app = event,
                        backgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }

        if (preReleases.isNotEmpty()) {
            item(key = "pre-releases-label") {
                Spacer(Modifier.height(12.dp))
                Column(PaddingHorizontal12Modifier) {
                    SectionLabel(stringRes(Res.string.nip82_section_prereleases))
                }
            }
            // The newest one only: an app that ships nightlies can have dozens ahead of its last
            // stable release, and each body loads its assets.
            items(
                if (showEarlierPreReleases) preReleases else preReleases.subList(0, 1),
                key = { "pre-${it.id}" },
            ) { release ->
                Column(PaddingHorizontal12Modifier) {
                    RenderSoftwareReleaseBody(
                        event = release,
                        app = event,
                        backgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
            if (preReleases.size > 1) {
                item(key = "pre-releases-toggle") {
                    Spacer(Modifier.height(8.dp))
                    ReleasesToggle(
                        count = preReleases.size - 1,
                        expanded = showEarlierPreReleases,
                        showLabel = Res.string.nip82_prereleases_show,
                        hideLabel = Res.string.nip82_prereleases_hide,
                        onToggle = { showEarlierPreReleases = !showEarlierPreReleases },
                    )
                }
            }
        }

        if (olderReleases.isNotEmpty()) {
            item(key = "older-releases-toggle") {
                Spacer(Modifier.height(8.dp))
                ReleasesToggle(
                    count = olderReleases.size,
                    expanded = showOlder,
                    showLabel = Res.string.nip82_older_releases_show,
                    hideLabel = Res.string.nip82_older_releases_hide,
                    onToggle = { showOlder = !showOlder },
                )
            }
            if (showOlder) {
                items(
                    olderReleases,
                    key = { "older-${it.id}" },
                ) { release ->
                    Spacer(Modifier.height(8.dp))
                    Column(PaddingHorizontal12Modifier) {
                        RenderSoftwareReleaseBody(
                            event = release,
                            app = event,
                            backgroundColor = backgroundColor,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }
        }

        item(key = "reactions") {
            Spacer(Modifier.height(12.dp))
            ReactionsRow(
                baseNote = note,
                showReactionDetail = true,
                addPadding = true,
                editState = null,
                accountViewModel = accountViewModel,
                nav = nav,
            )
            HorizontalDivider(thickness = DividerThickness)
        }

        if (comments.isEmpty()) {
            item(key = "no-comments") {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringRes(Res.string.nip82_no_comments),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                    modifier = PaddingHorizontal12Modifier,
                )
            }
        }

        itemsIndexed(
            comments,
            key = { _, item -> item.idHex },
            contentType = { _, _ -> "comment" },
        ) { _, item ->
            val level = threadViewModel.levelFlowForItem(item).collectAsStateWithLifecycle(0)

            NoteCompose(
                baseNote = item,
                modifier =
                    Modifier.drawReplyLevel(
                        level = level,
                        color = MaterialTheme.colorScheme.placeholderText,
                        selected = MaterialTheme.colorScheme.placeholderText,
                    ),
                isBoostedNote = false,
                unPackReply = ReplyRenderType.NONE,
                quotesLeft = 3,
                accountViewModel = accountViewModel,
                nav = nav,
            )
            HorizontalDivider(thickness = DividerThickness)
        }
    }
}

@Composable
private fun AppDetailHeader(
    note: AddressableNote,
    icon: String?,
    name: String,
    summary: String?,
    latestVersion: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = PaddingHorizontal12Modifier,
    ) {
        AppIcon(icon = icon, name = name, sizeDp = 72)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            AppAuthorLine(note, accountViewModel, nav)
            summary?.takeIf { it.isNotBlank() }?.let {
                Spacer(StdVertSpacer)
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        latestVersion?.let {
            Spacer(Modifier.width(8.dp))
            VersionChip(it)
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(PaddingHorizontal12Modifier) {
        SectionLabel(title)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.grayText,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ReleasesToggle(
    count: Int,
    expanded: Boolean,
    showLabel: StringResource,
    hideLabel: StringResource,
    onToggle: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                .clickable { onToggle() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringRes(if (expanded) hideLabel else showLabel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Chip(text = count.toString())
        }
    }
}
