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
package com.vitorpamplona.amethyst.ui.components

import android.content.res.Resources
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.translations_always_translate_to_lang
import com.vitorpamplona.amethyst.commons.resources.translations_auto
import com.vitorpamplona.amethyst.commons.resources.translations_never_translate_from_lang
import com.vitorpamplona.amethyst.commons.resources.translations_show_in_lang_first
import com.vitorpamplona.amethyst.commons.resources.translations_to
import com.vitorpamplona.amethyst.commons.resources.translations_translated_from
import com.vitorpamplona.amethyst.commons.ui.components.appendLink
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Font14SP
import com.vitorpamplona.amethyst.commons.ui.theme.MaxWidthPaddingTop5dp
import com.vitorpamplona.amethyst.commons.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import java.util.Locale

/**
 * The "Auto-translated from X to Y" footer shown beneath translated rich text. Tapping the source
 * or target labels toggles which version is displayed; tapping "Auto-translated" opens the
 * per-language preferences dropdown.
 */
@Composable
internal fun TranslationStatusBar(
    source: String,
    target: String,
    modifier: Modifier = MaxWidthPaddingTop5dp,
    accountViewModel: AccountViewModel,
    onShowOriginalChange: (Boolean) -> Unit,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    val sourceDisplay = remember(source) { Locale.forLanguageTag(source).displayName }
    val targetDisplay = remember(target) { Locale.forLanguageTag(target).displayName }

    Row(modifier = modifier) {
        TranslationStatusText(
            sourceDisplay = sourceDisplay,
            targetDisplay = targetDisplay,
            onAutoLabelClick = { dropdownExpanded = !dropdownExpanded },
            onSourceLabelClick = { onShowOriginalChange(true) },
            onTargetLabelClick = { onShowOriginalChange(false) },
        )

        if (dropdownExpanded) {
            LangSettingsDropdown(
                source = source,
                target = target,
                sourceDisplay = sourceDisplay,
                targetDisplay = targetDisplay,
                accountViewModel = accountViewModel,
                onDismiss = { dropdownExpanded = false },
            )
        }
    }
}

@Composable
private fun TranslationStatusText(
    sourceDisplay: String,
    targetDisplay: String,
    onAutoLabelClick: () -> Unit,
    onSourceLabelClick: () -> Unit,
    onTargetLabelClick: () -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.lessImportantLink
    val autoLabel = stringRes(Res.string.translations_auto)
    val translatedFromLabel = stringRes(Res.string.translations_translated_from)
    val toLabel = stringRes(Res.string.translations_to)

    Text(
        text =
            buildAnnotatedString {
                appendLink(autoLabel, textColor, onAutoLabelClick)
                append(" $translatedFromLabel ")
                appendLink(sourceDisplay, textColor, onSourceLabelClick)
                append(" $toLabel ")
                appendLink(targetDisplay, textColor, onTargetLabelClick)
            },
        style =
            LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
                fontSize = Font14SP,
            ),
        overflow = TextOverflow.Visible,
        maxLines = 3,
    )
}

@Composable
private fun LangSettingsDropdown(
    source: String,
    target: String,
    sourceDisplay: String,
    targetDisplay: String,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val deviceLocales = rememberDeviceLocales()
    val settings = accountViewModel.account.settings
    val preferenceForPair = settings.preferenceBetween(source, target)

    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        val blocked = source in accountViewModel.dontTranslateFrom()
        LangMenuItem(
            checked = blocked,
            label = stringRes(Res.string.translations_never_translate_from_lang, sourceDisplay),
            onClick = {
                accountViewModel.toggleDontTranslateFrom(source)
                if (!blocked) MlKitTranslationPlatform.onLanguageBlocked(source, accountViewModel)
                onDismiss()
            },
        )
        HorizontalDivider(thickness = DividerThickness)
        LangMenuItem(
            checked = preferenceForPair == source,
            label = stringRes(Res.string.translations_show_in_lang_first, sourceDisplay),
            onClick = {
                accountViewModel.prefer(source, target, source)
                onDismiss()
            },
        )
        LangMenuItem(
            checked = preferenceForPair == target,
            label = stringRes(Res.string.translations_show_in_lang_first, targetDisplay),
            onClick = {
                accountViewModel.prefer(source, target, target)
                onDismiss()
            },
        )
        HorizontalDivider(thickness = DividerThickness)
        for (lang in deviceLocales) {
            LangMenuItem(
                checked = settings.translateToContains(lang.language),
                label = stringRes(Res.string.translations_always_translate_to_lang, lang.displayName),
                onClick = {
                    onDismiss()
                    accountViewModel.updateTranslateTo(lang.language)
                },
            )
        }
    }
}

@Composable
private fun rememberDeviceLocales(): List<Locale> =
    remember {
        val list = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
        (0 until list.size()).mapNotNull { list.get(it) }
    }

@Composable
private fun LangMenuItem(
    checked: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { CheckmarkRow(checked, label) },
        onClick = onClick,
    )
}

@Composable
private fun CheckmarkRow(
    checked: Boolean,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (checked) {
            Icon(
                symbol = MaterialSymbols.Check,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.size(10.dp))
        Text(label)
    }
}
