package de.mm20.launcher2.comms.telephony

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import de.mm20.launcher2.applock.SettingsDeepLinkContract

object TelosDialer {
    const val CALL_ACTIVITY = "de.mm20.launcher2.ui.comms.CallActivity"
    const val DIALER_ACTIVITY = "de.mm20.launcher2.ui.comms.DialerHandleActivity"
    const val REQUEST_DEFAULT_DIALER = 7101

    fun isDefaultDialer(context: Context): Boolean {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
        return telecom.defaultDialerPackage == context.packageName
    }

    fun requestDefaultDialer(activity: Activity) {
        if (isDefaultDialer(activity)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                activity.startActivityForResult(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER),
                    REQUEST_DEFAULT_DIALER,
                )
                return
            }
        }
        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
        activity.startActivity(intent)
    }

    fun canRequestCallScreening(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.getSystemService(RoleManager::class.java)
                ?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true

    fun isCallScreeningApp(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true

    fun requestCallScreening(activity: Activity) {
        if (!canRequestCallScreening(activity) || isCallScreeningApp(activity)) return
        val manager = activity.getSystemService(RoleManager::class.java) ?: return
        activity.startActivity(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }

    data class SimLine(val handle: PhoneAccountHandle, val label: String)

    fun callCapableSims(context: Context): List<SimLine> {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) return emptyList()
        return try {
            val sms = de.mm20.launcher2.comms.sms.SmsSims.active(context)
            telecom.callCapablePhoneAccounts.mapIndexed { index, handle ->
                val account = telecom.getPhoneAccount(handle)
                val named = sms.getOrNull(index)?.label
                    ?: sms.firstOrNull { it.slot == index }?.label
                SimLine(
                    handle,
                    named
                        ?: account?.label?.toString()?.ifBlank { null }
                        ?: "SIM ${index + 1}",
                )
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun hasDualActiveSims(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return try {
            val manager = context.getSystemService(SubscriptionManager::class.java) ?: return false
            val list = manager.activeSubscriptionInfoList
            (list?.size ?: 0) >= 2
        } catch (_: Exception) {
            false
        }
    }

    fun withClir(number: String, enabled: Boolean, prefix: String): String {
        if (!enabled || number.isEmpty()) return number
        if (android.telephony.PhoneNumberUtils.isEmergencyNumber(number)) return number
        // MMI / USSD / secret codes (*#06#, *21*...#) must reach the network untouched
        if (number.startsWith("*") || number.startsWith("#")) return number
        val p = prefix.ifBlank { "#31#" }
        return if (number.startsWith(p)) number else p + number
    }

    fun placeCall(context: Context, phoneNumber: String, handle: PhoneAccountHandle? = null) {
        if (phoneNumber.isEmpty()) return
        val uri = Uri.fromParts("tel", phoneNumber, null)
        if (isDefaultDialer(context) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val telecom = context.getSystemService(TelecomManager::class.java)
            val extras = Bundle()
            extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
            if (handle != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            telecom?.placeCall(uri, extras)
            return
        }
        context.startActivity(
            Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openDialpad(context: Context, number: String = "") {
        val intent = Intent().apply {
            setClassName(context.packageName, SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
            putExtra(SettingsDeepLinkContract.EXTRA_ROUTE, SettingsDeepLinkContract.ROUTE_COMMS)
            putExtra(SettingsDeepLinkContract.EXTRA_COMMS_TAB, "dialpad")
            if (number.isNotEmpty()) {
                putExtra(SettingsDeepLinkContract.EXTRA_DIAL_NUMBER, number)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
