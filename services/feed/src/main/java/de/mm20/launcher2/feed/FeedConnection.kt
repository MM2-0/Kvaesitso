package de.mm20.launcher2.feed

import amirz.aidlbridge.IBridge
import amirz.aidlbridge.IBridgeCallback
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.libraries.launcherclient.ILauncherOverlay
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback
import de.mm20.launcher2.crashreporter.CrashReporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface FeedConnection {
    val available: StateFlow<Boolean>
    val ready: StateFlow<Boolean>

    fun restart()
    fun destroy()

    fun startScroll()
    fun endScroll()
    fun onScroll(progress: Float)
}

internal class FeedConnectionImpl(
    private val activity: AppCompatActivity,
    private val serviceIntent: Intent,
    private val callback: FeedCallback,
) : IBridgeCallback.Stub(), FeedConnection, ServiceConnection, DefaultLifecycleObserver {

    private var isActivityStarted = false
    private var isActivityResumed = false
    private val activityState: Int
        get() {
            return (if (isActivityResumed) 2 else 0) + (if (isActivityStarted) 1 else 0)
        }

    override val available = MutableStateFlow(true)
    override val ready = MutableStateFlow(false)

    private var overlay: ILauncherOverlay? = null

    init {
        start()
    }

    override fun startScroll() {
        try {
            overlay?.startScroll()
        } catch (e: RemoteException) {
            CrashReporter.logException(e)
        }
    }

    override fun endScroll() {
        try {
            overlay?.endScroll()
        } catch (e: RemoteException) {
            CrashReporter.logException(e)
        }
    }

    override fun onScroll(progress: Float) {
        try {
            overlay?.onScroll(progress)
        } catch (e: RemoteException) {
            CrashReporter.logException(e)
        }
    }

    fun start() {
        activity.lifecycle.addObserver(this)
        val service = activity.packageManager.resolveService(serviceIntent, 0)
        if (service?.serviceInfo == null) {
            available.value = false
            ready.value = false
            return
        } else {
            available.value = true
        }
        activity.bindService(serviceIntent, this, Flags)
    }

    override fun restart() {
        destroy()
        start()
    }

    override fun destroy() {
        activity.lifecycle.removeObserver(this)
        try {
            activity.unbindService(this)
        } catch (e: IllegalArgumentException) {
            // Service was not bound
        }
    }

    override fun onServiceConnected(
        name: ComponentName?,
        service: IBinder?
    ) {
        if (service == null) {
            ready.value = false
            available.value = false
            overlay = null
            return
        }

        try {
            if (service.interfaceDescriptor == IBridge.DESCRIPTOR) {
                val bridge = IBridge.Stub.asInterface(service)
                bridge.bindService(this, Flags)
            } else if (service.interfaceDescriptor == ILauncherOverlay.DESCRIPTOR) {
                overlay = ILauncherOverlay.Stub.asInterface(service)
                sendConfig()
                ready.value = true
            } else {
                Log.e(
                    "FeedConnection",
                    "Unknown service descriptor \"${service.interfaceDescriptor}\" for intent $serviceIntent"
                )
                available.value = false
                ready.value = false
                destroy()
            }
        } catch (e: RemoteException) {
            CrashReporter.logException(e)
            ready.value = false
            available.value = false
            destroy()
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Log.w("FeedConnection", "service has been disconnected")
        ready.value = false
        overlay = null
    }

    override fun onBindingDied(name: ComponentName?) {
        super.onBindingDied(name)
        Log.w("FeedConnection", "binding has died :(")
        restart()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        isActivityStarted = true
        overlay?.setActivityState(activityState)
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        isActivityResumed = true
        overlay?.setActivityState(activityState)
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        isActivityResumed = false
        overlay?.setActivityState(activityState)
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        isActivityStarted = false
        overlay?.setActivityState(activityState)

    }

    @Throws(RemoteException::class)
    private fun sendConfig() {
        val layoutParams = activity.window.attributes

        val callback = object : ILauncherOverlayCallback.Stub() {
            override fun overlayScrollChanged(progress: Float) {
                callback.onOverlayScrollChanged(progress)
            }

            override fun overlayStatusChanged(status: Int) {
                Log.d("FeedConnection", "overlayStatusChanged: $status")
            }

        }
        overlay?.windowAttached2(
            Bundle().also {
                it.putParcelable("layout_params", layoutParams)
                it.putParcelable("configuration", activity.resources.configuration);
                it.putInt("client_options", Flags)
            },
            callback,
        )

        overlay?.setActivityState(activityState)

    }

    companion object {
        private const val Flags = Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
    }

}