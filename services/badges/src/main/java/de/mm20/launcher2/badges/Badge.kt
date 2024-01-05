package de.mm20.launcher2.badges

import android.graphics.drawable.Drawable
import android.util.Log

interface Badge {
    val number: Int?
    val progress: Float?
    val iconRes: Int?
    val icon: Drawable?
}

fun Badge(
    number: Int? = null,
    progress: Float? = null,
    iconRes: Int? = null,
    icon: Drawable? = null
): Badge = MutableBadge(number, progress, iconRes, icon)

internal data class MutableBadge(
    override var number: Int? = null,
    override var progress: Float? = null,
    override var iconRes: Int? = null,
    override var icon: Drawable? = null
): Badge

fun Collection<Badge>.combine(): Badge? {
    if (isEmpty()) return null
    val badge = MutableBadge()
    var progresses = 0
    forEach {
        if (it.icon != null && badge.icon == null) badge.icon = it.icon
        if (it.iconRes != null && badge.iconRes == null) badge.iconRes = it.iconRes
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