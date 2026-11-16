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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.badges.award

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.badges.award.AwardBadgeViewModel
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.award_badge
import com.vitorpamplona.amethyst.commons.resources.award_badge_action
import com.vitorpamplona.amethyst.commons.resources.award_badge_loading
import com.vitorpamplona.amethyst.commons.resources.award_badge_remove_recipient
import com.vitorpamplona.amethyst.commons.resources.award_badge_search_label
import com.vitorpamplona.amethyst.commons.resources.award_badge_search_placeholder
import com.vitorpamplona.amethyst.commons.ui.components.Nip05OrPubkeyLine
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightPage
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@Composable
fun AwardBadgeScreen(
    kind: Int,
    pubKeyHex: HexKey,
    dTag: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vm: AwardBadgeViewModel = rememberViewModel { AwardBadgeViewModel() }

    LaunchedEffect(accountViewModel, kind, pubKeyHex, dTag) {
        vm.init(accountViewModel.account, kind, pubKeyHex, dTag)
    }

    var searchInput by remember { mutableStateOf("") }
    val selectedUsers = remember { mutableStateListOf<User>() }

    val userSuggestions =
        remember {
            UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
        }

    DisposableEffect(Unit) {
        onDispose { userSuggestions.reset() }
    }

    PlatformBackHandler {
        nav.popBack()
    }

    Scaffold(
        topBar = {
            ActionTopBar(
                postRes = Res.string.award_badge_action,
                titleRes = Res.string.award_badge,
                isActive = { vm.definition != null && selectedUsers.isNotEmpty() },
                onCancel = { nav.popBack() },
                onPost = {
                    val toAward = selectedUsers.toList()
                    accountViewModel.launchSigner {
                        vm.sendPost(toAward)
                        nav.popBack()
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePaddingSafe(),
        ) {
            BadgeSummary(vm)

            HorizontalDivider()

            if (selectedUsers.isNotEmpty()) {
                selectedUsers.toList().forEachIndexed { index, user ->
                    SelectedUserRow(
                        user = user,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        onClear = { selectedUsers.remove(user) },
                    )
                    if (index < selectedUsers.lastIndex) HorizontalDivider()
                }
                HorizontalDivider()
            }

            OutlinedTextField(
                value = searchInput,
                onValueChange = { newValue ->
                    searchInput = newValue
                    if (newValue.length > 2) {
                        userSuggestions.processCurrentWord(newValue)
                    } else {
                        userSuggestions.reset()
                    }
                },
                label = { Text(stringRes(Res.string.award_badge_search_label)) },
                placeholder = { Text(stringRes(Res.string.award_badge_search_placeholder)) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (searchInput.length > 2) {
                ShowUserSuggestionList(
                    userSuggestions = userSuggestions,
                    onSelect = { user ->
                        if (selectedUsers.none { it.pubkeyHex == user.pubkeyHex }) {
                            selectedUsers.add(user)
                        }
                        searchInput = ""
                        userSuggestions.reset()
                    },
                    accountViewModel = accountViewModel,
                    modifier = SuggestionListDefaultHeightPage,
                )
            }
        }
    }
}

@Composable
private fun BadgeSummary(vm: AwardBadgeViewModel) {
    val def = vm.definition
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (def == null) {
            Text(
                text = stringRes(Res.string.award_badge_loading),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = def.name() ?: def.dTag(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            def.description()?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SelectedUserRow(
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
    onClear: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserPicture(
            userHex = user.pubkeyHex,
            size = 40.dp,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        ) {
            Text(
                text = user.toBestDisplayName(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Nip05OrPubkeyLine(user)
        }
        TextButton(onClick = onClear) {
            Text(stringRes(Res.string.award_badge_remove_recipient))
        }
    }
}
