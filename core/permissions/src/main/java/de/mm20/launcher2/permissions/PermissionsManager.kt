package de.mm20.launcher2.permissions

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ktx.tryStartActivity
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

    fun onResume() {

    }

    fun hasPermission(permissionGroup: PermissionGroup): Flow<Boolean>

    /**
     * Special function for the Notification listener to report its status.
     * May not be called by anything else.
     */
    fun reportNotificationListenerState(running: Boolean)

    /**
     * Special function for the accessibility service to report its status.
     * May not be called by anything else.
     */
    fun reportAccessibilityServiceState(running: Boolean)
}

enum class PermissionGroup {
    Calendar,
    Location,
    Contacts,
    ExternalStorage,
    Notifications,
    AppShortcuts,
    Accessibility,
}

internal class PermissionsManagerImpl(
    private val context: Context
) : PermissionsManager {

    private val pendingPermissionRequests = mutableSetOf<PermissionGroup>()

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
    private val notificationsPermissionState = MutableStateFlow(false)
    private val accessibilityPermissionState = MutableStateFlow(false)
    private val appShortcutsPermissionState = MutableStateFlow(
        checkPermissionOnce(PermissionGroup.AppShortcuts)
    )

    override fun requestPermission(context: AppCompatActivity, permissionGroup: PermissionGroup) {
        when (permissionGroup) {
            PermissionGroup.Calendar -> {
                ActivityCompat.requestPermissions(
                    context,
                    calendarPermissions,
                    permissionGroup.ordinal
                )
            }

            PermissionGroup.Location -> {
                ActivityCompat.requestPermissions(
                    context,
                    locationPermissions,
                    permissionGroup.ordinal
                )
            }

            PermissionGroup.Contacts -> {
                ActivityCompat.requestPermissions(
                    context,
                    contactPermissions,
                    permissionGroup.ordinal
                )
            }

            PermissionGroup.ExternalStorage -> {
                if (isAtLeastApiLevel(Build.VERSION_CODES.R)) {
                    val intent =
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).also {
                            it.data = Uri.parse("package:${context.packageName}")
                        }
                    context.tryStartActivity(intent)
                    pendingPermissionRequests.add(PermissionGroup.ExternalStorage)
                } else {
                    ActivityCompat.requestPermissions(
                        context,
                        externalStoragePermissions,
                        permissionGroup.ordinal
                    )
                }
            }

            PermissionGroup.Notifications -> {
                try {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (e: ActivityNotFoundException) {
                    CrashReporter.logException(e)
                }
            }

            PermissionGroup.AppShortcuts -> {
                context.tryStartActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                pendingPermissionRequests.add(PermissionGroup.AppShortcuts)
            }

            PermissionGroup.Accessibility -> {
                try {
                    context.tryStartActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    pendingPermissionRequests.add(PermissionGroup.Accessibility)
                } catch (e: ActivityNotFoundException) {
                    CrashReporter.logException(e)
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

            PermissionGroup.Notifications -> {
                notificationsPermissionState.value
            }

            PermissionGroup.AppShortcuts -> {
                context.getSystemService<LauncherApps>()?.hasShortcutHostPermission() == true
            }

            PermissionGroup.Accessibility -> {
                accessibilityPermissionState.value
            }
        }
    }

    override fun hasPermission(permissionGroup: PermissionGroup): Flow<Boolean> {
        return when (permissionGroup) {
            PermissionGroup.Calendar -> calendarPermissionState
            PermissionGroup.Location -> locationPermissionState
            PermissionGroup.Contacts -> contactsPermissionState
            PermissionGroup.ExternalStorage -> externalStoragePermissionState
            PermissionGroup.Notifications -> notificationsPermissionState
            PermissionGroup.AppShortcuts -> appShortcutsPermissionState
            PermissionGroup.Accessibility -> accessibilityPermissionState
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
            PermissionGroup.Notifications -> notificationsPermissionState.value = granted
            PermissionGroup.AppShortcuts -> appShortcutsPermissionState.value = granted
            PermissionGroup.Accessibility -> accessibilityPermissionState.value = granted
        }
    }

    override fun onResume() {
        val iterator = pendingPermissionRequests.iterator()
        while (iterator.hasNext()) {
            when (iterator.next()) {
                PermissionGroup.ExternalStorage -> {
                    externalStoragePermissionState.value =
                        checkPermissionOnce(PermissionGroup.ExternalStorage)
                }

                PermissionGroup.AppShortcuts -> {
                    appShortcutsPermissionState.value =
                        checkPermissionOnce(PermissionGroup.AppShortcuts)
                }

                else -> {}
            }
            iterator.remove()
        }
    }

    override fun reportNotificationListenerState(running: Boolean) {
        notificationsPermissionState.value = running
    }

    override fun reportAccessibilityServiceState(running: Boolean) {
        accessibilityPermissionState.value = running
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