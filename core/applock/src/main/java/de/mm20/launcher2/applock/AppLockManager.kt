package de.mm20.launcher2.applock

import android.Manifest
import android.app.ActivityOptions
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.util.Log
import androidx.core.content.getSystemService
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.Application
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

data class AppLockState(
    val isSupported: Boolean,
    val isEnabled: Boolean,
) {
    companion object {
        val Unsupported = AppLockState(isSupported = false, isEnabled = false)
    }
}

class AppLockManager internal constructor(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
) {
    fun getAppLockState(application: Application): Flow<AppLockState> {
        if (Build.VERSION.SDK_INT_FULL < Build.VERSION_CODES_FULL.CINNAMON_BUN_2 ||
            application.user != Process.myUserHandle()
        ) {
            return flowOf(AppLockState.Unsupported)
        }

        if (context.checkSelfPermission(Manifest.permission.LOCK_APPS) != PackageManager.PERMISSION_GRANTED) {
            return flowOf(AppLockState.Unsupported)
        }

        fun getState(): AppLockState {
            try {
                val packageInfo = context.packageManager.getApplicationInfo(
                    application.componentName.packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_APP_LOCK_INFO)
                )

                return AppLockState(
                    isSupported = packageInfo.isAppLockSupported,
                    isEnabled = packageInfo.isAppLockEnabled
                )
            } catch (e: PackageManager.NameNotFoundException) {
                return AppLockState.Unsupported
            }
        }

        return permissionsManager.hasPermission(PermissionGroup.LockApps).flatMapLatest {
            callbackFlow {
                trySendBlocking(getState())
                val callback = object : LauncherApps.Callback() {
                    override fun onPackageAdded(
                        packageName: String?,
                        user: UserHandle?
                    ) {
                    }

                    override fun onPackageChanged(
                        packageName: String?,
                        user: UserHandle?
                    ) {
                        if (packageName == application.componentName.packageName) {
                            trySendBlocking(getState())
                        }
                    }

                    override fun onPackageRemoved(
                        packageName: String?,
                        user: UserHandle?
                    ) {
                    }

                    override fun onPackagesAvailable(
                        packageNames: Array<out String?>?,
                        user: UserHandle?,
                        replacing: Boolean
                    ) {
                    }

                    override fun onPackagesUnavailable(
                        packageNames: Array<out String?>?,
                        user: UserHandle?,
                        replacing: Boolean
                    ) {
                    }
                }

                val launcherApps = context.getSystemService<LauncherApps>()!!

                launcherApps.registerCallback(
                    callback,
                )


                awaitClose {
                    launcherApps.unregisterCallback(callback)
                }
            }
        }


    }

    fun setAppLockEnabled(
        context: Context,
        application: Application,
        enabled: Boolean
    ): Boolean {
        if (application.user != Process.myUserHandle()) return false
        if (Build.VERSION.SDK_INT_FULL < Build.VERSION_CODES_FULL.CINNAMON_BUN_2) return false

        if (context.checkSelfPermission(Manifest.permission.LOCK_APPS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        val pendingIntent = context.packageManager.getEnableAppLockIntentForPackage(
            application.componentName.packageName,
            enabled
        ) ?: return false

        try {
            pendingIntent.send(
                context, 0, null, null, null, null, ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                    )
                    .toBundle()
            )
            return true
        } catch (e: Exception) {
            Log.e("AppLockManager", "Failed to lock app", e)
            return false
        }
    }
}
