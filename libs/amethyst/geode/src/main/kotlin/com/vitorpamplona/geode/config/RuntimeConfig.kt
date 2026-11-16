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
package com.vitorpamplona.geode.config

import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRole
import com.vitorpamplona.quartz.nip86RelayManagement.server.BanStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * On-disk handle for the relay's **runtime** configuration — the
 * operator-mutable bits that [StaticConfig] deliberately can't carry
 * because they change while the relay is running:
 *
 *  - the live NIP-11 info doc (so `changerelayname/description/icon`
 *    survive a restart), and
 *  - the NIP-86 ban / allow / kind lists for pubkeys, events, and
 *    kinds (NIP-86 admin RPC mutates these directly), and
 *  - the NIP-43 role definitions, role assignments and invite codes
 *    managed through the NIP-86 role / claim methods.
 *
 * One JSON file per relay. Lives next to the SQLite event store by
 * convention, but the path is configurable independently via
 * [StaticConfig.AdminSection.state_file]. Atomic write via temp +
 * atomic rename so a crash mid-save can never leave the file
 * half-written.
 *
 * **Precedence with [StaticConfig].** [StaticConfig] supplies the
 * [seed] used the first time the relay boots without a persisted
 * file: the NIP-11 `[info]` section becomes the initial info doc,
 * and `[authorization]` lists become the initial pubkey / kind
 * allow / deny lists. As soon as the file exists on disk — written
 * by the first NIP-86 RPC mutation — the seed is never consulted
 * again. From then on the file is the only source of truth; later
 * edits to `[authorization]` in the TOML are ignored at boot.
 *
 * Pass `file = null` to keep everything in memory (useful for tests):
 * [load] then returns `null` and [save] becomes a no-op, so the
 * relay falls back to the [seed] forever.
 *
 * The [RuntimeConfigData] schema intentionally mirrors NIP-86 list
 * responses (`pubkey + reason`, `id + reason`) so a future
 * operator-tools CLI can read these straight from disk without
 * translation.
 */
class RuntimeConfig(
    val file: File?,
    /**
     * Initial values derived from [StaticConfig] — the baseline the
     * relay uses on first boot, before any NIP-86 RPC has produced a
     * file. Once [save] writes the file, [effective] returns the
     * persisted snapshot instead and this seed is discarded.
     */
    private val seed: RuntimeConfigData,
) {
    /** Load the snapshot from disk, or `null` if no file is configured / yet exists. */
    @Synchronized
    fun load(): RuntimeConfigData? {
        val f = file ?: return null
        if (!f.exists()) return null
        return try {
            json.decodeFromString(RuntimeConfigData.serializer(), f.readText())
        } catch (e: Exception) {
            // Corrupt file — log to stderr and refuse to overwrite. The
            // operator chooses whether to fix or delete; we don't blow
            // away their state silently.
            System.err.println("warning: failed to read relay state file ${f.absolutePath}: ${e.message}")
            null
        }
    }

    /**
     * Atomically write the snapshot. No-op when `file == null` — the
     * caller is running in memory-only mode (tests, ephemeral relays)
     * and there's nothing to persist.
     */
    @Synchronized
    fun save(state: RuntimeConfigData) {
        val f = file ?: return
        f.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val tmp = File(f.parentFile ?: f.absoluteFile.parentFile, "${f.name}.tmp")
        tmp.writeText(json.encodeToString(RuntimeConfigData.serializer(), state))
        Files.move(
            tmp.toPath(),
            f.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    /**
     * The state the relay should serve right now: the persisted
     * snapshot if the file exists, otherwise the [seed] from
     * [StaticConfig]. Call this once at boot to initialize in-memory
     * caches (NIP-11 doc, ban store); subsequent admin RPCs mutate
     * those caches in place and call [save], they don't re-read from
     * disk.
     */
    fun effective(): RuntimeConfigData = load() ?: seed

    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = false
            ignoreUnknownKeys = true
        }
}

@Serializable
data class RuntimeConfigData(
    val info: Nip11RelayInformation? = null,
    val bannedPubkeys: List<BannedEntry> = emptyList(),
    val allowedPubkeys: List<BannedEntry> = emptyList(),
    val bannedEvents: List<BannedEntry> = emptyList(),
    val allowedKinds: List<Int> = emptyList(),
    val disallowedKinds: List<Int> = emptyList(),
    val allowedEvents: List<BannedEntry> = emptyList(),
    val roles: List<RoleEntry> = emptyList(),
    val roleAssignments: List<RoleAssignmentEntry> = emptyList(),
    val claims: List<String> = emptyList(),
)

/** A NIP-43 role definition (NIP-86 `createrole` params). */
@Serializable
data class RoleEntry(
    val id: String,
    val label: String? = null,
    val description: String? = null,
    val color: Int? = null,
    val order: Int? = null,
) {
    fun toRole() = RelayRole(id, label, description, color, order)

    companion object {
        fun of(role: RelayRole) = RoleEntry(role.id, role.label, role.description, role.color, role.order)
    }
}

/** The NIP-43 role ids assigned to one pubkey. */
@Serializable
data class RoleAssignmentEntry(
    val pubkey: String,
    val roles: List<String>,
)

@Serializable
data class BannedEntry(
    val key: String,
    val reason: String? = null,
)

/** Bulk-load this snapshot into [banStore] without firing its `onMutation` hook. */
fun RuntimeConfigData.seedInto(banStore: BanStore) {
    banStore.seedFromSnapshot(
        bannedPubkeys = bannedPubkeys.map { it.key to it.reason },
        allowedPubkeys = allowedPubkeys.map { it.key to it.reason },
        bannedEvents = bannedEvents.map { it.key to it.reason },
        allowedKinds = allowedKinds,
        disallowedKinds = disallowedKinds,
        allowedEvents = allowedEvents.map { it.key to it.reason },
        roles = roles.map { it.toRole() },
        roleAssignments = roleAssignments.map { it.pubkey to it.roles },
        claims = claims,
    )
}

/** Capture the current state of [info] + [banStore] as a fresh snapshot. */
fun snapshotOf(
    info: Nip11RelayInformation,
    banStore: BanStore,
): RuntimeConfigData =
    RuntimeConfigData(
        info = info,
        bannedPubkeys = banStore.listBannedPubkeys().map { (k, r) -> BannedEntry(k, r) },
        allowedPubkeys = banStore.listAllowedPubkeys().map { (k, r) -> BannedEntry(k, r) },
        bannedEvents = banStore.listBannedEvents().map { (k, r) -> BannedEntry(k, r) },
        allowedKinds = banStore.listAllowedKinds(),
        disallowedKinds = banStore.listDisallowedKinds(),
        allowedEvents = banStore.listAllowedEvents().map { (k, r) -> BannedEntry(k, r) },
        roles = banStore.listRoles().map { RoleEntry.of(it) },
        roleAssignments = banStore.listRoleAssignments().map { (pk, ids) -> RoleAssignmentEntry(pk, ids) },
        claims = banStore.listClaims(),
    )
