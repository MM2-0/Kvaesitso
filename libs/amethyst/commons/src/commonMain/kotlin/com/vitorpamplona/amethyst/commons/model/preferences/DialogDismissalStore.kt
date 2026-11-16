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
package com.vitorpamplona.amethyst.commons.model.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import okio.IOException

/**
 * What this account has dismissed and does not want shown again —
 * confirmation dialogs, individual polls and invites, the donation card.
 *
 * Everything here defaults to "not dismissed", so a lost value costs the user
 * one more prompt rather than hiding something they never dismissed.
 *
 * Defaults match what the SharedPreferences implementation returned for a
 * missing key, so an account that never touched a setting behaves identically
 * before and after the migration.
 */
data class DialogDismissal(
    val hideDeleteRequestDialog: Boolean = false,
    val hideBlockAlertDialog: Boolean = false,
    val hideNip17WarningDialog: Boolean = false,
    val hideCommunityRulesViolations: Boolean = false,
    val dismissedPollNoteIds: Set<String> = emptySet(),
    val dismissedChannelInvites: Set<String> = emptySet(),
    val mutedPublicChats: Set<String> = emptySet(),
    val hasDonatedInVersion: Set<String> = emptySet(),
    val viewedPollResultNoteIdsJson: String? = null,
    val webOfTrustNudgeHiddenUntil: Long = 0L,
)

/** Reads and writes [DialogDismissal] in the account's DataStore. */
class DialogDismissalStore(
    private val store: DataStore<Preferences>,
) {
    companion object {
        val hideDeleteRequestDialog = booleanPreferencesKey("hide_delete_request_dialog")
        val hideBlockAlertDialog = booleanPreferencesKey("hide_block_alert_dialog")
        val hideNip17WarningDialog = booleanPreferencesKey("hide_nip24_warning_dialog")
        val hideCommunityRulesViolations = booleanPreferencesKey("hideCommunityRulesViolations")
        val dismissedPollNoteIds = stringSetPreferencesKey("dismissedPollNoteIds")
        val dismissedChannelInvites = stringSetPreferencesKey("dismissedChannelInvites")
        val mutedPublicChats = stringSetPreferencesKey("mutedPublicChats")
        val hasDonatedInVersion = stringSetPreferencesKey("hasDonatedInVersion")
        val viewedPollResultNoteIdsJson = stringPreferencesKey("viewedPollResultNoteIds")
        val webOfTrustNudgeHiddenUntil = longPreferencesKey("webOfTrustNudgeHiddenUntil")

        /**
         * What the `secret_keeper_<npub>` file called these, for the one-shot copy.
         *
         * Five of the nine were renamed on the way in, so the pairs below are
         * not derivable from either side alone.
         */
        val legacyTable =
            LegacyKeyTable(
                "migrated.dialogDismissal",
                listOf(
                    LegacyBooleanKey("hide_delete_request_dialog", hideDeleteRequestDialog),
                    LegacyBooleanKey("hide_block_alert_dialog", hideBlockAlertDialog),
                    LegacyBooleanKey("hide_nip24_warning_dialog", hideNip17WarningDialog),
                    LegacyBooleanKey("hideCommunityRulesViolations", hideCommunityRulesViolations),
                    LegacyStringSetKey("dismissed_poll_note_ids", dismissedPollNoteIds),
                    LegacyStringSetKey("dismissed_channel_invites", dismissedChannelInvites),
                    LegacyStringSetKey("muted_public_chats", mutedPublicChats),
                    LegacyStringSetKey("has_donated_in_version", hasDonatedInVersion),
                    LegacyStringKey("viewed_poll_result_note_ids", viewedPollResultNoteIdsJson),
                ),
            )
    }

    suspend fun load(): DialogDismissal {
        val prefs =
            store.data
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .first()

        return DialogDismissal(
            hideDeleteRequestDialog = prefs[hideDeleteRequestDialog] ?: false,
            hideBlockAlertDialog = prefs[hideBlockAlertDialog] ?: false,
            hideNip17WarningDialog = prefs[hideNip17WarningDialog] ?: false,
            hideCommunityRulesViolations = prefs[hideCommunityRulesViolations] ?: false,
            dismissedPollNoteIds = prefs[dismissedPollNoteIds] ?: emptySet(),
            dismissedChannelInvites = prefs[dismissedChannelInvites] ?: emptySet(),
            mutedPublicChats = prefs[mutedPublicChats] ?: emptySet(),
            hasDonatedInVersion = prefs[hasDonatedInVersion] ?: emptySet(),
            viewedPollResultNoteIdsJson = prefs[viewedPollResultNoteIdsJson],
            webOfTrustNudgeHiddenUntil = prefs[webOfTrustNudgeHiddenUntil] ?: 0L,
        )
    }

    /** Writes the whole group in one edit, so a crash cannot half-apply it. */
    suspend fun save(value: DialogDismissal) {
        store.edit { prefs ->
            prefs[hideDeleteRequestDialog] = value.hideDeleteRequestDialog
            prefs[hideBlockAlertDialog] = value.hideBlockAlertDialog
            prefs[hideNip17WarningDialog] = value.hideNip17WarningDialog
            prefs[hideCommunityRulesViolations] = value.hideCommunityRulesViolations
            prefs[dismissedPollNoteIds] = value.dismissedPollNoteIds
            prefs[dismissedChannelInvites] = value.dismissedChannelInvites
            prefs[mutedPublicChats] = value.mutedPublicChats
            prefs[hasDonatedInVersion] = value.hasDonatedInVersion
            prefs[webOfTrustNudgeHiddenUntil] = value.webOfTrustNudgeHiddenUntil
            value.viewedPollResultNoteIdsJson.let { if (it != null) prefs[viewedPollResultNoteIdsJson] = it else prefs.remove(viewedPollResultNoteIdsJson) }
        }
    }
}
