package de.mm20.launcher2.ui.overlays

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
fun Overlay(
    zIndex: Float = 0f,
    overlay: @Composable () -> Unit
) {
    val overlayManager = LocalOverlayManager.current
    DisposableEffect(Unit) {
        overlayManager.addOverlay(overlay, zIndex)
        onDispose {
            overlayManager.removeOverlay(overlay)
        }
    }
}