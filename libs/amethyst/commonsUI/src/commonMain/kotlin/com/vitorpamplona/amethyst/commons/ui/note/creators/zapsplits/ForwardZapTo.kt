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
package com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.load_from_text
import com.vitorpamplona.amethyst.commons.resources.zap_split_explainer
import com.vitorpamplona.amethyst.commons.resources.zap_split_search_and_add_user
import com.vitorpamplona.amethyst.commons.resources.zap_split_search_and_add_user_placeholder
import com.vitorpamplona.amethyst.commons.resources.zap_split_title
import com.vitorpamplona.amethyst.commons.ui.actions.rememberUrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.components.OutlinedThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.IZapField
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.ZapSplitIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlin.math.round

@Composable
fun ForwardZapTo(
    postViewModel: IZapField,
    accountViewModel: AccountViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp),
        ) {
            ZapSplitIcon()

            Text(
                text = stringRes(Res.string.zap_split_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier.padding(horizontal = 10.dp).weight(1f),
            )

            OutlinedButton(onClick = { postViewModel.updateZapFromText() }) {
                Text(text = stringRes(Res.string.load_from_text))
            }
        }

        HorizontalDivider(thickness = DividerThickness)

        Text(
            text = stringRes(Res.string.zap_split_explainer),
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.padding(vertical = 10.dp),
        )

        postViewModel.forwardZapTo.value.items.forEachIndexed { index, splitItem ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = Size10dp),
            ) {
                BaseUserPicture(splitItem.key, Size55dp, accountViewModel = accountViewModel)

                Spacer(modifier = DoubleHorzSpacer)

                Column(modifier = Modifier.weight(1f)) {
                    UsernameDisplay(splitItem.key, accountViewModel = accountViewModel)
                    Text(
                        text = "${(splitItem.percentage * 100).toInt()}%",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                }

                Spacer(modifier = DoubleHorzSpacer)

                Slider(
                    value = splitItem.percentage,
                    onValueChange = { sliderValue ->
                        val rounded = (round(sliderValue * 100)) / 100.0f
                        postViewModel.updateZapPercentage(index, rounded)
                    },
                    modifier = Modifier.weight(1.5f),
                )
            }
        }

        OutlinedThinPaddingTextField(
            state = postViewModel.forwardZapToEditting,
            onTextChanged = postViewModel::onForwardZapTextChanged,
            inputTransformation = MentionPreservingInputTransformation,
            outputTransformation = rememberUrlUserTagOutputTransformation(),
            label = { Text(text = stringRes(Res.string.zap_split_search_and_add_user)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringRes(Res.string.zap_split_search_and_add_user_placeholder),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        )
    }
}
