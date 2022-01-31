package de.mm20.launcher2.ui

import androidx.compose.runtime.Composable
import com.google.android.material.composethemeadapter.MdcTheme
import com.google.android.material.composethemeadapter3.Mdc3Theme


@Composable
fun MdcLauncherTheme(content: @Composable () -> Unit) {
    Mdc3Theme {
        MdcTheme(content = content)
    }
}