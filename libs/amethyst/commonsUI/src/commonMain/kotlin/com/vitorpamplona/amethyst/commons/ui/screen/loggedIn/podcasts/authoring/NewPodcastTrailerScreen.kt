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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.authoring

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_audio_picked
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_audio_url_placeholder
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_season_label
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_title_label
import com.vitorpamplona.amethyst.commons.resources.podcast_new_trailer
import com.vitorpamplona.amethyst.commons.resources.podcast_publishing_banner
import com.vitorpamplona.amethyst.commons.resources.podcast_trailer_title_placeholder
import com.vitorpamplona.amethyst.commons.resources.podcast_trailer_upload_cta
import com.vitorpamplona.amethyst.commons.resources.podcast_trailer_upload_hint
import com.vitorpamplona.amethyst.commons.resources.podcast_trailer_url_label
import com.vitorpamplona.amethyst.commons.service.upload.ui.StrippingFailureDialog
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.DocumentSelectSingle
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.PublishingTopBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.music.UploadInProgressBanner
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.music.UploadPlaceholder
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/** Composer for a Podcasting-2.0 trailer (`kind:30055`): title, a short audio/video clip, and season. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPodcastTrailerScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vm: NewPodcastTrailerViewModel = rememberViewModel { NewPodcastTrailerViewModel() }

    LaunchedEffect(accountViewModel) { vm.init(accountViewModel) }
    StrippingFailureDialog(vm.strippingFailureConfirmation)

    var wantsToPick by remember { mutableStateOf(false) }
    if (wantsToPick) {
        MediaFileSelect { picked ->
            wantsToPick = false
            vm.setPickedMedia(picked)
        }
    }

    val isBusy = vm.isSending.value
    LaunchedEffect(vm) { vm.completionEvents.collect { nav.popBack() } }

    Scaffold(
        topBar = {
            PublishingTopBar(
                titleRes = Res.string.podcast_new_trailer,
                onCancel = { nav.popBack() },
                isActive = { vm.isValid() && !isBusy },
                onPost = {
                    if (!vm.isValid() || isBusy) return@PublishingTopBar
                    vm.saveAndPublish(accountViewModel)
                },
            )
        },
    ) { pad ->
        Column(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePaddingSafe()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isBusy) UploadInProgressBanner(Res.string.podcast_publishing_banner)

            val pickedName = vm.pickedName.value
            if (pickedName != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .let { if (!isBusy) it.clickable { wantsToPick = true } else it }
                            .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        symbol = MaterialSymbols.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(text = pickedName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = stringRes(Res.string.podcast_episode_audio_picked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isBusy) {
                        TextButton(onClick = { vm.clearPickedMedia() }) { Text(stringRes(Res.string.cancel)) }
                    }
                }
            } else {
                UploadPlaceholder(
                    iconSymbol = MaterialSymbols.MusicNote,
                    ctaRes = Res.string.podcast_trailer_upload_cta,
                    hintRes = Res.string.podcast_trailer_upload_hint,
                    onClick = { wantsToPick = true },
                    aspectRatio = null,
                    enabled = !isBusy,
                )
            }

            SpellCheckedOutlinedTextField(
                value = vm.title.value,
                onValueChange = { vm.title.value = it },
                label = { Text(stringRes(Res.string.podcast_episode_title_label)) },
                placeholder = { Text(stringRes(Res.string.podcast_trailer_title_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = vm.title.value.isBlank(),
            )

            OutlinedTextField(
                value = vm.url.value,
                onValueChange = { vm.url.value = it },
                label = { Text(stringRes(Res.string.podcast_trailer_url_label)) },
                placeholder = { Text(stringRes(Res.string.podcast_episode_audio_url_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            OutlinedTextField(
                value = vm.season.value,
                onValueChange = { input -> vm.season.value = input.filter { it.isDigit() } },
                label = { Text(stringRes(Res.string.podcast_episode_season_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}

// Audio or video file via the document picker.
@Composable
private fun MediaFileSelect(onPicked: (SelectedMedia?) -> Unit) = DocumentSelectSingle(listOf("audio/*", "video/*"), onPicked)
