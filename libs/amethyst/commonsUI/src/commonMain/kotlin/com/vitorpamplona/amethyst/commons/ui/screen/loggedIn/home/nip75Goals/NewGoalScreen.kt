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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.nip75Goals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.goal_amount_label
import com.vitorpamplona.amethyst.commons.resources.goal_amount_placeholder
import com.vitorpamplona.amethyst.commons.resources.goal_description_label
import com.vitorpamplona.amethyst.commons.resources.goal_description_placeholder
import com.vitorpamplona.amethyst.commons.resources.goal_image_label
import com.vitorpamplona.amethyst.commons.resources.goal_image_placeholder
import com.vitorpamplona.amethyst.commons.resources.goal_set_deadline
import com.vitorpamplona.amethyst.commons.resources.goal_summary_label
import com.vitorpamplona.amethyst.commons.resources.goal_summary_placeholder
import com.vitorpamplona.amethyst.commons.resources.goal_website_label
import com.vitorpamplona.amethyst.commons.resources.goal_website_placeholder
import com.vitorpamplona.amethyst.commons.resources.new_goal
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.PublishingTopBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGoalScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val goalViewModel: NewGoalViewModel = rememberViewModel { NewGoalViewModel() }
    goalViewModel.init(accountViewModel)

    LaunchedEffect(goalViewModel, accountViewModel) {
        // no-op for now, could load drafts
    }

    NewGoalScreen(
        goalViewModel,
        accountViewModel,
        nav,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGoalScreen(
    goalViewModel: NewGoalViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    PlatformBackHandler {
        goalViewModel.cancel()
        nav.popBack()
    }

    Scaffold(
        topBar = {
            PublishingTopBar(
                titleRes = Res.string.new_goal,
                isActive = goalViewModel::canPost,
                onCancel = {
                    goalViewModel.cancel()
                    nav.popBack()
                },
                onPost = {
                    accountViewModel.launchSigner {
                        goalViewModel.sendPostSync()
                        nav.popBack()
                    }
                },
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
            NewGoalBody(goalViewModel)
        }
    }
}

@Composable
private fun NewGoalBody(goalViewModel: NewGoalViewModel) {
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        SpellCheckedOutlinedTextField(
            value = goalViewModel.description,
            onValueChange = { goalViewModel.description = it },
            label = { Text(stringRes(Res.string.goal_description_label)) },
            placeholder = { Text(stringRes(Res.string.goal_description_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = goalViewModel.amount,
            onValueChange = { goalViewModel.amount = it },
            label = { Text(stringRes(Res.string.goal_amount_label)) },
            placeholder = { Text(stringRes(Res.string.goal_amount_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(12.dp))

        SpellCheckedOutlinedTextField(
            value = goalViewModel.summary,
            onValueChange = { goalViewModel.summary = it },
            label = { Text(stringRes(Res.string.goal_summary_label)) },
            placeholder = { Text(stringRes(Res.string.goal_summary_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = goalViewModel.imageUrl,
            onValueChange = { goalViewModel.imageUrl = it },
            label = { Text(stringRes(Res.string.goal_image_label)) },
            placeholder = { Text(stringRes(Res.string.goal_image_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = goalViewModel.websiteUrl,
            onValueChange = { goalViewModel.websiteUrl = it },
            label = { Text(stringRes(Res.string.goal_website_label)) },
            placeholder = { Text(stringRes(Res.string.goal_website_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = CenterVertically) {
            Checkbox(
                checked = goalViewModel.wantsDeadline,
                onCheckedChange = { goalViewModel.wantsDeadline = it },
            )
            Text(
                text = stringRes(Res.string.goal_set_deadline),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
