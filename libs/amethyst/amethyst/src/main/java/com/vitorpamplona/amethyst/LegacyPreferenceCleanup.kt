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
package com.vitorpamplona.amethyst

import androidx.datastore.preferences.core.Preferences
import com.vitorpamplona.amethyst.commons.model.preferences.AccountIdentityStore
import com.vitorpamplona.amethyst.commons.model.preferences.AccountSecrets
import com.vitorpamplona.amethyst.commons.model.preferences.DialogDismissalStore
import com.vitorpamplona.amethyst.commons.model.preferences.FeedVisibilityStore
import com.vitorpamplona.amethyst.commons.model.preferences.GeohashIdentitySecrets
import com.vitorpamplona.amethyst.commons.model.preferences.LatestEventCacheStore
import com.vitorpamplona.amethyst.commons.model.preferences.LegacyAccountSecretNames
import com.vitorpamplona.amethyst.commons.model.preferences.LegacyKeyTable
import com.vitorpamplona.amethyst.commons.model.preferences.LegacyPreferenceSource
import com.vitorpamplona.amethyst.commons.model.preferences.NotificationPrefsStore
import com.vitorpamplona.amethyst.commons.model.preferences.RelayAuthStore
import com.vitorpamplona.amethyst.commons.model.preferences.TopNavFollowListStore
import com.vitorpamplona.amethyst.commons.model.preferences.UploadSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.readLegacyGeohashIdentity
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * How every key that can appear in a `secret_keeper_<npub>` file is accounted
 * for.
 *
 * Together with [LegacyAccountSecretNames], these two lists are what let
 * [LegacyPreferenceCleanup] treat any *other* key in the file as a reason not
 * to delete it. `LegacyKeyCoverageTest` holds them to covering all of
 * `PrefKeys`, so a key added later cannot quietly fall outside both.
 */
internal object LegacyAccountKeys {
    /**
     * The one-shot copies out of the account's legacy file.
     *
     * Each store owns the table of legacy names it came from, so the copy and
     * the check that the copy happened read the same list — see [LegacyKeyTable].
     */
    val tables =
        listOf(
            TopNavFollowListStore.legacyTable,
            LatestEventCacheStore.legacyTable,
            UploadSettingsStore.legacyTable,
            DialogDismissalStore.legacyTable,
            RelayAuthStore.legacyTable,
            FeedVisibilityStore.legacyTable,
            NotificationPrefsStore.legacyTable,
            AccountIdentityStore.legacyTable,
        )

    /**
     * Keys that are deliberately not carried across.
     *
     * Each costs something once and nothing after, and none is worth the code
     * to move it: queued attestations go unpublished, the one-shot
     * Global -> Curated notification rewrite runs one more time, and every feed
     * reads as unread once. `use_proxy` and `proxy_port` are only ever removed,
     * never read, and `tor_settings` has no reader left at all.
     */
    val accepted =
        setOf(
            PrefKeys.PENDING_ATTESTATIONS,
            PrefKeys.NOTIF_GLOBAL_TO_CURATED_MIGRATED,
            PrefKeys.LAST_READ_PER_ROUTE,
            PrefKeys.USE_PROXY,
            PrefKeys.PROXY_PORT,
            PrefKeys.TOR_SETTINGS,
        )
}

/** What [LegacyPreferenceCleanup] did, and why. */
sealed interface LegacyCleanupResult {
    /** There was no legacy file for this account. */
    data object NothingToDelete : LegacyCleanupResult

    data object Deleted : LegacyCleanupResult

    /** Nothing was touched. Each reason names one thing that would have been lost. */
    data class Kept(
        val reasons: List<String>,
    ) : LegacyCleanupResult
}

/** The per-account legacy file, as this needs it. */
interface LegacyAccountFiles {
    fun source(npub: String): LegacyPreferenceSource

    /**
     * The account's OTHER legacy file: the location-chat identity, which lives
     * in `secret_keeper_<pubkey hex>` rather than `secret_keeper_<npub>`
     * because that is the key its writer passed.
     *
     * Separate from [source] because [delete] removes both, and a check that
     * read the npub file for these keys would never find them — they are not
     * in it. That mistake was made once already.
     */
    fun geohashSource(npub: String): LegacyPreferenceSource

    fun exists(npub: String): Boolean

    /** Returns false when there was nothing to delete. */
    suspend fun delete(npub: String): Boolean
}

/** What the current, encrypted stores hold for an account. */
interface MigratedSecrets {
    /** Null when this account has not been copied across yet. */
    suspend fun secrets(npub: String): AccountSecrets?

    /** Null only when the account genuinely has no private key. Throws when the store is unreadable. */
    suspend fun privateKey(npub: String): String?

    /**
     * The location-chat identity, or null when it has not been copied across.
     *
     * Its own question because it migrates out of its own file: [AccountSecrets]
     * being present says nothing about whether this was carried over.
     */
    suspend fun geohashIdentity(npub: String): GeohashIdentitySecrets?
}

/**
 * Deletes an account's `secret_keeper_<npub>` file, but only once it can prove
 * nothing in it would be lost.
 *
 * # Why the check is not one rule
 *
 * The two halves of the migration are in different states, and asking the same
 * question of both would give the wrong answer for one of them.
 *
 * The plain per-account groups — settings, dialogs, feeds, cached events —
 * stopped being written to the legacy file when they moved, so that file is a
 * frozen snapshot of the day they migrated. Comparing values would flag every
 * setting the user has changed since. What is actually being asked of them is
 * "did the copy run", and [LegacyKeyTable.hasRun] answers it exactly:
 * `CopyOnceMigration` writes the values and its marker as a single
 * `Preferences`, committed atomically, so the marker cannot be set without them.
 *
 * The private key takes the strongest form: read it back and require it to
 * equal the legacy one. That comparison stays valid forever, because an npub is
 * derived from its private key, so the key for a given npub can never change.
 *
 * The secrets cannot be compared, and the reason is worth stating because the
 * obvious reading is wrong. They *are* dual-written today — but this whole
 * check only runs once [legacyWritesRetired] is true, and from that release on
 * the legacy copy is frozen while the live one keeps moving. An account that
 * re-pairs a bunker or adds a wallet after upgrading would then differ from the
 * file forever and never have it deleted. So they are gated the same way as the
 * plain groups: on the copy having run, which
 * [AccountSecretsEncryptedStores.loadSecrets] reports by returning non-null
 * only once its marker is set, and it writes that marker last.
 *
 * # Why an unrecognised key blocks
 *
 * A list of keys to check, maintained by hand, fails silently in the one
 * direction that matters: a key added later that no migration carries. So the
 * check runs the other way round — every key *in the file* must be claimed by
 * a table, be one of the secrets, or be on [accepted], the short list of
 * deliberate losses. Anything else stops the deletion and says so by name.
 *
 * # Cost
 *
 * [LegacyPreferenceSource.keys] goes through `EncryptedSharedPreferences.all`,
 * which decrypts every value in the file — there is no keys-only API. It is
 * called from the one place an account load is not already cached, so it costs
 * at most once per account per process, and nothing at all while
 * [legacyWritesRetired] is false.
 *
 * # Why deletion also waits on the legacy writes
 *
 * [legacyWritesRetired] is the other half. While the app still mirrors into
 * this file on every save, deleting it achieves nothing — the next save
 * recreates it, with a subset of what was there. Worse, it would look like it
 * had worked. So the file is only removed once it is no longer being written,
 * which is a separate release from this one.
 */
class LegacyPreferenceCleanup(
    private val tables: List<LegacyKeyTable>,
    private val accepted: Set<String>,
    private val files: LegacyAccountFiles,
    private val currentStore: suspend (String) -> Preferences,
    private val secrets: MigratedSecrets,
    private val legacyWritesRetired: Boolean,
) {
    companion object {
        private const val TAG = "LegacyPreferenceCleanup"

        const val STILL_WRITTEN = "the legacy file is still written on every save"
    }

    private val claimed: Set<String> = tables.flatMapTo(mutableSetOf()) { it.legacyNames } + LegacyAccountSecretNames.all

    /**
     * Everything that would be lost by deleting this account's legacy file.
     * Empty means nothing would be.
     *
     * A store that cannot be read is a reason, never a pass: the whole point is
     * to be sure, and "the check itself failed" is not sure.
     */
    suspend fun verify(npub: String): List<String> {
        val legacy =
            try {
                files.source(npub)
            } catch (e: Exception) {
                Log.w(TAG, "Could not open the legacy file for $npub", e)
                return listOf("the legacy file could not be read")
            }

        val reasons = mutableListOf<String>()

        val present = legacy.keys()
        (present - claimed - accepted).sorted().forEach {
            reasons += "no migration claims '$it'"
        }

        val current =
            try {
                currentStore(npub)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "Could not read the current store for $npub", e)
                return reasons + "the current store could not be read"
            }

        tables.forEach { table ->
            // A table whose keys the file never held has nothing to prove.
            if (present.none { it in table.legacyNames }) return@forEach
            if (!table.hasRun(current)) reasons += "the '${table.markerName}' copy has not run"
        }

        reasons += secretMismatches(npub, legacy)

        return reasons
    }

    private suspend fun secretMismatches(
        npub: String,
        legacy: LegacyPreferenceSource,
    ): List<String> {
        val reasons = mutableListOf<String>()

        try {
            if (secrets.secrets(npub) == null) reasons += "the secrets have not been copied across"
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Could not read the secrets store for $npub", e)
            reasons += "the secrets store could not be read"
        }

        val legacyKey = legacy.getString(LegacyAccountSecretNames.NOSTR_PRIVKEY)
        if (legacyKey != null) {
            try {
                when (secrets.privateKey(npub)) {
                    null -> reasons += "the private key has not been copied across"
                    legacyKey -> Unit
                    else -> reasons += "the stored private key differs from the legacy file"
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "Could not read the key store for $npub", e)
                reasons += "the key store could not be read"
            }
        }

        reasons += geohashMismatches(npub)

        return reasons
    }

    /**
     * Whether deleting this account's `secret_keeper_<pubkey hex>` file would
     * lose its location-chat identity.
     *
     * Read from [LegacyAccountFiles.geohashSource], not from the npub file the
     * rest of [verify] walks: these two keys were never in that one. An account
     * that never opened a location chat holds neither, and needs no copy.
     */
    private suspend fun geohashMismatches(npub: String): List<String> {
        val legacy =
            try {
                readLegacyGeohashIdentity(files.geohashSource(npub))
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the location-chat identity file for $npub", e)
                return listOf("the location-chat identity file could not be read")
            }

        if (legacy == GeohashIdentitySecrets()) return emptyList()

        return try {
            if (secrets.geohashIdentity(npub) == null) {
                listOf("the location-chat identity has not been copied across")
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Could not read the location-chat identity store for $npub", e)
            listOf("the location-chat identity store could not be read")
        }
    }

    /**
     * Deletes the account's legacy file if — and only if — [verify] comes back
     * empty and the app has stopped writing to it.
     */
    suspend fun deleteIfVerified(npub: String): LegacyCleanupResult {
        if (!files.exists(npub)) return LegacyCleanupResult.NothingToDelete

        if (!legacyWritesRetired) return LegacyCleanupResult.Kept(listOf(STILL_WRITTEN))

        val reasons = verify(npub)
        if (reasons.isNotEmpty()) {
            Log.i(TAG) { "Keeping the legacy file for $npub: ${reasons.joinToString("; ")}" }
            return LegacyCleanupResult.Kept(reasons)
        }

        return try {
            if (files.delete(npub)) {
                Log.i(TAG) { "Deleted the migrated legacy file for $npub" }
                LegacyCleanupResult.Deleted
            } else {
                LegacyCleanupResult.NothingToDelete
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Could not delete the legacy file for $npub", e)
            LegacyCleanupResult.Kept(listOf("the legacy file could not be deleted"))
        }
    }
}
