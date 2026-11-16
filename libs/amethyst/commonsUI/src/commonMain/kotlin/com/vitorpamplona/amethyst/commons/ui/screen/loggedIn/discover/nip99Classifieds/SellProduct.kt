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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip99Classifieds

import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue // kept for readonly dropdown ThinPaddingTextField
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.classifieds_category
import com.vitorpamplona.amethyst.commons.resources.classifieds_category_accessories
import com.vitorpamplona.amethyst.commons.resources.classifieds_category_art
import com.vitorpamplona.amethyst.commons.resources.classifieds_category_books
import com.vitorpamplona.amethyst.commons.resources.classifieds_category_clothing
import com.vitorpamplona.amethyst.commons.resources.classifieds_category_collectibles
import com.vitorpamplona.amethyst.commons.resources.classifieds_category_crafts
import com.vitorpamplona.amethyst.commons.resources.classifieds_category_electronics
import com.vitorpamplona.amethyst.commons.resources.classifieds_category_fitness
import com.vitorpamplona.amethyst.commons.resources.classifieds_category_food
import com.vitorpamplona.amethyst.commons.resources.classifieds_category_furniture
import com.vitorpamplona.amethyst.commons.resources.classifieds_category_home
import com.vitorpamplona.amethyst.commons.resources.classifieds_category_misc
import com.vitorpamplona.amethyst.commons.resources.classifieds_category_office
import com.vitorpamplona.amethyst.commons.resources.classifieds_category_other
import com.vitorpamplona.amethyst.commons.resources.classifieds_category_pets
import com.vitorpamplona.amethyst.commons.resources.classifieds_category_sports
import com.vitorpamplona.amethyst.commons.resources.classifieds_condition
import com.vitorpamplona.amethyst.commons.resources.classifieds_condition_fair
import com.vitorpamplona.amethyst.commons.resources.classifieds_condition_fair_explainer
import com.vitorpamplona.amethyst.commons.resources.classifieds_condition_good
import com.vitorpamplona.amethyst.commons.resources.classifieds_condition_good_explainer
import com.vitorpamplona.amethyst.commons.resources.classifieds_condition_like_new
import com.vitorpamplona.amethyst.commons.resources.classifieds_condition_like_new_explainer
import com.vitorpamplona.amethyst.commons.resources.classifieds_condition_new
import com.vitorpamplona.amethyst.commons.resources.classifieds_condition_new_explainer
import com.vitorpamplona.amethyst.commons.resources.classifieds_location
import com.vitorpamplona.amethyst.commons.resources.classifieds_location_placeholder
import com.vitorpamplona.amethyst.commons.resources.classifieds_price
import com.vitorpamplona.amethyst.commons.resources.classifieds_title
import com.vitorpamplona.amethyst.commons.resources.classifieds_title_placeholder
import com.vitorpamplona.amethyst.commons.ui.actions.rememberUrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.components.TextSpinner
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Font14SP
import com.vitorpamplona.amethyst.commons.ui.theme.Height100Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.SquaredQuoteBorderModifier
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip99Classifieds.tags.ConditionTag
import kotlinx.collections.immutable.toImmutableList

@Suppress("ViewModelConstructorInComposable")
@Preview
@Composable
fun SellProductPreview() {
    val accountViewModel = mockAccountViewModel()
    val postViewModel = NewProductViewModel()
    postViewModel.init(accountViewModel)

    ThemeComparisonColumn {
        SellProduct(postViewModel)
    }
}

@Composable
fun SellProduct(postViewModel: NewProductViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!postViewModel.productImages.isEmpty()) {
            LazyRow(Height100Modifier, horizontalArrangement = spacedBy(Size5dp)) {
                items(postViewModel.productImages) {
                    Box(SquaredQuoteBorderModifier) {
                        AsyncImage(
                            model = it.url,
                            contentDescription = it.alt ?: it.url,
                            contentScale = ContentScale.FillHeight,
                            modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(Res.string.classifieds_title),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            ThinPaddingTextField(
                state = postViewModel.title,
                onTextChanged = { postViewModel.onTitleChanged() },
                inputTransformation = MentionPreservingInputTransformation,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringRes(Res.string.classifieds_title_placeholder),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                spellCheck = true,
                outputTransformation =
                    rememberUrlUserTagOutputTransformation(),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                    ),
            )
        }

        HorizontalDivider(thickness = DividerThickness)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(Res.string.classifieds_price),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            ThinPaddingTextField(
                modifier = Modifier.fillMaxWidth(),
                state = postViewModel.price,
                onTextChanged = { postViewModel.onPriceChanged() },
                placeholder = {
                    Text(
                        text = "1000",
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number,
                    ),
                singleLine = true,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                    ),
            )
        }

        HorizontalDivider(thickness = DividerThickness)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(Res.string.classifieds_condition),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            val conditionTypes =
                listOf(
                    Triple(
                        ConditionTag.CONDITION.NEW,
                        stringRes(id = Res.string.classifieds_condition_new),
                        stringRes(id = Res.string.classifieds_condition_new_explainer),
                    ),
                    Triple(
                        ConditionTag.CONDITION.USED_LIKE_NEW,
                        stringRes(id = Res.string.classifieds_condition_like_new),
                        stringRes(id = Res.string.classifieds_condition_like_new_explainer),
                    ),
                    Triple(
                        ConditionTag.CONDITION.USED_GOOD,
                        stringRes(id = Res.string.classifieds_condition_good),
                        stringRes(id = Res.string.classifieds_condition_good_explainer),
                    ),
                    Triple(
                        ConditionTag.CONDITION.USED_FAIR,
                        stringRes(id = Res.string.classifieds_condition_fair),
                        stringRes(id = Res.string.classifieds_condition_fair_explainer),
                    ),
                )

            val conditionOptions =
                remember {
                    conditionTypes.map { TitleExplainer(it.second, it.third) }.toImmutableList()
                }

            TextSpinner(
                placeholder = conditionTypes.first { it.first == postViewModel.condition }.second,
                options = conditionOptions,
                onSelect = {
                    postViewModel.updateCondition(conditionTypes[it].first)
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = 5.dp, bottom = 1.dp),
            ) { currentOption, modifier ->
                ThinPaddingTextField(
                    value = TextFieldValue(currentOption),
                    onValueChange = {},
                    readOnly = true,
                    modifier = modifier,
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                        ),
                )
            }
        }

        HorizontalDivider(thickness = DividerThickness)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(Res.string.classifieds_category),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            val categoryList =
                listOf(
                    Res.string.classifieds_category_clothing,
                    Res.string.classifieds_category_accessories,
                    Res.string.classifieds_category_electronics,
                    Res.string.classifieds_category_furniture,
                    Res.string.classifieds_category_collectibles,
                    Res.string.classifieds_category_books,
                    Res.string.classifieds_category_pets,
                    Res.string.classifieds_category_sports,
                    Res.string.classifieds_category_fitness,
                    Res.string.classifieds_category_art,
                    Res.string.classifieds_category_crafts,
                    Res.string.classifieds_category_home,
                    Res.string.classifieds_category_office,
                    Res.string.classifieds_category_food,
                    Res.string.classifieds_category_misc,
                    Res.string.classifieds_category_other,
                )

            val categoryTypes = categoryList.map { Triple(it, stringRes(id = it), null) }

            val categoryOptions =
                remember {
                    categoryTypes.map { TitleExplainer(it.second, null) }.toImmutableList()
                }
            TextSpinner(
                placeholder = categoryTypes.firstOrNull { it.second == postViewModel.category.text.toString() }?.second ?: "",
                options = categoryOptions,
                onSelect = {
                    postViewModel.updateCategory(categoryTypes[it].second)
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = 5.dp, bottom = 1.dp),
            ) { currentOption, modifier ->
                ThinPaddingTextField(
                    value = TextFieldValue(currentOption),
                    onValueChange = {},
                    readOnly = true,
                    modifier = modifier,
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                        ),
                )
            }
        }

        HorizontalDivider(thickness = DividerThickness)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(Res.string.classifieds_location),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            ThinPaddingTextField(
                state = postViewModel.locationText,
                onTextChanged = { postViewModel.onLocationChanged() },
                inputTransformation = MentionPreservingInputTransformation,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringRes(Res.string.classifieds_location_placeholder),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                spellCheck = true,
                outputTransformation =
                    rememberUrlUserTagOutputTransformation(),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                    ),
            )
        }

        HorizontalDivider(thickness = DividerThickness)
    }
}
