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
package com.vitorpamplona.amethyst.commons.notifications.composers

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.amethyst.commons.nip34Git.title.GitItemTitles
import com.vitorpamplona.amethyst.commons.notifications.NotificationContent
import com.vitorpamplona.amethyst.commons.notifications.NotificationDraft
import com.vitorpamplona.amethyst.commons.notifications.NotificationMessage
import com.vitorpamplona.amethyst.commons.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.commons.notifications.NotificationTopic
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_code_channel_message_issue
import com.vitorpamplona.amethyst.commons.resources.app_notification_code_channel_message_patch
import com.vitorpamplona.amethyst.commons.resources.app_notification_code_channel_message_pr
import com.vitorpamplona.amethyst.commons.resources.app_notification_code_channel_message_pr_update
import com.vitorpamplona.amethyst.commons.resources.app_notification_code_channel_message_reply
import com.vitorpamplona.amethyst.commons.resources.app_notification_code_channel_message_status_applied
import com.vitorpamplona.amethyst.commons.resources.app_notification_code_channel_message_status_applied_issue
import com.vitorpamplona.amethyst.commons.resources.app_notification_code_channel_message_status_applied_patch
import com.vitorpamplona.amethyst.commons.resources.app_notification_code_channel_message_status_applied_pr
import com.vitorpamplona.amethyst.commons.resources.app_notification_code_channel_message_status_closed
import com.vitorpamplona.amethyst.commons.resources.app_notification_code_channel_message_status_closed_issue
import com.vitorpamplona.amethyst.commons.resources.app_notification_code_channel_message_status_closed_patch
import com.vitorpamplona.amethyst.commons.resources.app_notification_code_channel_message_status_closed_pr
import com.vitorpamplona.amethyst.commons.resources.app_notification_code_channel_message_status_draft
import com.vitorpamplona.amethyst.commons.resources.app_notification_code_channel_message_status_open
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent
import org.jetbrains.compose.resources.StringResource

/**
 * Git / code notifications — NIP-34 issues (1621), patches (1617), pull requests
 * (1618), PR updates (1619), replies (1622, legacy), and status transitions
 * (1630 open, 1631 applied/merged, 1632 closed, 1633 draft) on repos or threads
 * you're p-tagged into. Rendered as a slate card titled by the action ("X opened
 * an issue", "X merged a pull request", …) with the subject as the body.
 *
 * Status kinds resolve their title from the *target* event's kind (patch/PR/issue)
 * when it's in cache, so a merge on a PR reads "merged a pull request" but the
 * same 1631 targeting a plain kind-1617 patch reads "applied a patch". Falls
 * back to a generic wording when the target isn't yet resolved (rare: the
 * notification lands after the target because the p-tag subscription pulls
 * status events regardless of whether the target has been seen).
 */
object CodeNotificationComposer {
    fun compose(
        account: Account,
        event: GitIssueEvent,
    ) = draft(account, event.id, event.createdAt, event.pubKey, Res.string.app_notification_code_channel_message_issue, inRepository(event, event.subject() ?: event.content))

    fun compose(
        account: Account,
        event: GitPatchEvent,
    ) = draft(account, event.id, event.createdAt, event.pubKey, Res.string.app_notification_code_channel_message_patch, inRepository(event, event.subject() ?: event.content))

    fun compose(
        account: Account,
        event: GitPullRequestEvent,
    ) = draft(account, event.id, event.createdAt, event.pubKey, Res.string.app_notification_code_channel_message_pr, inRepository(event, event.subject() ?: event.content))

    fun compose(
        account: Account,
        event: GitPullRequestUpdateEvent,
    ) = draft(account, event.id, event.createdAt, event.pubKey, Res.string.app_notification_code_channel_message_pr_update, aboutItem(event.parentPullRequestId(), event.content))

    // GitReplyEvent (kind 1622) is deprecated in favour of NIP-22 comments, but
    // events already on relays still arrive and still have to be rendered.
    @Suppress("DEPRECATION")
    fun compose(
        account: Account,
        event: GitReplyEvent,
    ) = draft(account, event.id, event.createdAt, event.pubKey, Res.string.app_notification_code_channel_message_reply, event.content)

    fun compose(
        account: Account,
        event: GitStatusOpenEvent,
    ) = draft(account, event.id, event.createdAt, event.pubKey, Res.string.app_notification_code_channel_message_status_open, aboutItem(event.rootEventId(), event.content))

    fun compose(
        account: Account,
        event: GitStatusAppliedEvent,
    ) = draft(
        account,
        event.id,
        event.createdAt,
        event.pubKey,
        titleRes =
            titleForStatusOnTarget(
                event.rootEventId(),
                pr = Res.string.app_notification_code_channel_message_status_applied_pr,
                patch = Res.string.app_notification_code_channel_message_status_applied_patch,
                issue = Res.string.app_notification_code_channel_message_status_applied_issue,
                fallback = Res.string.app_notification_code_channel_message_status_applied,
            ),
        subject = aboutItem(event.rootEventId(), event.content),
    )

    fun compose(
        account: Account,
        event: GitStatusClosedEvent,
    ) = draft(
        account,
        event.id,
        event.createdAt,
        event.pubKey,
        titleRes =
            titleForStatusOnTarget(
                event.rootEventId(),
                pr = Res.string.app_notification_code_channel_message_status_closed_pr,
                patch = Res.string.app_notification_code_channel_message_status_closed_patch,
                issue = Res.string.app_notification_code_channel_message_status_closed_issue,
                fallback = Res.string.app_notification_code_channel_message_status_closed,
            ),
        subject = aboutItem(event.rootEventId(), event.content),
    )

    fun compose(
        account: Account,
        event: GitStatusDraftEvent,
    ) = draft(account, event.id, event.createdAt, event.pubKey, Res.string.app_notification_code_channel_message_status_draft, aboutItem(event.rootEventId(), event.content))

    /**
     * The body of a status or PR-update notification: which issue, patch or PR it is about (its
     * current title), then the event's own note. These events rarely carry text, so the content
     * alone left the notification reading "Alice closed an issue" without saying which.
     */
    private fun aboutItem(
        itemId: String?,
        content: String,
    ): String? {
        val item = itemId?.let { LocalCache.getNoteIfExists(it) }
        val title = item?.let { GitItemTitles.titleOf(it) }
        val head = item?.event?.let { inRepository(it, title) } ?: title
        return listOfNotNull(head, content.ifBlank { null }).joinToString("\n").ifBlank { null }
    }

    /**
     * "ngit · Fix the login screen": [text] after the name of the repository [item] is filed in, the
     * way a forge's mail subject leads with the repository, since one person often follows several.
     */
    private fun inRepository(
        item: Event,
        text: String?,
    ): String? {
        val address = GitAuthority.repositoriesOf(item).firstOrNull() ?: return text
        val name = (LocalCache.getAddressableNoteIfExists(address)?.event as? GitRepositoryEvent)?.name()?.ifBlank { null } ?: address.dTag
        return if (text.isNullOrBlank()) name else "$name · $text"
    }

    /**
     * Pick a title string for a status event based on the *target*'s kind, so
     * a 1631 on a kind-1618 PR reads "merged a pull request" while the same
     * status kind on a kind-1617 patch reads "applied a patch". [rootId] is
     * the marked-`root` `e` tag on the status event; when the target isn't in
     * cache we return [fallback] which is deliberately generic.
     */
    private fun titleForStatusOnTarget(
        rootId: String?,
        pr: StringResource,
        patch: StringResource,
        issue: StringResource,
        fallback: StringResource,
    ): StringResource {
        val targetKind = rootId?.let { LocalCache.getNoteIfExists(it)?.event?.kind } ?: return fallback
        return when (targetKind) {
            GitPullRequestEvent.KIND -> pr
            GitPatchEvent.KIND -> patch
            GitIssueEvent.KIND -> issue
            else -> fallback
        }
    }

    private fun draft(
        account: Account,
        id: String,
        createdAt: Long,
        authorPubkey: String,
        titleRes: StringResource,
        subject: String?,
    ): NotificationDraft? {
        val note = LocalCache.getNoteIfExists(id) ?: return null
        if (!account.isAcceptable(note)) return null

        val author = LocalCache.getOrCreateUser(authorPubkey)
        val uri = NotificationRoutes.noteUri(note, NotificationRoutes.accountNpub(account))
        val body = NotificationContent.excerpt(subject, 140)

        return NotificationDraft(
            id = id,
            users = listOf(author),
            notes = listOf(note),
            isComplete = { author.metadataOrNull()?.bestName() != null },
        ) {
            NotificationMessage(
                topic = NotificationTopic.CODE,
                id = id,
                title = loadStringRes(titleRes, author.toBestDisplayName()),
                body = body,
                time = createdAt,
                pictureUrl = author.profilePicture(),
                uri = uri,
            )
        }
    }
}
