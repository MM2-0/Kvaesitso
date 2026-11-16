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
package com.vitorpamplona.amethyst.commons.clink.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.onchain.ui.labelRes
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.clink_budget_amount_sats
import com.vitorpamplona.amethyst.commons.resources.clink_budget_daily
import com.vitorpamplona.amethyst.commons.resources.clink_budget_monthly
import com.vitorpamplona.amethyst.commons.resources.clink_budget_one_time
import com.vitorpamplona.amethyst.commons.resources.clink_budget_request
import com.vitorpamplona.amethyst.commons.resources.clink_budget_title
import com.vitorpamplona.amethyst.commons.resources.clink_budget_weekly
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.experimental.clink.debits.DebitFrequency
import org.jetbrains.compose.resources.StringResource

/**
 * Asks the user for a CLINK debit spending budget: an amount and a cadence (one-time, or
 * recurring per day/week/month). [onConfirm] receives the amount in sats and the chosen
 * [DebitFrequency] (null for one-time).
 */
@Composable
fun ClinkBudgetDialog(
    onConfirm: (amountSats: Long, frequency: DebitFrequency?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var cadence by remember { mutableStateOf(BudgetCadence.ONE_TIME) }

    val parsedAmount = amount.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.clink_budget_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { new -> amount = new.filter(Char::isDigit) },
                    label = { Text(stringRes(Res.string.clink_budget_amount_sats)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                BudgetCadence.entries.forEach { option ->
                    CadenceRow(
                        option = option,
                        selected = cadence == option,
                        onSelect = { cadence = option },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = parsedAmount > 0,
                onClick = { onConfirm(parsedAmount, cadence.toFrequency()) },
            ) {
                Text(stringRes(Res.string.clink_budget_request))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}

@Composable
private fun CadenceRow(
    option: BudgetCadence,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .selectable(selected = selected, onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(stringRes(option.labelRes))
    }
}

private enum class BudgetCadence(
    val labelRes: StringResource,
) {
    ONE_TIME(Res.string.clink_budget_one_time),
    DAILY(Res.string.clink_budget_daily),
    WEEKLY(Res.string.clink_budget_weekly),
    MONTHLY(Res.string.clink_budget_monthly),
    ;

    fun toFrequency(): DebitFrequency? =
        when (this) {
            ONE_TIME -> null
            DAILY -> DebitFrequency(1, DebitFrequency.UNIT_DAY)
            WEEKLY -> DebitFrequency(1, DebitFrequency.UNIT_WEEK)
            MONTHLY -> DebitFrequency(1, DebitFrequency.UNIT_MONTH)
        }
}
