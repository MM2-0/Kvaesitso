package de.mm20.launcher2.ktx

import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

fun UserHandle.getSerialNumber(context: Context): Long {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    return userManager.getSerialNumberForUser(this)
}