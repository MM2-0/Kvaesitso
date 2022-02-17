package de.mm20.launcher2.ui.launcher.search.common

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import de.mm20.launcher2.search.data.Application
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.toDp
import de.mm20.launcher2.ui.launcher.search.apps.AppItemGridPopup
import de.mm20.launcher2.ui.locals.LocalGridColumns
import kotlinx.coroutines.delay
import kotlin.math.ceil

@Composable
fun SearchResultGrid(items: List<Searchable>) {

    val columns = LocalGridColumns.current
    Column(
        modifier = Modifier.animateContentSize().fillMaxWidth().padding(4.dp)
    ) {
        for (i in 0 until ceil(items.size / columns.toFloat()).toInt()) {
            Row {
                for (j in 0 until columns) {
                    val item = items.getOrNull(i * columns + j)
                    if (item != null) {
                        GridItem(
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp, 8.dp), item = item
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
