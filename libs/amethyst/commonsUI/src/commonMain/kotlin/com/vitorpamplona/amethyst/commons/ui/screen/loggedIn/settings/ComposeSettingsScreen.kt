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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AccountPoWPreferences
import com.vitorpamplona.amethyst.commons.model.BooleanType
import com.vitorpamplona.amethyst.commons.model.SpellCheckType
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_client_tag_explainer
import com.vitorpamplona.amethyst.commons.resources.add_client_tag_title
import com.vitorpamplona.amethyst.commons.resources.ai_writing_setting_description
import com.vitorpamplona.amethyst.commons.resources.ai_writing_setting_title
import com.vitorpamplona.amethyst.commons.resources.auto_create_drafts_setting_description
import com.vitorpamplona.amethyst.commons.resources.auto_create_drafts_setting_title
import com.vitorpamplona.amethyst.commons.resources.compose_settings
import com.vitorpamplona.amethyst.commons.resources.compose_signature_setting_description
import com.vitorpamplona.amethyst.commons.resources.compose_signature_setting_hint
import com.vitorpamplona.amethyst.commons.resources.compose_signature_setting_title
import com.vitorpamplona.amethyst.commons.resources.pow_categories_explainer
import com.vitorpamplona.amethyst.commons.resources.pow_categories_title
import com.vitorpamplona.amethyst.commons.resources.pow_category_comments
import com.vitorpamplona.amethyst.commons.resources.pow_category_comments_explainer
import com.vitorpamplona.amethyst.commons.resources.pow_category_gift_wraps
import com.vitorpamplona.amethyst.commons.resources.pow_category_gift_wraps_explainer
import com.vitorpamplona.amethyst.commons.resources.pow_category_long_form
import com.vitorpamplona.amethyst.commons.resources.pow_category_long_form_explainer
import com.vitorpamplona.amethyst.commons.resources.pow_category_other_public
import com.vitorpamplona.amethyst.commons.resources.pow_category_other_public_explainer
import com.vitorpamplona.amethyst.commons.resources.pow_category_public_chat
import com.vitorpamplona.amethyst.commons.resources.pow_category_public_chat_explainer
import com.vitorpamplona.amethyst.commons.resources.pow_category_reactions
import com.vitorpamplona.amethyst.commons.resources.pow_category_reactions_explainer
import com.vitorpamplona.amethyst.commons.resources.pow_category_reports
import com.vitorpamplona.amethyst.commons.resources.pow_category_reports_explainer
import com.vitorpamplona.amethyst.commons.resources.pow_category_reposts
import com.vitorpamplona.amethyst.commons.resources.pow_category_reposts_explainer
import com.vitorpamplona.amethyst.commons.resources.pow_category_short_notes
import com.vitorpamplona.amethyst.commons.resources.pow_category_short_notes_explainer
import com.vitorpamplona.amethyst.commons.resources.pow_category_voice
import com.vitorpamplona.amethyst.commons.resources.pow_category_voice_explainer
import com.vitorpamplona.amethyst.commons.resources.pow_custom_difficulty_explainer
import com.vitorpamplona.amethyst.commons.resources.pow_custom_difficulty_title
import com.vitorpamplona.amethyst.commons.resources.pow_difficulty_estimate
import com.vitorpamplona.amethyst.commons.resources.pow_difficulty_explainer
import com.vitorpamplona.amethyst.commons.resources.pow_difficulty_off
import com.vitorpamplona.amethyst.commons.resources.pow_difficulty_title
import com.vitorpamplona.amethyst.commons.resources.pow_settings_title
import com.vitorpamplona.amethyst.commons.resources.spell_check_automatic
import com.vitorpamplona.amethyst.commons.resources.spell_check_built_in
import com.vitorpamplona.amethyst.commons.resources.spell_check_off
import com.vitorpamplona.amethyst.commons.resources.spell_check_setting_description
import com.vitorpamplona.amethyst.commons.resources.spell_check_setting_title
import com.vitorpamplona.amethyst.commons.resources.suggest_workouts_setting_description
import com.vitorpamplona.amethyst.commons.resources.suggest_workouts_setting_title
import com.vitorpamplona.amethyst.commons.resources.tracked_broadcasts_setting_description
import com.vitorpamplona.amethyst.commons.resources.tracked_broadcasts_setting_title
import com.vitorpamplona.amethyst.commons.service.pow.PoWCategory
import com.vitorpamplona.amethyst.commons.service.pow.PoWEstimator
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.pow.POW_PRESETS
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.pow.deviceHashesPerSecond
import com.vitorpamplona.amethyst.commons.ui.pow.loadApproxDuration
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsBlockTile
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsControlRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsStepper
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSubControlRow
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.StringResource

@Composable
fun ComposeSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = Res.string.compose_settings), nav)
        },
    ) { padding ->
        ComposeSettingsContent(
            sharedPrefs = accountViewModel.settings.uiSettingsFlow,
            accountViewModel = accountViewModel,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun ComposeSettingsContent(
    sharedPrefs: UiSettingsFlow,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsSection(Res.string.compose_settings) {
            BooleanSwitchTile(
                flow = sharedPrefs.automaticallyCreateDrafts,
                icon = MaterialSymbols.Drafts,
                title = Res.string.auto_create_drafts_setting_title,
                description = Res.string.auto_create_drafts_setting_description,
            )
            SettingsDivider()
            // The F-Droid build ships no on-device model, so the toggle would do nothing there.
            if (accountViewModel.host.supportsWritingAssistant) {
                BooleanSwitchTile(
                    flow = sharedPrefs.automaticallyProposeAiImprovements,
                    icon = MaterialSymbols.AutoAwesome,
                    title = Res.string.ai_writing_setting_title,
                    description = Res.string.ai_writing_setting_description,
                )
                SettingsDivider()
            }
            // Only where the platform checks spelling itself; on phones the keyboard does it.
            if (LocalAppPlatform.current.spellCheck != null) {
                SpellCheckTile(sharedPrefs)
                SettingsDivider()
            }
            BooleanSwitchTile(
                flow = sharedPrefs.useTrackedBroadcasts,
                icon = MaterialSymbols.CellTower,
                title = Res.string.tracked_broadcasts_setting_title,
                description = Res.string.tracked_broadcasts_setting_description,
            )
            SettingsDivider()
            BooleanSwitchTile(
                flow = sharedPrefs.suggestWorkoutsFromHealthConnect,
                icon = MaterialSymbols.DirectionsRun,
                title = Res.string.suggest_workouts_setting_title,
                description = Res.string.suggest_workouts_setting_description,
            )
            SettingsDivider()
            AddClientTagTile(accountViewModel)
            SettingsDivider()
            SignatureTile(accountViewModel, sharedPrefs.composeSignature)
        }

        SettingsSection(Res.string.pow_settings_title) {
            PowDifficultyTile(accountViewModel)
            SettingsDivider()
            PowCategoryChecklist(accountViewModel)
        }
    }
}

@Composable
private fun SpellCheckTile(sharedPrefs: UiSettingsFlow) {
    val spellCheck by sharedPrefs.spellCheck.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.MenuBook,
        title = Res.string.spell_check_setting_title,
        description = Res.string.spell_check_setting_description,
        options = SpellCheckType.entries,
        labelRes = {
            when (it) {
                SpellCheckType.AUTOMATIC -> Res.string.spell_check_automatic
                SpellCheckType.BUILT_IN -> Res.string.spell_check_built_in
                SpellCheckType.OFF -> Res.string.spell_check_off
            }
        },
        selected = spellCheck,
        onSelect = { sharedPrefs.spellCheck.tryEmit(it) },
    )
}

@Composable
private fun PowDifficultyTile(accountViewModel: AccountViewModel) {
    val difficulty by accountViewModel.account.settings.syncedSettings.proofOfWork
        .difficulty
        .collectAsStateWithLifecycle()

    SettingsBlockTile(
        icon = MaterialSymbols.Manufacturing,
        title = stringRes(Res.string.pow_difficulty_title),
        description = stringRes(Res.string.pow_difficulty_explainer),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = difficulty <= 0,
                onClick = { accountViewModel.updatePowDifficulty(0) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = POW_PRESETS.size + 1),
            ) {
                Text(stringRes(Res.string.pow_difficulty_off))
            }
            POW_PRESETS.forEachIndexed { index, preset ->
                SegmentedButton(
                    selected = difficulty == preset,
                    onClick = { accountViewModel.updatePowDifficulty(preset) },
                    shape = SegmentedButtonDefaults.itemShape(index = index + 1, count = POW_PRESETS.size + 1),
                ) {
                    Text(preset.toString())
                }
            }
        }

        PowTimeEstimate(difficulty)
    }

    SettingsSubControlRow(
        title = stringRes(Res.string.pow_custom_difficulty_title),
        description = stringRes(Res.string.pow_custom_difficulty_explainer),
    ) {
        SettingsStepper(
            value = difficulty,
            min = 0,
            max = AccountPoWPreferences.MAX_POW_DIFFICULTY,
            unsetLabel = stringRes(Res.string.pow_difficulty_off),
            onValueChange = accountViewModel::updatePowDifficulty,
        )
    }
}

/**
 * "≈ 45 s per post on this device" — turns the abstract bit count into a cost
 * the user can feel. The hash rate is benchmarked once (~250 ms on a worker
 * thread) and cached; the figure is a statistical mean, so any single post can
 * be luckier or unluckier.
 */
@Composable
private fun PowTimeEstimate(difficulty: Int) {
    if (difficulty <= 0) return

    val estimate by
        produceState<String?>(initialValue = null, difficulty) {
            val rate = deviceHashesPerSecond()
            value = loadApproxDuration(PoWEstimator.estimateSeconds(difficulty, rate))
        }

    estimate?.let {
        Text(
            text = stringRes(Res.string.pow_difficulty_estimate, it),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PowCategoryChecklist(accountViewModel: AccountViewModel) {
    val difficulty by accountViewModel.account.settings.syncedSettings.proofOfWork
        .difficulty
        .collectAsStateWithLifecycle()
    val enabledCategories by accountViewModel.account.settings.syncedSettings.proofOfWork
        .enabledCategories
        .collectAsStateWithLifecycle()

    val miningOn = difficulty > 0

    SettingsControlRow(
        icon = MaterialSymbols.Checklist,
        title = stringRes(Res.string.pow_categories_title),
        description = stringRes(Res.string.pow_categories_explainer),
    ) {}

    PoWCategory.entries.forEach { category ->
        val checked = category in enabledCategories
        SettingsSubControlRow(
            title = stringRes(category.titleRes()),
            description = stringRes(category.descriptionRes()),
            enabled = miningOn,
        ) {
            Switch(
                checked = checked,
                enabled = miningOn,
                onCheckedChange = { accountViewModel.updatePowCategory(category, it) },
            )
        }
    }
}

private fun PoWCategory.titleRes(): StringResource =
    when (this) {
        PoWCategory.SHORT_NOTES -> Res.string.pow_category_short_notes
        PoWCategory.COMMENTS -> Res.string.pow_category_comments
        PoWCategory.REPORTS -> Res.string.pow_category_reports
        PoWCategory.LONG_FORM -> Res.string.pow_category_long_form
        PoWCategory.VOICE -> Res.string.pow_category_voice
        PoWCategory.REPOSTS -> Res.string.pow_category_reposts
        PoWCategory.REACTIONS -> Res.string.pow_category_reactions
        PoWCategory.PUBLIC_CHAT -> Res.string.pow_category_public_chat
        PoWCategory.GIFT_WRAPS -> Res.string.pow_category_gift_wraps
        PoWCategory.OTHER_PUBLIC -> Res.string.pow_category_other_public
    }

private fun PoWCategory.descriptionRes(): StringResource =
    when (this) {
        PoWCategory.SHORT_NOTES -> Res.string.pow_category_short_notes_explainer
        PoWCategory.COMMENTS -> Res.string.pow_category_comments_explainer
        PoWCategory.REPORTS -> Res.string.pow_category_reports_explainer
        PoWCategory.LONG_FORM -> Res.string.pow_category_long_form_explainer
        PoWCategory.VOICE -> Res.string.pow_category_voice_explainer
        PoWCategory.REPOSTS -> Res.string.pow_category_reposts_explainer
        PoWCategory.REACTIONS -> Res.string.pow_category_reactions_explainer
        PoWCategory.PUBLIC_CHAT -> Res.string.pow_category_public_chat_explainer
        PoWCategory.GIFT_WRAPS -> Res.string.pow_category_gift_wraps_explainer
        PoWCategory.OTHER_PUBLIC -> Res.string.pow_category_other_public_explainer
    }

@Composable
private fun SignatureTile(
    accountViewModel: AccountViewModel,
    appWideSignature: MutableStateFlow<String>,
) {
    // The account's own signature; until it sets one, the app-wide signature it used before applies.
    val accountSignature by accountViewModel.account.settings.composeSignature
        .collectAsStateWithLifecycle()
    val appWide by appWideSignature.collectAsState()
    val value = accountSignature ?: appWide

    SettingsBlockTile(
        icon = MaterialSymbols.EditNote,
        title = stringRes(Res.string.compose_signature_setting_title),
        description = stringRes(Res.string.compose_signature_setting_description),
    ) {
        SpellCheckedOutlinedTextField(
            value = value,
            onValueChange = accountViewModel::updateComposeSignature,
            placeholder = { Text(stringRes(Res.string.compose_signature_setting_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AddClientTagTile(accountViewModel: AccountViewModel) {
    val addClientTag by accountViewModel.account.settings.syncedSettings.security
        .addClientTag
        .collectAsStateWithLifecycle()

    SwitchTile(
        icon = MaterialSymbols.Code,
        title = Res.string.add_client_tag_title,
        description = Res.string.add_client_tag_explainer,
        checked = addClientTag,
        onCheckedChange = accountViewModel::updateAddClientTag,
    )
}

@Composable
private fun BooleanSwitchTile(
    flow: MutableStateFlow<BooleanType>,
    icon: MaterialSymbol,
    title: StringResource,
    description: StringResource,
) {
    val value by flow.collectAsState()

    SwitchTile(
        icon = icon,
        title = title,
        description = description,
        checked = value == BooleanType.ALWAYS,
        onCheckedChange = { isOn ->
            flow.tryEmit(if (isOn) BooleanType.ALWAYS else BooleanType.NEVER)
        },
    )
}

@Composable
private fun SwitchTile(
    icon: MaterialSymbol,
    title: StringResource,
    description: StringResource,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsControlRow(
        icon = icon,
        title = stringRes(title),
        description = stringRes(description),
        onClick = { onCheckedChange(!checked) },
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun ComposeSettingsScreenPreview() {
    ThemeComparisonColumn {
        ComposeSettingsContent(UiSettingsFlow(), mockAccountViewModel())
    }
}
