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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AccentColorType
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.resourceId
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accent_color
import com.vitorpamplona.amethyst.commons.resources.accent_color_account_description
import com.vitorpamplona.amethyst.commons.resources.accent_color_application
import com.vitorpamplona.amethyst.commons.resources.accent_color_description
import com.vitorpamplona.amethyst.commons.resources.settings_section_appearance
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.contentColorOnAccent
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.ui.theme.previewColor
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Account Settings › Accent Color: this account's override of the application accent color
 * (Application Preferences › Accent Color). An account starts on "Application" and follows it.
 */
@Composable
fun AccentColorSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = Res.string.accent_color), nav)
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            SettingsSection(Res.string.settings_section_appearance) {
                AccentColorTile(accountViewModel.settings.uiSettingsFlow.accentColor, accountViewModel.account.settings)
            }
        }
    }
}

/**
 * The accent picker. With [accountSettings] it edits that account's override, led by an
 * "Application" option that clears it; without, it edits the application color [appAccent].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccentColorTile(
    appAccent: MutableStateFlow<AccentColorType>,
    accountSettings: AccountSettings?,
) {
    val app by appAccent.collectAsState()
    val accountAccent = accountSettings?.accentColor?.collectAsState()?.value
    val dark = !MaterialTheme.colorScheme.isLight

    SettingsBlockTile(
        icon = MaterialSymbols.Circle,
        title = stringRes(Res.string.accent_color),
        description =
            stringRes(
                if (accountSettings != null) Res.string.accent_color_account_description else Res.string.accent_color_description,
            ),
    ) {
        // FlowRow so every swatch stays visible (wraps to a second line on narrow screens) instead
        // of scrolling the last ones off-edge with no affordance.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (accountSettings != null) {
                AccentColorApplicationSwatch(
                    color = app.previewColor(dark),
                    selected = accountAccent == null,
                    onClick = { accountSettings.updateAccentColor(null) },
                )
            }
            AccentColorType.entries.forEach { option ->
                AccentColorSwatch(
                    color = option.previewColor(dark),
                    label = stringRes(option.resourceId),
                    selected = if (accountSettings != null) option == accountAccent else option == app,
                    onClick = {
                        if (accountSettings != null) {
                            accountSettings.updateAccentColor(option)
                        } else {
                            appAccent.tryEmit(option)
                        }
                    },
                )
            }
        }
    }
}

// A pill in the application accent, labelled, so it reads as "follow the application color"
// rather than as a seventh color that happens to match one of the swatches beside it.
@Composable
private fun AccentColorApplicationSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = contentColorOnAccent(color)
    Row(
        modifier =
            Modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else {
                        Modifier
                    },
                ).clickable(onClick = onClick)
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) {
            Icon(
                symbol = MaterialSymbols.Done,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = stringRes(Res.string.accent_color_application),
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun AccentColorSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else {
                        Modifier
                    },
                ).clickable(onClick = onClick)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                symbol = MaterialSymbols.Done,
                contentDescription = null,
                tint = contentColorOnAccent(color),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
