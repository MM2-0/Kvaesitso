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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.appupdate.ui.AppUpdateSettingsSection
import com.vitorpamplona.amethyst.commons.appupdate.ui.LocalAppUpdates
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.BooleanType
import com.vitorpamplona.amethyst.commons.model.ChatLayoutType
import com.vitorpamplona.amethyst.commons.model.ConnectivityType
import com.vitorpamplona.amethyst.commons.model.FeatureSetType
import com.vitorpamplona.amethyst.commons.model.FontFamilyType
import com.vitorpamplona.amethyst.commons.model.FontSizeType
import com.vitorpamplona.amethyst.commons.model.NavigationLayoutType
import com.vitorpamplona.amethyst.commons.model.NoteLayoutType
import com.vitorpamplona.amethyst.commons.model.ThemeType
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.model.resourceId
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.application_preferences
import com.vitorpamplona.amethyst.commons.resources.automatically_download_translation_models
import com.vitorpamplona.amethyst.commons.resources.automatically_download_translation_models_description
import com.vitorpamplona.amethyst.commons.resources.automatically_hide_nav_bars
import com.vitorpamplona.amethyst.commons.resources.automatically_hide_nav_bars_description
import com.vitorpamplona.amethyst.commons.resources.automatically_load_images_gifs
import com.vitorpamplona.amethyst.commons.resources.automatically_load_images_gifs_description
import com.vitorpamplona.amethyst.commons.resources.automatically_play_videos
import com.vitorpamplona.amethyst.commons.resources.automatically_play_videos_description
import com.vitorpamplona.amethyst.commons.resources.automatically_show_profile_picture
import com.vitorpamplona.amethyst.commons.resources.automatically_show_profile_picture_description
import com.vitorpamplona.amethyst.commons.resources.automatically_show_url_preview
import com.vitorpamplona.amethyst.commons.resources.automatically_show_url_preview_description
import com.vitorpamplona.amethyst.commons.resources.autoplay_videos
import com.vitorpamplona.amethyst.commons.resources.autoplay_videos_description
import com.vitorpamplona.amethyst.commons.resources.chat_layout
import com.vitorpamplona.amethyst.commons.resources.chat_layout_description
import com.vitorpamplona.amethyst.commons.resources.connectivity_type_always
import com.vitorpamplona.amethyst.commons.resources.connectivity_type_never
import com.vitorpamplona.amethyst.commons.resources.connectivity_type_unmetered_wifi_only_short
import com.vitorpamplona.amethyst.commons.resources.font_family
import com.vitorpamplona.amethyst.commons.resources.font_family_description
import com.vitorpamplona.amethyst.commons.resources.font_family_monospace_short
import com.vitorpamplona.amethyst.commons.resources.font_family_sans_serif_short
import com.vitorpamplona.amethyst.commons.resources.font_family_serif_short
import com.vitorpamplona.amethyst.commons.resources.font_family_system_short
import com.vitorpamplona.amethyst.commons.resources.font_size
import com.vitorpamplona.amethyst.commons.resources.font_size_description
import com.vitorpamplona.amethyst.commons.resources.language
import com.vitorpamplona.amethyst.commons.resources.language_description
import com.vitorpamplona.amethyst.commons.resources.navigation_layout
import com.vitorpamplona.amethyst.commons.resources.navigation_layout_deck
import com.vitorpamplona.amethyst.commons.resources.navigation_layout_description
import com.vitorpamplona.amethyst.commons.resources.navigation_layout_stack
import com.vitorpamplona.amethyst.commons.resources.navigation_layout_tabs
import com.vitorpamplona.amethyst.commons.resources.navigation_layout_windows
import com.vitorpamplona.amethyst.commons.resources.note_layout
import com.vitorpamplona.amethyst.commons.resources.note_layout_description
import com.vitorpamplona.amethyst.commons.resources.settings_section_appearance
import com.vitorpamplona.amethyst.commons.resources.settings_section_general
import com.vitorpamplona.amethyst.commons.resources.settings_section_media
import com.vitorpamplona.amethyst.commons.resources.theme
import com.vitorpamplona.amethyst.commons.resources.theme_description
import com.vitorpamplona.amethyst.commons.resources.ui_feature_set_type_complete_short
import com.vitorpamplona.amethyst.commons.resources.ui_feature_set_type_performance_short
import com.vitorpamplona.amethyst.commons.resources.ui_feature_set_type_simplified_short
import com.vitorpamplona.amethyst.commons.resources.ui_style
import com.vitorpamplona.amethyst.commons.resources.ui_style_description
import com.vitorpamplona.amethyst.commons.resources.xwayland_rendering
import com.vitorpamplona.amethyst.commons.resources.xwayland_rendering_description
import com.vitorpamplona.amethyst.commons.resources.xwayland_rendering_restart
import com.vitorpamplona.amethyst.commons.ui.components.LocalTranslationPlatform
import com.vitorpamplona.amethyst.commons.ui.components.SpinnerSelectionDialog
import com.vitorpamplona.amethyst.commons.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.DisplayServerChoice
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SegmentedChoiceTile
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsBlockTile
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsControlRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.ui.theme.toFontFamily
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.StringResource

@Composable
fun SettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = Res.string.application_preferences), nav)
        },
    ) {
        SettingsScreen(accountViewModel.settings.uiSettingsFlow, Modifier.padding(it))
    }
}

// Full-screen preview: the real top bar + the redesigned content, shown side by side in dark and
// light so both grounds (and the card-hairline contrast) can be eyeballed at once.
@Preview(name = "UI Preferences", device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun SettingsScreenPreview() {
    ThemeComparisonRow {
        Scaffold(
            topBar = {
                TopBarWithBackButton(stringRes(id = Res.string.application_preferences), EmptyNav())
            },
        ) { padding ->
            SettingsScreen(UiSettingsFlow(), Modifier.padding(padding))
        }
    }
}

@Composable
fun SettingsScreen(
    sharedPrefs: UiSettingsFlow,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsSection(Res.string.settings_section_appearance) {
            ThemeTile(sharedPrefs)
            SettingsDivider()
            AccentColorTile(sharedPrefs.accentColor, accountSettings = null)
            SettingsDivider()
            FontFamilyTile(sharedPrefs)
            SettingsDivider()
            FontSizeTile(sharedPrefs)
        }

        SettingsSection(Res.string.settings_section_media) {
            ImagePreviewTile(sharedPrefs)
            SettingsDivider()
            VideoPlaybackTile(sharedPrefs)
            SettingsDivider()
            AutoplayVideosTile(sharedPrefs)
            SettingsDivider()
            UrlPreviewTile(sharedPrefs)
            SettingsDivider()
            ProfilePictureTile(sharedPrefs)
            if (LocalTranslationPlatform.current.downloadsModels) {
                SettingsDivider()
                TranslationModelsTile(sharedPrefs)
            }
        }

        SettingsSection(Res.string.settings_section_general) {
            LanguageTile(sharedPrefs)
            SettingsDivider()
            UiModeTile(sharedPrefs)
            SettingsDivider()
            NoteLayoutTile(sharedPrefs)
            SettingsDivider()
            ChatLayoutTile(sharedPrefs)
            SettingsDivider()
            ImmersiveScrollingTile(sharedPrefs)
            val layouts = LocalAppPlatform.current.navigationLayouts
            if (layouts.size > 1) {
                SettingsDivider()
                NavigationLayoutTile(sharedPrefs, layouts)
            }
            LocalAppPlatform.current.displayServerChoice?.let {
                SettingsDivider()
                XWaylandTile(it)
            }
        }

        LocalAppUpdates.current?.let { AppUpdateSettingsSection(it) }
    }
}

/** A [SettingsControlRow] with a [Switch] backed by a [BooleanType] flow (ALWAYS ⇔ on). */
@Composable
private fun BooleanSwitchTile(
    flow: MutableStateFlow<BooleanType>,
    icon: MaterialSymbol,
    title: StringResource,
    description: StringResource,
) {
    val value by flow.collectAsState()
    val checked = value == BooleanType.ALWAYS
    // Explicit Unit return: tryEmit returns Boolean, so the lambda would otherwise infer as
    // (Boolean) -> Boolean and not fit Switch.onCheckedChange / SettingsControlRow.onClick.
    val toggle: (Boolean) -> Unit = { isOn -> flow.tryEmit(if (isOn) BooleanType.ALWAYS else BooleanType.NEVER) }

    SettingsControlRow(
        icon = icon,
        title = stringRes(title),
        description = stringRes(description),
        onClick = { toggle(!checked) },
    ) {
        Switch(checked = checked, onCheckedChange = toggle)
    }
}

@Composable
private fun ThemeTile(sharedPrefs: UiSettingsFlow) {
    val theme by sharedPrefs.theme.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.BrightnessMedium,
        title = Res.string.theme,
        description = Res.string.theme_description,
        options = ThemeType.entries,
        labelRes = { it.resourceId },
        selected = theme,
        onSelect = { sharedPrefs.theme.tryEmit(it) },
    )
}

/**
 * The connectivity choices this platform can honour. "Wi-Fi" only differs from "Always" where the
 * platform detects metered connections; elsewhere it is left out, unless it is the saved choice,
 * which the row must still show as selected.
 */
@Composable
private fun connectivityOptions(selected: ConnectivityType): List<ConnectivityType> =
    if (LocalAppPlatform.current.detectsMeteredConnections || selected == ConnectivityType.WIFI_ONLY) {
        ConnectivityType.entries
    } else {
        ConnectivityType.entries.filter { it != ConnectivityType.WIFI_ONLY }
    }

// Compact labels for the connectivity segmented rows — "Unmetered WiFi" does not fit a 3-up
// segment, so it collapses to "Wi-Fi"; the others are already short.
private fun ConnectivityType.shortLabelRes(): StringResource =
    when (this) {
        ConnectivityType.ALWAYS -> Res.string.connectivity_type_always
        ConnectivityType.WIFI_ONLY -> Res.string.connectivity_type_unmetered_wifi_only_short
        ConnectivityType.NEVER -> Res.string.connectivity_type_never
    }

private fun FontFamilyType.shortLabelRes(): StringResource =
    when (this) {
        FontFamilyType.SYSTEM -> Res.string.font_family_system_short
        FontFamilyType.SANS_SERIF -> Res.string.font_family_sans_serif_short
        FontFamilyType.SERIF -> Res.string.font_family_serif_short
        FontFamilyType.MONOSPACE -> Res.string.font_family_monospace_short
    }

private fun FeatureSetType.shortLabelRes(): StringResource =
    when (this) {
        FeatureSetType.COMPLETE -> Res.string.ui_feature_set_type_complete_short
        FeatureSetType.SIMPLIFIED -> Res.string.ui_feature_set_type_simplified_short
        FeatureSetType.PERFORMANCE -> Res.string.ui_feature_set_type_performance_short
    }

@Composable
private fun FontFamilyTile(sharedPrefs: UiSettingsFlow) {
    val fontFamily by sharedPrefs.fontFamily.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.Description,
        title = Res.string.font_family,
        description = Res.string.font_family_description,
        options = FontFamilyType.entries,
        labelRes = { it.shortLabelRes() },
        selected = fontFamily,
        onSelect = { sharedPrefs.fontFamily.tryEmit(it) },
        // Draw each option's name in the very typeface it selects.
        optionTextStyle = { LocalTextStyle.current.copy(fontFamily = it.toFontFamily() ?: FontFamily.Default) },
    )
}

@Composable
private fun FontSizeTile(sharedPrefs: UiSettingsFlow) {
    val fontSize by sharedPrefs.fontSize.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.ZoomOutMap,
        title = Res.string.font_size,
        description = Res.string.font_size_description,
        options = FontSizeType.entries,
        labelRes = { it.resourceId },
        selected = fontSize,
        onSelect = { sharedPrefs.fontSize.tryEmit(it) },
        // Scale each label to preview its size — Small looks small, Huge looks huge. A small 13sp
        // base keeps the spread gentle (~11–17sp) so the row stays tidy instead of lurching taller.
        optionTextStyle = { LocalTextStyle.current.copy(fontSize = 13.sp * it.scale) },
    )
}

@Composable
private fun UiModeTile(sharedPrefs: UiSettingsFlow) {
    val featureSet by sharedPrefs.featureSet.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.Tune,
        title = Res.string.ui_style,
        description = Res.string.ui_style_description,
        options = FeatureSetType.entries,
        labelRes = { it.shortLabelRes() },
        selected = featureSet,
        onSelect = { sharedPrefs.featureSet.tryEmit(it) },
    )
}

@Composable
private fun NoteLayoutTile(sharedPrefs: UiSettingsFlow) {
    val noteLayout by sharedPrefs.noteLayout.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.AutoMirrored.Feed,
        title = Res.string.note_layout,
        description = Res.string.note_layout_description,
        options = NoteLayoutType.entries,
        labelRes = { it.resourceId },
        selected = noteLayout,
        onSelect = { sharedPrefs.noteLayout.tryEmit(it) },
    )
}

@Composable
private fun ChatLayoutTile(sharedPrefs: UiSettingsFlow) {
    val chatLayout by sharedPrefs.chatLayout.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.AutoMirrored.Chat,
        title = Res.string.chat_layout,
        description = Res.string.chat_layout_description,
        options = ChatLayoutType.entries,
        labelRes = { it.resourceId },
        selected = chatLayout,
        onSelect = { sharedPrefs.chatLayout.tryEmit(it) },
    )
}

@Composable
private fun ImagePreviewTile(sharedPrefs: UiSettingsFlow) {
    val value by sharedPrefs.automaticallyShowImages.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.Image,
        title = Res.string.automatically_load_images_gifs,
        description = Res.string.automatically_load_images_gifs_description,
        options = connectivityOptions(value),
        labelRes = { it.shortLabelRes() },
        selected = value,
        onSelect = { sharedPrefs.automaticallyShowImages.tryEmit(it) },
    )
}

@Composable
private fun VideoPlaybackTile(sharedPrefs: UiSettingsFlow) {
    val value by sharedPrefs.automaticallyStartPlayback.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.Videocam,
        title = Res.string.automatically_play_videos,
        description = Res.string.automatically_play_videos_description,
        options = connectivityOptions(value),
        labelRes = { it.shortLabelRes() },
        selected = value,
        onSelect = { sharedPrefs.automaticallyStartPlayback.tryEmit(it) },
    )
}

@Composable
private fun UrlPreviewTile(sharedPrefs: UiSettingsFlow) {
    val value by sharedPrefs.automaticallyShowUrlPreview.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.Link,
        title = Res.string.automatically_show_url_preview,
        description = Res.string.automatically_show_url_preview_description,
        options = connectivityOptions(value),
        labelRes = { it.shortLabelRes() },
        selected = value,
        onSelect = { sharedPrefs.automaticallyShowUrlPreview.tryEmit(it) },
    )
}

@Composable
private fun TranslationModelsTile(sharedPrefs: UiSettingsFlow) {
    val value by sharedPrefs.automaticallyDownloadTranslationModels.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.Translate,
        title = Res.string.automatically_download_translation_models,
        description = Res.string.automatically_download_translation_models_description,
        options = connectivityOptions(value),
        labelRes = { it.shortLabelRes() },
        selected = value,
        onSelect = { sharedPrefs.automaticallyDownloadTranslationModels.tryEmit(it) },
    )
}

@Composable
private fun ProfilePictureTile(sharedPrefs: UiSettingsFlow) {
    val value by sharedPrefs.automaticallyShowProfilePictures.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.AccountCircle,
        title = Res.string.automatically_show_profile_picture,
        description = Res.string.automatically_show_profile_picture_description,
        options = connectivityOptions(value),
        labelRes = { it.shortLabelRes() },
        selected = value,
        onSelect = { sharedPrefs.automaticallyShowProfilePictures.tryEmit(it) },
    )
}

@Composable
private fun AutoplayVideosTile(sharedPrefs: UiSettingsFlow) {
    BooleanSwitchTile(
        flow = sharedPrefs.automaticallyPlayVideos,
        icon = MaterialSymbols.PlayCircle,
        title = Res.string.autoplay_videos,
        description = Res.string.autoplay_videos_description,
    )
}

@Composable
private fun ImmersiveScrollingTile(sharedPrefs: UiSettingsFlow) {
    BooleanSwitchTile(
        flow = sharedPrefs.automaticallyHideNavigationBars,
        icon = MaterialSymbols.Fullscreen,
        title = Res.string.automatically_hide_nav_bars,
        description = Res.string.automatically_hide_nav_bars_description,
    )
}

@Composable
private fun NavigationLayoutTile(
    sharedPrefs: UiSettingsFlow,
    layouts: List<NavigationLayoutType>,
) {
    val layout by sharedPrefs.navigationLayout.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.Dashboard,
        title = Res.string.navigation_layout,
        description = Res.string.navigation_layout_description,
        options = layouts,
        labelRes = {
            when (it) {
                NavigationLayoutType.STACK -> Res.string.navigation_layout_stack
                NavigationLayoutType.DECK -> Res.string.navigation_layout_deck
                NavigationLayoutType.TABS -> Res.string.navigation_layout_tabs
                NavigationLayoutType.WINDOWS -> Res.string.navigation_layout_windows
            }
        },
        selected = layout,
        onSelect = { sharedPrefs.navigationLayout.tryEmit(it) },
    )
}

@Composable
private fun XWaylandTile(choice: DisplayServerChoice) {
    val prefer by choice.preferXWayland.collectAsState()
    SettingsControlRow(
        icon = MaterialSymbols.DesktopWindows,
        title = stringRes(Res.string.xwayland_rendering),
        // Read once at startup: say so while the switch and the running app disagree.
        description = stringRes(if (prefer != choice.usingXWayland) Res.string.xwayland_rendering_restart else Res.string.xwayland_rendering_description),
        onClick = { choice.setPreferXWayland(!prefer) },
    ) {
        Switch(checked = prefer, onCheckedChange = choice::setPreferXWayland)
    }
}

// Language can't be a segmented row (50+ locales), so it reads as a disclosure row — icon + title +
// the current language on the right + a chevron — and tapping anywhere opens the picker dialog. That
// matches the app's other "opens a picker" rows instead of leaving a lone text-field dropdown here.
@Composable
private fun LanguageTile(sharedPrefs: UiSettingsFlow) {
    val languageEntries = LocalAppPlatform.current.rememberAppLanguages()
    val languageList = remember { languageEntries.keys.map { TitleExplainer(it) }.toImmutableList() }

    val language by sharedPrefs.preferredLanguage.collectAsState()
    val languageIndex = getLanguageIndex(languageEntries, language)
    val currentLabel = languageList.getOrNull(languageIndex)?.title ?: ""

    var showPicker by remember { mutableStateOf(false) }

    SettingsControlRow(
        icon = MaterialSymbols.Language,
        title = stringRes(Res.string.language),
        description = stringRes(Res.string.language_description),
        onClick = { showPicker = true },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            Icon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
            )
        }
    }

    if (showPicker) {
        SpinnerSelectionDialog(
            options = languageList,
            onDismiss = { showPicker = false },
        ) { index ->
            showPicker = false
            sharedPrefs.preferredLanguage.tryEmit(languageEntries[languageList[index].title])
        }
    }
}

fun getLanguageIndex(
    languageEntries: ImmutableMap<String, String>,
    language: String?,
): Int {
    var languageIndex: Int
    languageIndex =
        if (language != null) {
            languageEntries.values.toTypedArray().indexOf(language)
        } else {
            languageEntries.values.toTypedArray().indexOf(Locale.current.toLanguageTag())
        }
    if (languageIndex == -1) {
        languageIndex = languageEntries.values.toTypedArray().indexOf(Locale.current.language)
    }
    if (languageIndex == -1) languageIndex = languageEntries.values.toTypedArray().indexOf("en")
    return languageIndex
}
