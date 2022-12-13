package de.mm20.launcher2.ui.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.lang.reflect.InvocationTargetException

class NotificationShadeController(
    private val context: Context
) {
    @SuppressLint("WrongConstant")
    fun expandNotifications() {
        try {
            val statusBarService = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(statusBarService)
        } catch (e: IllegalAccessException) {
            Log.e("MM20", Log.getStackTraceString(e))
        } catch (e: InvocationTargetException) {
            Log.e("MM20", Log.getStackTraceString(e))
        } catch (e: NoSuchMethodException) {
            Log.e("MM20", Log.getStackTraceString(e))
        } catch (e: ClassNotFoundException) {
            Log.e("MM20", Log.getStackTraceString(e))
        }
    }
}

@Composable
fun rememberNotificationShadeController(): NotificationShadeController {
    val context = LocalContext.current
    return remember(context) {
        NotificationShadeController(context)
    }
}