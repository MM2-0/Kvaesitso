package de.mm20.launcher2.searchactions.actions

import android.content.Context
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.profiles.ProfileManager

data class PrivateSpaceLockAction(
    override val label: String,
    val isLocked: Boolean,
    val profile: Profile,
    val profileManager: ProfileManager,
) : SearchAction {
    override val icon = SearchActionIcon.PrivateSpace
    override val iconColor = 0
    override val customIcon = null

    override fun start(context: Context) {
        if (isAtLeastApiLevel(28)) {
            if (isLocked) {
                profileManager.unlockProfile(profile)
            } else {
                profileManager.lockProfile(profile)
            }
        }
    }
}
