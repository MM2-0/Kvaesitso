package de.mm20.launcher2.comms.radio

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.util.Log
import de.mm20.launcher2.ktx.TelosShellEngine
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import rikka.shizuku.Shizuku

object CellularRadio : KoinComponent {
    private const val TAG = "CellularRadio"
    private val commsSettings: CommsSettings by inject()

    suspend fun applyPreferred(context: Context, overrideMode: String? = null): Boolean {
        val snap = commsSettings.snapshot.first()
        val mode = overrideMode ?: snap.preferredNetworkMode
        if (mode == "auto") return true
        val ril = rilConstant(mode)
        val subId = runCatching {
            SubscriptionManager.getDefaultDataSubscriptionId()
        }.getOrDefault(-1)
        val cmd = if (subId > 0) {
            "cmd phone set-allowed-network-types-for-sub $subId $ril"
        } else {
            "cmd phone set-allowed-network-types-for-sub 1 $ril"
        }
        val order = when (snap.networkBackend) {
            "root" -> listOf("root", "shizuku")
            "shizuku" -> listOf("shizuku", "root")
            else -> listOf("shizuku", "root")
        }
        for (backend in order) {
            val ok = when (backend) {
                "shizuku" -> shizuku(cmd)
                else -> root(cmd)
            }
            if (ok) {
                Log.i(TAG, "Set network $mode via $backend")
                return true
            }
        }
        return false
    }

    private fun rilConstant(mode: String): Int = when (mode) {
        "lte" -> 11
        "nr" -> 23
        "nr_lte" -> 24
        else -> 26
    }

    private fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    private suspend fun shizuku(cmd: String): Boolean {
        if (!shizukuReady()) return false
        val res = TelosShellEngine.execResult(cmd, timeoutMs = 10_000L)
        if (!res.isSuccess) {
            Log.w(TAG, "Shizuku network cmd failed: ${res.stderr}")
        }
        return res.isSuccess
    }

    private suspend fun root(cmd: String): Boolean {
        val res = TelosShellEngine.execRootResult(cmd, timeoutMs = 10_000L)
        if (!res.isSuccess) {
            Log.w(TAG, "Root network cmd failed: ${res.stderr}")
        }
        return res.isSuccess
    }
}
