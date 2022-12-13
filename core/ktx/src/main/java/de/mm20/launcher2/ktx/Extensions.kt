package de.mm20.launcher2.ktx

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import org.json.JSONObject

fun jsonObjectOf(vararg pairs: Pair<String, Any?>): JSONObject {
    val json = JSONObject()
    for ((k, v) in pairs) {
        when (v) {
            is Float -> json.put(k, v.toDouble())
            is Double -> json.put(k, v)
            is Int -> json.put(k, v)
            is Long -> json.put(k, v)
            is Boolean -> json.put(k, v)
            is String -> json.put(k, v)
            else -> json.put(k, v)
        }
    }
    return json
}

@ChecksSdkIntAtLeast(parameter = 0)
fun isAtLeastApiLevel(apiLevel: Int): Boolean {
    return Build.VERSION.SDK_INT >= apiLevel
}

inline fun <reified T> Any?.castTo(): T {
    @Suppress("UNCHECKED_CAST")
    return this as T
}

inline fun <reified T> Any?.castToOrNull(): T? {
    @Suppress("UNCHECKED_CAST")
    return this as? T
}