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
package com.vitorpamplona.amethyst.service.pow

import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.resources.boost
import com.vitorpamplona.amethyst.commons.resources.post
import com.vitorpamplona.amethyst.commons.resources.pow_kind_chat_message
import com.vitorpamplona.amethyst.commons.resources.pow_kind_report
import com.vitorpamplona.amethyst.commons.resources.private_message
import com.vitorpamplona.amethyst.commons.resources.reaction
import com.vitorpamplona.amethyst.commons.resources.voice_post
import com.vitorpamplona.amethyst.commons.resources.voice_reply
import com.vitorpamplona.amethyst.commons.service.pow.powKindLabelRes
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent

/**
 * Android-resource twin of [powKindLabelRes], for [com.vitorpamplona.amethyst.service.pow.PowMiningForegroundService].
 *
 * A foreground-service notification is built inside Android's startForeground()
 * timeout, so its text has to resolve synchronously - the Compose catalog only
 * offers a composable and a suspend accessor, neither of which can run there.
 */
fun powKindLabelResId(kind: Int): Int =
    when (kind) {
        ReactionEvent.KIND -> R.string.reaction
        RepostEvent.KIND, GenericRepostEvent.KIND -> R.string.boost
        VoiceEvent.KIND -> R.string.voice_post
        VoiceReplyEvent.KIND -> R.string.voice_reply
        ReportEvent.KIND -> R.string.pow_kind_report
        GiftWrapEvent.KIND -> R.string.private_message
        ChannelMessageEvent.KIND, LiveActivitiesChatMessageEvent.KIND -> R.string.pow_kind_chat_message
        else -> R.string.post
    }
