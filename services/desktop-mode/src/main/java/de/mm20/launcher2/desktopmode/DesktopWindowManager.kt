// === TELOS_PENDING_REVIEW_START: desktop_window_snapping ===
package de.mm20.launcher2.desktopmode

import android.graphics.Rect
import de.mm20.launcher2.ktx.TelosShellEngine

class DesktopWindowManager {

    suspend fun snapTask(taskId: Int, bounds: Rect): Boolean {
        if (!TelosShellEngine.isPrivilegedAvailable()) return false
        val command = "cmd activity task resize $taskId ${bounds.left} ${bounds.top} ${bounds.right} ${bounds.bottom}"
        return TelosShellEngine.execResult(command, timeoutMs = 10_000L).isSuccess
    }

    suspend fun closeTask(taskId: Int): Boolean {
        if (!TelosShellEngine.isPrivilegedAvailable()) return false
        val command = "cmd activity remove-task $taskId"
        return TelosShellEngine.execResult(command, timeoutMs = 10_000L).isSuccess
    }
}
// === TELOS_PENDING_REVIEW_END: desktop_window_snapping ===
