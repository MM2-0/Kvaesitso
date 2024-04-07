package de.mm20.launcher2.ui.base

import androidx.compose.runtime.Composable

@Composable
fun ProvideCompositionLocals(content: @Composable () -> Unit) {
    ProvideCurrentTime {
        ProvideSettings {
            ProvideAppWidgetHost {
                content()
            }
        }
    }
}