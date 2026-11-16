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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitIconTile
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitLabelTag
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitStateIcon
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitCard
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitMetaStyle
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitRepositoryAnnouncements
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitStatus
import com.vitorpamplona.amethyst.commons.nip34Git.ui.statusKind
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_repo_external_host_body
import com.vitorpamplona.amethyst.commons.resources.git_repo_external_host_title
import com.vitorpamplona.amethyst.commons.resources.git_repo_maintainers
import com.vitorpamplona.amethyst.commons.resources.git_repo_open_in_browser
import com.vitorpamplona.amethyst.commons.resources.git_repo_personal_fork
import com.vitorpamplona.amethyst.commons.resources.git_repo_private
import com.vitorpamplona.amethyst.commons.resources.git_repo_push_first_body
import com.vitorpamplona.amethyst.commons.resources.git_repo_push_first_get_ngit
import com.vitorpamplona.amethyst.commons.resources.git_repo_push_first_title
import com.vitorpamplona.amethyst.commons.resources.git_repo_recent_activity
import com.vitorpamplona.amethyst.commons.resources.git_repo_stat_branches
import com.vitorpamplona.amethyst.commons.resources.git_repo_stat_files
import com.vitorpamplona.amethyst.commons.resources.git_repo_stat_tags
import com.vitorpamplona.amethyst.commons.resources.git_repo_stat_updated
import com.vitorpamplona.amethyst.commons.resources.git_untitled
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrlOrBlossom
import com.vitorpamplona.amethyst.commons.ui.components.CommandLine
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.timeAgo
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.gitSubjectOf
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip34Git.git.GitCommit
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.repository.NostrCloneUrl

// ---------------------------------------------------------------------------
// Hero / identity — name, description and topics in the dashboard language.
// ---------------------------------------------------------------------------

/** The top-bar title: the owner's avatar and the project name. */
@Composable
fun RepoTitleBar(
    event: GitRepositoryEvent?,
    fallback: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val owner = event?.pubKey?.let { LocalCache.checkGetOrCreateUser(it) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (owner != null) {
            ClickableUserPicture(
                baseUser = owner,
                size = 26.dp,
                accountViewModel = accountViewModel,
                onClick = { nav.nav(Route.Profile(it.pubkeyHex)) },
            )
        }
        Text(
            text = event?.name() ?: fallback,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * What the project is: its description in full, its topics and fork badge, and the people who
 * maintain it.
 */
@Composable
fun RepoHero(
    event: GitRepositoryEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val description = event.description()?.takeIf { it.isNotBlank() }
    val topics = remember(event) { event.hashtags().filter { it.isNotBlank() } }
    val isFork = event.isPersonalFork()
    val isPrivate = remember(event) { event.isPrivate() }
    // The confirmed maintainers (role tags and the legacy list, each confirmed by their own
    // announcement), as the lists and the status authority count them; the announcement's own
    // names until the others load.
    val announcements = observeGitRepositoryAnnouncements(event.dTag())
    val maintainers =
        remember(event, announcements) {
            LocalCache.gitAuthority
                .membership(event.address())
                ?.confirmedMaintainers
                ?.takeIf { it.isNotEmpty() }
                ?: (listOf(event.pubKey) + event.maintainers()).distinct()
        }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (description != null) {
            Text(text = description, style = MaterialTheme.typography.bodyLarge)
        }
        if (topics.isNotEmpty() || isFork || isPrivate) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isPrivate) GitLabelTag(stringRes(Res.string.git_repo_private))
                if (isFork) GitLabelTag(stringRes(Res.string.git_repo_personal_fork))
                topics.forEach { GitLabelTag(it) }
            }
        }
        // Who maintains it: a few by name, a crowd by face.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringRes(Res.string.git_repo_maintainers), style = gitMetaStyle())
            val named = maintainers.size <= 3
            maintainers.take(8).forEach { key ->
                val user = remember(key) { LocalCache.getOrCreateUser(key) }
                Row(
                    modifier = Modifier.clip(CircleShape).clickable { nav.nav(Route.Profile(key)) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    UserPicture(user = user, size = 20.dp, accountViewModel = accountViewModel, nav = nav)
                    if (named) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            UsernameDisplay(user, fontWeight = FontWeight.SemiBold, accountViewModel = accountViewModel)
                        }
                    }
                }
            }
            if (maintainers.size > 8) {
                Text(text = "+" + (maintainers.size - 8), style = gitMetaStyle())
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Social pulse — zaps, reactions and comments on the repository announcement.
// ---------------------------------------------------------------------------

@Composable
fun RepoSocialRow(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // The standard note footer: reply, boost, like, zap (+ zapraiser and the reaction gallery),
    // so the repository announcement gets the exact same interactions as any other note.
    ReactionsRow(
        baseNote = note,
        showReactionDetail = true,
        addPadding = false,
        editState = null,
        accountViewModel = accountViewModel,
        nav = nav,
    )
    HorizontalDivider(thickness = DividerThickness)
}

// ---------------------------------------------------------------------------
// Stat tiles — git facts (branches, tags, files, last updated).
// ---------------------------------------------------------------------------

@Composable
fun RepoStatTiles(
    branches: Int,
    tags: Int,
    files: Int,
    updatedEpochSec: Long?,
) {
    // One quiet line of facts, as a forge puts them under the name, in place of a tile dashboard.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        StatItem(MaterialSymbols.AltRoute, branches.toString(), stringRes(Res.string.git_repo_stat_branches))
        StatItem(MaterialSymbols.Tag, tags.toString(), stringRes(Res.string.git_repo_stat_tags))
        StatItem(MaterialSymbols.Description, compactCount(files), stringRes(Res.string.git_repo_stat_files))
        if (updatedEpochSec != null) {
            StatItem(MaterialSymbols.Schedule, timeAgo(updatedEpochSec, prefix = ""), stringRes(Res.string.git_repo_stat_updated))
        }
    }
}

@Composable
private fun StatItem(
    symbol: MaterialSymbol,
    value: String,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.grayText)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text(text = label.lowercase(), style = gitMetaStyle(), maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Language breakdown bar — proportional colours by file count.
// ---------------------------------------------------------------------------

class LanguageSlice(
    val name: String,
    val fraction: Float,
    val color: Color,
)

@Composable
fun RepoLanguageBar(slices: List<LanguageSlice>) {
    if (slices.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
        ) {
            slices.forEach { slice ->
                Box(
                    modifier =
                        Modifier
                            .weight(slice.fraction.coerceAtLeast(0.0001f))
                            .height(6.dp)
                            .background(slice.color),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(slice.color))
                    Text(
                        text = slice.name + "  " + (slice.fraction * 100).toInt() + "%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.grayText,
                    )
                }
            }
        }
    }
}

private class LangDef(
    val name: String,
    val color: Long,
) {
    override fun equals(other: Any?) = other is LangDef && other.name == name

    override fun hashCode() = name.hashCode()
}

private val EXT_TO_LANG: Map<String, LangDef> =
    buildMap {
        fun add(
            def: LangDef,
            vararg exts: String,
        ) = exts.forEach { put(it, def) }
        add(LangDef("Kotlin", 0xFFA97BFF), "kt", "kts")
        add(LangDef("Java", 0xFFB07219), "java")
        add(LangDef("JavaScript", 0xFFF1E05A), "js", "mjs", "cjs", "jsx")
        add(LangDef("TypeScript", 0xFF3178C6), "ts", "tsx")
        add(LangDef("Python", 0xFF3572A5), "py")
        add(LangDef("Ruby", 0xFF701516), "rb")
        add(LangDef("Rust", 0xFFDEA584), "rs")
        add(LangDef("Go", 0xFF00ADD8), "go")
        add(LangDef("C", 0xFF555555), "c", "h")
        add(LangDef("C++", 0xFFF34B7D), "cpp", "cc", "cxx", "hpp", "hh", "hxx")
        add(LangDef("C#", 0xFF178600), "cs")
        add(LangDef("Swift", 0xFFF05138), "swift")
        add(LangDef("PHP", 0xFF4F5D95), "php")
        add(LangDef("Shell", 0xFF89E051), "sh", "bash", "zsh", "ksh")
        add(LangDef("HTML", 0xFFE34C26), "html", "htm")
        add(LangDef("CSS", 0xFF563D7C), "css")
        add(LangDef("SCSS", 0xFFC6538C), "scss")
        add(LangDef("Markdown", 0xFF083FA1), "md", "markdown")
        add(LangDef("JSON", 0xFF40803F), "json")
        add(LangDef("YAML", 0xFFCB171E), "yml", "yaml")
        add(LangDef("XML", 0xFF0060AC), "xml")
        add(LangDef("Gradle", 0xFF02303A), "gradle")
        add(LangDef("Dart", 0xFF00B4AB), "dart")
        add(LangDef("TOML", 0xFF9C4221), "toml")
    }

private const val OTHER_COLOR = 0xFFBBBBBB

/** Buckets files by recognised language (by extension), returning the top slices + an "Other" remainder. */
fun computeLanguageBreakdown(files: List<String>): List<LanguageSlice> {
    if (files.isEmpty()) return emptyList()
    val counts = HashMap<LangDef, Int>()
    var other = 0
    for (f in files) {
        val ext = f.substringAfterLast('.', "").lowercase()
        val def = EXT_TO_LANG[ext]
        if (def == null) other++ else counts[def] = (counts[def] ?: 0) + 1
    }
    val total = files.size.toFloat()
    val ranked = counts.entries.sortedByDescending { it.value }
    val top = ranked.take(6)
    val remainder = ranked.drop(6).sumOf { it.value } + other

    val slices =
        top.map { LanguageSlice(it.key.name, it.value / total, Color(it.key.color)) }.toMutableList()
    if (remainder > 0) {
        slices.add(LanguageSlice("Other", remainder / total, Color(OTHER_COLOR)))
    }
    return slices
}

// ---------------------------------------------------------------------------
// External-host notice — for repos we can't clone over http(s) (htree://, nostr://, …).
// ---------------------------------------------------------------------------

/** True when the repo can be cloned over http(s), i.e. the smart-HTTP browser can fetch it. */
fun repoHasFetchableClone(event: GitRepositoryEvent): Boolean = event.clones().any { it.startsWith("http://") || it.startsWith("https://") }

/**
 * Shown instead of the snapshot dashboard for repos whose only clone URLs use a transport the
 * smart-HTTP browser can't fetch (e.g. Iris's `htree://`). Offers the web URL as an
 * open-in-browser fallback when one is present.
 */
@Composable
fun RepoExternalNotice(event: GitRepositoryEvent) {
    val web = remember(event) { event.webs().firstOrNull { it.startsWith("http://") || it.startsWith("https://") } }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .gitCard()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(MaterialSymbols.Public, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringRes(Res.string.git_repo_external_host_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = stringRes(Res.string.git_repo_external_host_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.grayText,
        )
        if (web != null) {
            ClickableUrlOrBlossom(url = web, urlText = stringRes(Res.string.git_repo_open_in_browser))
        }
    }
}

// ---------------------------------------------------------------------------
// First push — the owner's empty repository.
// ---------------------------------------------------------------------------

private val PushCardShape = RoundedCornerShape(20.dp)

/**
 * Shown to the owner when the repository's clone URLs hold no code yet, typically right after
 * creating it in the app: the two commands that push a project to it through ngit's
 * git-remote-nostr helper, each with a copy button.
 */
@Composable
fun RepoPushFirstCommit(event: GitRepositoryEvent) {
    val colors = MaterialTheme.colorScheme
    val npub = remember(event) { event.pubKey.hexToByteArray().toNpub() }
    val remote = remember(event) { NostrCloneUrl.build(npub, event.dTag(), event.relays().firstOrNull()) }
    val shortRemote = remember(remote) { remote.replace(npub, npub.toShortDisplay(5)) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(PushCardShape)
                .background(Brush.linearGradient(listOf(colors.primary.copy(alpha = 0.20f), colors.secondary.copy(alpha = 0.06f))))
                .border(1.dp, colors.primary.copy(alpha = 0.28f), PushCardShape)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GitIconTile(symbol = MaterialSymbols.CloudUpload, size = 40.dp)
            Text(
                text = stringRes(Res.string.git_repo_push_first_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = stringRes(Res.string.git_repo_push_first_body),
            style = MaterialTheme.typography.bodySmall,
            color = colors.grayText,
        )
        CommandLine(command = "git remote add origin $remote", display = "git remote add origin $shortRemote")
        CommandLine("git push -u origin HEAD")
        ClickableUrlOrBlossom(url = NGIT_URL, urlText = stringRes(Res.string.git_repo_push_first_get_ngit))
    }
}

private const val NGIT_URL = "https://ngit.dev"

// ---------------------------------------------------------------------------
// Last-commit strip.
// ---------------------------------------------------------------------------

@Composable
fun RepoLastCommit(
    commit: GitCommit,
    onClick: (() -> Unit)? = null,
) {
    // The forge's latest-commit bar: who, what and when, on one line.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .gitCard()
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = commit.authorName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = commit.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.grayText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = timeAgo(commit.authorTimeSec, prefix = ""), style = gitMetaStyle(), maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Recent activity pulse — newest issues / patches / status events.
// ---------------------------------------------------------------------------

@Composable
fun RepoActivityPulse(
    items: List<Note>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringRes(Res.string.git_repo_recent_activity),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Column(Modifier.fillMaxWidth().gitCard()) {
            items.forEachIndexed { index, note ->
                if (index > 0) HorizontalDivider(thickness = DividerThickness, color = MaterialTheme.colorScheme.subtleBorder)
                ActivityRow(note, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun ActivityRow(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event ?: return
    val kind = observeGitStatus(note)?.statusKind() ?: StatusKind.OPEN
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { nav.nav { routeFor(note, accountViewModel.account) } }
                .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GitStateIcon(kind = kind, forIssue = event is GitIssueEvent, contentDescription = null, size = 16.dp)
        Text(
            text = gitSubjectOf(note) ?: stringRes(Res.string.git_untitled),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = timeAgo(note.createdAt(), prefix = ""), style = gitMetaStyle(), maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Helpers.
// ---------------------------------------------------------------------------

/** Compacts large counts: 1234 -> "1.2k", 2_500_000 -> "2.5M". */
private fun compactCount(n: Int): String =
    when {
        n < 1_000 -> n.toString()
        n < 1_000_000 -> trimDecimal(n / 1_000.0) + "k"
        else -> trimDecimal(n / 1_000_000.0) + "M"
    }

private fun trimDecimal(v: Double): String {
    val rounded = (v * 10).toInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
