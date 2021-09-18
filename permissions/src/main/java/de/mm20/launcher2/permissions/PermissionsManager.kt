package de.mm20.launcher2.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.ktx.isAtLeastApiLevel

object PermissionsManager {

    fun requestPermission(context: Activity, permissionGroup: Int) {
        when (permissionGroup) {
            CALENDAR -> {
                ActivityCompat.requestPermissions(context, calendarPermissions, permissionGroup)
            }
            LOCATION -> {
                ActivityCompat.requestPermissions(context, locationPermissions, permissionGroup)
            }
            CONTACTS -> {
                ActivityCompat.requestPermissions(context, contactPermissions, permissionGroup)
            }
            EXTERNAL_STORAGE -> {
                if (isAtLeastApiLevel(Build.VERSION_CODES.R)) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).also {
                        it.data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } else {
                    ActivityCompat.requestPermissions(context, externalStoragePermissions, permissionGroup)
                }
            }
        }
    }

    fun checkPermission(context: Context, permissionGroup: Int): Boolean {
        return when (permissionGroup) {
            CALENDAR -> {
                calendarPermissions.all { context.checkPermission(it) }
            }
            LOCATION -> {
                locationPermissions.all { context.checkPermission(it) }
            }
            CONTACTS -> {
                contactPermissions.all { context.checkPermission(it) }
            }
            EXTERNAL_STORAGE -> {
                if (isAtLeastApiLevel(Build.VERSION_CODES.R)) {
                    Environment.isExternalStorageManager()
                } else {
                    externalStoragePermissions.all { context.checkPermission(it) }
                }
            }
            else -> false
        }
    }

    const val CALENDAR = 0x1
    private val calendarPermissions = arrayOf(Manifest.permission.READ_CALENDAR)
    const val LOCATION = 0x2
    private val locationPermissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    const val CONTACTS = 0x4
    private val contactPermissions = arrayOf(Manifest.permission.READ_CONTACTS)
    const val EXTERNAL_STORAGE = 0x8
    private val externalStoragePermissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    const val NOTIFICATIONS = 0x10

    const val ALL = 0x12
}