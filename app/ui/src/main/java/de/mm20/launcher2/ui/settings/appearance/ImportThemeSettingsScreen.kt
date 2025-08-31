package de.mm20.launcher2.ui.settings.appearance

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChangeCircle
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.SearchBar
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.settings.transparencies.checkerboard
import de.mm20.launcher2.ui.settings.typography.PreviewTexts
import de.mm20.launcher2.ui.theme.colorscheme.darkColorSchemeOf
import de.mm20.launcher2.ui.theme.colorscheme.lightColorSchemeOf
import de.mm20.launcher2.ui.theme.shapes.shapesOf
import de.mm20.launcher2.ui.theme.transparency.LocalTransparencyScheme
import de.mm20.launcher2.ui.theme.transparency.transparency
import de.mm20.launcher2.ui.theme.transparency.transparencySchemeOf
import de.mm20.launcher2.ui.theme.typography.typographyOf
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class ImportThemeSettingsRoute(val fromUri: String)

@Composable
fun ImportThemeSettingsScreen(
    fromUri: Uri,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val viewModel: ImportThemeSettingsScreenVM = viewModel()

    val scope = rememberCoroutineScope()

    val themeBundle = viewModel.themeBundle

    LaunchedEffect(fromUri) {
        viewModel.init(context, fromUri)
    }

    PreferenceScreen(title = stringResource(R.string.theme_import_title)) {

        if (viewModel.error) {
            item {
                PreferenceCategory {
                    Banner(
                        text = stringResource(R.string.import_theme_error),
                        icon = Icons.Rounded.ErrorOutline,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp),
                    )
                }
            }
        } else if (themeBundle != null) {
            item {
                PreferenceCategory {
                    Preference(
                        title = themeBundle.name,
                        summary = themeBundle.author?.takeIf { it.isNotBlank() })
                }
            }
            item {
                val isDarkMode = LocalDarkTheme.current
                var darkModePreview by remember { mutableStateOf(isDarkMode) }
                MaterialTheme(
                    colorScheme = themeBundle.colors?.let {
                        if (darkModePreview) darkColorSchemeOf(it) else lightColorSchemeOf(it)
                    } ?: MaterialTheme.colorScheme,
                    shapes = themeBundle.shapes?.let { shapesOf(it) } ?: MaterialTheme.shapes,
                    typography = themeBundle.typography?.let { typographyOf(it) } ?: MaterialTheme.typography,
                ) {
                    val transparencies = themeBundle.transparencies?.let { transparencySchemeOf(it) } ?: MaterialTheme.transparency
                    CompositionLocalProvider(
                        LocalTransparencyScheme provides transparencies
                    ) {
                        ThemePreview(
                            darkMode = darkModePreview,
                            onDarkModeChanged = { darkModePreview = it }
                        )
                    }
                }
            }
            item {
                PreferenceCategory(stringResource(R.string.import_theme_contents)) {
                    if (themeBundle.colors != null) {
                        Preference(
                            icon = Icons.Rounded.Palette,
                            title = stringResource(R.string.preference_screen_colors),
                            summary = themeBundle.colors?.name,
                            controls = if (viewModel.colorsExists) {
                                {
                                    Icon(
                                        Icons.Rounded.ChangeCircle,
                                        stringResource(R.string.import_theme_exists)
                                    )
                                }
                            } else null,
                        )
                    }
                    if (themeBundle.typography != null) {
                        Preference(
                            icon = Icons.Rounded.TextFields,
                            title = stringResource(R.string.preference_screen_typography),
                            summary = themeBundle.typography?.name,
                            controls = if (viewModel.typographyExists) {
                                {
                                    Icon(
                                        Icons.Rounded.ChangeCircle,
                                        stringResource(R.string.import_theme_exists)
                                    )
                                }
                            } else null,
                        )
                    }
                    if (themeBundle.shapes != null) {
                        Preference(
                            icon = Icons.Rounded.CropSquare,
                            title = stringResource(R.string.preference_screen_shapes),
                            summary = themeBundle.shapes?.name,
                            controls = if (viewModel.shapesExists) {
                                {
                                    Icon(
                                        Icons.Rounded.ChangeCircle,
                                        stringResource(R.string.import_theme_exists)
                                    )
                                }
                            } else null,
                        )
                    }
                    if (themeBundle.transparencies != null) {
                    Preference(
                        icon = Icons.Rounded.Opacity,
                        title = stringResource(R.string.preference_screen_transparencies),
                        summary = themeBundle.transparencies?.name,
                        controls = if (viewModel.transparenciesExists) {
                            {
                                Icon(
                                    Icons.Rounded.ChangeCircle,
                                    stringResource(R.string.import_theme_exists)
                                )
                            }
                        } else null,
                    )
                }
                    if (viewModel.colorsExists || viewModel.shapesExists) {
                        Banner(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.shapes.extraSmall
                                )
                                .padding(16.dp),
                            icon = Icons.Rounded.ChangeCircle,
                            text = stringResource(R.string.import_theme_exists)
                        )
                    }
                }
            }
            item {
                PreferenceCategory {
                    SwitchPreference(
                        title = stringResource(R.string.import_theme_apply),
                        value = viewModel.applyTheme,
                        onValueChanged = { viewModel.applyTheme = it },
                    )
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.shapes.extraSmall
                            )
                            .padding(16.dp),
                        enabled = !viewModel.loading,
                        onClick = {
                            scope.launch {
                                viewModel.import()?.join()
                                activity?.onBackPressed()
                            }

                        }
                    ) {
                        Text(stringResource(R.string.action_import))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePreview(
    darkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
) {
    val previewTexts = PreviewTexts()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .checkerboard(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.onPrimaryContainer,
                12.dp,
            )
            .background(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MaterialTheme.transparency.background),
                MaterialTheme.shapes.medium
            )
            .innerShadow(
                MaterialTheme.shapes.medium,
            ) {
                color = Color(0f, 0f, 0f, 0.2f)
                radius = with(density) { 8.dp.toPx() }
            }
    ) {
        SearchBar(
            style = SearchBarStyle.Solid,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp),
            level = SearchBarLevel.Raised,
            value = "",
            onValueChange = {},
            readOnly = true,
            menu = {
                IconButton(onClick = {}) {
                    Icon(Icons.Rounded.MoreVert, null)
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = MaterialTheme.transparency.surface),
                    MaterialTheme.shapes.medium
                )
                .padding(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.shapes.small
                        ),
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        previewTexts.Medium1,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        previewTexts.Medium2,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        previewTexts.TwoLines,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = true,
                    label = { Text(previewTexts.Short1) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Star, null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    },
                    onClick = { },
                )
                FilterChip(
                    selected = false,
                    label = { Text(previewTexts.Short2) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Star, null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    },
                    onClick = { },
                )
                Spacer(modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = {}) {
                    Icon(Icons.Rounded.Edit, null)
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Button(onClick = {}) { Text(previewTexts.Medium1) }
                OutlinedButton(onClick = {}) { Text(previewTexts.Medium2) }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    selected = !darkMode,
                    onClick = { onDarkModeChanged(false) }
                ) {
                    Icon(Icons.Rounded.LightMode, null)
                }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    selected = darkMode,
                    onClick = { onDarkModeChanged(true) }
                ) {
                    Icon(Icons.Rounded.DarkMode, null)
                }
            }
        }
    }
}

@Preview
@Composable
private fun ThemePreviewPreview() {
    ThemePreview(
        darkMode = false,
        onDarkModeChanged = {}
    )
}