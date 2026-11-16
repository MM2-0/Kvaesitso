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
import com.vitorpamplona.amethyst.commons.notifications.NotificationDraft
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_poll_channel_message
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent

/**
 * Picks the composer for the event kinds whose notification needs no platform decision:
 * zaps, reactions, reposts, badges, media, polls, articles and git. Returns null for any
 * other kind; text notes, comments, messages and chess are routed by the caller, which knows
 * whether a note is a reply and whether a feature is on.
 */
object StandardNotificationComposer {
    // GitReplyEvent (kind 1622) is deprecated in favour of NIP-22 comments, but events already
    // on relays still arrive and still notify.
    @Suppress("DEPRECATION")
    suspend fun compose(
        account: Account,
        event: Event,
    ): NotificationDraft? =
        when (event) {
            is ZapReceiptEvent -> ZapNotificationComposer.compose(account, event)
            is NutzapEvent -> ZapNotificationComposer.compose(account, event)
            is OnchainZapEvent -> ZapNotificationComposer.compose(account, event)

            is ReactionEvent -> ReactionNotificationComposer.compose(account, event)

            is RepostEvent -> RepostNotificationComposer.compose(account, event)
            is GenericRepostEvent -> RepostNotificationComposer.compose(account, event)

            is BadgeAwardEvent -> BadgeNotificationComposer.compose(account, event)

            is PictureEvent,
            is VideoNormalEvent,
            is VideoShortEvent,
            is AddressableNormalVideoEvent,
            is AddressableShortVideoEvent,
            -> MediaNotificationComposer.compose(account, event)

            is PollEvent -> MentionNotificationComposer.compose(account, event, titleRes = Res.string.app_notification_poll_channel_message)

            is HighlightEvent,
            is LongFormContentEvent,
            is WikiArticleEvent,
            -> ArticleNotificationComposer.compose(account, event)

            is GitIssueEvent -> CodeNotificationComposer.compose(account, event)
            is GitPatchEvent -> CodeNotificationComposer.compose(account, event)
            is GitPullRequestEvent -> CodeNotificationComposer.compose(account, event)
            is GitPullRequestUpdateEvent -> CodeNotificationComposer.compose(account, event)
            is GitReplyEvent -> CodeNotificationComposer.compose(account, event)
            is GitStatusOpenEvent -> CodeNotificationComposer.compose(account, event)
            is GitStatusAppliedEvent -> CodeNotificationComposer.compose(account, event)
            is GitStatusClosedEvent -> CodeNotificationComposer.compose(account, event)
            is GitStatusDraftEvent -> CodeNotificationComposer.compose(account, event)

            else -> null
        }
}
