package de.mm20.launcher2.ui.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.overlays.Overlay
import kotlinx.coroutines.CancellationException

@Composable
fun BottomSheet(
    expanded: Boolean,
    onDismissRequest: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    BottomSheet(
        state = expanded,
        expanded = { it },
        onDismissRequest = onDismissRequest,
    ) {
        content()
    }
}

/**
 * A non-dismissable bottom sheet.
 */
@Composable
fun <T>BottomSheet(
    state: T,
    expanded: (T) -> Boolean,
    onDismissRequest: () -> Unit = {},
    content: @Composable (state: T) -> Unit,
) {
    val expandedState = remember { MutableTransitionState(state) }
    expandedState.targetState = state

    val expandedCurrent = expanded(expandedState.currentState)
    val expandedTarget = expanded(expandedState.targetState)


    if (expandedCurrent || expandedTarget) {

        Overlay {
            val backProgress = remember { Animatable(0f) }

            val focusManager = LocalFocusManager.current
            LaunchedEffect(Unit) {
                focusManager.clearFocus(true)
            }

            PredictiveBackHandler {
                try {
                    it.collect {
                        backProgress.snapTo(it.progress)
                    }
                    onDismissRequest()
                    backProgress.animateTo(0f)
                } catch (_: CancellationException) {
                    backProgress.animateTo(0f)
                }

            }

            val transition = rememberTransition(expandedState)

            val scrimColor by transition.animateColor(
                transitionSpec = { MaterialTheme.motionScheme.slowEffectsSpec() },
            ) { if (expanded(it)) BottomSheetDefaults.ScrimColor else Color.Transparent }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
                    .graphicsLayer {
                        scaleX = 1f - backProgress.value * 0.1f
                        scaleY = 1f - backProgress.value * 0.1f
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .imePadding()
            ) {

                Box(Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            onDismissRequest()
                        }
                    }
                )

                val focusRequester = remember { FocusRequester() }

                if (expanded(transition.currentState)) {
                    DisposableEffect(Unit) {
                        focusRequester.requestFocus()
                        onDispose {
                            focusRequester.freeFocus()
                        }
                    }
                }


                transition.AnimatedVisibility(
                    visible = { expanded(it) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it },
                    exit = slideOutVertically { it },
                ) {
                    Surface(
                        shadowElevation = 1.dp,
                        shape = BottomSheetDefaults.ExpandedShape,
                        modifier = Modifier
                            .statusBarsPadding()
                            .imePadding()
                            .fillMaxWidth()
                            .widthIn(max = BottomSheetDefaults.SheetMaxWidth)
                            .align(Alignment.BottomCenter)
                            .wrapContentHeight()
                    ) {
                        content(
                            if (expandedTarget) expandedState.targetState else expandedState.currentState
                        )
                    }
                }
            }
        }
    }
}