package de.mm20.launcher2.ktx

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat

val Context.dp: Float
    get() = resources.displayMetrics.density


val Context.sp: Float
    get() = resources.displayMetrics.scaledDensity

fun Context.checkPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}


fun Context.tryStartActivity(intent: Intent, bundle: Bundle? = null): Boolean {
    return try {
        startActivity(intent, bundle)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
