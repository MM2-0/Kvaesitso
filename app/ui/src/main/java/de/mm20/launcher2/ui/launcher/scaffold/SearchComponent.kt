package de.mm20.launcher2.ui.launcher.scaffold

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.launcher.search.SearchColumn
import de.mm20.launcher2.ui.launcher.widgets.WidgetColumn
import kotlinx.coroutines.launch

class SearchComponent : ScaffoldComponent {
    override val content: ComponentContent = @Composable { modifier, insets, progress ->
        Column(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f * progress)),
        ) {
            SearchColumn(
                paddingValues = insets,
            )
        }
    }
}