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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.list.metadata

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.about_us
import com.vitorpamplona.amethyst.commons.resources.follow_pack_copy_name_label
import com.vitorpamplona.amethyst.commons.resources.follow_pack_creation_desc_label
import com.vitorpamplona.amethyst.commons.resources.follow_pack_creation_dialog_title
import com.vitorpamplona.amethyst.commons.resources.follow_pack_creation_name_label
import com.vitorpamplona.amethyst.commons.resources.follow_pack_edit_list_metadata
import com.vitorpamplona.amethyst.commons.resources.follow_pack_explainer
import com.vitorpamplona.amethyst.commons.resources.follow_pack_title
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_sign_events
import com.vitorpamplona.amethyst.commons.resources.picture_url
import com.vitorpamplona.amethyst.commons.resources.read_only_user
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectSingleFromGallery
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.CreatingTopBar
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.SettingsCategory
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.SettingsCategoryFirstModifier
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions

@Composable
fun FollowPackMetadataScreen(
    selectedDTag: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: FollowPackMetadataViewModel = rememberViewModel { FollowPackMetadataViewModel() }
    postViewModel.init(accountViewModel)

    if (selectedDTag != null) {
        LaunchedEffect(postViewModel) {
            postViewModel.load(selectedDTag)
        }
    } else {
        LaunchedEffect(postViewModel) {
            postViewModel.new()
        }
    }

    FollowPackMetadataScaffold(
        postViewModel = postViewModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Preview(device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
private fun DialogContentPreview() {
    val accountViewModel = mockAccountViewModel()
    val postViewModel: FollowPackMetadataViewModel = rememberViewModel { FollowPackMetadataViewModel() }
    postViewModel.init(accountViewModel)

    ThemeComparisonRow {
        FollowPackMetadataScaffold(
            postViewModel = postViewModel,
            accountViewModel = accountViewModel,
            nav = EmptyNav(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowPackMetadataScaffold(
    postViewModel: FollowPackMetadataViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            FollowPackMetadataTopBar(
                postViewModel = postViewModel,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(
                    start = 10.dp,
                    end = 10.dp,
                    top = pad.calculateTopPadding(),
                    bottom = pad.calculateBottomPadding(),
                ).consumeWindowInsets(pad)
                .imePaddingSafe(),
        ) {
            item {
                SettingsCategory(
                    Res.string.follow_pack_title,
                    Res.string.follow_pack_explainer,
                    SettingsCategoryFirstModifier,
                )

                ListName(postViewModel)

                Spacer(modifier = DoubleVertSpacer)

                Picture(postViewModel, accountViewModel)

                Spacer(modifier = DoubleVertSpacer)

                Description(postViewModel)
            }
        }
    }
}

@Composable
fun FollowPackMetadataTopBar(
    postViewModel: FollowPackMetadataViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (postViewModel.isNewPack) {
        CreatingTopBar(
            titleRes = Res.string.follow_pack_creation_dialog_title,
            isActive = postViewModel::canPost,
            onCancel = {
                postViewModel.clear()
                nav.popBack()
            },
            onPost = {
                try {
                    postViewModel.createOrUpdate()
                    nav.popBack()
                } catch (e: SignerExceptions.ReadOnlyException) {
                    accountViewModel.toastManager.toast(
                        Res.string.read_only_user,
                        Res.string.login_with_a_private_key_to_be_able_to_sign_events,
                    )
                }
            },
        )
    } else {
        SavingTopBar(
            titleRes = Res.string.follow_pack_edit_list_metadata,
            isActive = postViewModel::canPost,
            onCancel = {
                postViewModel.clear()
                nav.popBack()
            },
            onPost = {
                try {
                    postViewModel.createOrUpdate()
                    nav.popBack()
                } catch (e: SignerExceptions.ReadOnlyException) {
                    accountViewModel.toastManager.toast(
                        Res.string.read_only_user,
                        Res.string.login_with_a_private_key_to_be_able_to_sign_events,
                    )
                }
            },
        )
    }
}

@Composable
private fun Description(postViewModel: FollowPackMetadataViewModel) {
    SpellCheckedOutlinedTextField(
        label = { Text(text = stringRes(Res.string.follow_pack_creation_desc_label)) },
        modifier = Modifier.fillMaxWidth(),
        value = postViewModel.description.value,
        onValueChange = { postViewModel.description.value = it },
        placeholder = {
            Text(
                text = stringRes(Res.string.about_us),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        minLines = 3,
    )
}

@Composable
private fun Picture(
    postViewModel: FollowPackMetadataViewModel,
    accountViewModel: AccountViewModel,
) {
    OutlinedTextField(
        label = { Text(text = stringRes(Res.string.picture_url)) },
        modifier = Modifier.fillMaxWidth(),
        value = postViewModel.picture.value,
        onValueChange = { postViewModel.picture.value = it },
        placeholder = {
            Text(
                text = "https://mygroup.com/logo.jpg",
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        leadingIcon = {
            SelectSingleFromGallery(
                isUploading = postViewModel.isUploadingImageForPicture,
                tint = MaterialTheme.colorScheme.placeholderText,
                modifier = Modifier.padding(start = 2.dp),
            ) {
                postViewModel.uploadForPicture(it, onError = accountViewModel.toastManager::toast)
            }
        },
    )
}

@Composable
private fun ListName(postViewModel: FollowPackMetadataViewModel) {
    OutlinedTextField(
        label = { Text(text = stringRes(Res.string.follow_pack_creation_name_label)) },
        modifier = Modifier.fillMaxWidth(),
        value = postViewModel.name.value,
        onValueChange = { postViewModel.name.value = it },
        placeholder = {
            Text(
                text = stringRes(Res.string.follow_pack_copy_name_label),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
    )
}
