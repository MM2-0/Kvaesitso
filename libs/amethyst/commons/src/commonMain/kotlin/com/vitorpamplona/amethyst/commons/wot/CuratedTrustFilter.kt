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
package com.vitorpamplona.amethyst.commons.wot

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

/**
 * Whether the Web of Trust drops [event] from Curated notifications. The one list of exemptions
 * the in-app feed and push both use, so the two cannot drift apart:
 *
 *  - zaps of every kind cost the sender money;
 *  - chess has its own players, Buzz and Concord ([inJoinedCommunity]) their own membership;
 *  - private messages follow the DM tabs' Known rule (a stranger the user already wrote to is
 *    Known), see [Account.isKnownChatroom].
 *
 * [author] is who the notification is from (a zap's sender, not the receipt's signer). False while
 * no network is active.
 */
fun Account.curatedHidesByTrust(
    event: Event,
    author: HexKey = event.pubKey,
    inJoinedCommunity: Boolean = false,
): Boolean {
    if (inJoinedCommunity) return false
    val verdicts = currentTrustVerdicts()
    if (!verdicts.isActive) return false
    return when (event) {
        is ZapReceiptEvent, is Bolt12ZapEvent, is NutzapEvent, is OnchainZapEvent -> false
        is LiveChessGameAcceptEvent, is LiveChessMoveEvent, is StreamMessageV2Event, is ChatEvent -> false
        is ChatroomKeyable -> !isKnownChatroom(event.chatroomKey(signer.pubKey), event.pubKey)
        else -> verdicts.isOutside(author)
    }
}
