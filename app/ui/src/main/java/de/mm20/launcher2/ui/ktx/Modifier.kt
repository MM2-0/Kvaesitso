package de.mm20.launcher2.ui.ktx

import androidx.compose.ui.Modifier

fun Modifier.conditional(condition: Boolean, other: Modifier): Modifier {
    if (condition) {
        return this then other
    }
    return this
}
