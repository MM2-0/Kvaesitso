package de.mm20.launcher2.ui.component.preferences

import androidx.activity.compose.LocalActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.dragndrop.LazyDragAndDropColumn
import de.mm20.launcher2.ui.component.dragndrop.LazyDragAndDropListState
import de.mm20.launcher2.ui.component.dragndrop.rememberLazyDragAndDropListState
import de.mm20.launcher2.ui.locals.LocalBackStack


@Composable
@Deprecated("Use the overload with title as a composable function instead")
fun PreferenceScreen(
    title: String,
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
    topBarActions: @Composable RowScope.() -> Unit = {},
    helpUrl: String? = null,
    lazyColumnState: LazyListState = rememberLazyListState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: LazyListScope.() -> Unit,
) {

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            PreferenceScreenTopBar(
                title = title,
                actions = topBarActions,
                helpUrl = helpUrl,
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
                top = it.calculateTopPadding() + 12.dp,
                bottom = it.calculateBottomPadding() + 12.dp,
                start = 12.dp,
                end = 12.dp
            )
        )
    }
}


@Composable
fun DragAndDropPreferenceScreen(
    title: @Composable () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    helpUrl: String? = null,
    lazyColumnState: LazyDragAndDropListState,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(1.dp),
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            PreferenceScreenTopBar(
                title = title,
                actions = topBarActions,
                helpUrl = helpUrl,
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        LazyDragAndDropColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = lazyColumnState,
            content = content,
            verticalArrangement = verticalArrangement,
            contentPadding = PaddingValues(
                top = it.calculateTopPadding() + 12.dp,
                bottom = it.calculateBottomPadding() + 12.dp,
                start = 12.dp,
                end = 12.dp
            ),
            bidirectionalDrag = false,
        )
    }
}

@Composable
private fun PreferenceScreenTopBar(
    title: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    helpUrl: String? = null,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val backStack = LocalBackStack.current
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val activity = LocalActivity.current

    CenterAlignedTopAppBar(
        title = title,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        navigationIcon = {
            IconButton(onClick = {
                if (backStack.size <= 1) {
                    activity?.onBackPressed()
                } else {
                    backStack.removeLastOrNull()
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
            actions()
        },
        scrollBehavior = scrollBehavior,
    )
}