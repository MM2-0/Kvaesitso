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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.nip85TrustedAssertions.ui.EditNicknameDialog
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.block_hide_user
import com.vitorpamplona.amethyst.commons.resources.copy_user_id
import com.vitorpamplona.amethyst.commons.resources.edit_nickname
import com.vitorpamplona.amethyst.commons.resources.profile_actions_dialog_title
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.resources.quick_action_share_browser_link
import com.vitorpamplona.amethyst.commons.resources.report_hateful_speech
import com.vitorpamplona.amethyst.commons.resources.report_illegal_behaviour
import com.vitorpamplona.amethyst.commons.resources.report_impersonation
import com.vitorpamplona.amethyst.commons.resources.report_malware
import com.vitorpamplona.amethyst.commons.resources.report_nudity_porn
import com.vitorpamplona.amethyst.commons.resources.report_spam_scam
import com.vitorpamplona.amethyst.commons.resources.unblock_user
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.commons.ui.components.rememberTextSharer
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.note.externalLinkForUser
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip56Reports.ReportType
import kotlinx.coroutines.launch

@Composable
fun UserProfileDropDownMenu(
    user: User,
    popupExpanded: Boolean,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val quickActionShareBrowserLinkStr = stringRes(Res.string.quick_action_share_browser_link)
    val quickActionShareStr = stringRes(Res.string.quick_action_share)
    val isNicknameDialogOpen = remember { mutableStateOf(false) }

    if (isNicknameDialogOpen.value) {
        EditNicknameDialog(
            user = user,
            accountViewModel = accountViewModel,
            onDismiss = { isNicknameDialogOpen.value = false },
        )
    }

    if (!popupExpanded) return

    M3ActionDialog(
        title = stringRes(Res.string.profile_actions_dialog_title),
        onDismiss = onDismiss,
    ) {
        val clipboardManager = LocalClipboard.current
        val scope = rememberCoroutineScope()
        val textSharer = rememberTextSharer()

        // Share section
        M3ActionSection {
            M3ActionRow(
                icon = MaterialSymbols.ContentCopy,
                text = stringRes(Res.string.copy_user_id),
            ) {
                scope.launch {
                    clipboardManager.setText(user.pubkeyNpub())
                    onDismiss()
                }
            }
            M3ActionRow(
                icon = MaterialSymbols.Share,
                text = stringRes(Res.string.quick_action_share),
            ) {
                textSharer.share(externalLinkForUser(user), quickActionShareBrowserLinkStr, quickActionShareStr)
                onDismiss()
            }
        }

        // Moderation section (if not self)
        if (accountViewModel.userProfile() != user) {
            M3ActionSection {
                M3ActionRow(
                    icon = MaterialSymbols.Edit,
                    text = stringRes(Res.string.edit_nickname),
                ) {
                    isNicknameDialogOpen.value = true
                    onDismiss()
                }
            }

            M3ActionSection {
                if (accountViewModel.account.isHidden(user)) {
                    M3ActionRow(
                        icon = MaterialSymbols.CheckCircle,
                        text = stringRes(Res.string.unblock_user),
                    ) {
                        accountViewModel.show(user)
                        onDismiss()
                    }
                } else {
                    M3ActionRow(
                        icon = MaterialSymbols.Block,
                        text = stringRes(Res.string.block_hide_user),
                        isDestructive = true,
                    ) {
                        accountViewModel.hide(user)
                        onDismiss()
                    }
                }
            }

            // Report section
            M3ActionSection {
                M3ActionRow(icon = MaterialSymbols.Report, text = stringRes(Res.string.report_spam_scam), isDestructive = true) {
                    accountViewModel.report(user, ReportType.SPAM)
                    onDismiss()
                }
                M3ActionRow(icon = MaterialSymbols.Report, text = stringRes(Res.string.report_hateful_speech), isDestructive = true) {
                    accountViewModel.report(user, ReportType.PROFANITY)
                    onDismiss()
                }
                M3ActionRow(icon = MaterialSymbols.Report, text = stringRes(Res.string.report_impersonation), isDestructive = true) {
                    accountViewModel.report(user, ReportType.IMPERSONATION)
                    onDismiss()
                }
                M3ActionRow(icon = MaterialSymbols.Report, text = stringRes(Res.string.report_nudity_porn), isDestructive = true) {
                    accountViewModel.report(user, ReportType.NUDITY)
                    onDismiss()
                }
                M3ActionRow(icon = MaterialSymbols.Report, text = stringRes(Res.string.report_illegal_behaviour), isDestructive = true) {
                    accountViewModel.report(user, ReportType.ILLEGAL)
                    onDismiss()
                }
                M3ActionRow(icon = MaterialSymbols.Report, text = stringRes(Res.string.report_malware), isDestructive = true) {
                    accountViewModel.report(user, ReportType.MALWARE)
                    onDismiss()
                }
            }
        }
    }
}
