package de.mm20.launcher2.ui.ktx

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import kotlin.math.pow

fun LazyListLayoutInfo.blendIntoViewScale(key: Any, degree: Float = 1f): Float =
    visibleItemsInfo.firstOrNull { it.key == key }?.let {

        val itemStart = it.offset
        val itemEnd = it.offset + it.size

        val atLeftEnd = itemStart < viewportStartOffset
        val atRightEnd = itemEnd > viewportEndOffset

        if (!atLeftEnd && !atRightEnd) {
            return 1f
        }

        val factor = 1f - (if (atLeftEnd) viewportStartOffset - itemStart else itemEnd - viewportEndOffset) / it.size.toFloat()

        if (degree != 1f) factor.pow(degree) else factor

    } ?: 1f