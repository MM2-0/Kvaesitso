package de.mm20.launcher2.ui.settings.colorscheme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.colorpicker.HctColorPicker
import de.mm20.launcher2.ui.component.colorpicker.rememberHctColorPickerState
import de.mm20.launcher2.ui.component.preferences.SwitchPreference

@Composable
fun CorePaletteColorPreference(
    title: String,
    value: Int?,
    onValueChange: (Int?) -> Unit,
    defaultValue: Int,
    modifier: Modifier = Modifier,
    autoGenerate: (() -> Int?)? = null,
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(
                onClick = { showDialog = true },
            )
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorSwatch(
            color = Color(value ?: defaultValue),
            modifier = Modifier
                .padding(end = 20.dp)
                .size(48.dp),
        )

        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
    }

    var currentValue by remember { mutableStateOf(value) }
    DismissableBottomSheet(
        expanded = showDialog,
        onDismissRequest = {
            onValueChange(currentValue)
            showDialog = false
        }) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            SwitchPreference(
                icon = R.drawable.rule_settings_24px,
                title = stringResource(R.string.theme_color_scheme_system_default),
                value = currentValue == null,
                onValueChanged = {
                    currentValue = if (it) null else defaultValue
                },
                containerColor = Color.Transparent,
            )
            AnimatedVisibility(
                currentValue != null,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                ),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                )
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    val colorPickerState = rememberHctColorPickerState(
                        initialColor = Color(value ?: defaultValue),
                        onColorChanged = {
                            currentValue = it.toArgb()
                        }
                    )
                    HctColorPicker(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterHorizontally),
                        state = colorPickerState,
                    )

                    if (autoGenerate != null) {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 16.dp)
                        )

                        TextButton(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .align(Alignment.End),
                            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                            onClick = {
                                val autoGenerated = autoGenerate()
                                currentValue = autoGenerated
                                if (autoGenerated != null) {
                                    colorPickerState.setColor(Color(autoGenerated))
                                }
                            }
                        ) {
                            Icon(
                                painterResource(R.drawable.wand_stars_20px), null,
                                modifier = Modifier
                                    .padding(ButtonDefaults.IconSpacing)
                                    .size(ButtonDefaults.IconSize)
                            )
                            Text(stringResource(R.string.theme_color_scheme_autogenerate))
                        }
                    }
                }
            }
        }
    }
}