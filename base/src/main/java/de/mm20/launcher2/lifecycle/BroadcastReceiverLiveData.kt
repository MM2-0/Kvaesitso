package de.mm20.launcher2.lifecycle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.LiveData

class BroadcastReceiverLiveData<T>(
    context: Context,
    private val intentFilter: IntentFilter,
    private val transformFunction: (Context, Intent) -> T
): LiveData<T>() {

    private val context = context.applicationContext
    private val receiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val newValue = transformFunction(context, intent)
            postValue(newValue)
        }

    }

    override fun onActive() {
        super.onActive()
        context.registerReceiver(receiver, intentFilter)
    }

    override fun onInactive() {
        super.onInactive()
        context.unregisterReceiver(receiver)
    }
}