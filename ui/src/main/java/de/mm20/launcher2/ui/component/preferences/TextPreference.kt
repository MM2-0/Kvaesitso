package de.mm20.launcher2.ui.component.preferences

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
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
                    textStyle = MaterialTheme.typography.bodyLarge,
                    onValueChange = { textFieldValue = it },
                    placeholder = placeholder?.let {
                        {
                            Text(
                                text = it,
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
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                }) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                    )
                }
            }
        )
    }
}