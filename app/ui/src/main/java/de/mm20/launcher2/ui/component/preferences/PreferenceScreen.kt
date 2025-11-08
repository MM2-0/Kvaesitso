package de.mm20.launcher2.ui.component.preferences

import androidx.activity.compose.LocalActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalNavController


@Composable
fun PreferenceScreen(
    title: String,
    floatingActionButton: @Composable () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    helpUrl: String? = null,
    lazyColumnState: LazyListState = rememberLazyListState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: LazyListScope.() -> Unit,
) {
    PreferenceScreen(
        title = {
            Text(
                title,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
                maxLines = 1
            )
        },
        floatingActionButton = floatingActionButton,
        topBarActions = topBarActions,
        helpUrl = helpUrl,
        lazyColumnState = lazyColumnState,
        verticalArrangement = verticalArrangement,
        content = content
    )
}

@Composable
fun PreferenceScreen(
    title: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    helpUrl: String? = null,
    lazyColumnState: LazyListState = rememberLazyListState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: LazyListScope.() -> Unit,
) {
    val navController = LocalNavController.current

    val context = LocalContext.current

    val colorScheme = MaterialTheme.colorScheme

    val touchSlop = LocalViewConfiguration.current.touchSlop
    var fabVisible by remember { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (consumed.y < -touchSlop) fabVisible = false
                else if (consumed.y > touchSlop) fabVisible = true
                return super.onPostScroll(consumed, available, source)
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val activity = LocalActivity.current
    Scaffold(
        floatingActionButton = {
            AnimatedVisibility(
                fabVisible,
                enter = scaleIn(),
                exit = scaleOut(),
            ) {
                floatingActionButton()
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = title,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (navController?.navigateUp() != true) {
                            activity?.onBackPressed()
                        }
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_24px),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (helpUrl != null) {
                        IconButton(onClick = {
                            CustomTabsIntent.Builder()
                                .setDefaultColorSchemeParams(
                                    CustomTabColorSchemeParams.Builder()
                                        .setToolbarColor(colorScheme.primaryContainer.toArgb())
                                        .setSecondaryToolbarColor(colorScheme.secondaryContainer.toArgb())
                                        .build()
                                )
                                .build().launchUrl(context, helpUrl.toUri())
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.help_24px),
                                contentDescription = stringResource(R.string.help)
                            )
                        }
                    }
                    topBarActions()
                },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = lazyColumnState,
            content = content,
            verticalArrangement = verticalArrangement,
            contentPadding = PaddingValues(
                top = it.calculateTopPadding(),
                bottom = it.calculateBottomPadding() + 4.dp,
                start = 12.dp,
                end = 12.dp
            )
        )
    }

}