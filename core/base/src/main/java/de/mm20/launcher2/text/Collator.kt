package de.mm20.launcher2.text

import android.icu.text.Collator
import kotlin.concurrent.getOrSet


private val threadLocalCollator = ThreadLocal.withInitial { 
    android.icu.text.Collator.getInstance().apply { 
        strength = android.icu.text.Collator.SECONDARY
    }
}

val Collator = threadLocalCollator.getOrSet { 
    android.icu.text.Collator.getInstance().apply { 
        strength = android.icu.text.Collator.SECONDARY
    }
}