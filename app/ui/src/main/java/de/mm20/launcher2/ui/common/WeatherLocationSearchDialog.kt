package de.mm20.launcher2.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.SmallMessage
import kotlinx.coroutines.launch

@Composable
fun WeatherLocationSearchDialog(
    onDismissRequest: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val viewModel: WeatherLocationSearchDialogVM = viewModel()
    val isSearching by viewModel.isSearchingLocation
    val locations by viewModel.locationResults

    BottomSheetDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.preference_location),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
            ) {
                Text(
                    text = stringResource(android.R.string.cancel),
                )
            }
        }
    ) {
        var query by remember { mutableStateOf("") }
        Column(
            modifier = Modifier.fillMaxWidth().padding(it)
        ) {
            Row(
                Modifier.padding(bottom = 16.dp)
            ) {
                OutlinedTextField(
                    singleLine = true,
                    value = query,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    onValueChange = {
                        query = it
                        scope.launch {
                            viewModel.searchLocation(it)
                        }
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                    },
                    modifier = Modifier
                        .weight(1f)
                )
            }
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                )
            } else if (locations.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    items(locations) {
                        Text(
                            text = it.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLocation(it)
                                    onDismissRequest()
                                }
                                .padding(
                                    vertical = 16.dp
                                )
                        )
                    }
                }

            } else if (query.isNotEmpty()) {
                SmallMessage(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Error,
                    text = stringResource(R.string.weather_location_search_no_result)
                )
            }
        }
    }
}