package de.mm20.launcher2.ui.theme.typography

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.*
import de.mm20.launcher2.ui.theme.typography.fontfamily.getDeviceHeadlineFontFamily

fun getDeviceDefaultTypography(context: Context): Typography {
    return makeTypography(headlineFamily = getDeviceHeadlineFontFamily(context))
}