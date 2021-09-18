package de.mm20.launcher2.ui

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

@Composable
fun pluralResource(@PluralsRes id: Int, quantity: Int): String {
    return resources().getQuantityString(id, quantity)
}

@Composable
fun pluralResource(@PluralsRes id: Int, quantity: Int, vararg formatArgs: Any): String {
    return resources().getQuantityString(id, quantity, *formatArgs)
}

@Composable
private fun resources(): Resources {
    LocalConfiguration.current
    return LocalContext.current.resources
}