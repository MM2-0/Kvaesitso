package de.mm20.launcher2.badges.providers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Work
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeIcon
import de.mm20.launcher2.icons.PrivateSpace
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.profiles.ProfileManager
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Searchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ProfileBadgeProvider : BadgeProvider, KoinComponent {
    private val profileManager: ProfileManager by inject()

    override fun getBadge(searchable: Searchable): Flow<Badge?> = flow {
        val userHandle = when(searchable) {
            is Application -> searchable.user
            is AppShortcut -> searchable.user
            else -> null
        }
        if (userHandle != null) {
            emitAll(
                profileManager.getProfile(userHandle).map {
                    when(it?.type) {
                        Profile.Type.Work -> WorkProfile
                        Profile.Type.Private -> PrivateProfile
                        Profile.Type.Cloned -> ClonedProfile
                        else -> null
                    }
                }
            )
        } else {
            emit(null)
        }
    }

    companion object {
        private val WorkProfile = Badge(
            icon = BadgeIcon(Icons.Rounded.Work)
        )

        private val PrivateProfile = Badge(
            icon = BadgeIcon(Icons.Rounded.PrivateSpace)
        )

        private val ClonedProfile = Badge(
            icon = BadgeIcon(Icons.Rounded.ContentCopy)
        )
    }
}