package de.mm20.launcher2.ui.settings.typography

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.serialization.UUIDSerializer
import de.mm20.launcher2.themes.typography.DefaultEmphasizedTextStyles
import de.mm20.launcher2.themes.typography.DefaultTextStyles
import de.mm20.launcher2.themes.typography.FontManager
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.theme.typography.fontFamilyOf
import de.mm20.launcher2.ui.theme.typography.typographyOf
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import de.mm20.launcher2.themes.typography.FontFamily as ThemeFontFamily
import de.mm20.launcher2.themes.typography.FontWeight as ThemeFontWeight
import de.mm20.launcher2.themes.typography.TextStyle as ThemeTextStyle

@Serializable
data class TypographySettingsRoute(
    @Serializable(with = UUIDSerializer::class) val id: UUID
) : NavKey

@Composable
fun TypographySettingsScreen(themeId: UUID) {
    val viewModel: TypographySettingsScreenVM = viewModel()

    val context = LocalContext.current

    val theme by remember(
        viewModel,
        themeId
    ) { viewModel.getTypography(themeId) }.collectAsStateWithLifecycle(null)

    val previewTypography = theme?.let { typographyOf(it) }
    val previewTexts = PreviewTexts()

    var editName by remember { mutableStateOf(false) }

    if (editName) {
        var name by remember(theme) { mutableStateOf(theme?.name ?: "") }
        AlertDialog(
            onDismissRequest = { editName = false },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateTypography(theme!!.copy(name = name))
                        editName = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        )
    }

    PreferenceScreen(
        title = {
            Text(
                theme?.name ?: "",
                modifier = Modifier.clickable {
                    editName = true
                },
            )
        },
        helpUrl = "https://kvaesitso.mm20.de/docs/user-guide/customization/color-schemes",
    ) {
        if (theme == null || previewTypography == null) return@PreferenceScreen

        item {
            PreferenceCategory(title = stringResource(R.string.preference_typography_fonts)) {
                FontPreference(title = "Brand", theme!!.fonts["brand"], onValueChange = {
                    viewModel.updateTypography(
                        theme!!.copy(
                            fonts = theme!!.fonts.toMutableMap().apply { put("brand", it) })
                    )
                })
                FontPreference(
                    title = "Plain",
                    theme!!.fonts["plain"],
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                fonts = theme!!.fonts.toMutableMap().apply { put("plain", it) })
                        )
                    }
                )
            }
        }
        item {
            PreferenceCategory("Body") {
                TypographyPreview(previewTypography, previewTexts) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ShapedLauncherIcon(
                            size = 48.dp,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            previewTexts.Medium1,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                TextStylePreference(
                    title = "Body Small",
                    textStyle = previewTypography.bodySmall,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.bodySmall,
                    defaultValue = DefaultTextStyles.bodySmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(bodySmall = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Body Medium",
                    textStyle = previewTypography.bodyMedium,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.bodyMedium,
                    defaultValue = DefaultTextStyles.bodyMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(bodyMedium = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Body Large",
                    textStyle = previewTypography.bodyLarge,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.bodyLarge,
                    defaultValue = DefaultTextStyles.bodyLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(bodyLarge = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Body Small Emphasized",
                    textStyle = previewTypography.bodySmallEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.bodySmall,
                    parentValue = theme!!.styles.bodySmall,
                    defaultValue = DefaultEmphasizedTextStyles.bodySmall,
                    defaultValueParent = DefaultTextStyles.bodySmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(bodySmall = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Body Medium Emphasized",
                    textStyle = previewTypography.bodyMediumEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.bodyMedium,
                    parentValue = theme!!.styles.bodyMedium,
                    defaultValue = DefaultEmphasizedTextStyles.bodyMedium,
                    defaultValueParent = DefaultTextStyles.bodyMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(bodyMedium = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Body Large Emphasized",
                    textStyle = previewTypography.bodyLargeEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.bodyLarge,
                    parentValue = theme!!.styles.bodyLarge,
                    defaultValue = DefaultEmphasizedTextStyles.bodyLarge,
                    defaultValueParent = DefaultTextStyles.bodyLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(bodyLarge = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
            }
        }
        item {
            PreferenceCategory("Label") {
                TypographyPreview(previewTypography, previewTexts) {
                    FilterChip(
                        modifier = Modifier
                            .padding(end = 16.dp),
                        label = {
                            Text(
                                previewTexts.Short1
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.tag_20px),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        },
                        selected = false,
                        onClick = {},
                    )
                    Button(onClick = {}) {
                        Text(previewTexts.Medium2)
                    }
                }
                TextStylePreference(
                    title = "Label Small",
                    textStyle = previewTypography.labelSmall,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.labelSmall,
                    defaultValue = DefaultTextStyles.labelSmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(labelSmall = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Label Medium",
                    textStyle = previewTypography.labelMedium,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.labelMedium,
                    defaultValue = DefaultTextStyles.labelMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(labelMedium = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Label Large",
                    textStyle = previewTypography.labelLarge,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.labelLarge,
                    defaultValue = DefaultTextStyles.labelLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(labelLarge = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Label Small Emphasized",
                    textStyle = previewTypography.labelSmallEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.labelSmall,
                    parentValue = theme!!.styles.labelSmall,
                    defaultValue = DefaultEmphasizedTextStyles.labelSmall,
                    defaultValueParent = DefaultTextStyles.labelSmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(labelSmall = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Label Medium Emphasized",
                    textStyle = previewTypography.labelMediumEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.labelMedium,
                    parentValue = theme!!.styles.labelMedium,
                    defaultValue = DefaultEmphasizedTextStyles.labelMedium,
                    defaultValueParent = DefaultTextStyles.labelMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(labelMedium = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Label Large Emphasized",
                    textStyle = previewTypography.labelLargeEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.labelLarge,
                    parentValue = theme!!.styles.labelLarge,
                    defaultValue = DefaultEmphasizedTextStyles.labelLarge,
                    defaultValueParent = DefaultTextStyles.labelLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(labelLarge = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
            }
        }
        item {
            PreferenceCategory("Title") {
                TypographyPreview(previewTypography, previewTexts) {
                    PrimaryTabRow(0, modifier = Modifier.width(300.dp)) {
                        LeadingIconTab(
                            selected = true,
                            text = { Text(previewTexts.Short1) },
                            icon = {
                                Icon(
                                    painterResource(R.drawable.person_24px_filled),
                                    contentDescription = null,
                                )
                            },
                            onClick = {}
                        )
                        LeadingIconTab(
                            selected = false,
                            text = { Text(previewTexts.Short2) },
                            icon = {
                                Icon(
                                    painterResource(R.drawable.enterprise_24px),
                                    contentDescription = null
                                )
                            },
                            onClick = {}
                        )
                    }
                }
                TextStylePreference(
                    title = "Title Small",
                    textStyle = previewTypography.titleSmall,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.titleSmall,
                    defaultValue = DefaultTextStyles.titleSmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(titleSmall = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Title Medium",
                    textStyle = previewTypography.titleMedium,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.titleMedium,
                    defaultValue = DefaultTextStyles.titleMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(titleMedium = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Title Large",
                    textStyle = previewTypography.titleLarge,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.titleLarge,
                    defaultValue = DefaultTextStyles.titleLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(titleLarge = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Title Small Emphasized",
                    textStyle = previewTypography.titleSmallEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.titleSmall,
                    parentValue = theme!!.styles.titleSmall,
                    defaultValue = DefaultEmphasizedTextStyles.titleSmall,
                    defaultValueParent = DefaultTextStyles.titleSmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(titleSmall = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Title Medium Emphasized",
                    textStyle = previewTypography.titleMediumEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.titleMedium,
                    parentValue = theme!!.styles.titleMedium,
                    defaultValue = DefaultEmphasizedTextStyles.titleMedium,
                    defaultValueParent = DefaultTextStyles.titleMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(titleMedium = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Title Large Emphasized",
                    textStyle = previewTypography.titleLargeEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.titleLarge,
                    parentValue = theme!!.styles.titleLarge,
                    defaultValue = DefaultEmphasizedTextStyles.titleLarge,
                    defaultValueParent = DefaultTextStyles.titleLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles = theme!!.emphasizedStyles.copy(titleLarge = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
            }
        }
        item {
            PreferenceCategory("Headline") {
                TextStylePreference(
                    title = "Headline Small",
                    textStyle = previewTypography.headlineSmall,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.headlineSmall,
                    defaultValue = DefaultTextStyles.headlineSmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(headlineSmall = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Headline Medium",
                    textStyle = previewTypography.headlineMedium,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.headlineMedium,
                    defaultValue = DefaultTextStyles.headlineMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(headlineMedium = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Headline Large",
                    textStyle = previewTypography.headlineLarge,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.headlineLarge,
                    defaultValue = DefaultTextStyles.headlineLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(headlineLarge = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Headline Small Emphasized",
                    textStyle = previewTypography.headlineSmallEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.headlineSmall,
                    parentValue = theme!!.styles.headlineSmall,
                    defaultValue = DefaultEmphasizedTextStyles.headlineSmall,
                    defaultValueParent = DefaultTextStyles.headlineSmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles =
                                    theme!!.emphasizedStyles.copy(headlineSmall = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Headline Medium Emphasized",
                    textStyle = previewTypography.headlineMediumEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.headlineMedium,
                    parentValue = theme!!.styles.headlineMedium,
                    defaultValue = DefaultEmphasizedTextStyles.headlineMedium,
                    defaultValueParent = DefaultTextStyles.headlineMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles =
                                    theme!!.emphasizedStyles.copy(headlineMedium = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Headline Large Emphasized",
                    textStyle = previewTypography.headlineLargeEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.headlineLarge,
                    parentValue = theme!!.styles.headlineLarge,
                    defaultValue = DefaultEmphasizedTextStyles.headlineLarge,
                    defaultValueParent = DefaultTextStyles.headlineLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles =
                                    theme!!.emphasizedStyles.copy(headlineLarge = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
            }
        }
        item {
            PreferenceCategory("Display") {
                TextStylePreference(
                    title = "Display Small",
                    textStyle = previewTypography.displaySmall,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.displaySmall,
                    defaultValue = DefaultTextStyles.displaySmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(displaySmall = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Display Medium",
                    textStyle = previewTypography.displayMedium,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.displayMedium,
                    defaultValue = DefaultTextStyles.displayMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(displayMedium = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Display Large",
                    textStyle = previewTypography.displayLarge,
                    fonts = theme!!.fonts,
                    value = theme!!.styles.displayLarge,
                    defaultValue = DefaultTextStyles.displayLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                styles = theme!!.styles.copy(displayLarge = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Display Small Emphasized",
                    textStyle = previewTypography.displaySmallEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.displaySmall,
                    parentValue = theme!!.styles.displaySmall,
                    defaultValue = DefaultEmphasizedTextStyles.displaySmall,
                    defaultValueParent = DefaultTextStyles.displaySmall,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles =
                                    theme!!.emphasizedStyles.copy(displaySmall = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Display Medium Emphasized",
                    textStyle = previewTypography.displayMediumEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.displayMedium,
                    parentValue = theme!!.styles.displayMedium,
                    defaultValue = DefaultEmphasizedTextStyles.displayMedium,
                    defaultValueParent = DefaultTextStyles.displayMedium,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles =
                                    theme!!.emphasizedStyles.copy(displayMedium = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
                TextStylePreference(
                    title = "Display Large Emphasized",
                    textStyle = previewTypography.displayLargeEmphasized,
                    fonts = theme!!.fonts,
                    value = theme!!.emphasizedStyles.displayLarge,
                    parentValue = theme!!.styles.displayLarge,
                    defaultValue = DefaultEmphasizedTextStyles.displayLarge,
                    defaultValueParent = DefaultTextStyles.displayLarge,
                    onValueChange = {
                        viewModel.updateTypography(
                            theme!!.copy(
                                emphasizedStyles =
                                    theme!!.emphasizedStyles.copy(displayLarge = it)
                            )
                        )
                    },
                    previewTexts = previewTexts,
                )
            }
        }
    }
}

@Composable
private fun FontPreference(
    title: String,
    value: ThemeFontFamily?,
    onValueChange: (ThemeFontFamily?) -> Unit = {},
) {
    val preview = PreviewTexts()
    val context = LocalContext.current
    val fontManager = FontManager(context)

    var showDialog by remember { mutableStateOf(false) }
    var showFontSettings by remember { mutableStateOf(false) }

    val fontVariantAxes = remember(value) {
        if (value is ThemeFontFamily.VariableFontFamily) {
            fontManager.getFontSettings(value)
        } else {
            emptyList()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.shapes.extraSmall
        )
    ) {
        Box(
            modifier = Modifier.weight(1f)
        ) {
            Preference(
                title = title,
                summary = getFontName(context, value),
                icon = {
                    Text(
                        text = preview.ExtraShort,
                        style = TextStyle(
                            fontFamily = remember(value) { fontFamilyOf(context, value) },
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center,
                        )
                    )
                },
                onClick = { showDialog = true },
                containerColor = Color.Transparent,
            )
        }
        if (fontVariantAxes.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            IconButton(
                modifier = Modifier.padding(horizontal = 12.dp),
                onClick = { showFontSettings = true }
            ) {
                Icon(painterResource(R.drawable.tune_24px), null)
            }
        }
    }

    DismissableBottomSheet(
        expanded = showDialog,
        onDismissRequest = { showDialog = false },
    ) {
        val fonts = remember { fontManager.getInstalledFonts() }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (fonts.builtIn.isNotEmpty()) {
                item {
                    FontPickerCategory(
                        preview.ExtraShort,
                        null,
                        fonts.builtIn,
                        onFontClick = {
                            onValueChange(it)
                            showDialog = false
                        })
                }
            }
            if (fonts.deviceDefault.isNotEmpty()) {
                item {
                    FontPickerCategory(
                        preview.ExtraShort,
                        stringResource(R.string.font_category_device_default),
                        fonts.deviceDefault,
                        onFontClick = {
                            onValueChange(it)
                            showDialog = false
                        })
                }
            }
            if (fonts.generic.isNotEmpty()) {
                item {
                    FontPickerCategory(
                        preview.ExtraShort,
                        stringResource(R.string.font_category_generic),
                        fonts.generic,
                        onFontClick = {
                            onValueChange(it)
                            showDialog = false
                        })
                }
            }
        }
    }

    if (value is ThemeFontFamily.VariableFontFamily) {
        DismissableBottomSheet(
            expanded = showFontSettings,
            onDismissRequest = {
                showFontSettings = false
            }
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .heightIn(min = 200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = preview.TwoLines,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = fontFamilyOf(context, value)
                    )
                }

                PreferenceCategory {
                    for (axis in fontVariantAxes) {
                        SliderPreference(
                            title = axis.label,
                            value = value.settings.get(axis.name) ?: axis.defaultValue,
                            onValueChanged = {
                                val map = value.settings.toMutableMap()
                                map[axis.name] = it

                                if (value is ThemeFontFamily.LauncherDefault) {
                                    onValueChange(value.copy(settings = map))
                                }
                            },
                            min = axis.range.start,
                            max = axis.range.endInclusive,
                            step = axis.step,
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun FontPickerCategory(
    previewText: String, categoryName: String?,
    fonts: List<ThemeFontFamily?>,
    onFontClick: (ThemeFontFamily?) -> Unit = {},
) {
    val context = LocalContext.current
    PreferenceCategory(categoryName) {
        for (font in fonts) {
            val f = remember(font) { fontFamilyOf(context, font) }
            Preference(
                title = {
                    Text(
                        getFontName(context, font),
                        fontFamily = f
                    )
                },
                icon = {
                    Text(
                        text = previewText,
                        style = TextStyle(
                            fontFamily = f,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center,
                        )
                    )
                },
                summary = getFontSummary(context, font)?.let { { Text(it, fontFamily = f) } },
                onClick = {
                    onFontClick(font)
                },
            )
        }
    }
}


private fun getFontName(context: Context, fontFamily: ThemeFontFamily?): String {
    return when (fontFamily) {
        is ThemeFontFamily.LauncherDefault -> "Google Sans Flex"
        is ThemeFontFamily.DeviceHeadline -> context.getString(R.string.font_name_device_headline)
        is ThemeFontFamily.DeviceBody -> context.getString(R.string.font_name_device_body)
        is ThemeFontFamily.System -> fontFamily.name
        is ThemeFontFamily.SansSerif -> "sans-serif"
        is ThemeFontFamily.Serif -> "serif"
        is ThemeFontFamily.Monospace -> "monospace"
        null -> "default"
    }
}

private fun getFontSummary(context: Context, fontFamily: ThemeFontFamily?): String? {
    return when (fontFamily) {
        is ThemeFontFamily.DeviceHeadline -> {
            val resId = context.resources
                .getIdentifier("config_headlineFontFamily", "string", "android")
            if (resId != 0) return context.getString(resId)
            return "sans-serif"
        }

        is ThemeFontFamily.DeviceBody -> {
            val resId = context.resources
                .getIdentifier("config_bodyFontFamily", "string", "android")
            if (resId != 0) return context.getString(resId)
            return "sans-serif"
        }

        else -> null
    }
}

@Composable
private fun SliderRow(
    @DrawableRes icon: Int,
    min: Float,
    max: Float,
    step: Float = 1f,
    value: Float,
    onValueChange: (Float) -> Unit,
    formatValue: (Float) -> String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Slider(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            steps = ((max - min) / step).roundToInt() - 1,
        )
        Text(
            modifier = Modifier.width(32.dp),
            text = formatValue(value),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TextStylePreference(
    title: String,
    textStyle: TextStyle,
    fonts: Map<String, ThemeFontFamily?>,
    value: ThemeTextStyle<ThemeFontWeight?>?,
    parentValue: ThemeTextStyle<ThemeFontWeight.Absolute?>? = null,
    defaultValue: ThemeTextStyle<ThemeFontWeight>?,
    defaultValueParent: ThemeTextStyle<ThemeFontWeight.Absolute?>? = null,
    onValueChange: (ThemeTextStyle<ThemeFontWeight.Absolute?>?) -> Unit = {},
    previewTexts: PreviewTexts,
) {
    val context = LocalContext.current

    var showDialog by remember { mutableStateOf(false) }

    Preference(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
        },
        icon = {
            Text(
                text = previewTexts.ExtraShort,
                style = textStyle,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
        },
        onClick = { showDialog = true },
    )
    var fontFamily by remember(value) {
        mutableStateOf(value?.fontFamily)
    }
    var fontSize by remember(value) {
        mutableStateOf(value?.fontSize)
    }
    var lineHeight by remember(value) {
        mutableStateOf(value?.lineHeight)
    }
    var weight by remember(value) {
        mutableStateOf((value?.fontWeight as? ThemeFontWeight.Absolute)?.weight)
    }
    var letterSpacing by remember(value) {
        mutableStateOf(value?.letterSpacing)
    }

    DismissableBottomSheet(
        expanded = showDialog,
        onDismissRequest = {
            onValueChange(
                ThemeTextStyle(
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    fontWeight = weight?.let { ThemeFontWeight.Absolute(it) },
                    letterSpacing = letterSpacing
                )
            )
            showDialog = false
        },
    ) {


        val actualFontFamily = fontFamily
            ?: parentValue?.fontFamily
            ?: defaultValue?.fontFamily
            ?: defaultValueParent?.fontFamily!!
        val actualFontSize = fontSize
            ?: parentValue?.fontSize
            ?: defaultValue?.fontSize
            ?: defaultValueParent?.fontSize!!
        val actualLineHeight = lineHeight
            ?: parentValue?.lineHeight
            ?: defaultValue?.lineHeight
            ?: defaultValueParent?.lineHeight!!
        val actualWeight = when {
            weight != null -> FontWeight(weight!!)
            parentValue?.fontWeight != null -> FontWeight(parentValue.fontWeight!!.weight)
            defaultValue?.fontWeight is ThemeFontWeight.Absolute -> FontWeight((defaultValue.fontWeight as ThemeFontWeight.Absolute).weight)
            defaultValue?.fontWeight is ThemeFontWeight.Relative &&
                    defaultValueParent?.fontWeight is ThemeFontWeight.Absolute -> {
                FontWeight(
                    (defaultValueParent.fontWeight as ThemeFontWeight.Absolute).weight +
                            (defaultValue.fontWeight as ThemeFontWeight.Relative).relativeWeight
                )
            }

            else -> FontWeight.Normal
        }
        val actualLetterSpacing = letterSpacing
            ?: parentValue?.letterSpacing
            ?: defaultValue?.letterSpacing
            ?: defaultValueParent?.letterSpacing!!

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = previewTexts.TwoLines,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = fontFamilyOf(context, fonts[actualFontFamily]),
                    fontSize = actualFontSize.sp,
                    lineHeight = actualLineHeight.em,
                    fontWeight = actualWeight,
                    letterSpacing = actualLetterSpacing.em,
                )
            }
            Row(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                for ((name, font) in fonts) {
                    val f = remember(font) { fontFamilyOf(context, font) }
                    Column(
                        modifier = Modifier
                            .width(56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {

                        OutlinedIconToggleButton(
                            modifier = Modifier.size(56.dp),
                            checked = name == actualFontFamily,
                            onCheckedChange = {
                                if (it) fontFamily = name
                            },
                        ) {
                            Text(
                                text = previewTexts.ExtraShort,
                                fontFamily = f,
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Text(
                            text = name.capitalize(LocaleList.current),
                            fontFamily = f,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(56.dp),
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    }
                }
            }

            SliderRow(
                icon = R.drawable.format_bold_24px,
                min = 100f,
                max = 900f,
                step = 100f,
                value = actualWeight.weight.toFloat(),
                onValueChange = { weight = it.toInt() },
                formatValue = {
                    it.roundToInt().toString()
                }
            )
            SliderRow(
                icon = R.drawable.format_size_24px,
                min = floor((defaultValue?.fontSize ?: defaultValueParent?.fontSize)!! / 2f),
                max = ceil((defaultValue?.fontSize ?: defaultValueParent?.fontSize)!! * 2f),
                value = actualFontSize.toFloat(),
                onValueChange = {
                    fontSize = it.roundToInt()
                },
                formatValue = {
                    it.roundToInt().toString()
                }
            )
            SliderRow(
                icon = R.drawable.format_line_spacing_24px,
                min = 0.5f,
                max = 2f,
                step = 0.05f,
                value = actualLineHeight,
                onValueChange = { lineHeight = it },
                formatValue = {
                    (it * 100).roundToInt().toString() + "%"
                }
            )
            SliderRow(
                icon = R.drawable.format_letter_spacing_24px,
                min = -0.25f,
                max = 1f,
                step = 0.01f,
                value = actualLetterSpacing,
                onValueChange = { letterSpacing = (it * 100f).roundToInt() / 100f },
                formatValue = {
                    (it * 100).roundToInt().toString() + "%"
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(top = 16.dp)
            )

            TextButton(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.End),
                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                onClick = {
                    fontFamily = null
                    fontSize = null
                    lineHeight = null
                    weight = null
                    letterSpacing = null
                    onValueChange(null)
                }
            ) {
                Icon(
                    painterResource(R.drawable.restart_alt_20px), null,
                    modifier = Modifier
                        .padding(ButtonDefaults.IconSpacing)
                        .size(ButtonDefaults.IconSize)
                )
                Text(stringResource(R.string.preference_restore_default))
            }
        }
    }
}

@Composable
private fun TypographyPreview(
    previewTypography: Typography,
    previewTexts: PreviewTexts,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .horizontalScroll(rememberScrollState())
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MaterialTheme(
            typography = previewTypography
        ) {
            content()
        }
    }
}