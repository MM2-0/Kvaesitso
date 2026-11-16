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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.gitRepo.dal.isSeriesRoot
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.nip34Git.GitBrowseState
import com.vitorpamplona.amethyst.commons.nip34Git.GitPrivateRepositoryAuth
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepoSnapshotCache
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepositoryBrowserViewModel
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeTargets
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPullRequestRevision
import com.vitorpamplona.amethyst.commons.nip34Git.status.publishGitStatus
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitActivityTarget
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitCiStatusBadge
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitItemReference
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitLabelTag
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitRefTag
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitReviewPanel
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitStatusPill
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitTriageBar
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitVersionedSummary
import com.vitorpamplona.amethyst.commons.nip34Git.ui.MarkGitItemRead
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitMetaStyle
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitPullRequestPushText
import com.vitorpamplona.amethyst.commons.nip34Git.ui.isGitThreadRoot
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitItemLabels
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitItemTitle
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitPullRequestUpdates
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitRepositoryAnnouncements
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observePreviousRevision
import com.vitorpamplona.amethyst.commons.nip34Git.ui.rememberCanGitTriage
import com.vitorpamplona.amethyst.commons.nip34Git.ui.rememberGitMerge
import com.vitorpamplona.amethyst.commons.nip34Git.ui.rememberGitReviewHooks
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.gitRepo.LoadGitRepositoryAnnouncements
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_branch
import com.vitorpamplona.amethyst.commons.resources.git_clone_address
import com.vitorpamplona.amethyst.commons.resources.git_pr_update_description
import com.vitorpamplona.amethyst.commons.resources.git_repo_personal_fork
import com.vitorpamplona.amethyst.commons.resources.git_repo_tab_issues
import com.vitorpamplona.amethyst.commons.resources.git_repo_tab_patches
import com.vitorpamplona.amethyst.commons.resources.kind_git_repo
import com.vitorpamplona.amethyst.commons.ui.components.CommandLine
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.components.ScopedViewModelStore
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.GitDiffStatSummary
import com.vitorpamplona.amethyst.commons.ui.note.GitDiffView
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.LoadDecryptedContent
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.GitPullRequestUpdateButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.GitRepositoryBrowserViewModelFactory
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoExternalNotice
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoLanguageBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoLastCommit
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoStatTiles
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.computeLanguageBreakdown
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.repoHasFetchableClone
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.HalfDoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.Size16dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size8dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.patch.UnifiedDiffParser
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private val ChipShape = RoundedCornerShape(8.dp)
private val HeaderSpacing = Arrangement.spacedBy(Size8dp)
private val LinkRowSpacing = Arrangement.spacedBy(Size8dp)
private val CoverNoteSpacing = Modifier.padding(bottom = Size8dp)

@Composable
private fun GitCardContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
private fun IconBadge(
    symbol: MaterialSymbol,
    contentDescription: String?,
    background: Color,
    tint: Color,
    size: Dp = 28.dp,
    iconSize: Dp = Size16dp,
) {
    Row(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(background),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            symbol = symbol,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = tint,
        )
    }
}

@Composable
private fun TypeChip(
    text: String,
    background: Color,
    contentColor: Color,
    symbol: MaterialSymbol? = null,
) {
    Row(
        modifier =
            Modifier
                .clip(ChipShape)
                .background(background)
                .padding(horizontal = Size8dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Size5dp),
    ) {
        symbol?.let {
            Icon(
                symbol = it,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/** The labels under an item's title; in its own thread a member's triage bar draws them instead, with their actions. */
@Composable
private fun gitHeaderLabels(
    note: Note,
    makeItShort: Boolean,
    accountViewModel: AccountViewModel,
): ImmutableList<String> {
    val labels = observeGitItemLabels(note)
    val triaged = !makeItShort && isGitThreadRoot(note) && rememberCanGitTriage(note, accountViewModel)
    return if (triaged) persistentListOf() else labels
}

@Composable
private fun GitItemHeader(
    repository: Address?,
    section: GitItemSection,
    title: String?,
    labels: ImmutableList<String>,
    nav: INav,
    accountViewModel: AccountViewModel,
    state: @Composable () -> Unit,
) {
    if (repository != null) {
        GitRepositoryBreadcrumb(repository, section, accountViewModel, nav)
        Spacer(modifier = Modifier.height(6.dp))
    }

    if (title != null) {
        val base = LocalTextStyle.current.fontSize
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontSize = if (base.isSpecified) base * 1.2f else 20.sp,
            lineHeight = if (base.isSpecified) base * 1.5f else 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Ellipsis,
            maxLines = 4,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        state()
        labels.forEach { GitLabelTag(it) }
    }
}

/** Which of a repository's lists an item belongs to, for its breadcrumb. */
private enum class GitItemSection { ISSUES, PULLS }

@Composable
private fun GitRepositoryBreadcrumb(
    repository: Address,
    section: GitItemSection,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Code,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.grayText,
        )
        LoadAddressableNote(repository) { repoNote ->
            val repoEvent = repoNote?.let { observeNoteEvent<GitRepositoryEvent>(it, accountViewModel).value }
            Text(
                text = repoEvent?.name() ?: repository.dTag,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).clip(ChipShape).clickable { nav.nav(Route.GitRepository(repository)) },
            )
        }
        Text(text = "/", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.grayText)
        Text(
            text = stringRes(if (section == GitItemSection.ISSUES) Res.string.git_repo_tab_issues else Res.string.git_repo_tab_patches),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.grayText,
            maxLines = 1,
            modifier =
                Modifier.clip(ChipShape).clickable {
                    nav.nav(if (section == GitItemSection.ISSUES) Route.GitRepositoryIssues(repository) else Route.GitRepositoryPulls(repository))
                },
        )
    }
}

/** "hzrd149 wants to merge nixos-desktop into main", as the refs alone: source → target. */
@Composable
private fun GitBranchLine(
    source: String?,
    target: String?,
) {
    if (source == null && target == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.AltRoute,
            contentDescription = stringRes(id = Res.string.git_branch),
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.grayText,
        )
        if (source != null) GitRefTag(source, Modifier.weight(1f, fill = false))
        if (target != null) {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.grayText,
            )
            GitRefTag(target, Modifier.weight(1f, fill = false))
        }
    }
}

/**
 * Where to fetch the commits from, folded into one "Clone" line: a clone URL is a long string of
 * keys nobody reads, and the one thing to do with it is copy a `git clone`.
 */
@Composable
private fun GitCloneSection(cloneUrls: List<String>) {
    if (cloneUrls.isEmpty()) return
    var expanded by remember(cloneUrls) { mutableStateOf(false) }
    val host =
        remember(cloneUrls) {
            cloneUrls
                .first()
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
        }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            modifier =
                Modifier
                    .clip(ChipShape)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.grayText,
            )
            Text(
                text = stringRes(id = Res.string.git_clone_address).trimEnd(':'),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (cloneUrls.size > 1) "$host +${cloneUrls.size - 1}" else host,
                style = gitMetaStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                symbol = if (expanded) MaterialSymbols.KeyboardArrowUp else MaterialSymbols.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.grayText,
            )
        }
        if (expanded) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cloneUrls.forEach { CommandLine("git clone $it") }
            }
        }
    }
}

/**
 * A `git format-patch` message without its mail headers: the `From <sha>` line, `From:`, `Date:`
 * and the (possibly folded) `Subject:`, which the card already shows as the title.
 */
private fun formatPatchBody(message: String): String {
    val lines = message.lines()
    var index = 0
    var inSubject = false
    while (index < lines.size) {
        val line = lines[index]
        val isHeader =
            line.startsWith("From ") || line.startsWith("From:") || line.startsWith("Date:") || line.startsWith("Subject:")
        if (isHeader) {
            inSubject = line.startsWith("Subject:")
        } else if (!(inSubject && (line.startsWith(" ") || line.startsWith("\t")))) {
            break
        }
        index++
    }
    return lines.drop(index).joinToString("\n").trim()
}

/**
 * Some NIP-34 clients duplicate the subject tag as a leading markdown heading of the
 * content ("# Title\n\n…"). The card already renders the subject as its own title, so
 * when the first line is a heading matching [subject], drop it from the body.
 */
private fun stripDuplicatedSubject(
    content: String,
    subject: String?,
): String {
    if (subject.isNullOrBlank()) return content
    val trimmed = content.trimStart()
    if (!trimmed.startsWith("#")) return content
    val firstLineEnd = trimmed.indexOf('\n').let { if (it < 0) trimmed.length else it }
    val heading = trimmed.substring(0, firstLineEnd).trimStart('#').trim()
    if (!heading.equals(subject.trim(), ignoreCase = true)) return content
    return trimmed.substring(firstLineEnd).trimStart()
}

/**
 * The shared markdown body used by patches, issues and pull requests: honors
 * the collapsed [makeItShort] preview for the logged-in user's own posts and
 * otherwise renders the full content with sensitivity warnings and uncited
 * hashtags. The subject, when present, is rendered separately as a title by the
 * caller, so it is not inlined here — pass it as [renderedSubject] so a copy
 * duplicated into the content as a leading heading is stripped.
 */
@Composable
private fun GitMarkdownBody(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    renderedSubject: String? = null,
) {
    LoadDecryptedContent(note, accountViewModel) { rawBody ->
        val body = remember(rawBody, renderedSubject) { stripDuplicatedSubject(rawBody, renderedSubject) }
        val isAuthorTheLoggedUser =
            remember(note.event) { accountViewModel.isLoggedUser(note.author) }

        if (makeItShort && isAuthorTheLoggedUser) {
            Text(
                text = body,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            val callbackUri = remember(note) { note.toNostrUri() }

            SensitivityWarning(
                note = note,
                accountViewModel = accountViewModel,
            ) {
                val tags = remember(note) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }

                TranslatableRichTextViewer(
                    content = body,
                    canPreview = canPreview && !makeItShort,
                    quotesLeft = quotesLeft,
                    modifier = Modifier.fillMaxWidth(),
                    tags = tags,
                    backgroundColor = backgroundColor,
                    id = note.idHex,
                    callbackUri = callbackUri,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            // The `t` tags are the item's labels, already drawn under its title.
        }
    }
}

/**
 * The summary of an issue or PR: the version of it [editState] shows, its current cover note
 * unless the header's pencil stepped back to an older one or the item's own description
 * ([GitVersionedSummary]).
 */
@Composable
private fun GitCoverNoteAndDescription(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
    renderedSubject: String?,
) {
    GitVersionedSummary(note, editState, canPreview, quotesLeft, backgroundColor, accountViewModel, nav, CoverNoteSpacing) {
        GitMarkdownBody(note, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav, renderedSubject)
    }
}

@Composable
fun RenderGitPatchEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GitPatchEvent ?: return
    // Quoted (the post a comment answers, a repost's preview): name it, don't reprint it.
    if (makeItShort) {
        GitItemReference(baseNote, nav, bordered = false)
        return
    }

    RenderGitPatchEvent(
        event,
        baseNote,
        makeItShort,
        canPreview,
        quotesLeft,
        backgroundColor,
        editState,
        accountViewModel,
        nav,
    )
}

@Composable
private fun RenderGitPatchEvent(
    noteEvent: GitPatchEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // The repository announcements its statuses, updates, labels and cover notes are judged by.
    LoadGitRepositoryAnnouncements(noteEvent)
    // Statuses name a series' first patch, so a later patch shows the series' state, and reading
    // it reads the series the dashboard lists.
    val seriesRoot = remember(noteEvent) { noteEvent.seriesRootId()?.let { LocalCache.getOrCreateNote(it) } ?: note }
    val inThread = isGitThreadRoot(note)
    if (inThread) MarkGitItemRead(seriesRoot, accountViewModel)

    GitCardContainer {
        GitItemHeader(
            repository = remember(noteEvent) { noteEvent.repositoryAddress() },
            section = GitItemSection.PULLS,
            title = observeGitItemTitle(note),
            labels = gitHeaderLabels(note, makeItShort, accountViewModel),
            nav = nav,
            accountViewModel = accountViewModel,
        ) {
            GitStatusPill(item = seriesRoot, defaultIfMissing = StatusKind.OPEN)
            GitCiStatusBadge(targetIdHex = note.idHex, accountViewModel = accountViewModel, nav = nav)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // A patch's body is its commit message and diff: a cover note sits above it, never in place of it.
        GitVersionedSummary(note, editState, canPreview, quotesLeft, backgroundColor, accountViewModel, nav, CoverNoteSpacing, descriptionIsSummary = false) {
            // In a collapsed feed preview keep the lightweight markdown body. Otherwise parse the
            // patch: its thread draws the file-by-file diff, a feed or a notification only says
            // how big it is, since a diff is something to work through, not to scroll past.
            val parsed = if (makeItShort) null else remember(noteEvent) { UnifiedDiffParser.parse(noteEvent.content) }
            if (parsed != null && parsed.hasDiff) {
                // The title is drawn above; the message keeps only the body under git's headers.
                val body = remember(parsed) { formatPatchBody(parsed.message) }
                if (body.isNotBlank()) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (inThread) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = HalfDoubleVertSpacer)
                }
                if (inThread) {
                    val review = rememberGitReviewHooks(note, noteEvent.commit(), accountViewModel, nav)
                    GitDiffView(parsed, Modifier.fillMaxWidth(), review.onLineClick, review.lineAnnotations, review.hasAnnotations)
                } else {
                    GitDiffStatSummary(parsed)
                }
            } else {
                GitMarkdownBody(note, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
            }
        }

        if (!makeItShort && inThread) {
            GitLocalCheckoutSection(note, null, emptyList(), null, patch = noteEvent.content)
            GitTriageBar(note, accountViewModel)
            GitReviewPanel(note, accountViewModel)
            GitStatusActionsWithMerge(note, accountViewModel)
        }
    }
}

@Composable
fun RenderGitIssueEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GitIssueEvent ?: return
    // Quoted (the post a comment answers, a repost's preview): name it, don't reprint it.
    if (makeItShort) {
        GitItemReference(baseNote, nav, bordered = false)
        return
    }

    RenderGitIssueEvent(
        event,
        baseNote,
        makeItShort,
        canPreview,
        quotesLeft,
        backgroundColor,
        editState,
        accountViewModel,
        nav,
    )
}

@Composable
private fun RenderGitIssueEvent(
    noteEvent: GitIssueEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // The repository announcements its statuses, updates, labels and cover notes are judged by.
    LoadGitRepositoryAnnouncements(noteEvent)
    if (isGitThreadRoot(note)) MarkGitItemRead(note, accountViewModel)

    GitCardContainer {
        val subject = remember(noteEvent) { noteEvent.subject()?.takeIf { it.isNotBlank() } }
        // A maintainer may have renamed it since (NIP-32 `#subject`): show the current title.
        GitItemHeader(
            repository = remember(noteEvent) { noteEvent.repositoryAddress() },
            section = GitItemSection.ISSUES,
            title = observeGitItemTitle(note),
            labels = gitHeaderLabels(note, makeItShort, accountViewModel),
            nav = nav,
            accountViewModel = accountViewModel,
        ) {
            GitStatusPill(item = note, defaultIfMissing = StatusKind.OPEN, forIssue = true)
        }

        Spacer(modifier = Modifier.height(12.dp))

        GitCoverNoteAndDescription(note, makeItShort, canPreview, quotesLeft, backgroundColor, editState, accountViewModel, nav, renderedSubject = subject)

        if (!makeItShort && isGitThreadRoot(note)) {
            GitTriageBar(note, accountViewModel)
            GitStatusActions(note, accountViewModel)
        }
    }
}

@Composable
fun RenderGitPullRequestEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GitPullRequestEvent ?: return
    // Quoted (the post a comment answers, a repost's preview): name it, don't reprint it.
    if (makeItShort) {
        GitItemReference(baseNote, nav, bordered = false)
        return
    }

    RenderGitPullRequestEvent(
        event,
        baseNote,
        makeItShort,
        canPreview,
        quotesLeft,
        backgroundColor,
        editState,
        accountViewModel,
        nav,
    )
}

@Composable
private fun RenderGitPullRequestEvent(
    noteEvent: GitPullRequestEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // The repository announcements its statuses, updates, labels and cover notes are judged by.
    LoadGitRepositoryAnnouncements(noteEvent)
    if (isGitThreadRoot(note)) MarkGitItemRead(note, accountViewModel)

    // A later pull-request update (kind 1619) revises this PR with a newer commit /
    // merge base. Fold the most recent one in so the card reflects the current state.
    val update = observeGitPullRequestUpdates(note).lastOrNull()

    GitCardContainer {
        val subject = remember(noteEvent) { noteEvent.subject()?.takeIf { it.isNotBlank() } }
        // A maintainer may have renamed it since (NIP-32 `#subject`): show the current title.
        GitItemHeader(
            repository = remember(noteEvent) { noteEvent.repositoryAddress() },
            section = GitItemSection.PULLS,
            title = observeGitItemTitle(note),
            labels = gitHeaderLabels(note, makeItShort, accountViewModel),
            nav = nav,
            accountViewModel = accountViewModel,
        ) {
            GitStatusPill(item = note, defaultIfMissing = StatusKind.OPEN)
            GitCiStatusBadge(targetIdHex = note.idHex, accountViewModel = accountViewModel, nav = nav)
        }

        // Where it comes from, and where it goes when it isn't the default branch (`b`).
        val source = remember(noteEvent) { noteEvent.branchName()?.takeIf { it.isNotBlank() } }
        val target = remember(noteEvent) { noteEvent.targetBranch()?.takeIf { it.isNotBlank() } }
        val currentCommit =
            remember(noteEvent, update) {
                (update?.currentCommit() ?: noteEvent.currentCommit())?.takeIf { it.isNotBlank() }
            }
        val mergeBase =
            remember(noteEvent, update) {
                (update?.mergeBase() ?: noteEvent.mergeBase())?.takeIf { it.isNotBlank() }
            }
        val cloneUrls =
            remember(noteEvent, update) {
                // The update's servers carry its new tip; the PR's own may still serve it too.
                (update?.cloneUrls().orEmpty() + noteEvent.cloneUrls()).filter { it.isNotBlank() }.distinct()
            }

        // Commit hashes stay off the card: a bare short hash is nothing a reader can act on. They
        // still drive the changes below, and the thread's timeline says when an update moved them.
        if (source != null || target != null) {
            Spacer(modifier = Modifier.height(8.dp))
            GitBranchLine(source, target)
        }

        Spacer(modifier = Modifier.height(12.dp))

        GitCoverNoteAndDescription(note, makeItShort, canPreview, quotesLeft, backgroundColor, editState, accountViewModel, nav, renderedSubject = subject)

        if (!makeItShort && isGitThreadRoot(note)) {
            GitCloneSection(cloneUrls)
            GitLocalCheckoutSection(note, source, cloneUrls, currentCommit)
            GitPullRequestChanges(cloneUrls, currentCommit, mergeBase, accountViewModel, rememberGitReviewHooks(note, currentCommit, accountViewModel, nav))
            val repositoryNote = remember(noteEvent) { noteEvent.repositoryAddress()?.let { LocalCache.getOrCreateAddressableNote(it) } }
            GitPullRequestUpdateButton(note, repositoryNote, currentCommit, cloneUrls, accountViewModel, nav)
            GitTriageBar(note, accountViewModel)
            GitReviewPanel(note, accountViewModel)
            GitStatusActionsWithMerge(note, accountViewModel)
        }
    }
}

@Composable
fun RenderGitPullRequestUpdateEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GitPullRequestUpdateEvent ?: return

    RenderGitPullRequestUpdateEvent(
        event,
        baseNote,
        makeItShort,
        canPreview,
        quotesLeft,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

/**
 * A PR update (kind 1619) on its own, in a feed, a notification or a quote. Says what moved
 * ("Pushed 4246e71 (was 70d31ba), rebased onto bca8dc9") and quotes the PR it updates, since
 * nothing else on screen names it. In the PR's own thread it is a timeline row instead
 * (GitThreadTimelineRow).
 */
@Composable
private fun RenderGitPullRequestUpdateEvent(
    noteEvent: GitPullRequestUpdateEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val targetNote = note.replyTo?.lastOrNull()
    val pullRequest = targetNote?.let { observeNoteEvent<GitPullRequestEvent>(it, accountViewModel).value }
    // The quoted PR carries the repository header and its clone URLs; draw them here only
    // when there is no PR to quote.
    val quotesPullRequest = pullRequest != null && !makeItShort && quotesLeft > 0

    GitCardContainer {
        val repository = remember(noteEvent) { noteEvent.repositoryAddress() }
        if (repository != null && !quotesPullRequest) {
            Spacer(modifier = StdVertSpacer)
            GitRepositoryBreadcrumb(repository, GitItemSection.PULLS, accountViewModel, nav)
        }

        val current = remember(noteEvent) { GitPullRequestRevision.of(noteEvent) }
        val previous = observePreviousRevision(noteEvent, pullRequest)
        val summary = gitPullRequestPushText(current, previous, sentence = true)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                symbol = MaterialSymbols.Sync,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.grayText,
            )
            Text(
                text = summary ?: stringRes(id = Res.string.git_pr_update_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }

        // Where the new commits are fetched from is the PR thread's business (its Clone line).
        if (quotesPullRequest) {
            Spacer(modifier = StdVertSpacer)
            GitActivityTarget(note, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

@Composable
fun RenderGitRepositoryEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GitRepositoryEvent ?: return

    RenderGitRepositoryEvent(event, baseNote, accountViewModel, nav)
}

@Composable
private fun RenderGitRepositoryEvent(
    noteEvent: GitRepositoryEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val title = noteEvent.name() ?: noteEvent.dTag()
    val summary = noteEvent.description()
    val topics = remember(noteEvent) { noteEvent.hashtags().filter { it.isNotBlank() } }
    val isPersonalFork = remember(noteEvent) { noteEvent.isPersonalFork() }

    GitCardContainer {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = HeaderSpacing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconBadge(
                symbol = MaterialSymbols.Code,
                contentDescription = null,
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                tint = MaterialTheme.colorScheme.primary,
            )

            Column(modifier = Modifier.weight(1f, fill = true)) {
                Text(
                    text = stringRes(id = Res.string.kind_git_repo),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isPersonalFork) {
                TypeChip(
                    text = stringRes(id = Res.string.git_repo_personal_fork),
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    symbol = MaterialSymbols.AltRoute,
                )
            }
        }

        summary?.let {
            Spacer(modifier = HalfDoubleVertSpacer)
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        RepoSnapshotDashboard(noteEvent, note, accountViewModel, nav)

        if (topics.isNotEmpty()) {
            Spacer(modifier = HalfDoubleVertSpacer)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Size5dp),
                verticalArrangement = Arrangement.spacedBy(Size5dp),
            ) {
                topics.forEach { topic -> GitLabelTag(topic) }
            }
        }
    }
}

/**
 * Loads a shallow snapshot of the repository over smart-HTTP and renders the same
 * stat tiles / language bar / last-commit strip used on the project home, in place of
 * the old web/clone links. Fetches lazily (once) when the card is composed.
 */
@Composable
private fun RepoSnapshotDashboard(
    noteEvent: GitRepositoryEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // A store per card: in the screen's store, every repo scrolled past kept its model and loaded
    // snapshot until the screen was popped, around GitRepoSnapshotCache's deliberately small LRU.
    // Scrolling back reads that cache first.
    key(note.idHex) {
        ScopedViewModelStore {
            RepoSnapshotDashboardContent(noteEvent, note, accountViewModel, nav)
        }
    }
}

@Composable
private fun RepoSnapshotDashboardContent(
    noteEvent: GitRepositoryEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val cacheKey = remember(noteEvent) { noteEvent.address().toValue() }
    val browser: GitRepositoryBrowserViewModel =
        viewModel(
            key = note.idHex + "GitRepoCardBrowser",
            factory = GitRepositoryBrowserViewModelFactory(accountViewModel.httpClientBuilder::okHttpClientForPreview, GitPrivateRepositoryAuth(accountViewModel.account.signer)),
        )
    LaunchedEffect(noteEvent) { browser.loadOnce(noteEvent.clones(), cacheKey) }
    val browserState by browser.state.collectAsStateWithLifecycle()
    // Prefer the live state, but fall back to a cached snapshot so an already-fetched repo
    // renders synchronously — including in the share-to-image capture, which won't wait for a
    // fresh clone.
    val snapshot = (browserState as? GitBrowseState.Loaded)?.snapshot ?: remember(cacheKey) { GitRepoSnapshotCache.get(cacheKey) }
    if (snapshot == null) {
        // Repos we can't clone over http(s) (e.g. Iris's htree://) get an open-in-browser notice
        // instead of an indefinitely-empty dashboard.
        if (!repoHasFetchableClone(noteEvent)) {
            Spacer(modifier = HalfDoubleVertSpacer)
            RepoExternalNotice(noteEvent)
        }
        return
    }

    val fileNames = remember(snapshot) { snapshot.walkFileNames() }
    val slices = remember(fileNames) { computeLanguageBreakdown(fileNames) }

    Spacer(modifier = HalfDoubleVertSpacer)
    RepoStatTiles(
        branches = snapshot.branches.size,
        tags = snapshot.tags.size,
        files = fileNames.size,
        updatedEpochSec = snapshot.tipCommit?.authorTimeSec,
    )
    if (slices.isNotEmpty()) {
        Spacer(modifier = HalfDoubleVertSpacer)
        RepoLanguageBar(slices)
    }
    snapshot.tipCommit?.let { commit ->
        Spacer(modifier = HalfDoubleVertSpacer)
        RepoLastCommit(commit) { nav.nav(Route.GitRepository(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag())) }
    }
}

/**
 * [GitStatusActions] with the in-app merge for a pull request or a patch series' root; "Mark
 * merged" stays for a merge made with git.
 */
@Composable
private fun GitStatusActionsWithMerge(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val event = note.event
    if (event !is GitPullRequestEvent && !(event is GitPatchEvent && event.isSeriesRoot())) {
        GitStatusActions(note, accountViewModel)
        return
    }
    val onMerge =
        rememberGitMerge(note, accountViewModel) {
            accountViewModel.launchSigner { accountViewModel.account.publishGitStatus(note, GitStatusEvent.KIND_APPLIED) }
        }
    // Offer the in-app merge only where it can land: a repository with a GRASP server.
    val announcements = observeGitRepositoryAnnouncements(event)
    val onGrasp =
        remember(event, announcements) {
            GitAuthority
                .repositoriesOf(event)
                .firstOrNull()
                ?.let { LocalCache.gitAuthority.membership(it) }
                ?.let { GitMergeTargets.of(it.confirmedAnnouncements).grasp.isNotEmpty() } ?: true
        }
    GitStatusActions(note, accountViewModel, onMerge.takeIf { onGrasp })
}
