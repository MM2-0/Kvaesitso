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
package com.vitorpamplona.amethyst.commons.nip56Reports.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.block_only
import com.vitorpamplona.amethyst.commons.resources.report_dialog_additional_reason_label
import com.vitorpamplona.amethyst.commons.resources.report_dialog_additional_reason_placeholder
import com.vitorpamplona.amethyst.commons.resources.report_dialog_block_hide_user_btn
import com.vitorpamplona.amethyst.commons.resources.report_dialog_blocking_a_user
import com.vitorpamplona.amethyst.commons.resources.report_dialog_illegal
import com.vitorpamplona.amethyst.commons.resources.report_dialog_impersonation
import com.vitorpamplona.amethyst.commons.resources.report_dialog_nudity
import com.vitorpamplona.amethyst.commons.resources.report_dialog_post_report_btn
import com.vitorpamplona.amethyst.commons.resources.report_dialog_profanity
import com.vitorpamplona.amethyst.commons.resources.report_dialog_reminder_public
import com.vitorpamplona.amethyst.commons.resources.report_dialog_report_btn
import com.vitorpamplona.amethyst.commons.resources.report_dialog_select_reason_label
import com.vitorpamplona.amethyst.commons.resources.report_dialog_select_reason_placeholder
import com.vitorpamplona.amethyst.commons.resources.report_dialog_spam
import com.vitorpamplona.amethyst.commons.resources.report_dialog_title
import com.vitorpamplona.amethyst.commons.resources.report_malware
import com.vitorpamplona.amethyst.commons.resources.violence
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.TextSpinner
import com.vitorpamplona.amethyst.commons.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.LightRedColor
import com.vitorpamplona.quartz.nip56Reports.ReportType
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource

/** The NIP-56 reasons the dialog offers, in display order, with their labels. */
private val ReportReasons =
    listOf(
        ReportType.SPAM to Res.string.report_dialog_spam,
        ReportType.PROFANITY to Res.string.report_dialog_profanity,
        ReportType.IMPERSONATION to Res.string.report_dialog_impersonation,
        ReportType.NUDITY to Res.string.report_dialog_nudity,
        ReportType.ILLEGAL to Res.string.report_dialog_illegal,
        ReportType.MALWARE to Res.string.report_malware,
        ReportType.VIOLENCE to Res.string.violence,
    )

/**
 * Block-or-report dialog for a post. [onBlock] hides the author; [onReport] files a public
 * NIP-56 report with the chosen reason and optional text (the host also hides the author).
 * Both close the dialog through [onDismiss] afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDialog(
    onBlock: () -> Unit,
    onReport: (reason: ReportType, additionalReason: String) -> Unit,
    onDismiss: () -> Unit,
    /** Who will see the report: everyone by default, or only the members of the message's group. */
    reminder: StringResource = Res.string.report_dialog_reminder_public,
) {
    val labels = ReportReasons.map { stringRes(it.second) }
    // Keyed on the labels so a locale change while the dialog is open relabels the spinner;
    // typing in the reason field recomposes with equal labels and reuses the list.
    val reasonOptions = remember(labels) { labels.map { TitleExplainer(it) }.toImmutableList() }
    var additionalReason by remember { mutableStateOf("") }
    var selectedReason by remember { mutableIntStateOf(-1) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = stringRes(id = Res.string.report_dialog_title)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { ArrowBackIcon() } },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
            },
        ) { pad ->
            Column(
                modifier =
                    Modifier.padding(16.dp, pad.calculateTopPadding(), 16.dp, pad.calculateBottomPadding()),
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                SpacerH16()
                SectionHeader(text = stringRes(id = Res.string.block_only))
                SpacerH16()
                Text(
                    text = stringRes(Res.string.report_dialog_blocking_a_user),
                )
                SpacerH16()
                ActionButton(
                    text = stringRes(Res.string.report_dialog_block_hide_user_btn),
                    icon = MaterialSymbols.Block,
                    onClick = {
                        onBlock()
                        onDismiss()
                    },
                )
                SpacerH16()

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface, thickness = DividerThickness)

                SpacerH16()
                SectionHeader(text = stringRes(Res.string.report_dialog_report_btn))
                SpacerH16()
                Text(stringRes(reminder))
                SpacerH16()
                TextSpinner(
                    label = stringRes(Res.string.report_dialog_select_reason_label),
                    placeholder = stringRes(Res.string.report_dialog_select_reason_placeholder),
                    options = reasonOptions,
                    onSelect = { selectedReason = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                SpacerH16()
                SpellCheckedOutlinedTextField(
                    value = additionalReason,
                    onValueChange = { additionalReason = it },
                    placeholder = {
                        Text(text = stringRes(Res.string.report_dialog_additional_reason_placeholder))
                    },
                    label = { Text(stringRes(Res.string.report_dialog_additional_reason_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SpacerH16()

                ActionButton(
                    text = stringRes(Res.string.report_dialog_post_report_btn),
                    icon = MaterialSymbols.Report,
                    enabled = selectedReason in 0..ReportReasons.lastIndex,
                    onClick = {
                        onReport(ReportReasons[selectedReason].first, additionalReason)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable private fun SpacerH16() = Spacer(modifier = Modifier.height(16.dp))

@Composable
private fun SectionHeader(text: String) =
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 18.sp,
    )

@Composable
private fun ActionButton(
    text: String,
    icon: MaterialSymbol,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = Button(
    onClick = onClick,
    enabled = enabled,
    colors = ButtonDefaults.buttonColors(containerColor = LightRedColor),
    modifier = Modifier.fillMaxWidth(),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            symbol = icon,
            contentDescription = null,
            tint = Color.White,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = Color.White)
    }
}
