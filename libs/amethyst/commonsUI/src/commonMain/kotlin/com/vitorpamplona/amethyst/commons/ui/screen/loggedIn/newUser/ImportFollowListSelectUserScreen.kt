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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.newUser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.searchCommand.UserSearchDataSourceSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.import_follow_list
import com.vitorpamplona.amethyst.commons.resources.import_follows_tips
import com.vitorpamplona.amethyst.commons.resources.name_search_npub1_alice_example_com
import com.vitorpamplona.amethyst.commons.resources.profile_to_import_from
import com.vitorpamplona.amethyst.commons.resources.skip_for_now
import com.vitorpamplona.amethyst.commons.resources.start_with_a_great_feed_by_following_the_same_people_as_someone_you_trust
import com.vitorpamplona.amethyst.commons.resources.supports_npub_nip_05_hex_and_namecoin_bit_d_id
import com.vitorpamplona.amethyst.commons.resources.tip
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserLine
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip05DnsIdentifiers.INip05Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

@Stable
class ImportFollowListSelectUserViewModel(
    val account: Account,
    val nip05Client: INip05Client,
) : ViewModel() {
    var identifier by mutableStateOf("")
    val userSuggestions = UserSuggestionState(account, nip05Client)
    val listState = LazyListState(0, 0)

    val searchTerm =
        userSuggestions.searchTerm
            .onEach {
                if (!it.isNullOrBlank()) {
                    listState.scrollToItem(0, 0)
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, userSuggestions.currentWord)

    val results =
        userSuggestions.results
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    class Factory(
        val account: Account,
        val nip05Client: INip05Client,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = ImportFollowListSelectUserViewModel(account, nip05Client) as T
    }
}

@Composable
fun ImportFollowListSelectUserScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: ImportFollowListSelectUserViewModel =
        rememberViewModel(
            factory = ImportFollowListSelectUserViewModel.Factory(accountViewModel.account, accountViewModel.nip05ClientBuilder()),
        )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {},
                nav = nav,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            InputSelectUserBody(
                viewModel = viewModel,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun InputSelectUserBody(
    viewModel: ImportFollowListSelectUserViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        ImportHeader()
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = viewModel.identifier,
            onValueChange = {
                viewModel.identifier = it
                viewModel.userSuggestions.processCurrentWord(it)
            },
            label = { Text(stringRes(Res.string.profile_to_import_from)) },
            placeholder = { Text(stringRes(Res.string.name_search_npub1_alice_example_com)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            supportingText = { Text(stringRes(Res.string.supports_npub_nip_05_hex_and_namecoin_bit_d_id)) },
        )

        Spacer(Modifier.height(8.dp))

        CustomShowUserSuggestionList(
            viewModel = viewModel,
            onSelect = { user ->
                nav.nav(Route.ImportFollowsPickFollows(user.pubkeyHex))
            },
            modifier = Modifier.weight(1f),
            accountViewModel = accountViewModel,
        )

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = nav::popBack) { Text(stringRes(Res.string.skip_for_now)) }
        }
    }
}

@Composable
private fun ImportHeader() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                MaterialSymbols.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringRes(Res.string.import_follow_list),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringRes(Res.string.start_with_a_great_feed_by_following_the_same_people_as_someone_you_trust),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CustomShowUserSuggestionList(
    viewModel: ImportFollowListSelectUserViewModel,
    onSelect: (User) -> Unit,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    UserSearchDataSourceSubscription(viewModel.userSuggestions, accountViewModel)

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect {
                viewModel.userSuggestions.invalidateData()
            }
        }
        launch(Dispatchers.IO) {
            LocalCache.live.deletedEventBundles.collect {
                viewModel.userSuggestions.invalidateData()
            }
        }
    }

    CustomWatchResponses(viewModel, onSelect, accountViewModel, modifier)
}

@Composable
fun CustomWatchResponses(
    viewModel: ImportFollowListSelectUserViewModel,
    onSelect: (User) -> Unit,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val suggestions by viewModel.results.collectAsStateWithLifecycle()

    if (suggestions.isNotEmpty()) {
        LazyColumn(
            contentPadding = PaddingValues(top = 10.dp),
            modifier = modifier,
            state = viewModel.listState,
        ) {
            itemsIndexed(suggestions, key = { _, item -> item.pubkeyHex }) { _, item ->
                UserLine(item, accountViewModel) { onSelect(item) }
                HorizontalDivider(
                    thickness = DividerThickness,
                )
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = modifier.padding(24.dp),
        ) {
            Text(
                stringRes(Res.string.tip),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                stringRes(Res.string.import_follows_tips),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
