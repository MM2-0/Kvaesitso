package dev.intelia.launcher.ui

import android.content.Intent
import android.service.notification.NotificationListenerService

/** Permission bridge only. Does not read notification payloads or own windows. */
class InteliaIslandService : NotificationListenerService() {
    companion object {
        @Volatile internal var bridgeConnected = false; private set
        @Volatile internal var calibrationActive = false
    }
    private fun refresh() = sendBroadcast(Intent("dev.intelia.launcher.REFRESH_PULSE").setPackage(packageName))
    override fun onListenerConnected() { bridgeConnected = true; refresh() }
    override fun onListenerDisconnected() { bridgeConnected = false; refresh(); super.onListenerDisconnected() }
    override fun onDestroy() { bridgeConnected = false; refresh(); super.onDestroy() }
    override fun dump(fd: java.io.FileDescriptor, writer: java.io.PrintWriter, args: Array<out String>?) {
        writer.println("Pulse mediaBridge=$bridgeConnected calibration=$calibrationActive")
    }
}
