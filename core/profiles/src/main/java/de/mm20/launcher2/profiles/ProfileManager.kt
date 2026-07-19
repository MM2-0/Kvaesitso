package de.mm20.launcher2.profiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.LauncherUserInfo
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ProfileWithState(
    val profile: Profile,
    val state: Profile.State,
)

class ProfileManager(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
) {
    private companion object {
        private const val TAG = "ProfileManager"
    }

    private val mutex = Mutex()

    private val userManager = context.getSystemService<UserManager>()!!
    private val launcherApps = context.getSystemService<LauncherApps>()!!

    private val scope = CoroutineScope(Dispatchers.Default + Job())


    private val profileMap =
        MutableStateFlow(mapOf<Profile.Type, ProfileWithState>())

    /**
     * List of all profiles, sorted by type (Personal, Work, Private)
     */
    val profiles: Flow<List<Profile>> = profileMap.map {
        it.values.map { it.profile }.sortedBy { it.type }
    }

    val profileStates: Flow<Map<Profile, Profile.State>> = profileMap.map {
        it.values.associate { it.profile to it.state }
    }

    /**
     * List of profiles that are currently unlocked
     */
    val unlockedProfiles: Flow<List<Profile>> = profileMap.map {
        it.values.mapNotNull {
            if (it.state.locked) null else it.profile
        }
    }

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                scope.launch {
                    refreshProfiles()
                }
            }
        }
        context.registerReceiver(
            receiver, IntentFilter().apply {
                addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
                addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
                addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
                if (isAtLeastApiLevel(34)) {
                    addAction(Intent.ACTION_PROFILE_ADDED)
                    addAction(Intent.ACTION_PROFILE_REMOVED)
                }
                if (isAtLeastApiLevel(31)) {
                    addAction(Intent.ACTION_PROFILE_ACCESSIBLE)
                    addAction(Intent.ACTION_PROFILE_INACCESSIBLE)
                }

            }
        )
        scope.launch {
            if (isAtLeastApiLevel(35)) {
                permissionsManager.hasPermission(PermissionGroup.ManageProfiles).collectLatest {
                    refreshProfiles()
                }
            } else {
                refreshProfiles()
            }
        }
    }

    private suspend fun refreshProfiles() {
        mutex.withLock {
            val profiles = mutableMapOf<Profile.Type, ProfileWithState>()

            for (userHandle in launcherApps.profiles) {
                val serial = userManager.getSerialNumberForUser(userHandle)
                if (android.os.Build.MANUFACTURER == "samsung" && serial == 150L) continue // Hide Samsung Secure Folder

                val type = getProfileType(userHandle)

                if (profiles[type] == null) {
                    profiles[type] = ProfileWithState(
                        Profile(
                            type = getProfileType(userHandle),
                            userHandle = userHandle,
                            serial = serial,
                        ),
                        getProfileStateByUserHandle(userHandle),
                    )
                }
            }
            profileMap.value = profiles
        }
    }

    /**
     * Returns the profile for the given user handle, or null if it doesn't exist.
     */
    fun getProfileByUserHandle(userHandle: UserHandle): Flow<Profile?> {
        return profileMap.map {
            it.values.find { it.profile.userHandle == userHandle }?.profile
        }
    }

    /**
     * Returns the profile of the given type, or null if it doesn't exist.
     */
    fun getProfile(profileType: Profile.Type): Profile? {
        return profileMap.value[profileType]?.profile
    }

    /**
     * Returns the state of the given profile, or null if it doesn't exist.
     */
    fun getProfileState(profile: Profile): Flow<Profile.State?> {
        return profileMap.map {
            it[profile.type]?.state
        }
    }

    /**
     * Returns the current state of the given profile, or null if it doesn't exist.
     */
    fun getCurrentProfileState(profile: Profile): Profile.State? {
        return profileMap.value[profile.type]?.state
    }


    /**
     * Tries to unlock the given profile. Silently fails when there is an error.
     */
    @RequiresApi(28)
    fun unlockProfile(profile: Profile) {
        try {
            userManager.requestQuietModeEnabled(false, profile.userHandle)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Unable to unlock profile ${profile.serial}", e)
        }
    }

    /**
     * Tries to lock the given profile. Silently fails when there is an error.
     */
    @RequiresApi(28)
    fun lockProfile(profile: Profile) {
        try {
            userManager.requestQuietModeEnabled(true, profile.userHandle)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Unable to lock profile ${profile.serial}", e)
        }
    }

    private fun getProfileType(userHandle: UserHandle): Profile.Type {
        if (isAtLeastApiLevel(35)) {
            val launcherUserInfo = launcherApps.getLauncherUserInfo(userHandle)
            return when (launcherUserInfo?.userType) {
                UserManager.USER_TYPE_PROFILE_PRIVATE -> Profile.Type.Private
                UserManager.USER_TYPE_PROFILE_MANAGED -> Profile.Type.Work
                else -> Profile.Type.Personal

            }
        }
        return if (userHandle == Process.myUserHandle()) Profile.Type.Personal else Profile.Type.Work
    }

    private fun getProfileStateByUserHandle(userHandle: UserHandle): Profile.State {
        val locked = !userManager.isUserUnlocked(userHandle)
        val hidden = if (isAtLeastApiLevel(36) && locked) {
            launcherApps.getLauncherUserInfo(userHandle)
                ?.userConfig
                ?.getBoolean(LauncherUserInfo.PRIVATE_SPACE_ENTRYPOINT_HIDDEN, false)
                ?: false
        } else {
            false
        }
        return Profile.State(locked = locked, hidden = hidden)
    }

}
