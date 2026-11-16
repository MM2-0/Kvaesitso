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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.marmot_system_admin_added
import com.vitorpamplona.amethyst.commons.resources.marmot_system_admin_added_passive
import com.vitorpamplona.amethyst.commons.resources.marmot_system_admin_removed
import com.vitorpamplona.amethyst.commons.resources.marmot_system_admin_removed_passive
import com.vitorpamplona.amethyst.commons.resources.marmot_system_avatar_changed
import com.vitorpamplona.amethyst.commons.resources.marmot_system_avatar_changed_passive
import com.vitorpamplona.amethyst.commons.resources.marmot_system_group_disbanded
import com.vitorpamplona.amethyst.commons.resources.marmot_system_group_disbanded_passive
import com.vitorpamplona.amethyst.commons.resources.marmot_system_group_renamed
import com.vitorpamplona.amethyst.commons.resources.marmot_system_group_renamed_passive
import com.vitorpamplona.amethyst.commons.resources.marmot_system_member_added
import com.vitorpamplona.amethyst.commons.resources.marmot_system_member_added_passive
import com.vitorpamplona.amethyst.commons.resources.marmot_system_member_left
import com.vitorpamplona.amethyst.commons.resources.marmot_system_member_removed
import com.vitorpamplona.amethyst.commons.resources.marmot_system_member_removed_passive
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatFeedRowRenderer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.marmot.foundation.appEvents.MarmotAppEvent
import com.vitorpamplona.marmot.foundation.appEvents.MarmotSystemEvent
import com.vitorpamplona.marmot.foundation.appEvents.MarmotSystemType
import org.jetbrains.compose.resources.StringResource

/**
 * Renders a kind:1210 group system row as a centered caption.
 *
 * These are not chat and must not read like it. A row is derived locally from
 * canonical group state rather than received as a message, so it has no sender
 * to attribute a bubble to — and its `content` is JSON, which a chat bubble
 * would render verbatim.
 *
 * The caption is built from the row's STRUCTURED fields, with its `text` member
 * used only as a fallback. That ordering is the spec's ("Clients SHOULD render
 * from the structured fields instead") and it is what lets the same row read in
 * the viewer's own terms rather than the writer's.
 */
class MarmotSystemRowRenderer(
    private val accountViewModel: AccountViewModel,
) : ChatFeedRowRenderer {
    override fun claims(note: Note): Boolean = note.event?.kind == MarmotAppEvent.KIND_SYSTEM

    @Composable
    override fun Render(note: Note) {
        val event = note.event ?: return
        val row = remember(event.id) { MarmotSystemEvent.fromAppEvent(MarmotAppEvent.fromEvent(event)) }
        // An unknown `system_type` decodes to null rather than throwing, because
        // the registry grows and an unfamiliar row must not break the feed. There
        // is nothing honest to draw for one, so it is simply not drawn.
        if (row == null) return
        val caption = caption(row)
        // A two-party row with no subject has nothing true to say; the spec's
        // fallback text would be a generic label, not information.
        if (caption.isEmpty()) return

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = caption,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    /**
     * The row in words, naming people rather than pubkeys where we know them.
     *
     * Each type has an active and a passive phrasing: the actor is optional —
     * a row derived from a commit whose committer we cannot attribute is still
     * a true row — so "who did it" is never assumed. The row's own `text`
     * member is the last fallback, which is what it exists for.
     */
    @Composable
    private fun caption(row: MarmotSystemEvent): String {
        val actor = row.actor?.let { displayName(it) }
        val subject = row.subject?.let { displayName(it) }
        return when (row.systemType) {
            MarmotSystemType.MEMBER_ADDED ->
                twoParty(subject, actor, Res.string.marmot_system_member_added, Res.string.marmot_system_member_added_passive)

            MarmotSystemType.MEMBER_REMOVED ->
                twoParty(subject, actor, Res.string.marmot_system_member_removed, Res.string.marmot_system_member_removed_passive)

            MarmotSystemType.MEMBER_LEFT ->
                subject?.let { stringRes(Res.string.marmot_system_member_left, it) } ?: row.text

            MarmotSystemType.ADMIN_ADDED ->
                twoParty(subject, actor, Res.string.marmot_system_admin_added, Res.string.marmot_system_admin_added_passive)

            MarmotSystemType.ADMIN_REMOVED ->
                twoParty(subject, actor, Res.string.marmot_system_admin_removed, Res.string.marmot_system_admin_removed_passive)

            MarmotSystemType.GROUP_RENAMED ->
                row.name?.let { name ->
                    if (actor != null) {
                        stringRes(Res.string.marmot_system_group_renamed, actor, name)
                    } else {
                        stringRes(Res.string.marmot_system_group_renamed_passive, name)
                    }
                } ?: row.text

            MarmotSystemType.GROUP_AVATAR_CHANGED ->
                actor?.let { stringRes(Res.string.marmot_system_avatar_changed, it) }
                    ?: stringRes(Res.string.marmot_system_avatar_changed_passive)

            MarmotSystemType.GROUP_DISBANDED ->
                actor?.let { stringRes(Res.string.marmot_system_group_disbanded, it) }
                    ?: stringRes(Res.string.marmot_system_group_disbanded_passive)
        }
    }

    /**
     * A row about one member, phrased actively when the committer is known and
     * passively when it is not.
     */
    @Composable
    private fun twoParty(
        subject: String?,
        actor: String?,
        active: StringResource,
        passive: StringResource,
    ): String {
        if (subject == null) return ""
        return if (actor != null) stringRes(active, actor, subject) else stringRes(passive, subject)
    }

    /** A known display name, or a short key when the account is a stranger. */
    @Composable
    private fun displayName(pubkeyHex: String): String {
        val user = LocalCache.getUserIfExists(pubkeyHex)
        return user?.toBestDisplayName() ?: pubkeyHex.take(8)
    }
}
