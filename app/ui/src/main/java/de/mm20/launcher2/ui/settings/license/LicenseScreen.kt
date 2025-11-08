package de.mm20.launcher2.ui.settings.license

import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.licenses.OpenSourceLibrary
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun LicenseScreen(library: OpenSourceLibrary) {
    val context = LocalContext.current
    val viewModel: LicenseScreenVM = viewModel()
    val navController = LocalNavController.current

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState()
        )
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(library.name)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController?.navigateUp()
                    }) {
                        Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    val colorScheme = MaterialTheme.colorScheme
                    IconButton(onClick = {
                        CustomTabsIntent.Builder()
                            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder()
                                .setToolbarColor(colorScheme.primaryContainer.toArgb())
                                .build())
                            .build()
                            .launchUrl(context, Uri.parse(library.url))
                    }) {
                        Icon(
                            painterResource(R.drawable.open_in_browser_24px),
                            contentDescription = stringResource(
                                R.string.open_webpage
                            )
                        )
                    }
                }
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(it)
        ) {
            library.description?.let {
                item {
                    PreferenceCategory {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(text = it)
                        }
                    }
                }
            }
            item {
                PreferenceCategory {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(id = library.licenseName),
                            style = MaterialTheme.typography.titleMedium
                        )
                        library.copyrightNote?.let {
                            Text(
                                text = it,
                                modifier = Modifier.padding(vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        val licenseText by viewModel.getLicenseText(library).collectAsState(null)
                        licenseText?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}