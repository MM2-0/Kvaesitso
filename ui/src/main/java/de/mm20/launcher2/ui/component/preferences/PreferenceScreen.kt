package de.mm20.launcher2.ui.component.preferences

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.accompanist.insets.systemBarsPadding
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun PreferenceScreen(
    title: String,
    content: LazyListScope.() -> Unit
) {
    val navController = LocalNavController.current
    val systemUiController = rememberSystemUiController()
    systemUiController.setStatusBarColor(MaterialTheme.colors.surface)
    systemUiController.setNavigationBarColor(Color.Black)
    Box(
        modifier = Modifier.systemBarsPadding()
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    backgroundColor = MaterialTheme.colors.surface,
                    title = {
                        Text(title)
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            navController?.navigateUp()
                        }) {
                            Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                content = content
            )
        }
    }

}