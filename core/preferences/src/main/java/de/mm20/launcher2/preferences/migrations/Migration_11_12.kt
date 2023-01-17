package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.GestureSettings
import de.mm20.launcher2.preferences.Settings.LayoutSettings

class Migration_11_12: VersionedMigration(11, 12) {
    override suspend fun applyMigrations(builder: Settings.Builder): Settings.Builder {
        val oldLayout = builder.appearance.layout
        when(oldLayout) {
            LayoutSettings.Layout.Pager -> {
                builder.setLayout(
                    LayoutSettings.newBuilder()
                        .setBaseLayout(LayoutSettings.Layout.Pager)
                        .setBottomSearchBar(true)
                        .setReverseSearchResults(true)
                )
            }
            LayoutSettings.Layout.PagerReversed -> {
                builder.setLayout(
                    LayoutSettings.newBuilder()
                        .setBaseLayout(LayoutSettings.Layout.PagerReversed)
                        .setBottomSearchBar(true)
                        .setReverseSearchResults(true)
                )
            }
            else -> {
                builder.setLayout(
                    LayoutSettings.newBuilder()
                        .setBaseLayout(LayoutSettings.Layout.PullDown)
                        .setBottomSearchBar(false)
                        .setReverseSearchResults(false)
                )
                    .setGestures(
                        GestureSettings.newBuilder()
                            .setDoubleTap(GestureSettings.GestureAction.LockScreen)
                            .setLongPress(GestureSettings.GestureAction.None)
                            .setSwipeDown(GestureSettings.GestureAction.OpenNotificationDrawer)
                            .setSwipeLeft(GestureSettings.GestureAction.None)
                            .setSwipeRight(GestureSettings.GestureAction.None)
                    )
            }
        }
        return builder
    }
}