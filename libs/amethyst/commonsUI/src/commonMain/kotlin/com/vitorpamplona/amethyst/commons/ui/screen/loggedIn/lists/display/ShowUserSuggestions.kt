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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.display

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults.cardElevation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.searchCommand.UserSearchDataSourceSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.in_the_list
import com.vitorpamplona.amethyst.commons.resources.remove
import com.vitorpamplona.amethyst.commons.resources.search_and_add_a_user
import com.vitorpamplona.amethyst.commons.ui.note.AboutDisplay
import com.vitorpamplona.amethyst.commons.ui.note.ClearTextIcon
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.AnimateOnNewSearch
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.HalfVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.LightRedColor
import com.vitorpamplona.amethyst.commons.ui.theme.PopupUpEffect
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun RenderAddUserFieldAndSuggestions(
    userSuggestions: UserSuggestionState,
    hasUserFlow: (User) -> Flow<Boolean>,
    addUserToSet: (User) -> Unit,
    removeUserFromSet: (User) -> Unit,
    accountViewModel: AccountViewModel,
) {
    UserSearchDataSourceSubscription(userSuggestions, accountViewModel)

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect {
                userSuggestions.invalidateData()
            }
        }
        launch(Dispatchers.IO) {
            LocalCache.live.deletedEventBundles.collect {
                userSuggestions.invalidateData()
            }
        }
    }

    Spacer(HalfVertSpacer)

    var userName by remember(userSuggestions) { mutableStateOf(TextFieldValue(userSuggestions.currentWord.value)) }
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        label = { Text(text = stringRes(Res.string.search_and_add_a_user)) },
        modifier = Modifier.padding(horizontal = Size10dp).fillMaxWidth(),
        value = userName,
        onValueChange = {
            userName = it
            userSuggestions.processCurrentWord(it.text)
        },
        singleLine = true,
        trailingIcon = {
            IconButton(
                onClick = {
                    userName = TextFieldValue("")
                    userSuggestions.processCurrentWord("")
                    focusManager.clearFocus()
                },
            ) {
                ClearTextIcon()
            }
        },
    )

    ShowUserSuggestions(
        userSuggestions = userSuggestions,
        hasUserFlow = hasUserFlow,
        onSelect = { user ->
            addUserToSet(user)
            userName =
                userName.copy(
                    selection = TextRange(0, userName.text.length),
                )
        },
        onDelete = { user ->
            removeUserFromSet(user)
            userName =
                userName.copy(
                    selection = TextRange(0, userName.text.length),
                )
        },
        accountViewModel = accountViewModel,
    )
}

@Composable
fun ShowUserSuggestions(
    userSuggestions: UserSuggestionState,
    hasUserFlow: (User) -> Flow<Boolean>,
    onSelect: (User) -> Unit,
    onDelete: (User) -> Unit,
    accountViewModel: AccountViewModel,
) {
    val listState = rememberLazyListState()

    AnimateOnNewSearch(userSuggestions, listState)

    val suggestions by userSuggestions.results.collectAsStateWithLifecycle(emptyList())

    if (suggestions.isNotEmpty()) {
        Card(
            modifier = Modifier.padding(start = 11.dp, end = 11.dp),
            elevation = cardElevation(5.dp),
            shape = PopupUpEffect,
        ) {
            LazyColumn(
                contentPadding = PaddingValues(top = 10.dp),
                modifier =
                    Modifier
                        .heightIn(0.dp, 200.dp),
                state = listState,
            ) {
                itemsIndexed(suggestions, key = { _, item -> item.pubkeyHex }) { _, baseUser ->
                    DrawUser(baseUser, hasUserFlow, onSelect, onDelete, accountViewModel)

                    HorizontalDivider(
                        thickness = DividerThickness,
                    )
                }
            }
        }
    }

    Spacer(StdVertSpacer)
}

@Composable
fun DrawUser(
    baseUser: User,
    hasUserFlow: (User) -> Flow<Boolean>,
    onSelect: (User) -> Unit,
    onDelete: (User) -> Unit,
    accountViewModel: AccountViewModel,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = { onSelect(baseUser) })
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 10.dp,
                    bottom = 10.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClickableUserPicture(baseUser, 55.dp, accountViewModel, Modifier, null)

        Column(
            modifier =
                Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UsernameDisplay(
                    baseUser,
                    accountViewModel = accountViewModel,
                )
                HasUserTag(baseUser, hasUserFlow, onDelete)
            }

            AboutDisplay(baseUser, accountViewModel)
        }
    }
}

@Composable
private fun RowScope.HasUserTag(
    baseUser: User,
    hasUserFlow: (User) -> Flow<Boolean>,
    onDelete: (User) -> Unit,
) {
    val hasUserState by hasUserFlow(baseUser).collectAsStateWithLifecycle(false)
    if (hasUserState) {
        Spacer(StdHorzSpacer)
        Text(
            text = stringRes(id = Res.string.in_the_list),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                remember {
                    Modifier
                        .clip(SmallBorder)
                        .background(Color.Black)
                        .padding(horizontal = 5.dp)
                },
        )
        Spacer(Modifier.weight(1f))
        IconButton(
            modifier = Modifier.size(30.dp).padding(start = 10.dp),
            onClick = { onDelete(baseUser) },
        ) {
            Icon(
                symbol = MaterialSymbols.Cancel,
                contentDescription = stringRes(id = Res.string.remove),
                modifier = Modifier.size(15.dp),
                tint = LightRedColor,
            )
        }
    }
}
