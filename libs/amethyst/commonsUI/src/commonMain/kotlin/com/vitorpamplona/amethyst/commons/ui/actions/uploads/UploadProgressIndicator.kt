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
package com.vitorpamplona.amethyst.commons.ui.actions.uploads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.uploading_state_compressing
import com.vitorpamplona.amethyst.commons.resources.uploading_state_downloading
import com.vitorpamplona.amethyst.commons.resources.uploading_state_error
import com.vitorpamplona.amethyst.commons.resources.uploading_state_finished
import com.vitorpamplona.amethyst.commons.resources.uploading_state_hashing
import com.vitorpamplona.amethyst.commons.resources.uploading_state_ready
import com.vitorpamplona.amethyst.commons.resources.uploading_state_server_processing
import com.vitorpamplona.amethyst.commons.resources.uploading_state_uploading
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource

@Composable
fun UploadProgressIndicator(
    orchestrator: UploadOrchestrator,
    modifier: Modifier = Modifier,
) {
    val progressValue = orchestrator.progress.collectAsState().value
    val progressStatusValue = orchestrator.progressState.collectAsState().value

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(55.dp),
            contentAlignment = Alignment.Center,
        ) {
            val animatedProgress =
                animateFloatAsState(
                    targetValue = progressValue.toFloat(),
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                ).value

            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier =
                    Size55Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background),
                strokeWidth = 5.dp,
            )

            val txt =
                when (progressStatusValue) {
                    is UploadingState.Ready -> stringRes(Res.string.uploading_state_ready)
                    is UploadingState.Compressing -> stringRes(Res.string.uploading_state_compressing)
                    is UploadingState.Uploading -> stringRes(Res.string.uploading_state_uploading)
                    is UploadingState.ServerProcessing -> stringRes(Res.string.uploading_state_server_processing)
                    is UploadingState.Downloading -> stringRes(Res.string.uploading_state_downloading)
                    is UploadingState.Hashing -> stringRes(Res.string.uploading_state_hashing)
                    is UploadingState.Finished -> stringRes(Res.string.uploading_state_finished)
                    is UploadingState.Error -> stringRes(Res.string.uploading_state_error)
                }

            Text(
                txt,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }

        // The badge fits one word; the reason is what the user can act on.
        if (progressStatusValue is UploadingState.Error) {
            Text(
                stringRes(progressStatusValue.errorResource, *progressStatusValue.params),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
            )
        }
    }
}
