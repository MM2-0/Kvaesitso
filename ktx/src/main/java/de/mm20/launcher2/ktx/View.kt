package de.mm20.launcher2.ktx

import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlin.coroutines.CoroutineContext

val View.dp: Float
    get() = context.dp

val View.sp: Float
    get() = context.sp

fun View.asViewGroup(): ViewGroup? {
    return this as? ViewGroup
}

val View.lifecycleScope
get() = (context as LifecycleOwner).lifecycleScope

fun View.setPadding(vertical: Int, horizontal: Int) {
    setPadding(vertical, horizontal, vertical, horizontal)
}