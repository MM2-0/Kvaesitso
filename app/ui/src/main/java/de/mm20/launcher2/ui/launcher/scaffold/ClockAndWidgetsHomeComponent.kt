package de.mm20.launcher2.ui.launcher.scaffold

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.mm20.launcher2.preferences.ui.ClockWidgetSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ktx.toDp
import de.mm20.launcher2.ui.launcher.widgets.WidgetColumn
import de.mm20.launcher2.ui.launcher.widgets.clock.ClockWidget
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

internal object ClockAndWidgetsHomeComponent : ScaffoldComponent() {
    private var editMode by mutableStateOf(false)
    private val scrollState = ScrollState(0)

    override val isAtTop: State<Boolean?> = derivedStateOf {
        !scrollState.canScrollBackward
    }

    override val isAtBottom: State<Boolean?> = derivedStateOf {
        !scrollState.canScrollForward || scrollState.value == 0
    }

    override val drawBackground: Boolean = false

    // In note widget
    override val hasIme: Boolean = true

    override val showSearchBar: Boolean = false

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        val scope = rememberCoroutineScope()

        val clockWidgetSettings: ClockWidgetSettings = koinInject()
        val fillHeight by clockWidgetSettings.fillHeight.collectAsState(null)

        if (fillHeight == null) return

        val topPadding by animateDpAsState(if (editMode) 80.dp else 0.dp)
        val previousScroll = remember { mutableIntStateOf(scrollState.value) }

        LaunchedEffect(
            scrollState.value,
            scrollState.canScrollForward,
            scrollState.canScrollBackward
        ) {
            val delta = scrollState.value - previousScroll.intValue
            previousScroll.intValue = scrollState.value
            if (!editMode) {
                state.onComponentScroll(delta.toFloat())
            }
        }

        Column(
            modifier = modifier
                .verticalScroll(scrollState, enabled = !state.isDragged)
                .padding(horizontal = 8.dp)
                .padding(top = topPadding)
                .padding(insets),
        ) {
            val bottomPadding by animateDpAsState(
                if (fillHeight == true && scrollState.value == 0) insets.calculateBottomPadding()
                else 0.dp
            )

            ClockWidget(
                modifier = Modifier
                    .fillMaxWidth()
                    then if (fillHeight == true) {
                        Modifier
                            .padding(bottom = bottomPadding)
                            .height(state.size.height.toDp() - insets.calculateTopPadding() - insets.calculateBottomPadding())
                } else Modifier,
                editMode = editMode,
                fillScreenHeight = fillHeight == true,
            )
            WidgetColumn(
                modifier = Modifier
                    .padding(top = 16.dp),
                editMode = editMode,
                onEditModeChange = {
                    scope.launch { state.lock(hideSearchBar = true) }
                    editMode = it
                },
            )
        }
        if (editMode) {
            BackHandler {
                editMode = false
                scope.launch { state.unlock() }
            }
        }
        AnimatedVisibility(
            editMode,
            modifier = Modifier.zIndex(10f),
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.menu_edit_widgets)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            editMode = false
                            scope.launch { state.unlock() }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            stringResource(R.string.action_done)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    }

    override suspend fun onDismiss(state: LauncherScaffoldState) {
        super.onDismiss(state)
        scrollState.scrollTo(0)
    }
}