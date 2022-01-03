package de.mm20.launcher2.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface PermissionsManager {
    fun requestPermission(context: AppCompatActivity, permissionGroup: PermissionGroup)

    /**
     * Check if this permission is granted right now without receiving further updates
     * about the granted state.
     * @return true if the given permission group is fully granted
     */
    fun checkPermissionOnce(permissionGroup: PermissionGroup): Boolean

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    )

    fun hasPermission(permissionGroup: PermissionGroup): Flow<Boolean>
}

enum class PermissionGroup {
    Calendar,
    Location,
    Contacts,
    ExternalStorage
}

class PermissionsManagerImpl(
    private val context: Context
) : PermissionsManager {

    private val calendarPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.Calendar)
    )
    private val contactsPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.Contacts)
    )
    private val externalStoragePermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.ExternalStorage)
    )
    private val locationPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.Location)
    )

    override fun requestPermission(activity: AppCompatActivity, permissionGroup: PermissionGroup) {
        when (permissionGroup) {
            PermissionGroup.Calendar -> {
                ActivityCompat.requestPermissions(
                    activity,
                    calendarPermissions,
                    permissionGroup.ordinal
                )
            }
            PermissionGroup.Location -> {
                ActivityCompat.requestPermissions(
                    activity,
                    locationPermissions,
                    permissionGroup.ordinal
                )
            }
            PermissionGroup.Contacts -> {
                ActivityCompat.requestPermissions(
                    activity,
                    contactPermissions,
                    permissionGroup.ordinal
                )
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

    override fun checkPermissionOnce(permissionGroup: PermissionGroup): Boolean {
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
        }
    }

    override fun hasPermission(permissionGroup: PermissionGroup): Flow<Boolean> {
        return when (permissionGroup) {
            PermissionGroup.Calendar -> calendarPermissionState
            PermissionGroup.Location -> locationPermissionState
            PermissionGroup.Contacts -> contactsPermissionState
            PermissionGroup.ExternalStorage -> externalStoragePermissionState
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val permissionGroup = PermissionGroup.values().getOrNull(requestCode) ?: return
        val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        when (permissionGroup) {
            PermissionGroup.Calendar -> calendarPermissionState.value = granted
            PermissionGroup.Location -> locationPermissionState.value = granted
            PermissionGroup.Contacts -> contactsPermissionState.value = granted
            PermissionGroup.ExternalStorage -> externalStoragePermissionState.value = granted
        }
    }

    companion object {
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
}