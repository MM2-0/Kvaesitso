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

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.ui.components.RenderUserAsClickableText
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHalfTopPadding
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent

@Composable
fun RenderPublicMessage(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    isBoostedNote: Boolean = false,
) {
    val noteEvent = note.event as? PublicMessageEvent ?: return
    val content = remember(noteEvent) { noteEvent.peopleAndContent() }

    // A boosted note inside a zap/nutzap/onchain activity card is always shown as a
    // compact 2-line preview, even when the logged-in user is only a zap-split
    // beneficiary (and thus not the author) of the post being zapped.
    if (makeItShort && (isBoostedNote || accountViewModel.isLoggedUser(note.author))) {
        Text(
            text = content,
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
            TranslatableRichTextViewer(
                content = content,
                canPreview = canPreview && !makeItShort,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth(),
                tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() },
                backgroundColor = backgroundColor,
                id = note.idHex,
                callbackUri = callbackUri,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        if (noteEvent.hasHashtags()) {
            DisplayUncitedHashtags(noteEvent, noteEvent.content, callbackUri, accountViewModel, nav)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayUncitedUsers(
    event: PublicMessageEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    @Suppress("ProduceStateDoesNotAssignValue")
    val uncitedUsers by produceState(initialValue = emptyList()) {
        val users = event.groupKeySetWithoutOwner() - event.citedUsers()
        if (users.isNotEmpty()) {
            val newUsers = accountViewModel.loadUsersSync(users.toList())

            if (newUsers.isNotEmpty()) {
                value = newUsers
            }
        }
    }

    if (uncitedUsers.isNotEmpty()) {
        FlowRow(HalfHalfTopPadding) {
            uncitedUsers.forEach { user ->
                RenderUserAsClickableText(user, "", accountViewModel, nav)
            }
        }
    }
}
