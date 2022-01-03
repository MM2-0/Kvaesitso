package de.mm20.launcher2.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.launch

@Composable
fun WeatherLocationSearchDialog(
    onDismissRequest: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val viewModel : WeatherLocationSearchDialogVM = viewModel()
    val isSearching by viewModel.isSearchingLocation.observeAsState(initial = false)
    val locations by viewModel.locationResults.observeAsState(emptyList())
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            tonalElevation = 16.dp,
            shadowElevation = 16.dp,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.preference_location),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp
                        )
                )
                var query by remember { mutableStateOf("") }
                OutlinedTextField(
                    singleLine = true,
                    value = query,
                    onValueChange = {
                        query = it
                        scope.launch {
                            viewModel.searchLocation(it)
                        }
                    }, modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .weight(1f)
                            .padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = 16.dp)
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
                                        horizontal = 24.dp,
                                        vertical = 16.dp
                                    )
                            )
                        }
                    }

                }
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.close),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

        }
    }
}