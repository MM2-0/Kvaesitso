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
package com.vitorpamplona.amethyst.commons.nip23LongContent.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.metadata_panel_add_tag
import com.vitorpamplona.amethyst.commons.resources.metadata_panel_banner_url
import com.vitorpamplona.amethyst.commons.resources.metadata_panel_remove_tag
import com.vitorpamplona.amethyst.commons.resources.metadata_panel_slug
import com.vitorpamplona.amethyst.commons.resources.metadata_panel_slug_description
import com.vitorpamplona.amethyst.commons.resources.metadata_panel_summary
import com.vitorpamplona.amethyst.commons.resources.metadata_panel_title
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * Form fields for article metadata: title, summary, banner, tags, slug.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetadataPanel(
    title: String,
    onTitleChange: (String) -> Unit,
    summary: String,
    onSummaryChange: (String) -> Unit,
    bannerUrl: String,
    onBannerUrlChange: (String) -> Unit,
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    slug: String,
    onSlugChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tagInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpellCheckedOutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            maxLength = 256,
            label = { Text(stringRes(Res.string.metadata_panel_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("${title.length}/256") },
        )

        SpellCheckedOutlinedTextField(
            value = summary,
            onValueChange = onSummaryChange,
            maxLength = 1024,
            label = { Text(stringRes(Res.string.metadata_panel_summary)) },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("${summary.length}/1024") },
        )

        OutlinedTextField(
            value = bannerUrl,
            onValueChange = onBannerUrlChange,
            label = { Text(stringRes(Res.string.metadata_panel_banner_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Tags chip input
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text(stringRes(Res.string.metadata_panel_add_tag)) },
                    singleLine = true,
                    modifier =
                        Modifier.weight(1f).onKeyEvent { event ->
                            if (event.key == Key.Enter && tagInput.isNotBlank()) {
                                val newTag = tagInput.trim().lowercase()
                                if (newTag !in tags) {
                                    onTagsChange(tags + newTag)
                                }
                                tagInput = ""
                                true
                            } else {
                                false
                            }
                        },
                )
            }

            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    tags.forEach { tag ->
                        AssistChip(
                            onClick = { onTagsChange(tags - tag) },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    MaterialSymbols.Close,
                                    contentDescription = stringRes(Res.string.metadata_panel_remove_tag, tag),
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = slug,
            onValueChange = onSlugChange,
            label = { Text(stringRes(Res.string.metadata_panel_slug)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(stringRes(Res.string.metadata_panel_slug_description)) },
        )
    }
}
