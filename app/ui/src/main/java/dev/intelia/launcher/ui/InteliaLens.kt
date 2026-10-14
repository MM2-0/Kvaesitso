package dev.intelia.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import kotlin.math.min

@Composable
internal fun InteliaLens(apps: List<Application?>, reducedMotion: Boolean, onSearch: () -> Unit,
    onLaunch: (Application) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var session by remember { mutableStateOf<ArcGesture?>(null) }
    val haptic = LocalHapticFeedback.current
    val searchLabel = stringResource(R.string.intelia_search)
    val density = LocalDensity.current
    val scale by animateFloatAsState(if (open) 1.08f else 1f, tween(if (reducedMotion) 0 else 100), label = "lens-touch")
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { session?.cancel(); open = false; selected = null }
    BoxWithConstraints(modifier.fillMaxWidth().height(200.dp)) {
        val span = min(maxWidth.value - 52f, 300f)
        val centers = apps.mapIndexed { index, app ->
            val position = if (apps.size <= 1) 0f else (index.toFloat() / (apps.size - 1) - .5f) * 2f
            if (app == null) null else (position * span / 2f) to (-90f + 28f * position * position)
        }
        val reveal by animateFloatAsState(if (open) 1f else 0f, tween(if (reducedMotion) 0 else 140), label = "arc-reveal")
        if (reveal > 0f) {
            if (apps.none { it != null }) Text(stringResource(R.string.intelia_arc_empty),
                Modifier.align(Alignment.TopCenter).padding(top = 38.dp), fontSize = 12.sp)
            apps.forEachIndexed { index, app ->
                val center = centers[index]
                if (app != null && center != null) Column(Modifier.align(Alignment.BottomCenter)
                    .offset(x = center.first.dp, y = center.second.dp - 18.dp)
                    .graphicsLayer { alpha = reveal; translationY = (1f - reveal) * 12.dp.toPx(); scaleX = if (selected == index) 1.12f else 1f; scaleY = scaleX },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (selected == index) Text(app.labelOverride ?: app.label, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(bottom = 6.dp))
                    IconButton(onClick = { open = false; session?.cancel(); onLaunch(app) }, modifier = Modifier.size(44.dp).semantics { contentDescription = app.labelOverride ?: app.label }) {
                        AppIcon(app, Modifier.size(44.dp))
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics {
                contentDescription = "LENS"
                onClick(label = searchLabel) { onSearch(); true }
                onLongClick(label = "ARC") { open = true; true }
            }
            .pointerInput(apps, maxWidth) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val hold = awaitLongPressOrCancellation(down.id)
                    if (hold == null) {
                        val event = currentEvent
                        if (event.changes.size == 1 && event.changes.first().id == down.id && event.changes.first().changedToUpIgnoreConsumed() &&
                            (event.changes.first().position - down.position).getDistance() < viewConfiguration.touchSlop) onSearch()
                        return@awaitEachGesture
                    }
                    if (hold.id != down.id || currentEvent.changes.size != 1) return@awaitEachGesture
                    val gesture = ArcGesture(centers)
                    session = gesture; open = true; selected = null
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.find { it.id == down.id }
                            if (change == null || event.changes.size != 1 || change.isConsumed) { gesture.cancel(); break }
                            val x = with(density) { (change.position.x - size.width / 2f).toDp().value }
                            val y = with(density) { (change.position.y - size.height / 2f).toDp().value }
                            val previous = selected
                            selected = gesture.move(x, y)
                            if (selected != null && selected != previous) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            change.consume()
                            if (!change.pressed) {
                                gesture.finish()?.let { apps.getOrNull(it)?.let(onLaunch) }
                                break
                            }
                        }
                    } finally { gesture.cancel(); session = null; open = false; selected = null }
                }
            }, contentAlignment = Alignment.Center) { HomeGlyph(0, null) }
    }
}
