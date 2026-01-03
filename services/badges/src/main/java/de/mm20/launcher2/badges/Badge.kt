package de.mm20.launcher2.badges

import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector

sealed interface BadgeIcon {
    @JvmInline
    value class Drawable(val drawable: android.graphics.drawable.Drawable): BadgeIcon

    @JvmInline
    value class Vector(@DrawableRes val iconRes: Int): BadgeIcon
}

fun BadgeIcon(drawable: Drawable): BadgeIcon = BadgeIcon.Drawable(drawable)

fun BadgeIcon(@DrawableRes iconRes: Int): BadgeIcon = BadgeIcon.Vector(iconRes)

interface Badge {
    val number: Int?
    val progress: Float?
    val icon: BadgeIcon?
}

fun Badge(
    number: Int? = null,
    progress: Float? = null,
    icon: BadgeIcon? = null
): Badge = MutableBadge(number, progress, icon)

internal data class MutableBadge(
    override var number: Int? = null,
    override var progress: Float? = null,
    override var icon: BadgeIcon? = null
): Badge

fun Collection<Badge>.combine(): Badge? {
    if (isEmpty()) return null
    val badge = MutableBadge()
    var progresses = 0
    forEach {
        if (it.icon != null && badge.icon == null) badge.icon = it.icon
        it.number?.let { a ->
            badge.number?.let { b -> badge.number = a + b } ?: run {
                badge.number = a
            }
        }
        it.progress?.let { a ->
            badge.progress?.let { b ->
                badge.progress = a + b
            } ?: run {
                badge.progress = a
            }
            progresses++
        }
    }
    if (progresses > 0) {
        badge.progress?.let { badge.progress = it / progresses }
    }
    return badge
}