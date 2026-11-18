// === TELOS_PENDING_REVIEW_START: desktop_window_snapping ===
package de.mm20.launcher2.desktopmode

import android.graphics.Rect
import android.util.Log
import de.mm20.launcher2.appmanagement.stealth.TelosPrivilegedDaemon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopWindowManager {

    suspend fun snapTask(taskId: Int, bounds: Rect): Boolean {
        val command = "cmd activity task resize $taskId ${bounds.left} ${bounds.top} ${bounds.right} ${bounds.bottom}"
        return TelosPrivilegedDaemon.exec(command).isSuccess
    }

    suspend fun closeTask(taskId: Int): Boolean {
        val command = "cmd activity remove-task $taskId"
        return TelosPrivilegedDaemon.exec(command).isSuccess
    }
}
// === TELOS_PENDING_REVIEW_END: desktop_window_snapping ===
