package de.mm20.launcher2.feed

import amirz.aidlbridge.IBridgeCallback
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder

class ServiceConnection: IBridgeCallback.Stub(), ServiceConnection {
    override fun onServiceConnected(
        name: ComponentName?,
        service: IBinder?
    ) {

    }

    override fun onServiceDisconnected(name: ComponentName?) {
    }
}