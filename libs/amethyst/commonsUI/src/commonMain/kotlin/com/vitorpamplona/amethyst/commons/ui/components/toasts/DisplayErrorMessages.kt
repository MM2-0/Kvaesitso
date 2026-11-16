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
package com.vitorpamplona.amethyst.commons.ui.components.toasts

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.notices.ui.NoticeDialog
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.error_dialog_button_ok
import com.vitorpamplona.amethyst.commons.ui.components.InformationDialog
import com.vitorpamplona.amethyst.commons.ui.components.toasts.ActionableStringToastMsg
import com.vitorpamplona.amethyst.commons.ui.components.toasts.ResourceToastMsg
import com.vitorpamplona.amethyst.commons.ui.components.toasts.StringToastMsg
import com.vitorpamplona.amethyst.commons.ui.components.toasts.ThrowableToastMsg
import com.vitorpamplona.amethyst.commons.ui.components.toasts.ThrowableToastMsg2
import com.vitorpamplona.amethyst.commons.ui.components.toasts.ToastManager
import com.vitorpamplona.amethyst.commons.ui.components.toasts.multiline.MultiErrorToastMsg
import com.vitorpamplona.amethyst.commons.ui.components.toasts.multiline.MultiUserErrorMessageDialog
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun DisplayErrorMessages(
    toastManager: ToastManager,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val openDialogMsg = toastManager.toasts.collectAsStateWithLifecycle(null)

    openDialogMsg.value?.let { obj ->
        when (obj) {
            is ResourceToastMsg -> {
                val params = obj.params
                if (params != null) {
                    InformationDialog(
                        stringRes(obj.titleResId),
                        stringRes(obj.resourceId, *params),
                        severity = obj.severity,
                    ) {
                        toastManager.clearToasts()
                    }
                } else {
                    InformationDialog(
                        stringRes(obj.titleResId),
                        stringRes(obj.resourceId),
                        severity = obj.severity,
                    ) {
                        toastManager.clearToasts()
                    }
                }
            }

            is StringToastMsg -> {
                InformationDialog(
                    obj.title,
                    obj.msg,
                    severity = obj.severity,
                ) {
                    toastManager.clearToasts()
                }
            }

            is ActionableStringToastMsg -> {
                // Closing runs the message's action, so the button says OK rather than Close.
                InformationDialog(
                    obj.title,
                    obj.msg,
                    severity = obj.severity,
                    confirmLabel = stringRes(Res.string.error_dialog_button_ok),
                ) {
                    obj.action()
                    toastManager.clearToasts()
                }
            }

            is ThrowableToastMsg -> {
                InformationDialog(obj) {
                    toastManager.clearToasts()
                }
            }

            is ThrowableToastMsg2 -> {
                InformationDialog(obj) {
                    toastManager.clearToasts()
                }
            }

            is MultiErrorToastMsg -> {
                MultiUserErrorMessageDialog(obj, accountViewModel, nav)
            }

            is NoticeToastMsg -> {
                NoticeDialog(obj.notice, severity = obj.severity) {
                    toastManager.clearToasts()
                }
            }
        }
    }
}
