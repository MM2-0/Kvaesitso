package de.mm20.launcher2.ui.overlays

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf

class OverlayManager {
    val overlays = mutableStateListOf<Overlay>()

    fun addOverlay(overlay: @Composable () -> Unit, zIndex: Float = 0f) {
        overlays.add(Overlay(overlay, zIndex))
    }

    fun removeOverlay(overlay: @Composable () -> Unit) {
        overlays.removeAll { overlay == it.overlay }
    }
}

data class Overlay(
    val overlay: @Composable () -> Unit,
    val zIndex: Float = 0f,
) {
    @Composable
    operator fun invoke() {
        overlay()
    }
}