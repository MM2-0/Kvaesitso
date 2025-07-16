package de.mm20.launcher2.ui.settings.colorscheme

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun ColorSchemePreferenceCategory(
    title: String,
    previewColorScheme: ColorScheme,
    darkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    colorPreferences: @Composable () -> Unit = {},
    preview: @Composable RowScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {

            MaterialTheme(
                colorScheme = previewColorScheme
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.extraSmall)
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    preview()
                }
            }
            colorPreferences()
        }
    }

}