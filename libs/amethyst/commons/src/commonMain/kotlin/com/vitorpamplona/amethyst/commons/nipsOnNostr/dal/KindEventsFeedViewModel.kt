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
package com.vitorpamplona.amethyst.commons.nipsOnNostr.dal

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.viewmodels.AndroidFeedViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.FeedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/** The kind page's sample of events of [kind], kept current from the cache like any feed. */
@Stable
class KindEventsFeedViewModel(
    val kind: Int,
    val account: Account,
) : AndroidFeedViewModel(KindEventsFeedFilter(kind, account)) {
    init {
        rebuildOnMutes(account)
    }

    class Factory(
        val kind: Int,
        val account: Account,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = KindEventsFeedViewModel(kind, account) as T
    }
}

/**
 * Rebuilds the feed whenever the account mutes, blocks or unmutes someone. An additive feed only
 * adds what arrives, so a note by a newly hidden author would otherwise stay listed until the page
 * is reopened.
 */
private fun FeedViewModel.rebuildOnMutes(account: Account) {
    viewModelScope.launch(Dispatchers.IO) {
        account.hiddenUsers.flow
            .drop(1)
            .collect { invalidateData() }
    }
}
