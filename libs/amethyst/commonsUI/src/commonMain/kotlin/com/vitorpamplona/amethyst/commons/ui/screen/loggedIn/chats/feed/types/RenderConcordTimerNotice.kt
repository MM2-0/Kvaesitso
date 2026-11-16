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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.chats.ui.ChatSystemMessage
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.concord_timer_notice_off
import com.vitorpamplona.amethyst.commons.resources.concord_timer_notice_set
import com.vitorpamplona.amethyst.commons.resources.duration_days
import com.vitorpamplona.amethyst.commons.resources.duration_hours
import com.vitorpamplona.amethyst.commons.resources.duration_minutes
import com.vitorpamplona.amethyst.commons.resources.duration_weeks
import com.vitorpamplona.amethyst.commons.resources.duration_years
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size18dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordTimerNoticeEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Concord timer notice (CORD-08 §4, kind 1740) as an inline system line: "Alice set disappearing
 * messages to 30 days" / "Alice turned off disappearing messages", with the actor's avatar. The feed
 * only reaches here for a notice whose author holds MANAGE_METADATA (see `Account.isAcceptable`).
 */
@Composable
fun RenderConcordTimerNotice(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? ConcordTimerNoticeEvent ?: return
    val secs = event.timerSecs() ?: return
    val actor = observeUserNameByHex(event.pubKey, accountViewModel)
    val text =
        if (secs > 0) {
            stringRes(Res.string.concord_timer_notice_set, actor, concordTimerText(secs))
        } else {
            stringRes(Res.string.concord_timer_notice_off, actor)
        }

    ChatSystemMessage(
        text = text,
        onClick = { nav.nav(Route.Profile(event.pubKey)) },
        leading = {
            UserPicture(
                userHex = event.pubKey,
                size = Size18dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
    )
}

/**
 * A disappearing-messages timer in words, in the largest whole unit that divides it: "1 year",
 * "1 week", "30 days", "90 days" — the offered presets read the way staff picked them.
 */
@Composable
fun concordTimerText(seconds: Long): String {
    val day = TimeUtils.ONE_DAY.toLong()
    return when {
        seconds >= 365 * day && seconds % (365 * day) == 0L -> (seconds / (365 * day)).toInt().let { pluralStringRes(Res.plurals.duration_years, it, it) }
        seconds >= 7 * day && seconds % (7 * day) == 0L -> (seconds / (7 * day)).toInt().let { pluralStringRes(Res.plurals.duration_weeks, it, it) }
        seconds >= day -> (seconds / day).toInt().let { pluralStringRes(Res.plurals.duration_days, it, it) }
        seconds >= TimeUtils.ONE_HOUR -> (seconds / TimeUtils.ONE_HOUR).toInt().let { pluralStringRes(Res.plurals.duration_hours, it, it) }
        else -> (seconds / TimeUtils.ONE_MINUTE).toInt().coerceAtLeast(1).let { pluralStringRes(Res.plurals.duration_minutes, it, it) }
    }
}
