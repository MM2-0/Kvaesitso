package de.mm20.launcher2.ui.settings.colorscheme

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.themes.colors.ColorRef
import de.mm20.launcher2.themes.colors.CorePaletteColor
import de.mm20.launcher2.themes.colors.FullCorePalette
import de.mm20.launcher2.themes.colors.StaticColor
import de.mm20.launcher2.themes.colors.atTone
import de.mm20.launcher2.themes.colors.get
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.colorpicker.HctColorPicker
import de.mm20.launcher2.ui.component.colorpicker.rememberHctColorPickerState
import de.mm20.launcher2.ui.ktx.hct
import hct.Hct
import kotlin.math.roundToInt
import de.mm20.launcher2.themes.colors.Color as ThemeColor

@Composable
fun ThemeColorPreference(
    title: String,
    value: ThemeColor?,
    corePalette: FullCorePalette,
    onValueChange: (ThemeColor?) -> Unit,
    defaultValue: ThemeColor,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(
                onClick = { showDialog = true },
            )
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorSwatch(
            color = Color((value ?: defaultValue).get(corePalette)),
            modifier = Modifier.padding(end = 20.dp).size(48.dp),
        )

        Text(
            title,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
    }

    if (showDialog) {
        var currentValue by remember { mutableStateOf(value) }

        val actualValue = currentValue ?: defaultValue
        DismissableBottomSheet(onDismissRequest = {
            onValueChange(currentValue)
            showDialog = false
        }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(it),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    ToggleButton(
                        modifier = Modifier.weight(1f),
                        checked = actualValue is ColorRef,
                        onCheckedChange = {
                            if (actualValue is ColorRef) return@ToggleButton
                            currentValue = defaultValue
                        },
                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
                    ) {
                        Icon(
                            painterResource(
                                if (actualValue is ColorRef) R.drawable.check_20px else R.drawable.palette_20px
                            ),
                            null,
                            modifier = Modifier
                                .padding(end = ToggleButtonDefaults.IconSpacing)
                                .size(ToggleButtonDefaults.IconSize)
                        )
                        Text(stringResource(R.string.theme_color_scheme_palette_color))
                    }
                    ToggleButton(
                        modifier = Modifier.weight(1f),
                        checked = actualValue is StaticColor,
                        onCheckedChange = {
                            currentValue = StaticColor(actualValue.get(corePalette))
                        },
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
                    ) {
                        Icon(
                            painterResource(
                                if (actualValue is StaticColor) R.drawable.check_20px else R.drawable.colorize_20px
                            ),
                            null,
                            modifier = Modifier
                                .padding(end = ToggleButtonDefaults.IconSpacing)
                                .size(ToggleButtonDefaults.IconSize)
                        )
                        Text(stringResource(R.string.theme_color_scheme_custom_color))
                    }
                }
                AnimatedContent(
                    actualValue,
                    label = "AnimatedContent",
                    contentKey = { it is StaticColor }
                ) { themeColor ->
                    Column {
                        if (themeColor is StaticColor) {
                            val colorPickerState = rememberHctColorPickerState(
                                initialColor = Color(themeColor.color),
                                onColorChanged = {
                                    currentValue = StaticColor(it.toArgb())
                                }
                            )
                            HctColorPicker(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp)
                                    .align(Alignment.CenterHorizontally),
                                state = colorPickerState
                            )
                        } else if (themeColor is ColorRef) {
                            val hct = Hct.fromInt(corePalette.get(themeColor.color))
                            val hue = hct.hue.toFloat()
                            val chroma = hct.chroma.toFloat()
                            var tone by remember(value == null) { mutableStateOf(themeColor.tone.toFloat()) }
                            Row(
                                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                            ) {
                                ColorSwatch(
                                    color = Color(
                                        corePalette
                                            .get(CorePaletteColor.Primary)
                                            .atTone(tone.toInt())
                                    ),
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(64.dp)
                                        .clickable {
                                            currentValue = ColorRef(
                                                CorePaletteColor.Primary,
                                                tone.roundToInt()
                                            )
                                        },
                                    selected = themeColor.color == CorePaletteColor.Primary,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                ColorSwatch(
                                    color = Color(
                                        corePalette
                                            .get(CorePaletteColor.Secondary)
                                            .atTone(tone.toInt())
                                    ),
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(64.dp)
                                        .clickable {
                                            currentValue = ColorRef(
                                                CorePaletteColor.Secondary,
                                                tone.roundToInt()
                                            )
                                        },
                                    selected = themeColor.color == CorePaletteColor.Secondary,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                ColorSwatch(
                                    color = Color(
                                        corePalette
                                            .get(CorePaletteColor.Tertiary)
                                            .atTone(tone.toInt())
                                    ),
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(64.dp)
                                        .clickable {
                                            currentValue = ColorRef(
                                                CorePaletteColor.Tertiary,
                                                tone.roundToInt()
                                            )
                                        },
                                    selected = themeColor.color == CorePaletteColor.Tertiary,
                                )
                            }
                            Row(
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                ColorSwatch(
                                    color = Color(
                                        corePalette
                                            .get(CorePaletteColor.Neutral)
                                            .atTone(tone.toInt())
                                    ),
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(64.dp)
                                        .clickable {
                                            currentValue = ColorRef(
                                                CorePaletteColor.Neutral,
                                                tone.roundToInt()
                                            )
                                        },
                                    selected = themeColor.color == CorePaletteColor.Neutral,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                ColorSwatch(
                                    color = Color(
                                        corePalette
                                            .get(CorePaletteColor.NeutralVariant)
                                            .atTone(tone.toInt())
                                    ),
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(64.dp)
                                        .clickable {
                                            currentValue = ColorRef(
                                                CorePaletteColor.NeutralVariant,
                                                tone.roundToInt()
                                            )
                                        },
                                    selected = themeColor.color == CorePaletteColor.NeutralVariant,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                ColorSwatch(
                                    color = Color(
                                        corePalette
                                            .get(CorePaletteColor.Error)
                                            .atTone(tone.toInt())
                                    ),
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(64.dp)
                                        .clickable {
                                            currentValue = ColorRef(
                                                CorePaletteColor.Error,
                                                tone.roundToInt()
                                            )
                                        },
                                    selected = themeColor.color == CorePaletteColor.Error,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                            ) {
                                Text(
                                    text = "T",
                                    modifier = Modifier.width(32.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center
                                )
                                Slider(
                                    modifier = Modifier.weight(1f),
                                    value = tone,
                                    valueRange = 0f..100f,
                                    onValueChange = {
                                        tone = it
                                        currentValue = themeColor.copy(tone = it.roundToInt())
                                    },
                                    track = {
                                        Canvas(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(20.dp)
                                        ) {
                                            drawRoundRect(
                                                brush = Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color.hct(
                                                            hue,
                                                            chroma,
                                                            0f
                                                        ),
                                                        Color.hct(
                                                            hue,
                                                            chroma,
                                                            10f
                                                        ),
                                                        Color.hct(
                                                            hue,
                                                            chroma,
                                                            20f
                                                        ),
                                                        Color.hct(
                                                            hue,
                                                            chroma,
                                                            30f
                                                        ),
                                                        Color.hct(
                                                            hue,
                                                            chroma,
                                                            40f
                                                        ),
                                                        Color.hct(
                                                            hue,
                                                            chroma,
                                                            50f
                                                        ),
                                                        Color.hct(
                                                            hue,
                                                            chroma,
                                                            60f
                                                        ),
                                                        Color.hct(
                                                            hue,
                                                            chroma,
                                                            70f
                                                        ),
                                                        Color.hct(
                                                            hue,
                                                            chroma,
                                                            80f
                                                        ),
                                                        Color.hct(
                                                            hue,
                                                            chroma,
                                                            90f
                                                        ),
                                                        Color.hct(
                                                            hue,
                                                            chroma,
                                                            100f
                                                        ),
                                                    )
                                                ),
                                                style = Fill,
                                                cornerRadius = CornerRadius(
                                                    10.dp.toPx(),
                                                    10.dp.toPx()
                                                )
                                            )
                                        }
                                    },
                                    thumb = {
                                        Box(
                                            modifier = Modifier
                                                .padding(vertical = 2.dp, horizontal = 8.dp)
                                                .size(16.dp)
                                                .shadow(1.dp, CircleShape)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                    }
                                )
                                Text(
                                    text = tone.roundToInt().toString(),
                                    modifier = Modifier.width(32.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 16.dp)
                        )

                        TextButton(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .align(Alignment.End),
                            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                            onClick = { currentValue = null }
                        ) {
                            Icon(
                                painterResource(R.drawable.restart_alt_20px),
                                null,
                                modifier = Modifier
                                    .padding(ButtonDefaults.IconSpacing)
                                    .size(ButtonDefaults.IconSize)
                            )
                            Text(stringResource(R.string.preference_restore_default))
                        }
                    }
                }
            }
        }
    }
}