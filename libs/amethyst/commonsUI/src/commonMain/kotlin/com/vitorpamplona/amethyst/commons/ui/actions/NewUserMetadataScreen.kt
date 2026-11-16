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
package com.vitorpamplona.amethyst.commons.ui.actions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.about_me
import com.vitorpamplona.amethyst.commons.resources.avatar_url
import com.vitorpamplona.amethyst.commons.resources.banner_url
import com.vitorpamplona.amethyst.commons.resources.clink_offer_label
import com.vitorpamplona.amethyst.commons.resources.collapse
import com.vitorpamplona.amethyst.commons.resources.display_name
import com.vitorpamplona.amethyst.commons.resources.expand
import com.vitorpamplona.amethyst.commons.resources.github
import com.vitorpamplona.amethyst.commons.resources.github_proof_url_template
import com.vitorpamplona.amethyst.commons.resources.lightning_address
import com.vitorpamplona.amethyst.commons.resources.lnurl
import com.vitorpamplona.amethyst.commons.resources.mastodon
import com.vitorpamplona.amethyst.commons.resources.mastodon_proof_url_template
import com.vitorpamplona.amethyst.commons.resources.my_display_name
import com.vitorpamplona.amethyst.commons.resources.my_name
import com.vitorpamplona.amethyst.commons.resources.new_user_metadata_banner_hint
import com.vitorpamplona.amethyst.commons.resources.new_user_metadata_lightning_address_hint
import com.vitorpamplona.amethyst.commons.resources.new_user_metadata_nip05_hint
import com.vitorpamplona.amethyst.commons.resources.new_user_metadata_picture_hint
import com.vitorpamplona.amethyst.commons.resources.new_user_metadata_pronouns_hint
import com.vitorpamplona.amethyst.commons.resources.new_user_metadata_website_hint
import com.vitorpamplona.amethyst.commons.resources.nip_05
import com.vitorpamplona.amethyst.commons.resources.profile
import com.vitorpamplona.amethyst.commons.resources.pronouns
import com.vitorpamplona.amethyst.commons.resources.social_proof
import com.vitorpamplona.amethyst.commons.resources.twitter
import com.vitorpamplona.amethyst.commons.resources.twitter_proof_url_template
import com.vitorpamplona.amethyst.commons.resources.username
import com.vitorpamplona.amethyst.commons.resources.website_url
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectSingleFromGallery
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewUserMetadataScreen(
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val postViewModel: NewUserMetadataViewModel = rememberViewModel { NewUserMetadataViewModel() }
    postViewModel.init(accountViewModel)

    val socialExpanded = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(postViewModel, accountViewModel) {
        postViewModel.load()

        // Auto-expand social proofs if any have data
        if (postViewModel.twitter.value.isNotBlank() || postViewModel.mastodon.value.isNotBlank() || postViewModel.github.value.isNotBlank()) {
            socialExpanded.value = true
        }
    }

    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = Res.string.profile,
                onCancel = {
                    nav.popBack()
                },
                onPost = {
                    accountViewModel.launchSigner {
                        postViewModel.create()
                    }
                    nav.popBack()
                },
            )
        },
    ) { pad ->
        Column(
            modifier =
                Modifier
                    .padding(
                        start = 10.dp,
                        end = 10.dp,
                        top = pad.calculateTopPadding(),
                        bottom = pad.calculateBottomPadding(),
                    ).consumeWindowInsets(pad)
                    .imePaddingSafe(),
        ) {
            Column(
                modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState()),
            ) {
                // -- Profile (always visible) --
                OutlinedTextField(
                    label = { Text(text = stringRes(Res.string.username)) },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.name.value,
                    onValueChange = { postViewModel.name.value = it },
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.my_name),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    prefix = { Text("@") },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    label = { Text(text = stringRes(Res.string.display_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.displayName.value,
                    onValueChange = { postViewModel.displayName.value = it },
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.my_display_name),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(10.dp))

                SpellCheckedOutlinedTextField(
                    label = { Text(text = stringRes(Res.string.about_me)) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    value = postViewModel.about.value,
                    onValueChange = { postViewModel.about.value = it },
                    placeholder = {
                        Text(
                            text = stringRes(id = Res.string.about_me),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                    maxLines = 10,
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    label = { Text(text = stringRes(Res.string.avatar_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.picture.value,
                    onValueChange = { postViewModel.picture.value = it },
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.new_user_metadata_picture_hint),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    leadingIcon = {
                        SelectSingleFromGallery(
                            isUploading = postViewModel.isUploadingImageForPicture,
                            tint = MaterialTheme.colorScheme.placeholderText,
                            modifier = Modifier.padding(start = 5.dp),
                        ) {
                            postViewModel.uploadForPicture(it, accountViewModel.mediaUploader, onError = accountViewModel.toastManager::toast)
                        }
                    },
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    label = { Text(text = stringRes(Res.string.banner_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.banner.value,
                    onValueChange = { postViewModel.banner.value = it },
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.new_user_metadata_banner_hint),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    leadingIcon = {
                        SelectSingleFromGallery(
                            isUploading = postViewModel.isUploadingImageForBanner,
                            tint = MaterialTheme.colorScheme.placeholderText,
                            modifier = Modifier.padding(start = 5.dp),
                        ) {
                            postViewModel.uploadForBanner(it, accountViewModel.mediaUploader, onError = accountViewModel.toastManager::toast)
                        }
                    },
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    label = { Text(text = stringRes(Res.string.nip_05) + " (NIP-05)") },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.nip05.value,
                    onValueChange = { postViewModel.nip05.value = it },
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.new_user_metadata_nip05_hint),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    label = { Text(text = stringRes(Res.string.website_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.website.value,
                    onValueChange = { postViewModel.website.value = it },
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.new_user_metadata_website_hint),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    label = { Text(text = stringRes(Res.string.pronouns)) },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.pronouns.value,
                    onValueChange = { postViewModel.pronouns.value = it },
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.new_user_metadata_pronouns_hint),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    label = { Text(text = stringRes(Res.string.lightning_address)) },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.lnAddress.value,
                    onValueChange = { postViewModel.lnAddress.value = it },
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.new_user_metadata_lightning_address_hint),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    label = { Text(text = stringRes(Res.string.lnurl)) },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.lnURL.value,
                    onValueChange = { postViewModel.lnURL.value = it },
                    placeholder = {
                        Text(
                            text = "LNURL1…",
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    label = { Text(text = stringRes(Res.string.clink_offer_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    value = postViewModel.clinkOffer.value,
                    onValueChange = { postViewModel.clinkOffer.value = it },
                    placeholder = {
                        Text(
                            text = "noffer1…",
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    singleLine = true,
                )

                // -- Social Proofs --
                ExpandableSection(
                    title = stringRes(Res.string.social_proof),
                    expanded = socialExpanded,
                ) {
                    OutlinedTextField(
                        label = { Text(text = stringRes(Res.string.twitter)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.twitter.value,
                        onValueChange = { postViewModel.twitter.value = it },
                        placeholder = {
                            Text(
                                text = stringRes(Res.string.twitter_proof_url_template),
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringRes(Res.string.mastodon)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.mastodon.value,
                        onValueChange = { postViewModel.mastodon.value = it },
                        placeholder = {
                            Text(
                                text = stringRes(Res.string.mastodon_proof_url_template),
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        label = { Text(text = stringRes(Res.string.github)) },
                        modifier = Modifier.fillMaxWidth(),
                        value = postViewModel.github.value,
                        onValueChange = { postViewModel.github.value = it },
                        placeholder = {
                            Text(
                                text = stringRes(Res.string.github_proof_url_template),
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    expanded: MutableState<Boolean>,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { expanded.value = !expanded.value }
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            symbol = if (expanded.value) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = if (expanded.value) stringRes(Res.string.collapse) else stringRes(Res.string.expand),
            tint = MaterialTheme.colorScheme.placeholderText,
        )
    }

    AnimatedVisibility(
        visible = expanded.value,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Column {
            content()
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
