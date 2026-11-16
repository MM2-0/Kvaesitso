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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.interestSets.list.metadata

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.nip51Lists.interestSets.InterestSet
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Stable
class InterestSetMetadataViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account

    var interestSet by mutableStateOf<InterestSet?>(null)
    val isNewList by derivedStateOf { interestSet == null }

    val name = mutableStateOf(TextFieldValue())

    val canPost by derivedStateOf {
        name.value.text.isNotBlank()
    }

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    fun new() {
        interestSet = null
        clear()
    }

    fun load(dTag: String) {
        interestSet = account.interestSets.getInterestSet(dTag)
        name.value = TextFieldValue(interestSet?.title ?: "")
    }

    fun createOrUpdate() {
        accountViewModel.launchSigner {
            val set = interestSet
            if (set == null) {
                account.interestSets.createInterestSet(
                    title = name.value.text,
                    account = account,
                )
            } else {
                account.interestSets.renameInterestSet(
                    newName = name.value.text,
                    set = set,
                    account = account,
                )
            }
            clear()
        }
    }

    fun clear() {
        name.value = TextFieldValue()
    }
}
