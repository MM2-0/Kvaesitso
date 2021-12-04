package de.mm20.launcher2.ui.component.preferences

import android.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.mm20.launcher2.ui.component.ColorPicker

@Composable
fun ColorPreference(
    title: String,
    icon: ImageVector? = null,
    value: Color,
    summary: String? = null,
    onValueChanged: (Color) -> Unit,
    enabled: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        onClick = {
            showDialog = true
        },
        controls = {
            ColorPreview(color = value)
        }
    )
    if (showDialog) {
        var selectedValue by remember { mutableStateOf(value) }
        Dialog(onDismissRequest = { showDialog = false }) {
            Card(
                elevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(
                            start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp
                        )
                    )
                    ColorPicker(
                        value = selectedValue,
                        onValueChanged = { selectedValue = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp, end = 8.dp, top = 16.dp, start = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            onValueChanged(selectedValue)
                            showDialog = false
                        }) {
                            Text(text = stringResource(id = R.string.ok))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColorPreview(color: Color) {
    Surface(
        modifier = Modifier.size(32.dp),
        shape = RoundedCornerShape(16.dp),
        color = color
    ) {}
}