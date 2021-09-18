package de.mm20.launcher2.ui.widget

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.mm20.launcher2.ui.ktx.toIntOffset
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetType

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun WidgetCard(widget: Widget, editMode: Boolean = false) {
    var dragOffset by remember { mutableStateOf(IntOffset.Zero) }
    val animatedOffset by animateIntOffsetAsState(dragOffset)
    var dragged by remember { mutableStateOf(false)}
    Card(
            modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .offset { animatedOffset }
                    .zIndex(if (dragged) 1f else 0f),
            elevation = animateDpAsState(if (dragged) 8.dp else 0.dp).value
    ) {
        Column {
            if (widget.type == WidgetType.INTERNAL) {
                when (widget.data) {
                    "weather" -> WeatherWidget()
                    "music" -> MusicWidget()
                    "calendar" -> CalendarWidget()
                }
            } else{
                PlatformWidget(widget)
            }
        }
    }
}