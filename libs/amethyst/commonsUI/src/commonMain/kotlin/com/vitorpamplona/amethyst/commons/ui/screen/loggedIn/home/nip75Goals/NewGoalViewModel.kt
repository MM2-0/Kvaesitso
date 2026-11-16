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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.nip75Goals

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip75ZapGoals.ZapGoalEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Stable
class NewGoalViewModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    var description by mutableStateOf(TextFieldValue(""))
    var amount by mutableStateOf(TextFieldValue(""))
    var summary by mutableStateOf(TextFieldValue(""))
    var imageUrl by mutableStateOf(TextFieldValue(""))
    var websiteUrl by mutableStateOf(TextFieldValue(""))

    var wantsDeadline by mutableStateOf(false)
    var deadlineTimestamp by mutableLongStateOf(TimeUtils.now() + TimeUtils.ONE_WEEK)

    fun init(accountVM: AccountViewModel) {
        this.accountViewModel = accountVM
        this.account = accountVM.account
    }

    fun canPost(): Boolean =
        description.text.isNotBlank() &&
            amount.text.isNotBlank() &&
            amount.text.toLongOrNull() != null &&
            (amount.text.toLongOrNull() ?: 0) > 0

    fun cancel() {
        description = TextFieldValue("")
        amount = TextFieldValue("")
        summary = TextFieldValue("")
        imageUrl = TextFieldValue("")
        websiteUrl = TextFieldValue("")
        wantsDeadline = false
        deadlineTimestamp = TimeUtils.now() + TimeUtils.ONE_WEEK
    }

    suspend fun sendPostSync() {
        val template = createTemplate() ?: return
        onUiThread { cancel() }
        account.signAndComputeBroadcast(template)
    }

    private fun createTemplate(): EventTemplate<out Event>? {
        val amountSats = amount.text.toLongOrNull() ?: return null
        val amountMillisats = amountSats * 1000L

        val relays =
            account.outboxRelays.flow.value
                .toList()

        val closedAt = if (wantsDeadline) deadlineTimestamp else null
        val img = imageUrl.text.ifBlank { null }
        val sum = summary.text.ifBlank { null }
        val web = websiteUrl.text.ifBlank { null }

        return ZapGoalEvent.build(
            description = description.text,
            amount = amountMillisats,
            relays = relays,
            closedAt = closedAt,
            image = img,
            summary = sum,
            websiteUrl = web,
        )
    }
}
