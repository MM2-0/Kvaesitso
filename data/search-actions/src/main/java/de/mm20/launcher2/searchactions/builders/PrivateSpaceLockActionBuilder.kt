package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.profiles.ProfileManager
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.PrivateSpaceLockAction
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PrivateSpaceLockActionBuilder(
    override val label: String,
) : SearchActionBuilder, KoinComponent {

    private val profileManager: ProfileManager by inject()

    constructor(context: Context) : this(context.getString(R.string.search_query_private_space))

    override val key = "private_space"
    override val icon = SearchActionIcon.PrivateSpace

    override fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction? {
        if (!isAtLeastApiLevel(35)) return null

        val privateProfile = profileManager.getProfile(Profile.Type.Private) ?: return null
        val profileState = profileManager.getCurrentProfileState(privateProfile) ?: return null

        val keyword = context.getString(R.string.search_query_private_space).lowercase()
        if (keyword != classifiedQuery.text.trim().lowercase()) return null

        return PrivateSpaceLockAction(
            label = context.getString(
                if (profileState.locked) R.string.search_action_private_space_unlock
                else R.string.search_action_private_space_lock
            ),
            isLocked = profileState.locked,
            profile = privateProfile,
            profileManager = profileManager,
        )
    }
}
