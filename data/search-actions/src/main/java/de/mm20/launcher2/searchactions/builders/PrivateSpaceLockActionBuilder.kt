package de.mm20.launcher2.searchactions.builders

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserManager
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.PrivateSpaceLockAction
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon

class PrivateSpaceLockActionBuilder(
    override val label: String,
) : SearchActionBuilder {

    constructor(context: Context) : this(context.getString(R.string.search_query_private_space))

    override val key = "private_space"
    override val icon = SearchActionIcon.PrivateSpace

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        if (!isAtLeastApiLevel(35)) return null

        val keyword = context.getString(R.string.search_query_private_space).lowercase()
        val score = ResultScore.from(
            query = classifiedQuery.text.lowercase(),
            primaryFields = listOf(keyword),
        )
        if (score.score < 0.8f) return null

        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
        val userManager = context.getSystemService(UserManager::class.java) ?: return null

        val privateHandle = launcherApps.profiles.firstOrNull {
            launcherApps.getLauncherUserInfo(it)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE
        } ?: return null

        val isLocked = !userManager.isUserUnlocked(privateHandle)
        return PrivateSpaceLockAction(
            label = context.getString(
                if (isLocked) R.string.search_action_private_space_unlock
                else R.string.search_action_private_space_lock
            ),
            isLocked = isLocked,
            userHandle = privateHandle,
        )
    }
}
