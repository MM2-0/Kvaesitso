package de.mm20.launcher2.ui.media.akou

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/** Opt-in applies on entry; neither recomposition nor toggling it starts a second sample. */
@Composable
internal fun AkouAutoListenEffect(
    enabled: Boolean,
    ready: Boolean,
    foreground: Boolean,
    attempted: Boolean,
    canRecord: () -> Boolean,
    onAttempt: () -> Unit,
    onListen: suspend () -> Unit,
    onPermissionDenied: () -> Unit,
) {
    val currentEnabled by rememberUpdatedState(enabled)
    LaunchedEffect(ready, foreground) {
        if (ready && foreground && currentEnabled && !attempted) {
            onAttempt()
            if (canRecord()) onListen() else onPermissionDenied()
        }
    }
}
