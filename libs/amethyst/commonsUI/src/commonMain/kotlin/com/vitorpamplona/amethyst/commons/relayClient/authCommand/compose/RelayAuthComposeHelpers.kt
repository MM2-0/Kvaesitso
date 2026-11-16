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
package com.vitorpamplona.amethyst.commons.relayClient.authCommand.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_auth_purpose_my_inbox
import com.vitorpamplona.amethyst.commons.resources.relay_auth_purpose_my_own_relay
import com.vitorpamplona.amethyst.commons.resources.relay_auth_purpose_notify_inbox
import com.vitorpamplona.amethyst.commons.resources.relay_auth_purpose_other
import com.vitorpamplona.amethyst.commons.resources.relay_auth_purpose_post_venue
import com.vitorpamplona.amethyst.commons.resources.relay_auth_purpose_read_outbox
import com.vitorpamplona.amethyst.commons.resources.relay_auth_purpose_read_venue
import com.vitorpamplona.amethyst.commons.resources.relay_auth_purpose_send_dm
import com.vitorpamplona.amethyst.commons.resources.relay_auth_purpose_thread
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import org.jetbrains.compose.resources.StringResource

/**
 * Loads [pubkey] from the local cache, get-or-creating (and subscribing) if absent, then hands the
 * [User] (or null while it loads) to [content]. Shared by the auth prompt dialog and the relay-auth
 * settings screen, which both render a person by pubkey while their metadata streams in.
 */
@Composable
fun LoadRelayAuthUser(
    pubkey: HexKey,
    content: @Composable (User?) -> Unit,
) {
    var user by remember(pubkey) { mutableStateOf(LocalCache.getUserIfExists(pubkey)) }
    if (user == null) {
        LaunchedEffect(pubkey) { user = LocalCache.checkGetOrCreateUser(pubkey) }
    }
    content(user)
}

/**
 * A short chip label naming what a relay was doing for us under one [AuthPurposeKind] — "your inbox",
 * "a conversation", "people you read".
 *
 * Deliberately *not* the `relay_auth_why_*` sentences the prompt uses. Those are addressed to someone
 * deciding right now ("It won't serve … to readers it can't identify"); these caption a past login in
 * the settings log, where a full sentence per row would be the wall of text this screen just lost.
 */
internal fun relayAuthPurposeLabelRes(kind: AuthPurposeKind): StringResource =
    when (kind) {
        AuthPurposeKind.SEND_DM -> Res.string.relay_auth_purpose_send_dm
        AuthPurposeKind.NOTIFY_INBOX -> Res.string.relay_auth_purpose_notify_inbox
        AuthPurposeKind.READ_OUTBOX -> Res.string.relay_auth_purpose_read_outbox
        AuthPurposeKind.POST_VENUE -> Res.string.relay_auth_purpose_post_venue
        AuthPurposeKind.READ_VENUE -> Res.string.relay_auth_purpose_read_venue
        AuthPurposeKind.MY_INBOX -> Res.string.relay_auth_purpose_my_inbox
        AuthPurposeKind.THREAD -> Res.string.relay_auth_purpose_thread
        AuthPurposeKind.MY_OWN_RELAY -> Res.string.relay_auth_purpose_my_own_relay
        AuthPurposeKind.OTHER -> Res.string.relay_auth_purpose_other
    }
