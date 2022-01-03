package de.mm20.launcher2.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.ktx.isAtLeastApiLevel

interface PermissionsManager {
    fun requestPermission(context: AppCompatActivity, permissionGroup: PermissionGroup)

    fun checkPermission(permissionGroup: PermissionGroup): Boolean
}

enum class PermissionGroup {
    Calendar,
    Location,
    Contacts,
    ExternalStorage
}

class PermissionsManagerImpl(
    private val context: Context
): PermissionsManager {

    override fun requestPermission(activity: AppCompatActivity, permissionGroup: PermissionGroup) {
        when (permissionGroup) {
            PermissionGroup.Calendar -> {
                ActivityCompat.requestPermissions(activity, calendarPermissions, permissionGroup.ordinal)
            }
            PermissionGroup.Location -> {
                ActivityCompat.requestPermissions(activity, locationPermissions, permissionGroup.ordinal)
            }
            PermissionGroup.Contacts -> {
                ActivityCompat.requestPermissions(activity, contactPermissions, permissionGroup.ordinal)
            }
            PermissionGroup.ExternalStorage -> {
                if (isAtLeastApiLevel(Build.VERSION_CODES.R)) {
                    val intent =
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).also {
                            it.data = Uri.parse("package:${activity.packageName}")
                        }
                    activity.startActivity(intent)
                } else {
                    ActivityCompat.requestPermissions(
                        activity,
                        externalStoragePermissions,
                        permissionGroup.ordinal
                    )
                }
            }
        }
    }

    override fun checkPermission(permissionGroup: PermissionGroup): Boolean {
        return when (permissionGroup) {
            PermissionGroup.Calendar -> {
                calendarPermissions.all { context.checkPermission(it) }
            }
            PermissionGroup.Location -> {
                locationPermissions.all { context.checkPermission(it) }
            }
            PermissionGroup.Contacts -> {
                contactPermissions.all { context.checkPermission(it) }
            }
            PermissionGroup.ExternalStorage -> {
                if (isAtLeastApiLevel(Build.VERSION_CODES.R)) {
                    Environment.isExternalStorageManager()
                } else {
                    externalStoragePermissions.all { context.checkPermission(it) }
                }
            }
            else -> false
        }
    }

    private val calendarPermissions = arrayOf(Manifest.permission.READ_CALENDAR)
    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    private val contactPermissions = arrayOf(Manifest.permission.READ_CONTACTS)
    private val externalStoragePermissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
}