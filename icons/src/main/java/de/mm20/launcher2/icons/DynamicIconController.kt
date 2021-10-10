package de.mm20.launcher2.icons

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import java.lang.ref.WeakReference

class DynamicIconController(val context: Context): LifecycleObserver {

    private var timeReceiver: BroadcastReceiver? = null
    private val registeredIcons = mutableListOf<WeakReference<DynamicLauncherIcon>>()

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun resume() {
        timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                updateAllIcons(context)
            }
        }
        val filter = IntentFilter(Intent.ACTION_TIME_TICK).also {
            it.addAction(Intent.ACTION_TIME_CHANGED)
            it.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(timeReceiver, filter)
        updateAllIcons(context)
    }

    private fun updateAllIcons(context: Context) {
        val iterator = registeredIcons.iterator()
        while (iterator.hasNext()) {
            val iconRef = iterator.next()
            if (iconRef.get() != null) iconRef.get()?.update(context)
            else iterator.remove()
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun pause() {
        try {
            context.unregisterReceiver(timeReceiver)
        } catch (e: IllegalArgumentException) {

        }
        timeReceiver = null
    }

    fun registerIcon(icon: DynamicLauncherIcon) {
        icon.update(context)
        registeredIcons.add(WeakReference(icon))
    }
}