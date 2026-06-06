package de.mm20.launcher2.searchactions.actions

import android.content.Context
import android.os.UserHandle
import android.os.UserManager
import de.mm20.launcher2.ktx.isAtLeastApiLevel

data class PrivateSpaceLockAction(
    override val label: String,
    val isLocked: Boolean,
    val userHandle: UserHandle,
) : SearchAction {
    override val icon = SearchActionIcon.PrivateSpace
    override val iconColor = 0
    override val customIcon = null

    override fun start(context: Context) {
        if (isAtLeastApiLevel(28)) {
            context.getSystemService(UserManager::class.java)
                ?.requestQuietModeEnabled(!isLocked, userHandle)
        }
    }
}
