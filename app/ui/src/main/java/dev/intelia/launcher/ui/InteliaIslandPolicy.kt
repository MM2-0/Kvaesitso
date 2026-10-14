package dev.intelia.launcher.ui

internal fun showInteliaIsland(enabled: Boolean, overlayAllowed: Boolean, interactive: Boolean, locked: Boolean, hidden: Boolean) =
    enabled && overlayAllowed && interactive && !locked && !hidden
