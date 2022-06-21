package de.mm20.launcher2.ktx

import androidx.fragment.app.Fragment

val Fragment.dp: Float
    get() = context?.dp ?: 0f

val Fragment.sp: Float
    get() = context?.sp ?: 0f
