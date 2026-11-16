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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.kind_release_artifact_set
import com.vitorpamplona.amethyst.commons.resources.nip82_commit_label
import com.vitorpamplona.amethyst.commons.resources.nip82_cpu_apple_silicon
import com.vitorpamplona.amethyst.commons.resources.nip82_cpu_arm64
import com.vitorpamplona.amethyst.commons.resources.nip82_cpu_armv7
import com.vitorpamplona.amethyst.commons.resources.nip82_cpu_intel_mac
import com.vitorpamplona.amethyst.commons.resources.nip82_cpu_riscv64
import com.vitorpamplona.amethyst.commons.resources.nip82_cpu_wasm32
import com.vitorpamplona.amethyst.commons.resources.nip82_cpu_wasm64
import com.vitorpamplona.amethyst.commons.resources.nip82_cpu_x86
import com.vitorpamplona.amethyst.commons.resources.nip82_cpu_x86_64
import com.vitorpamplona.amethyst.commons.resources.nip82_download
import com.vitorpamplona.amethyst.commons.resources.nip82_downloads_count
import com.vitorpamplona.amethyst.commons.resources.nip82_for_this_device
import com.vitorpamplona.amethyst.commons.resources.nip82_min_android_api
import com.vitorpamplona.amethyst.commons.resources.nip82_min_platform_version
import com.vitorpamplona.amethyst.commons.resources.nip82_new_release
import com.vitorpamplona.amethyst.commons.resources.nip82_not_from_developer
import com.vitorpamplona.amethyst.commons.resources.nip82_os_android
import com.vitorpamplona.amethyst.commons.resources.nip82_os_freebsd
import com.vitorpamplona.amethyst.commons.resources.nip82_os_ios
import com.vitorpamplona.amethyst.commons.resources.nip82_os_linux
import com.vitorpamplona.amethyst.commons.resources.nip82_os_macos
import com.vitorpamplona.amethyst.commons.resources.nip82_os_other
import com.vitorpamplona.amethyst.commons.resources.nip82_os_wasm
import com.vitorpamplona.amethyst.commons.resources.nip82_os_windows
import com.vitorpamplona.amethyst.commons.resources.nip82_section_downloads
import com.vitorpamplona.amethyst.commons.resources.nip82_version_label
import com.vitorpamplona.amethyst.commons.softwareapps.DownloadGroups
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareAssetDownloads
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareCpu
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareOs
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwarePlatforms
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareReleases
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.BookmarkItem
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.BookmarkMemberRow
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.PagedMemberColumn
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.bookmarkItems
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.listTitle
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.formatLongDate
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.util.devicePlatformIds
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.isNip82SoftwareRelease
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The NIP-82 releases of [app], newest version first, kept live from the cache. Only releases
 * [SoftwareReleases.isReleaseOf] accepts are returned: signed by the app's publisher or a
 * maintainer it credits, and pointing at the app through their `a` tag.
 *
 * The cache index narrows on the release's `i` tag and its trusted signers, so an author with
 * many apps does not pull every release they ever published into the observer.
 */
@Composable
fun produceNip82Releases(app: SoftwareApplicationEvent): State<List<ReleaseArtifactSetEvent>> {
    val flow =
        remember(app.id) {
            val matcher = SoftwareReleases.ReleaseMatcher(app)
            LocalCache
                .observeNotes(SoftwareReleases.filter(app))
                .map { notes ->
                    SoftwareReleases.sorted(
                        notes.mapNotNull { note ->
                            (note.event as? ReleaseArtifactSetEvent)?.takeIf(matcher::matches)
                        },
                    )
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }
    return flow.collectAsStateWithLifecycle(initialValue = emptyList())
}

/**
 * NIP-82 kind 30063 — a software release as a note. Leads with the app it belongs to (icon
 * and name, from the release's `a` tag), then the version, the release notes as markdown and
 * the platforms it ships for.
 *
 * In a feed ([expanded] false) the platforms come from the release's aggregate `f` tags, so
 * the card fetches none of its assets. Expanded (the release's own thread) it lists the
 * downloads itself.
 *
 * The app's icon, name and the downloads are shown only once the app has loaded and
 * [SoftwareReleases] accepts the release as the app's own: anyone can sign a release that
 * points at someone else's app, and its downloads must not appear under that app's branding.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderSoftwareRelease(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    expanded: Boolean = false,
) {
    // kind 30063 is shared between NIP-51 release artifact sets and NIP-82 software
    // releases; only render the NIP-82 form here.
    val event = note.event as? ReleaseArtifactSetEvent ?: return
    if (!event.isNip82SoftwareRelease()) return

    val appAddress = remember(event) { event.appAddress() }
    val appNote = remember(appAddress) { appAddress?.let { LocalCache.getOrCreateAddressableNote(it) } }
    val app = appNote?.let { observeNoteEvent<SoftwareApplicationEvent>(it, accountViewModel).value }
    val trusted = remember(event, app) { SoftwareReleases.canShow(event, app) }
    val shownApp = app.takeIf { trusted }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Size5dp)
                .clip(QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                .padding(12.dp),
    ) {
        ReleaseAppHeader(event, shownApp, appAddress.takeIf { shownApp != null }, nav)

        if (app != null && !trusted) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringRes(Res.string.nip82_not_from_developer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (expanded && trusted) {
            ReleaseDetails(event, app, backgroundColor, accountViewModel, nav)
        } else {
            ReleaseNotes(event, backgroundColor, accountViewModel, nav)
            ReleaseSummaryFooter(event, shownApp)
        }
    }
}

/**
 * Kind 30063 in its NIP-51 form, a release artifact set: the shape NIP-82 releases grew out of,
 * still published by apps that predate NIP-82. NIP-51 puts the release notes in `content` (its own
 * example does), so they are drawn as notes, never treated as encrypted private items; the `e`/`a`
 * tags are the artifacts. Many of these sets carry no items at all, only a version and the notes.
 */
@Composable
fun RenderReleaseArtifactSet(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? ReleaseArtifactSetEvent ?: return
    if (event.isNip82SoftwareRelease()) return

    val artifacts = remember(event) { event.tags.bookmarkItems().filterIsInstance<BookmarkItem.NoteRef>() }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Size5dp)
                .clip(QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = listTitle(event.title(), event.dTag(), Res.string.kind_release_artifact_set),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                event.description()?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            event.version()?.let {
                Spacer(Modifier.width(8.dp))
                VersionChip(it)
            }
        }

        ReleaseNotes(event, backgroundColor, accountViewModel, nav)

        if (artifacts.isNotEmpty()) {
            PagedMemberColumn(artifacts, backgroundColor, Modifier.fillMaxWidth().padding(top = 5.dp)) { item ->
                key(item.key) {
                    BookmarkMemberRow(item.tag, quotesLeft, backgroundColor, accountViewModel, nav)
                }
            }
        }
    }
}

/**
 * A release on its app's page: no app header (the page is the app), the version and date,
 * the release notes and the downloads.
 */
@Composable
fun RenderSoftwareReleaseBody(
    event: ReleaseArtifactSetEvent,
    app: SoftwareApplicationEvent,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Size5dp)
                .clip(QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                event.version()?.let {
                    Text(
                        text = stringRes(Res.string.nip82_version_label, it),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = remember(event.createdAt) { formatLongDate(event.createdAt) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
            ChannelChip(event.channel())
        }

        ReleaseDetails(event, app, backgroundColor, accountViewModel, nav)
    }
}

/** Icon, app name and "New release" on the left; version and a non-default channel on the right. */
@Composable
internal fun ReleaseAppHeader(
    event: ReleaseArtifactSetEvent,
    app: SoftwareApplicationEvent?,
    appAddress: Address?,
    nav: INav,
) {
    val name = app?.name() ?: event.appId().orEmpty()
    val openApp = appAddress?.let { { nav.nav(Route.SoftwareAppDetail(it)) } }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (openApp != null) Modifier.clickable(onClick = openApp) else Modifier,
    ) {
        AppIcon(icon = app?.icon(), name = name, sizeDp = 40)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringRes(Res.string.nip82_new_release),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
            )
        }
        event.version()?.let {
            Spacer(Modifier.width(8.dp))
            VersionChip(it)
        }
        ChannelChip(event.channel())
    }
}

/** `main` is NIP-82's default channel, so only the others earn a chip. */
@Composable
private fun ChannelChip(channel: String?) {
    if (channel == null || SoftwareReleases.isMainChannel(channel)) return
    Spacer(Modifier.width(6.dp))
    Chip(channel.uppercase(), tint = MaterialTheme.colorScheme.tertiaryContainer)
}

/** The release notes, through the same viewer as a note body: markdown, translation, "Show more". */
@Composable
private fun ReleaseNotes(
    event: ReleaseArtifactSetEvent,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val notes = event.releaseNotes()
    if (notes.isBlank()) return

    val tags = remember(event) { event.tags.toImmutableListOfLists() }
    Spacer(StdVertSpacer)
    TranslatableRichTextViewer(
        content = notes,
        canPreview = true,
        quotesLeft = 1,
        modifier = Modifier.fillMaxWidth(),
        tags = tags,
        backgroundColor = backgroundColor,
        id = event.id,
        authorPubKey = event.pubKey,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/**
 * The OSes the release ships for (its aggregate `f` tags, or [app]'s when it omits them) and
 * how many downloads it bundles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReleaseSummaryFooter(
    event: ReleaseArtifactSetEvent,
    app: SoftwareApplicationEvent?,
) {
    val oses = remember(event, app) { SoftwareReleases.oses(event, app) }
    val assetCount = remember(event) { event.assets().size }
    if (oses.isEmpty() && assetCount == 0) return

    Spacer(StdVertSpacer)
    Row(verticalAlignment = Alignment.CenterVertically) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            oses.forEach { Chip(osLabel(it)) }
        }
        if (assetCount > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = pluralStringRes(Res.plurals.nip82_downloads_count, assetCount, assetCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}

/** Commit, full release notes and the downloads grouped by OS. */
@Composable
private fun ReleaseDetails(
    event: ReleaseArtifactSetEvent,
    app: SoftwareApplicationEvent?,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val assetIds = remember(event) { event.assets().map { it.eventId } }
    // One observer per asset: each fetches its event from the relays when missing.
    val assets = assetIds.map { id -> key(id) { rememberAssetEvent(id, accountViewModel) } }.filterNotNull()

    // NIP-82 puts `commit` on assets; some publishers put it on the release instead.
    val commit = assets.firstNotNullOfOrNull { it.commit() } ?: event.commit()
    commit?.let { CommitLine(it, app, nav) }

    ReleaseNotes(event, backgroundColor, accountViewModel, nav)

    if (assetIds.isNotEmpty()) {
        Spacer(StdVertSpacer)
        DownloadsList(assets, event.version(), accountViewModel)
    }
}

@Composable
private fun rememberAssetEvent(
    eventId: HexKey,
    accountViewModel: AccountViewModel,
): SoftwareAssetEvent? {
    val note by produceState(LocalCache.getNoteIfExists(eventId), eventId) {
        if (value == null) value = LocalCache.checkGetOrCreateNote(eventId)
    }
    val loaded = note ?: return null
    return observeNoteEvent<SoftwareAssetEvent>(loaded, accountViewModel).value
}

/** "Commit 821591a", opening the app's NIP-34 repository when the app links one. */
@Composable
private fun CommitLine(
    commit: String,
    app: SoftwareApplicationEvent?,
    nav: INav,
) {
    val repo = remember(app) { app?.gitRepository() }
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringRes(Res.string.nip82_commit_label, commit.take(7)),
        style = MaterialTheme.typography.bodySmall,
        color = if (repo != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.grayText,
        modifier =
            if (repo != null) {
                Modifier.clickable { nav.nav(Route.GitRepository(repo.kind, repo.pubKeyHex, repo.dTag)) }
            } else {
                Modifier
            },
    )
}

@Composable
private fun DownloadsList(
    assets: List<SoftwareAssetEvent>,
    releaseVersion: String?,
    accountViewModel: AccountViewModel,
) {
    val devicePlatforms = remember { devicePlatformIds() }
    val groups = remember(assets, devicePlatforms) { SoftwareAssetDownloads.group(assets, devicePlatforms) }
    DownloadsSection(groups, releaseVersion) { asset -> DownloadButton(asset, accountViewModel) }
}

/**
 * The release's downloads, grouped by OS in [SoftwareOs] order, with the best fit for this
 * device repeated on top. [downloadButton] draws each row's action.
 */
@Composable
internal fun DownloadsSection(
    groups: DownloadGroups,
    releaseVersion: String?,
    downloadButton: @Composable (SoftwareAssetEvent) -> Unit,
) {
    Text(
        text = stringRes(Res.string.nip82_section_downloads),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.grayText,
        fontWeight = FontWeight.SemiBold,
    )

    groups.forThisDevice?.let { asset ->
        val os = groups.deviceOs ?: SoftwarePlatforms.osesOf(asset).first()
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringRes(Res.string.nip82_for_this_device),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        DownloadRow(asset, os, releaseVersion, highlighted = true, downloadButton)
    }

    groups.byOs.forEach { (os, list) ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = osLabel(os),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            list.forEach { asset ->
                key(asset.id) {
                    DownloadRow(asset, os, releaseVersion, highlighted = false, downloadButton)
                }
            }
        }
    }
}

/**
 * One download: [SoftwareAssetDownloads.describe]'s title ("ZIP · msvc", or the file name),
 * then the CPU, size, minimum OS version, and the asset's own version when it differs from
 * the release's.
 */
@Composable
private fun DownloadRow(
    asset: SoftwareAssetEvent,
    os: SoftwareOs,
    releaseVersion: String?,
    highlighted: Boolean,
    downloadButton: @Composable (SoftwareAssetEvent) -> Unit,
) {
    val text = remember(asset, os, releaseVersion) { SoftwareAssetDownloads.describe(asset, os, releaseVersion) }
    val archs =
        text.cpuPlatforms
            .mapNotNull { archLabel(it) }
            .distinct()
            .joinToString(", ")
    val details =
        listOfNotNull(
            text.fileKind,
            archs.ifEmpty { null },
            text.sizeBytes?.let { formatBytes(it) },
            text.minPlatformVersion?.let { minPlatformLabel(os, it) },
            text.assetVersion?.let { stringRes(Res.string.nip82_version_label, it) },
        ).joinToString(" · ")

    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .then(
                    if (highlighted) {
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.subtleBorder, shape)
                    },
                ).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            // Sibling file names share long prefixes ("app-installer-1.8.22-…") and differ at the
            // end (".sha256", ".json"), so a file name is cut in the middle, not at the end.
            Text(
                text = text.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = if (text.titleIsFileName) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis,
            )
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        downloadButton(asset)
    }
}

/**
 * Opens the asset's url. Without one, NIP-82 has clients find the file by its `x` hash on
 * Blossom: the resolver probes the publisher's Blossom servers, with the viewer's default
 * Blossom server (when it is one) as a first-try hint and the last resort.
 */
@Composable
private fun DownloadButton(
    asset: SoftwareAssetEvent,
    accountViewModel: AccountViewModel,
) {
    val uri = LocalUriHandler.current
    val finder = LocalAppServices.current.blossomServerFinder
    val scope = rememberCoroutineScope()
    val directUrl = remember(asset) { SoftwareAssetDownloads.url(asset) }
    val hash = remember(asset) { asset.hash() }
    if (directUrl == null && hash == null) return

    DownloadAction(
        onClick = {
            if (directUrl != null) {
                runCatching { uri.openUri(directUrl) }
            } else if (hash != null) {
                scope.launch {
                    val server =
                        accountViewModel.account.settings.defaultFileServer
                            .takeIf { it.type == ServerType.Blossom }
                            ?.baseUrl
                    val extension = SoftwareAssetDownloads.extension(asset)
                    val blossomUri =
                        BlossomUri(
                            sha256 = hash,
                            extension = extension ?: "bin",
                            servers = listOfNotNull(server),
                            authors = listOf(asset.pubKey),
                            size = asset.sizeInBytes()?.toLong(),
                        ).toUriString()
                    val url =
                        finder.findServerUrl(blossomUri)
                            ?: server?.let { BlossomServerUrl.blob(it, hash, extension.orEmpty()) }
                    if (url != null) runCatching { uri.openUri(url) }
                }
            }
        },
    )
}

/** The "Download" link at the end of a download row. */
@Composable
internal fun DownloadAction(onClick: () -> Unit) {
    val label = stringRes(Res.string.nip82_download)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Download,
            contentDescription = label,
            modifier = Size16Modifier,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun minPlatformLabel(
    os: SoftwareOs,
    version: String,
): String =
    if (os == SoftwareOs.ANDROID) {
        stringRes(Res.string.nip82_min_android_api, version)
    } else {
        stringRes(Res.string.nip82_min_platform_version, osLabel(os), version)
    }

@Composable
fun osLabel(os: SoftwareOs): String =
    stringRes(
        when (os) {
            SoftwareOs.ANDROID -> Res.string.nip82_os_android
            SoftwareOs.IOS -> Res.string.nip82_os_ios
            SoftwareOs.MACOS -> Res.string.nip82_os_macos
            SoftwareOs.WINDOWS -> Res.string.nip82_os_windows
            SoftwareOs.LINUX -> Res.string.nip82_os_linux
            SoftwareOs.FREEBSD -> Res.string.nip82_os_freebsd
            SoftwareOs.WASM -> Res.string.nip82_os_wasm
            SoftwareOs.OTHER -> Res.string.nip82_os_other
        },
    )

/** A platform identifier's CPU as people name it, or its raw architecture when unknown. */
@Composable
internal fun archLabel(platformId: String): String? {
    val cpu = SoftwarePlatforms.cpu(platformId) ?: return SoftwarePlatforms.rawArch(platformId)
    return stringRes(
        when (cpu) {
            SoftwareCpu.APPLE_SILICON -> Res.string.nip82_cpu_apple_silicon
            SoftwareCpu.INTEL_MAC -> Res.string.nip82_cpu_intel_mac
            SoftwareCpu.ARM64 -> Res.string.nip82_cpu_arm64
            SoftwareCpu.ARMV7 -> Res.string.nip82_cpu_armv7
            SoftwareCpu.X86_64 -> Res.string.nip82_cpu_x86_64
            SoftwareCpu.X86 -> Res.string.nip82_cpu_x86
            SoftwareCpu.RISCV64 -> Res.string.nip82_cpu_riscv64
            SoftwareCpu.WASM32 -> Res.string.nip82_cpu_wasm32
            SoftwareCpu.WASM64 -> Res.string.nip82_cpu_wasm64
        },
    )
}
