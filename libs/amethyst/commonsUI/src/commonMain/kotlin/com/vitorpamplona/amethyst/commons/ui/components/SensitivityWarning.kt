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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.content_warning
import com.vitorpamplona.amethyst.commons.resources.content_warning_explanation
import com.vitorpamplona.amethyst.commons.resources.content_warning_with_reason
import com.vitorpamplona.amethyst.commons.resources.show_anyway
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.commons.ui.theme.PaddingHorizontal12Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningTag
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarningReason
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip92IMeta.imetas

@Composable
fun SensitivityWarning(
    note: Note,
    accountViewModel: AccountViewModel,
    content: @Composable () -> Unit,
) {
    val noteEvent = note.event

    if (noteEvent == null) {
        content()
    } else {
        SensitivityWarning(noteEvent, accountViewModel, content)
    }
}

@Composable
fun SensitivityWarning(
    event: Event,
    accountViewModel: AccountViewModel,
    content: @Composable () -> Unit,
) {
    val hasSensitiveContent = remember(event) { event.isSensitiveOrNSFW() }

    if (hasSensitiveContent) {
        val reason = remember(event) { event.contentWarningReason() }
        ObserveSensitivityWarning(reason, accountViewModel, content)
    } else {
        content()
    }
}

@Composable
fun SensitivityWarning(
    reason: String?,
    accountViewModel: AccountViewModel,
    content: @Composable () -> Unit,
) {
    if (reason != null) {
        ObserveSensitivityWarning(reason, accountViewModel, content)
    } else {
        content()
    }
}

@Composable
fun ContentWarningGate(
    isSensitive: Boolean,
    reasons: Set<String>,
    preloadUrls: List<String>,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier.fillMaxWidth(),
    backdrop: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (!isSensitive) {
        content()
        return
    }

    val accountState = accountViewModel.showSensitiveContent().collectAsStateWithLifecycle()

    var showContentWarningNote by remember(accountState) { mutableStateOf(accountState.value != true) }

    if (showContentWarningNote && preloadUrls.isNotEmpty()) {
        val context = LocalPlatformContext.current
        LaunchedEffect(preloadUrls) {
            preloadUrls.forEach { url ->
                runCatching {
                    SingletonImageLoader.get(context).enqueue(ImageRequest.Builder(context).data(url).build())
                }
            }
        }
    }

    CrossfadeIfEnabled(targetState = showContentWarningNote) {
        if (it) {
            if (backdrop != null) {
                Box(modifier = modifier.clipToBounds()) {
                    backdrop()
                    ContentWarningOverlayBody(reasons) { showContentWarningNote = false }
                }
            } else {
                ContentWarningNote(reasons.firstOrNull()) { showContentWarningNote = false }
            }
        } else {
            content()
        }
    }
}

@Composable
fun ObserveSensitivityWarning(
    reason: String?,
    accountViewModel: AccountViewModel,
    content: @Composable () -> Unit,
) {
    val accountState = accountViewModel.showSensitiveContent().collectAsStateWithLifecycle()

    var showContentWarningNote by remember(accountState) { mutableStateOf(accountState.value != true) }

    CrossfadeIfEnabled(targetState = showContentWarningNote) {
        if (it) {
            ContentWarningNote(reason) { showContentWarningNote = false }
        } else {
            content()
        }
    }
}

@Preview
@Composable
fun ContentWarningNotePreview() {
    ThemeComparisonColumn {
        ContentWarningNote(null) {}
    }
}

@Preview
@Composable
fun ContentWarningNoteWithReasonPreview() {
    ThemeComparisonColumn {
        ContentWarningNote("Spoilers") {}
    }
}

@Preview
@Composable
fun ContentWarningNoteWithBigReasonPreview() {
    ThemeComparisonColumn {
        ContentWarningNote("Spoilers, monkeys, bannanas, and other things") {}
    }
}

@Composable
fun BlurhashBackdrop(
    blurhash: String?,
    description: String?,
    thumbhash: String? = null,
) {
    DisplayBlurHash(
        blurhash = blurhash,
        description = description,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        thumbhash = thumbhash,
    )
}

@Composable
fun BlurhashGridBackdrop(media: List<MediaUrlImage>) {
    AutoNonlazyGrid(media.size) { idx ->
        val item = media[idx]
        if (item.blurhash != null || item.thumbhash != null) {
            DisplayBlurHash(
                blurhash = item.blurhash,
                description = item.description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                thumbhash = item.thumbhash,
            )
        }
    }
}

/**
 * Sizes a media container from a reported width/height ratio, falling back to width-only when
 * there is no usable one.
 *
 * A ratio that is not finite and positive counts as no ratio: `Modifier.aspectRatio` throws
 * `IllegalArgumentException` on `0f` and on `NaN`, which would take down the composition around
 * the media rather than just mis-sizing it. Callers are expected to hand over a clean value
 * (`DimensionTag.aspectRatioOrNull`, `MediaAspectRatioCache`), so this is the backstop for the one
 * that forgets, not the primary guard.
 */
fun mediaSizingModifier(
    ratio: Float?,
    contentScale: ContentScale,
): Modifier =
    when {
        contentScale == ContentScale.Crop -> Modifier.fillMaxSize()
        ratio != null && ratio > 0f && ratio.isFinite() -> Modifier.fillMaxWidth().aspectRatio(ratio)
        else -> Modifier.fillMaxWidth()
    }

@Composable
private fun ContentWarningOverlayBody(
    reasons: Set<String>,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .height(80.dp)
                    .width(90.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.Visibility,
                    contentDescription = stringRes(Res.string.content_warning),
                    modifier =
                        Modifier
                            .size(70.dp)
                            .align(Alignment.BottomStart),
                    tint = Color.White,
                )
                Icon(
                    symbol = MaterialSymbols.Warning,
                    contentDescription = stringRes(Res.string.content_warning),
                    modifier =
                        Modifier
                            .size(30.dp)
                            .align(Alignment.TopEnd),
                    tint = Color.White,
                )
            }

            Text(
                text = stringRes(Res.string.content_warning),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White,
                softWrap = true,
                textAlign = TextAlign.Center,
            )

            if (reasons.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    reasons.forEach { reason ->
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    text = reason,
                                    color = Color.White,
                                )
                            },
                            colors =
                                AssistChipDefaults.assistChipColors(
                                    disabledContainerColor = Color.White.copy(alpha = 0.15f),
                                    disabledLabelColor = Color.White,
                                ),
                            border = null,
                        )
                    }
                }
            }

            FilledTonalButton(
                modifier = Modifier.padding(top = 10.dp),
                onClick = onDismiss,
                shape = ButtonBorder,
                contentPadding = ButtonPadding,
            ) {
                Text(
                    text = stringRes(Res.string.show_anyway),
                )
            }
        }
    }
}

@Composable
fun ContentWarningNote(
    reason: String?,
    onDismiss: () -> Unit,
) {
    Column {
        Row(modifier = PaddingHorizontal12Modifier) {
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Box(
                        Modifier
                            .height(80.dp)
                            .width(90.dp),
                    ) {
                        Icon(
                            symbol = MaterialSymbols.Visibility,
                            contentDescription = stringRes(Res.string.content_warning),
                            modifier =
                                Modifier
                                    .size(70.dp)
                                    .align(Alignment.BottomStart),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                        Icon(
                            symbol = MaterialSymbols.Warning,
                            contentDescription = stringRes(Res.string.content_warning),
                            modifier =
                                Modifier
                                    .size(30.dp)
                                    .align(Alignment.TopEnd),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        text =
                            if (reason.isNullOrBlank()) {
                                stringRes(Res.string.content_warning)
                            } else {
                                stringRes(Res.string.content_warning_with_reason, reason)
                            },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        softWrap = true,
                        textAlign = TextAlign.Center,
                    )
                }

                if (reason.isNullOrBlank()) {
                    Row {
                        Text(
                            text = stringRes(Res.string.content_warning_explanation),
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 10.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    FilledTonalButton(
                        modifier = Modifier.padding(top = 10.dp),
                        onClick = onDismiss,
                        shape = ButtonBorder,
                        contentPadding = ButtonPadding,
                    ) {
                        Text(
                            text = stringRes(Res.string.show_anyway),
                        )
                    }
                }
            }
        }
    }
}

fun collectContentWarningReasons(event: Event): Set<String> {
    val reasons = linkedSetOf<String>()
    event.contentWarningReason()?.takeIf { it.isNotBlank() }?.let { reasons.add(it) }
    event.imetas().forEach { iMeta ->
        iMeta.properties[ContentWarningTag.TAG_NAME]
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { reasons.add(it) }
    }
    return reasons
}
