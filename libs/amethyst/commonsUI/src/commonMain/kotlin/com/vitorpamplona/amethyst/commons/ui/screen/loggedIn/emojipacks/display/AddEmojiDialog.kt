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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.emoji_add_dialog_title
import com.vitorpamplona.amethyst.commons.resources.emoji_pack_address_label
import com.vitorpamplona.amethyst.commons.resources.emoji_private_explainer
import com.vitorpamplona.amethyst.commons.resources.emoji_private_toggle
import com.vitorpamplona.amethyst.commons.resources.emoji_public_explainer
import com.vitorpamplona.amethyst.commons.resources.emoji_shortcode_invalid
import com.vitorpamplona.amethyst.commons.resources.emoji_shortcode_label
import com.vitorpamplona.amethyst.commons.resources.emoji_url_label
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectSingleFromGallery
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag

@Composable
fun AddEmojiDialog(
    viewModel: EmojiPackViewModel,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
    onConfirm: (EmojiUrlTag, Boolean) -> Unit,
) {
    var shortcode by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var packAddressText by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }

    val shortcodeValid by remember {
        derivedStateOf {
            shortcode.isNotBlank() && EmojiUrlTag.isValidShortcode(shortcode)
        }
    }

    val shortcodeShowError by remember {
        derivedStateOf {
            shortcode.isNotBlank() && !EmojiUrlTag.isValidShortcode(shortcode)
        }
    }

    val canConfirm by remember {
        derivedStateOf {
            shortcodeValid && url.isNotBlank()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringRes(Res.string.emoji_add_dialog_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.Top,
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = shortcode,
                    onValueChange = { shortcode = it.trim(':').trim() },
                    label = { Text(stringRes(Res.string.emoji_shortcode_label)) },
                    isError = shortcodeShowError,
                    supportingText = {
                        if (shortcodeShowError) {
                            Text(stringRes(Res.string.emoji_shortcode_invalid))
                        }
                    },
                )
                Spacer(DoubleVertSpacer)
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringRes(Res.string.emoji_url_label)) },
                    leadingIcon = {
                        SelectSingleFromGallery(
                            isUploading = viewModel.isUploadingEmojiImage,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp),
                        ) { selected ->
                            viewModel.uploadEmojiImage(
                                uri = selected,
                                uploader = accountViewModel.mediaUploader,
                                onUploaded = { uploadedUrl -> url = uploadedUrl },
                                onError = accountViewModel.toastManager::toast,
                            )
                        }
                    },
                )
                Spacer(DoubleVertSpacer)
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = packAddressText,
                    onValueChange = { packAddressText = it },
                    label = { Text(stringRes(Res.string.emoji_pack_address_label)) },
                )
                Spacer(DoubleVertSpacer)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        symbol = if (isPrivate) MaterialSymbols.Lock else MaterialSymbols.LockOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringRes(Res.string.emoji_private_toggle),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                    )
                }
                Spacer(DoubleVertSpacer)
                Text(
                    text =
                        stringRes(
                            if (isPrivate) {
                                Res.string.emoji_private_explainer
                            } else {
                                Res.string.emoji_public_explainer
                            },
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canConfirm,
                onClick = {
                    val parsedAddress =
                        packAddressText.trim().takeIf { it.isNotEmpty() }?.let {
                            runCatching { Address.parse(it) }.getOrNull()
                        }
                    onConfirm(
                        EmojiUrlTag(code = shortcode, url = url.trim(), emojiSet = parsedAddress),
                        isPrivate,
                    )
                },
            ) {
                Text(stringRes(Res.string.add))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}
