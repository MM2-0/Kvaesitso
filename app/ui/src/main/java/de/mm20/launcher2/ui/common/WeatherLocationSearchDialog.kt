package de.mm20.launcher2.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.SmallMessage
import kotlinx.coroutines.launch

@Composable
fun WeatherLocationSearchDialog(
    expanded: Boolean,
    onDismissRequest: () -> Unit
) {

    DismissableBottomSheet(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        val scope = rememberCoroutineScope()
        val viewModel: WeatherLocationSearchDialogVM = viewModel()
        val isSearching by viewModel.isSearchingLocation
        val locations by viewModel.locationResults

        var query by remember { mutableStateOf("") }
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            DockedSearchBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = 8.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                expanded = false,
                onExpandedChange = {},
                inputField = {
                    SearchBarDefaults.InputField(
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.search_24px),
                                contentDescription = null
                            )
                        },
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        query = query,
                        onQueryChange = {
                            query = it
                            scope.launch {
                                viewModel.searchLocation(it)
                            }
                        },
                    )
                }
            ) {}
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(16.dp)
                        .navigationBarsPadding()
                )
            } else if (locations.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = WindowInsets.navigationBars.asPaddingValues()
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
                                .padding(16.dp)
                        )
                    }
                }

            } else if (query.isNotEmpty()) {
                SmallMessage(
                    modifier = Modifier.fillMaxWidth()
                        .padding(16.dp),
                    icon = R.drawable.error_24px,
                    text = stringResource(R.string.weather_location_search_no_result)
                )
            }
        }
    }
}