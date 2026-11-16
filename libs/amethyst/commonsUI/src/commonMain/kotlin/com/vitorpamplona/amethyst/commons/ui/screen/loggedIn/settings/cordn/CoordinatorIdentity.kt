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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.contextvm.cep06Announcements.CvmServerAnnouncementEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * What to call a coordinator, and the face to put next to it.
 *
 * A coordinator is a Nostr identity -- ContextVM addresses it with ordinary
 * p-tags, and CEP-23 says outright that a server MAY publish NIP-01 profile
 * metadata (`CvmKinds.PROFILE_METADATA` is kind 0 for exactly this reason). So
 * the app can name one the same way it names anybody else, and showing 64 hex
 * characters instead was throwing that away.
 *
 * ## The order, and why
 *
 * 1. `CoordinatorConfig.label` -- documented as "what the user calls it. Never
 *    a claim -- a coordinator cannot prove a name." The only name here that
 *    means anything, so it wins.
 * 2. The CEP-6 announcement's name, when the cache holds one. Before the
 *    profile because a coordinator added from discovery was *picked* by this
 *    name, and having the settings screen rename it afterwards would be its own
 *    small confusion.
 * 3. The kind 0's display name (CEP-23).
 * 4. The key's first characters.
 *
 * Both 2 and 3 are the coordinator's own word for itself, and two servers may
 * publish the same one, which is why neither outranks a label.
 *
 * The profile is observed whatever the outcome, so the avatar still loads for a
 * coordinator that is named by 1 or 2.
 */
@Composable
fun coordinatorDisplayName(
    pubKey: HexKey,
    label: String?,
    accountViewModel: AccountViewModel,
): String {
    // Both unconditional: each one registers the subscription that fetches what
    // it reads, so neither may come and go with whether an earlier source in the
    // chain happens to have an answer.
    val fromProfile = observeUserNameByHex(pubKey, accountViewModel)
    val announced = observeAnnouncedServerName(pubKey, accountViewModel)

    return label?.takeIf { it.isNotBlank() }
        ?: announced
        ?: fromProfile
}

/**
 * The name from the coordinator's CEP-6 announcement, as the cache holds it.
 *
 * A [CvmServerAnnouncementEvent] is a replaceable event like any other now, so
 * reading it here gets the newest one per coordinator, already verified, and
 * fetched by the same event-finder data source the rest of the app uses --
 * rather than a hand-rolled fetch, a hand-rolled newest-wins and a second copy
 * of the name kept beside the cache.
 */
@Composable
fun observeAnnouncedServerName(
    pubKey: HexKey,
    accountViewModel: AccountViewModel,
): String? {
    val note =
        remember(pubKey) {
            LocalCache.getOrCreateAddressableNote(Address(CvmServerAnnouncementEvent.KIND, pubKey, ""))
        }
    val announcement by observeNoteEvent<CvmServerAnnouncementEvent>(note, accountViewModel)

    return announcement?.serverName()
}

/**
 * A coordinator rendered as the user it is: avatar, name, and whatever acts on
 * it.
 *
 * The avatar navigates to the profile, because [UserPicture] already does and
 * a coordinator's profile is as worth reading as anyone's -- more, given it is
 * the party whose metadata exposure the group info screen is about.
 *
 * [name] is a `RowScope` slot so a caller can weight it, which both of them
 * need; before it was, they weighted it anyway and compiled only because
 * `ColumnScope.weight` happens to produce the same element. [trailing] is its
 * own slot for the same reason: an action smuggled through a slot called `name`
 * is a lie about where it lands.
 */
@Composable
fun CoordinatorIdentityRow(
    pubKey: HexKey,
    label: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    trailing: @Composable RowScope.() -> Unit = {},
    name: @Composable RowScope.(String) -> Unit,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UserPicture(userHex = pubKey, size = size, accountViewModel = accountViewModel, nav = nav)
        name(coordinatorDisplayName(pubKey, label, accountViewModel))
        trailing()
    }
}
