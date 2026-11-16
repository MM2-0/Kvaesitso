/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.nip02FollowList.FollowAction
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents the current follow relationship status.
 *
 * @property isFollowing Whether the target user is currently followed
 * @property contactList The current ContactListEvent (NIP-02 kind 3)
 */
@Immutable
data class FollowStatus(
    val isFollowing: Boolean,
    val contactList: ContactListEvent?,
)

/**
 * Reactive state for tracking follow/unfollow status and actions.
 *
 * Combines current follow status with action state (loading, success, error)
 * using LoadingState pattern. Delegates business logic to FollowAction.
 *
 * Usage:
 * ```
 * val followState = FollowState(myPubKeyHex)
 *
 * // Update when contact list arrives from relay
 * followState.updateContactList(contactListEvent, targetPubKeyHex)
 *
 * // Follow action
 * followState.setFollowLoading()
 * try {
 *     val updated = FollowAction.follow(targetPubKeyHex, signer, contactList)
 *     relayManager.broadcast(updated)
 *     followState.setFollowSuccess(updated, targetPubKeyHex)
 * } catch (e: Exception) {
 *     followState.setFollowError(e.message ?: "Follow failed")
 * }
 *
 * // Observe in UI
 * when (val state = followState.state.collectAsState().value) {
 *     is LoadingState.Idle -> { /* Not loaded yet */ }
 *     is LoadingState.Loading -> CircularProgressIndicator()
 *     is LoadingState.Success -> {
 *         val status = state.data
 *         if (status.isFollowing) UnfollowButton() else FollowButton()
 *     }
 *     is LoadingState.Error -> ErrorMessage(state.message)
 * }
 * ```
 *
 * @property myPubKeyHex The current user's public key hex (for context)
 */
@Stable
class FollowState(
    private val myPubKeyHex: String,
) {
    private val _state = MutableStateFlow<LoadingState<FollowStatus>>(LoadingState.Idle)
    val state: StateFlow<LoadingState<FollowStatus>> = _state.asStateFlow()

    /**
     * Updates the follow status based on a ContactListEvent.
     *
     * Checks if targetPubKeyHex is in the contact list's p-tags.
     *
     * @param event The ContactListEvent (NIP-02 kind 3)
     * @param targetPubKeyHex The public key of the user to check
     */
    fun updateContactList(
        event: ContactListEvent,
        targetPubKeyHex: String,
    ) {
        val isFollowing = FollowAction.isFollowing(targetPubKeyHex, event)
        _state.value = LoadingState.Success(FollowStatus(isFollowing, event))
    }

    /**
     * Sets the state to Loading (follow/unfollow action in progress).
     *
     * Call this before initiating a follow/unfollow action.
     */
    fun setFollowLoading() {
        _state.value = LoadingState.Loading
    }

    /**
     * Sets the state to Success with updated follow status.
     *
     * Call this after successfully broadcasting a follow/unfollow event.
     *
     * @param newContactList The updated ContactListEvent
     * @param targetPubKeyHex The public key of the user that was followed/unfollowed
     */
    fun setFollowSuccess(
        newContactList: ContactListEvent,
        targetPubKeyHex: String,
    ) {
        val isFollowing = FollowAction.isFollowing(targetPubKeyHex, newContactList)
        _state.value = LoadingState.Success(FollowStatus(isFollowing, newContactList))
    }

    /**
     * Sets the state to Error.
     *
     * Call this if follow/unfollow action fails.
     *
     * @param message The error message
     * @param throwable Optional throwable for debugging
     */
    fun setFollowError(
        message: String,
        throwable: Throwable? = null,
    ) {
        _state.value = LoadingState.Error(message, throwable)
    }

    /**
     * Gets the current follow status, if loaded.
     *
     * @return FollowStatus if state is Success, null otherwise
     */
    fun currentStatusOrNull(): FollowStatus? = state.value.dataOrNull()

    /**
     * Gets the current ContactListEvent, if loaded.
     *
     * @return ContactListEvent if state is Success, null otherwise
     */
    fun currentContactListOrNull(): ContactListEvent? = currentStatusOrNull()?.contactList

    /**
     * Checks if currently following, if loaded.
     *
     * @return true if following, false if not following or not loaded
     */
    fun isFollowing(): Boolean = currentStatusOrNull()?.isFollowing ?: false
}
