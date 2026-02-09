package de.mm20.launcher2.ui.launcher.scaffold

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.mm20.launcher2.preferences.WidgetScreenTarget
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.widgets.WidgetColumn
import kotlinx.coroutines.launch

internal class WidgetsComponent(
    private val target: WidgetScreenTarget
) : ScaffoldComponent() {

    companion object {
        /**
         * Cache for widget component instances.
         * Components are created lazily only when needed.
         */
        private val componentCache = mutableMapOf<WidgetScreenTarget, WidgetsComponent>()

        /**
         * Get or create a WidgetsComponent for the given target.
         * This ensures we reuse the same instance for each target.
         */
        fun forTarget(target: WidgetScreenTarget): WidgetsComponent {
            return componentCache.getOrPut(target) {
                WidgetsComponent(target)
            }
        }
    }

    private val scrollState = ScrollState(0)

    override val isAtTop: State<Boolean?> = derivedStateOf {
        !scrollState.canScrollBackward
    }

    override val isAtBottom: State<Boolean?> = derivedStateOf {
        !scrollState.canScrollForward
    }

    // In note widget
    override val hasIme: Boolean = true

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
        var editMode by rememberSaveable { mutableStateOf(false) }

        val scope = rememberCoroutineScope()
        val topPadding by animateDpAsState(if (editMode) 64.dp else 0.dp)

        val previousScroll = remember { mutableIntStateOf(scrollState.value) }

        LaunchedEffect(scrollState.value, scrollState.canScrollForward, scrollState.canScrollBackward) {
            val delta = scrollState.value - previousScroll.intValue
            previousScroll.intValue = scrollState.value
            if (!editMode) {
                state.onComponentScroll(delta.toFloat())
            }
        }

        Column(
            modifier = modifier
                .verticalScroll(scrollState)
                .padding(horizontal = 8.dp)
                .padding(top = topPadding)
                .padding(insets),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WidgetColumn(
                modifier = Modifier.widthIn(max = 900.dp).fillMaxHeight(),
                editMode = editMode,
                onEditModeChange = {
                    scope.launch { state.lock(hideSearchBar = true) }
                    editMode = it
                },
                parentId = target.scopeId.toString(),
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_done))
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
