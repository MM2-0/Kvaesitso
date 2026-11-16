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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.health_connect_rationale_calories
import com.vitorpamplona.amethyst.commons.resources.health_connect_rationale_distance
import com.vitorpamplona.amethyst.commons.resources.health_connect_rationale_elevation
import com.vitorpamplona.amethyst.commons.resources.health_connect_rationale_exercise
import com.vitorpamplona.amethyst.commons.resources.health_connect_rationale_headline
import com.vitorpamplona.amethyst.commons.resources.health_connect_rationale_heart_rate
import com.vitorpamplona.amethyst.commons.resources.health_connect_rationale_intro
import com.vitorpamplona.amethyst.commons.resources.health_connect_rationale_limit_optional
import com.vitorpamplona.amethyst.commons.resources.health_connect_rationale_limit_publish
import com.vitorpamplona.amethyst.commons.resources.health_connect_rationale_limit_window
import com.vitorpamplona.amethyst.commons.resources.health_connect_rationale_limit_write
import com.vitorpamplona.amethyst.commons.resources.health_connect_rationale_limits_title
import com.vitorpamplona.amethyst.commons.resources.health_connect_rationale_privacy_policy
import com.vitorpamplona.amethyst.commons.resources.health_connect_rationale_steps
import com.vitorpamplona.amethyst.commons.resources.health_connect_rationale_title
import com.vitorpamplona.amethyst.commons.resources.health_connect_rationale_what_title
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * Static, account-free explanation of what Amethyst reads from Health Connect and why. Shown both
 * from Health Connect itself (see [HealthConnectRationaleActivity]) and from the Connect card in
 * the workout composer, so the user can read the rationale before granting anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectRationaleScreen(
    onOpenPrivacyPolicy: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            ShorterTopAppBar(
                title = { Text(stringRes(Res.string.health_connect_rationale_title)) },
                navigationIcon = {
                    IconButton(onClose) {
                        Icon(
                            symbol = MaterialSymbols.Close,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringRes(Res.string.health_connect_rationale_headline),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringRes(Res.string.health_connect_rationale_intro),
                style = MaterialTheme.typography.bodyMedium,
            )

            SectionTitle(stringRes(Res.string.health_connect_rationale_what_title))
            Bullet(stringRes(Res.string.health_connect_rationale_exercise))
            Bullet(stringRes(Res.string.health_connect_rationale_distance))
            Bullet(stringRes(Res.string.health_connect_rationale_calories))
            Bullet(stringRes(Res.string.health_connect_rationale_heart_rate))
            Bullet(stringRes(Res.string.health_connect_rationale_steps))
            Bullet(stringRes(Res.string.health_connect_rationale_elevation))

            SectionTitle(stringRes(Res.string.health_connect_rationale_limits_title))
            Bullet(stringRes(Res.string.health_connect_rationale_limit_window))
            Bullet(stringRes(Res.string.health_connect_rationale_limit_write))
            Bullet(stringRes(Res.string.health_connect_rationale_limit_publish))
            Bullet(stringRes(Res.string.health_connect_rationale_limit_optional))

            OutlinedButton(
                onClick = onOpenPrivacyPolicy,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Text(stringRes(Res.string.health_connect_rationale_privacy_policy))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
