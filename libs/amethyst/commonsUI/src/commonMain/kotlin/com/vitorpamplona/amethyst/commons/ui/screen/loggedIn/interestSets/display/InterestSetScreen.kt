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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.interestSets.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip51Lists.interestSets.InterestSet
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.interest_set_add_hashtag
import com.vitorpamplona.amethyst.commons.resources.interest_set_hashtag_add_placeholder
import com.vitorpamplona.amethyst.commons.resources.interest_set_hashtag_private_toggle
import com.vitorpamplona.amethyst.commons.resources.interest_set_toggle_visibility
import com.vitorpamplona.amethyst.commons.resources.interest_sets_empty
import com.vitorpamplona.amethyst.commons.resources.interest_sets_title
import com.vitorpamplona.amethyst.commons.resources.quick_action_delete
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun InterestSetScreen(
    dTag: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val sets by accountViewModel.account.interestSets.listFeedFlow
        .collectAsStateWithLifecycle()
    val set = sets.firstOrNull { it.identifier == dTag }

    Scaffold(
        topBar = {
            TopBarWithBackButton(caption = set?.title ?: stringRes(Res.string.interest_sets_title), nav)
        },
    ) { paddingValues ->
        if (set == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringRes(Res.string.interest_sets_empty))
            }
        } else {
            InterestSetContent(
                set = set,
                paddingValues = paddingValues,
                accountViewModel = accountViewModel,
            )
        }
    }
}

@Composable
private fun InterestSetContent(
    set: InterestSet,
    paddingValues: PaddingValues,
    accountViewModel: AccountViewModel,
) {
    var newHashtag by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newHashtag,
                onValueChange = { newHashtag = it.trimStart('#').trim() },
                label = { Text(stringRes(Res.string.interest_set_hashtag_add_placeholder)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.None,
                    ),
            )
            IconButton(
                onClick = {
                    val tag = newHashtag.trim()
                    if (tag.isNotEmpty()) {
                        accountViewModel.launchSigner {
                            accountViewModel.account.interestSets.addHashtagToSet(
                                hashtag = tag,
                                identifier = set.identifier,
                                isPrivate = isPrivate,
                                account = accountViewModel.account,
                            )
                        }
                        newHashtag = ""
                    }
                },
                enabled = newHashtag.isNotBlank(),
            ) {
                Icon(MaterialSymbols.Add, contentDescription = stringRes(Res.string.interest_set_add_hashtag))
            }
        }

        FilterChip(
            selected = isPrivate,
            onClick = { isPrivate = !isPrivate },
            label = { Text(stringRes(Res.string.interest_set_hashtag_private_toggle)) },
            leadingIcon = {
                Icon(
                    symbol = if (isPrivate) MaterialSymbols.Lock else MaterialSymbols.LockOpen,
                    contentDescription = null,
                )
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 10.dp),
        ) {
            val publics = set.publicHashtags.sorted()
            val privates = set.privateHashtags.sorted()

            items(publics, key = { "public-$it" }) { tag ->
                HashtagRow(
                    hashtag = tag,
                    isPrivate = false,
                    onDelete = {
                        accountViewModel.launchSigner {
                            accountViewModel.account.interestSets.removeHashtagFromSet(
                                hashtag = tag,
                                identifier = set.identifier,
                                account = accountViewModel.account,
                            )
                        }
                    },
                    onToggle = {
                        accountViewModel.launchSigner {
                            accountViewModel.account.interestSets.moveHashtagInSet(
                                hashtag = tag,
                                identifier = set.identifier,
                                isCurrentlyPrivate = false,
                                account = accountViewModel.account,
                            )
                        }
                    },
                )
            }

            items(privates, key = { "private-$it" }) { tag ->
                HashtagRow(
                    hashtag = tag,
                    isPrivate = true,
                    onDelete = {
                        accountViewModel.launchSigner {
                            accountViewModel.account.interestSets.removeHashtagFromSet(
                                hashtag = tag,
                                identifier = set.identifier,
                                account = accountViewModel.account,
                            )
                        }
                    },
                    onToggle = {
                        accountViewModel.launchSigner {
                            accountViewModel.account.interestSets.moveHashtagInSet(
                                hashtag = tag,
                                identifier = set.identifier,
                                isCurrentlyPrivate = true,
                                account = accountViewModel.account,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HashtagRow(
    hashtag: String,
    isPrivate: Boolean,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
) {
    ListItem(
        headlineContent = { Text("#$hashtag") },
        leadingContent = {
            IconButton(onClick = onToggle) {
                Icon(
                    symbol = if (isPrivate) MaterialSymbols.Lock else MaterialSymbols.LockOpen,
                    contentDescription = stringRes(Res.string.interest_set_toggle_visibility),
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    symbol = MaterialSymbols.Delete,
                    contentDescription = stringRes(Res.string.quick_action_delete),
                )
            }
        },
    )
}
