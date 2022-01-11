package de.mm20.launcher2.ui.component.preferences

import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource

@Composable
fun TextPreference(
    title: String,
    value: String,
    summary: String? = value,
    onValueChanged: (String) -> Unit,
    placeholder: String? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(
        title = title,
        summary = summary,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        var textFieldValue by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    placeholder = placeholder?.let {
                        {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onValueChanged(textFieldValue)
                    showDialog = false
                }) {
                    Text(
                        text = stringResource(android.R.string.ok),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                }) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }
}