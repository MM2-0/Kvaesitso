package de.mm20.launcher2.ktx

import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

private val cache = mutableMapOf<Int, Long>()

fun UserHandle.getSerialNumber(context: Context): Long {
    return cache.getOrPut(hashCode()) {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        userManager.getSerialNumberForUser(this)
    }
}