package de.mm20.launcher2.ui.component.preferences

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.ui.locals.LocalNavController


@Composable
fun PreferenceScreen(
    title: String,
    floatingActionButton: @Composable () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    helpUrl: String? = null,
    content: LazyListScope.() -> Unit,
) {
    val navController = LocalNavController.current
    val systemUiController = rememberSystemUiController()
    systemUiController.setStatusBarColor(MaterialTheme.colorScheme.surface)
    systemUiController.setNavigationBarColor(Color.Black)

    val context = LocalContext.current

    val colorScheme = MaterialTheme.colorScheme

    val activity = LocalContext.current as? AppCompatActivity
    Scaffold(
        floatingActionButton = floatingActionButton,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        title,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (navController?.navigateUp() != true) {
                            activity?.onBackPressed()
                        }
                    }) {
                        Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (helpUrl != null) {
                        IconButton(onClick = {
                            CustomTabsIntent.Builder()
                                .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder()
                                    .setToolbarColor(colorScheme.primaryContainer.toArgb())
                                    .setSecondaryToolbarColor(colorScheme.secondaryContainer.toArgb())
                                    .build()
                                )
                                .build().launchUrl(context, Uri.parse(helpUrl))
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.HelpOutline,
                                contentDescription = "Help"
                            )
                        }
                    }
                    topBarActions()
                }
            )
        }) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            content = content,
        )
    }

}