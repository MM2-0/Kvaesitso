package de.mm20.launcher2.ui.component.preferences
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.activity.compose.LocalActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.frostedGlass
import de.mm20.launcher2.ui.component.rememberVirtualAppBlur
import de.mm20.launcher2.ui.component.virtualAppElevated
import de.mm20.launcher2.ui.component.virtualAppGlass
import de.mm20.launcher2.ui.component.virtualAppSettingsPane
import de.mm20.launcher2.ui.theme.transparency.transparency
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
    bottomBar: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    PreferenceScreen(
        title = {
            Text(
                title,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
                maxLines = 2
            )
        },
        topBarActions = topBarActions,
        helpUrl = helpUrl,
        lazyColumnState = lazyColumnState,
        verticalArrangement = verticalArrangement,
        bottomBar = bottomBar,
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
    bottomBar: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val blur = rememberVirtualAppBlur()
    val bar = virtualAppElevated()

    Scaffold(
        topBar = {
            PreferenceScreenTopBar(
                modifier = Modifier.virtualAppGlass(blur, bar),
                title = title,
                actions = topBarActions,
                helpUrl = helpUrl,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = bottomBar,
        containerColor = Color.Transparent
    ) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gutter = de.mm20.launcher2.ui.layout.AdaptiveLayoutPolicy.contentGutter(maxWidth.value).dp
            val direction = LocalLayoutDirection.current
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(insets)
                    .imePadding()
                    .frostedGlass(
                        virtualAppSettingsPane(),
                        fallbackColor = MaterialTheme.colorScheme.surface,
                    )
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                state = lazyColumnState,
                content = content,
                verticalArrangement = verticalArrangement,
                contentPadding = PaddingValues(
                    top = insets.calculateTopPadding() + 12.dp,
                    bottom = insets.calculateBottomPadding() + 12.dp,
                    start = insets.calculateStartPadding(direction) + gutter,
                    end = insets.calculateEndPadding(direction) + gutter
                )
            )
        }
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
    val blur = rememberVirtualAppBlur()
    val bar = virtualAppElevated()

    Scaffold(
        topBar = {
            PreferenceScreenTopBar(
                modifier = Modifier.virtualAppGlass(blur, bar),
                title = title,
                actions = topBarActions,
                helpUrl = helpUrl,
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = Color.Transparent
    ) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gutter = de.mm20.launcher2.ui.layout.AdaptiveLayoutPolicy.contentGutter(maxWidth.value).dp
            val direction = LocalLayoutDirection.current
            LazyDragAndDropColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(insets)
                    .imePadding()
                    .frostedGlass(
                        virtualAppSettingsPane(),
                        fallbackColor = MaterialTheme.colorScheme.surface,
                    )
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                state = lazyColumnState,
                content = content,
                verticalArrangement = verticalArrangement,
                contentPadding = PaddingValues(
                    top = insets.calculateTopPadding() + 12.dp,
                    bottom = insets.calculateBottomPadding() + 12.dp,
                    start = insets.calculateStartPadding(direction) + gutter,
                    end = insets.calculateEndPadding(direction) + gutter
                ),
                bidirectionalDrag = false,
            )
        }
    }
}

@Composable
private fun PreferenceScreenTopBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    helpUrl: String? = null,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val backStack = LocalBackStack.current
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val activity = LocalActivity.current

    // Launcher typography uses relative em line heights. Density.toDp(TextUnit) accepts
    // sp only; measure two actual lines so em and unspecified heights both resolve safely.
    val titleSize = rememberTextMeasurer().measure(
        AnnotatedString("Ag\nAg"),
        style = MaterialTheme.typography.titleLarge,
        maxLines = 2,
    ).size
    val expandedHeight = maxOf(64.dp, with(LocalDensity.current) { titleSize.height.toDp() + 16.dp })

    CenterAlignedTopAppBar(
        modifier = modifier,
        title = { de.mm20.launcher2.ui.component.TelosCyberHeading { title() } },
        expandedHeight = expandedHeight,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
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
                    painter = painterResource(BaseR.drawable.arrow_back_24px),
                    contentDescription = stringResource(I18nR.string.menu_back)
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
                        painter = painterResource(BaseR.drawable.help_24px),
                        contentDescription = stringResource(I18nR.string.help)
                    )
                }
            }
            actions()
        },
        scrollBehavior = scrollBehavior,
    )
}
