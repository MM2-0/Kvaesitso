/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3

import android.annotation.TargetApi
import android.content.ComponentName
import android.content.Intent
import android.graphics.RectF
import android.os.*
import android.util.Log
import android.view.SurfaceControl
import de.mm20.launcher2.ktx.isAtLeastApiLevel

/**
 * Class to encapsulate the handshake protocol between Launcher and gestureNav.
 */
class GestureNavContract(
    val componentName: ComponentName,
    val user: UserHandle,
    private val mCallback: Message
) {
    /**
     * Sends the position information to the receiver
     */
    @TargetApi(Build.VERSION_CODES.R)
    fun sendEndPosition(position: RectF? = null, surfaceControl: SurfaceControl? = null) {
        val result = Bundle()
        result.putParcelable(EXTRA_ICON_POSITION, position)
        result.putParcelable(EXTRA_ICON_SURFACE, surfaceControl)
        val callback = Message.obtain()
        callback.copyFrom(mCallback)
        callback.data = result
        try {
            callback.replyTo.send(callback)
        } catch (e: RemoteException) {
            Log.e(TAG, "Error sending icon position", e)
        }
    }

    companion object {
        private const val TAG = "GestureNavContract"
        const val EXTRA_GESTURE_CONTRACT = "gesture_nav_contract_v1"
        const val EXTRA_ICON_POSITION = "gesture_nav_contract_icon_position"
        const val EXTRA_ICON_SURFACE = "gesture_nav_contract_surface_control"
        const val EXTRA_REMOTE_CALLBACK = "android.intent.extra.REMOTE_CALLBACK"

        /**
         * Clears and returns the GestureNavContract if it was present in the intent.
         */
        fun fromIntent(intent: Intent): GestureNavContract? {
            if (!isAtLeastApiLevel(Build.VERSION_CODES.R)) {
                return null
            }
            val extras = intent.getBundleExtra(EXTRA_GESTURE_CONTRACT)
                ?: return null
            intent.removeExtra(EXTRA_GESTURE_CONTRACT)
            val componentName = extras.getParcelable<ComponentName>(Intent.EXTRA_COMPONENT_NAME)
            val userHandle = extras.getParcelable<UserHandle>(Intent.EXTRA_USER)
            val callback = extras.getParcelable<Message>(EXTRA_REMOTE_CALLBACK)
            return if (componentName != null && userHandle != null && callback != null && callback.replyTo != null
            ) {
                GestureNavContract(componentName, userHandle, callback)
            } else null
        }
    }
}
