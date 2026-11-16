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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.nip85TrustedAssertions.ui.EditNicknameDialog
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserNickname
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nickname_private
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The nickname (petname) and private note the account keeps for [baseUser],
 * shown above the profile's own display name without replacing it. The lock
 * marks it as private — both fields live NIP-44 encrypted in the account's
 * contact card. Tapping the card opens the editor.
 */
@Composable
fun UserNicknameCard(
    baseUser: User,
    accountViewModel: AccountViewModel,
) {
    val nickname by observeUserNickname(baseUser, accountViewModel)
    val card = nickname ?: return

    val isEditDialogOpen = remember { mutableStateOf(false) }

    if (isEditDialogOpen.value) {
        EditNicknameDialog(
            user = baseUser,
            accountViewModel = accountViewModel,
            onDismiss = { isEditDialogOpen.value = false },
        )
    }

    OutlinedCard(
        onClick = { isEditDialogOpen.value = true },
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 3.5.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                card.petName?.let {
                    CreateTextWithEmoji(
                        text = it,
                        tags = card.tags,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )
                }

                if (card.petName != null && card.summary != null) {
                    HorizontalDivider(thickness = DividerThickness)
                }

                card.summary?.let {
                    CreateTextWithEmoji(
                        text = it,
                        tags = card.tags,
                        color = MaterialTheme.colorScheme.placeholderText,
                        fontSize = 14.sp,
                    )
                }
            }

            Icon(
                symbol = MaterialSymbols.Lock,
                contentDescription = stringRes(Res.string.nickname_private),
                tint = MaterialTheme.colorScheme.placeholderText,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(14.dp),
            )
        }
    }
}
