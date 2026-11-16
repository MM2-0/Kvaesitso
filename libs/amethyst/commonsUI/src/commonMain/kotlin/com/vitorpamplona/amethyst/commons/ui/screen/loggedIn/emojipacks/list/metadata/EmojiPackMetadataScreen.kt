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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.list.metadata

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.edit_emoji_pack
import com.vitorpamplona.amethyst.commons.resources.emoji_pack_description_label
import com.vitorpamplona.amethyst.commons.resources.emoji_pack_image_label
import com.vitorpamplona.amethyst.commons.resources.emoji_pack_name_label
import com.vitorpamplona.amethyst.commons.resources.emoji_pack_upload_image_cta
import com.vitorpamplona.amethyst.commons.resources.emoji_pack_upload_image_hint
import com.vitorpamplona.amethyst.commons.resources.new_emoji_pack
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.commons.ui.components.FormImageTile
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.CreatingTopBar
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun EmojiPackMetadataScreen(
    packIdentifier: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: EmojiPackMetadataViewModel = rememberViewModel { EmojiPackMetadataViewModel() }
    viewModel.init(accountViewModel)

    if (packIdentifier != null) {
        LaunchedEffect(viewModel) {
            viewModel.load(packIdentifier)
        }
    } else {
        LaunchedEffect(viewModel) {
            viewModel.new()
        }
    }

    EmojiPackMetadataScaffold(viewModel, accountViewModel, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiPackMetadataScaffold(
    viewModel: EmojiPackMetadataViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scrollState = rememberScrollState()

    var wantsToPickImage by remember { mutableStateOf(false) }

    if (wantsToPickImage) {
        GallerySelectSingle(
            onImageUri = { media ->
                wantsToPickImage = false
                if (media != null) {
                    viewModel.pickMedia(media)
                }
            },
        )
    }

    val onSubmit: () -> Unit = {
        viewModel.submit(
            uploader = accountViewModel.mediaUploader,
            onSuccess = { nav.popBack() },
            onError = accountViewModel.toastManager::toast,
        )
    }

    Scaffold(
        topBar = {
            EmojiPackMetadataTopBar(
                viewModel = viewModel,
                nav = nav,
                onSubmit = onSubmit,
            )
        },
    ) { pad ->
        Surface(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePaddingSafe(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                ) {
                    PackImagePicker(
                        viewModel = viewModel,
                        onPickImage = { wantsToPickImage = true },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PackFormFields(viewModel)
                }
            }
        }
    }
}

@Composable
private fun EmojiPackMetadataTopBar(
    viewModel: EmojiPackMetadataViewModel,
    nav: INav,
    onSubmit: () -> Unit,
) {
    if (viewModel.isNewPack) {
        CreatingTopBar(
            titleRes = Res.string.new_emoji_pack,
            isActive = viewModel::canPost,
            onCancel = {
                viewModel.clear()
                nav.popBack()
            },
            onPost = onSubmit,
        )
    } else {
        SavingTopBar(
            titleRes = Res.string.edit_emoji_pack,
            isActive = viewModel::canPost,
            onCancel = {
                viewModel.clear()
                nav.popBack()
            },
            onPost = onSubmit,
        )
    }
}

@Composable
private fun PackImagePicker(
    viewModel: EmojiPackMetadataViewModel,
    onPickImage: () -> Unit,
) {
    FormImageTile {
        val picked = viewModel.pickedMedia
        val currentUrl = viewModel.picture.value.text

        when {
            picked != null -> {
                HeroImagePreview(
                    model = picked.uri,
                    onClick = onPickImage,
                )
            }

            currentUrl.isNotBlank() -> {
                HeroImagePreview(
                    model = currentUrl,
                    onClick = onPickImage,
                )
            }

            else -> {
                UploadPlaceholder(onClick = onPickImage)
            }
        }
    }
}

@Composable
private fun HeroImagePreview(
    model: Any,
    onClick: () -> Unit,
) {
    AsyncImage(
        model = model,
        contentDescription = stringRes(Res.string.emoji_pack_image_label),
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
    )
}

@Composable
private fun UploadPlaceholder(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(12.dp),
                ).clickable(onClick = onClick)
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                symbol = MaterialSymbols.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringRes(Res.string.emoji_pack_upload_image_cta),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringRes(Res.string.emoji_pack_upload_image_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PackFormFields(viewModel: EmojiPackMetadataViewModel) {
    OutlinedTextField(
        value = viewModel.name.value,
        onValueChange = { viewModel.name.value = it },
        label = { Text(text = stringRes(Res.string.emoji_pack_name_label)) },
        placeholder = {
            Text(
                text = stringRes(Res.string.emoji_pack_name_label),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
    )

    Spacer(modifier = Modifier.height(12.dp))

    SpellCheckedOutlinedTextField(
        value = viewModel.description.value,
        onValueChange = { viewModel.description.value = it },
        label = { Text(text = stringRes(Res.string.emoji_pack_description_label)) },
        modifier =
            Modifier
                .fillMaxWidth()
                .height(120.dp),
        minLines = 2,
        maxLines = 6,
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
    )
}
