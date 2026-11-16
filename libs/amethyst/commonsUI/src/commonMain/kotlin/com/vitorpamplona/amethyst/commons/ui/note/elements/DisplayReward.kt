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
package com.vitorpamplona.amethyst.commons.ui.note.elements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReplies
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.bounty_label
import com.vitorpamplona.amethyst.commons.resources.pledge_amount_in_sats
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.note.HeaderPill
import com.vitorpamplona.amethyst.commons.ui.note.buttons.CloseButton
import com.vitorpamplona.amethyst.commons.ui.note.buttons.PostButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.util.logSignerErrors
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.parseBigDecimalOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@Stable data class Reward(
    val amount: BigDecimal,
)

@Composable
fun DisplayReward(
    baseReward: Reward,
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    var popupExpanded by remember { mutableStateOf(false) }

    RenderPledgeAmount(baseNote, baseReward, accountViewModel) { popupExpanded = true }

    if (popupExpanded) {
        AddBountyAmountDialog(baseNote, accountViewModel) { popupExpanded = false }
    }
}

@Composable
private fun RenderPledgeAmount(
    baseNote: Note,
    baseReward: Reward,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val repliesState by observeNoteReplies(baseNote, accountViewModel)
    var reward by remember {
        mutableStateOf(
            showAmount(baseReward.amount),
        )
    }

    var hasPledge by remember {
        mutableStateOf(
            false,
        )
    }

    LaunchedEffect(key1 = repliesState) {
        launch(Dispatchers.IO) {
            repliesState?.note?.pledgedAmountByOthers()?.let {
                val newRewardAmount = showAmount(baseReward.amount.add(it))
                if (newRewardAmount != reward) {
                    reward = newRewardAmount
                }
            }
            val newHasPledge = repliesState?.note?.hasPledgeBy(accountViewModel.userProfile()) == true
            if (hasPledge != newHasPledge) {
                launch(Dispatchers.Main) { hasPledge = newHasPledge }
            }
        }
    }

    HeaderPill(
        symbol = MaterialSymbols.Bolt,
        text = reward,
        contentDescription = stringRes(Res.string.bounty_label),
        iconTint = if (hasPledge) BitcoinOrange else null,
        onClick = onClick,
    )
}

class AddBountyAmountViewModel : ViewModel() {
    private var account: Account? = null
    private var bounty: Note? = null

    var nextAmount by mutableStateOf(TextFieldValue(""))

    fun load(
        account: Account,
        bounty: Note?,
    ) {
        this.account = account
        this.bounty = bounty
    }

    fun sendPost() {
        val newValue = parseBigDecimalOrNull(nextAmount.text.trim())

        if (newValue != null) {
            viewModelScope.launch(Dispatchers.IO) {
                // A refused signature keeps the typed amount so the user can try again.
                logSignerErrors("AddBountyAmount") {
                    account?.let { myAccount ->
                        bounty?.let { bountyInner ->
                            myAccount.sendAddBounty(
                                newValue,
                                bountyInner,
                            )
                        }
                    }

                    nextAmount = TextFieldValue("")
                }
            }
        }
    }

    fun cancel() {
        nextAmount = TextFieldValue("")
    }

    fun hasChanged(): Boolean = nextAmount.text.trim().toLongOrNull() != null
}

@Composable
fun AddBountyAmountDialog(
    bounty: Note,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
) {
    val postViewModel: AddBountyAmountViewModel = rememberViewModel { AddBountyAmountViewModel() }
    postViewModel.load(accountViewModel.account, bounty)
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = { onClose() },
        properties =
            DialogProperties(
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface {
            Column(
                modifier = Modifier.padding(10.dp).width(IntrinsicSize.Min),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CloseButton(
                        onPress = {
                            postViewModel.cancel()
                            onClose()
                        },
                    )

                    PostButton(
                        onPost = {
                            postViewModel.sendPost()
                            onClose()
                        },
                        isActive = postViewModel.hasChanged(),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        label = { Text(text = stringRes(Res.string.pledge_amount_in_sats)) },
                        value = postViewModel.nextAmount,
                        onValueChange = { postViewModel.nextAmount = it },
                        keyboardOptions =
                            KeyboardOptions.Default.copy(
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Number,
                            ),
                        placeholder = {
                            Text(
                                text = "10000, 50000, 5000000",
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                        singleLine = true,
                    )
                }
            }
        }
    }
}
