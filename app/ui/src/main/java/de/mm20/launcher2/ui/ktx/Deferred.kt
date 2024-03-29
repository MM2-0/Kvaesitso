package de.mm20.launcher2.ui.ktx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Deferred

@Composable
fun <T> Deferred<T>?.asState(initialValue: T): State<T> {
    return produceState(initialValue) {
        if (this@asState != null) {
            value = await()
        }
    }
}