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

import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.geode.RelayInfo
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRole
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuntimeConfigTest {
    private lateinit var dir: File
    private lateinit var stateFile: File
    private val url = RelayUrlNormalizer.normalize("ws://127.0.0.1:7770/")

    @BeforeTest
    fun setup() {
        dir = Files.createTempDirectory("quartz-relay-persist-").toFile()
        stateFile = File(dir, "admin.json")
    }

    @AfterTest
    fun teardown() {
        dir.deleteRecursively()
    }

    /** Default RuntimeConfigData seed for tests: just an info doc, no authorization seeds. */
    private fun defaultSeed(info: Nip11RelayInformation = RelayInfo.default(url).document) = RuntimeConfigData(info = info)

    /** RelayEngine bound to [stateFile] with the given (or default) seed. */
    private fun relayPersisted(seed: RuntimeConfigData = defaultSeed()) = RelayEngine(url = url, runtimeConfig = RuntimeConfig(file = stateFile, seed = seed))

    @Test
    fun firstBootWritesNothingUntilFirstMutation() {
        val relay = relayPersisted()
        try {
            // No mutation yet → file does not exist.
            assertTrue(!stateFile.exists(), "fresh relay must not eagerly write a snapshot")
        } finally {
            relay.close()
        }
    }

    @Test
    fun banPubkeyTriggersSnapshotAndSurvivesRestart() {
        val pk = "a".repeat(64)
        val r1 = relayPersisted()
        try {
            r1.banStore.banPubkey(pk, "spam")
        } finally {
            r1.close()
        }
        assertTrue(stateFile.exists(), "snapshot must be written after a mutation")

        // Fresh relay reads the snapshot and sees the ban.
        val r2 = relayPersisted()
        try {
            assertTrue(r2.banStore.isBanned(pk))
            assertEquals("spam", r2.banStore.listBannedPubkeys()[0].second)
        } finally {
            r2.close()
        }
    }

    @Test
    fun updateInfoSurvivesRestart() {
        val r1 = relayPersisted()
        try {
            r1.updateInfo { it.copy(name = "renamed") }
        } finally {
            r1.close()
        }

        val r2 = relayPersisted()
        try {
            assertEquals("renamed", r2.info.document.name)
        } finally {
            r2.close()
        }
    }

    @Test
    fun allowKindRoundTripsAcrossRestart() {
        val r1 = relayPersisted()
        try {
            r1.banStore.allowKind(1)
            r1.banStore.allowKind(7)
            r1.banStore.disallowKind(4)
        } finally {
            r1.close()
        }

        val r2 = relayPersisted()
        try {
            assertEquals(listOf(1, 7), r2.banStore.listAllowedKinds())
            assertEquals(listOf(4), r2.banStore.listDisallowedKinds())
        } finally {
            r2.close()
        }
    }

    @Test
    fun eventAllowListRolesAndClaimsSurviveRestart() {
        val pk = "b".repeat(64)
        val allowedId = "c".repeat(64)
        val r1 = relayPersisted()
        try {
            r1.banStore.allowEvent(allowedId, "approved")
            r1.banStore.createRole(RelayRole("mod", label = "Moderator", description = "keeps order", color = 120, order = 2))
            r1.banStore.assignRole(pk, "mod")
            r1.banStore.createClaim("invite-123")
        } finally {
            r1.close()
        }

        val r2 = relayPersisted()
        try {
            assertEquals(listOf(allowedId to "approved"), r2.banStore.listAllowedEvents())
            assertTrue(r2.banStore.isAllowedEvent(allowedId))
            assertEquals(RelayRole("mod", "Moderator", "keeps order", 120, 2), r2.banStore.getRole("mod"))
            assertEquals(listOf("mod"), r2.banStore.rolesOf(pk))
            assertEquals(listOf("invite-123"), r2.banStore.listClaims())
        } finally {
            r2.close()
        }
    }

    @Test
    fun oldStateFileWithoutNewSectionsStillLoads() {
        // A snapshot written before the event allow list / roles / claims existed.
        stateFile.writeText("""{"info":{"name":"old"},"bannedEvents":[{"key":"${"d".repeat(64)}","reason":"x"}]}""")
        val r = relayPersisted()
        try {
            assertTrue(r.banStore.isBannedEvent("d".repeat(64)))
            assertEquals(emptyList(), r.banStore.listAllowedEvents())
            assertEquals(emptyList(), r.banStore.listRoles())
            assertEquals(emptyList(), r.banStore.listClaims())
        } finally {
            r.close()
        }
    }

    @Test
    fun corruptStateFileIsTolerated() {
        stateFile.writeText("not valid json {")
        // Should not throw — just log and start fresh.
        val r = relayPersisted()
        try {
            assertTrue(r.banStore.listBannedPubkeys().isEmpty())
            assertTrue(r.banStore.listAllowedKinds().isEmpty())
        } finally {
            r.close()
        }
    }

    @Test
    fun snapshotWriteIsAtomicViaTempFile() {
        // After a mutation completes, no `.tmp` file should remain.
        val r = relayPersisted()
        try {
            r.banStore.banPubkey("b".repeat(64))
            val tmp = File(dir, "admin.json.tmp")
            assertTrue(!tmp.exists(), "tmp file must be moved into place, not left behind")
        } finally {
            r.close()
        }
    }

    @Test
    fun missingStateFileMeansInMemoryOnly() {
        val r = RelayEngine(url = url) // default runtimeConfig: file=null
        try {
            r.banStore.banPubkey("c".repeat(64))
            // No snapshot path → nothing on disk in our temp dir.
            assertNull(dir.list()?.firstOrNull { it.startsWith("admin") })
        } finally {
            r.close()
        }
    }

    @Test
    fun runtimeConfigRoundTripsAllSections() {
        val rc = RuntimeConfig(stateFile, seed = RuntimeConfigData(info = Nip11RelayInformation()))
        val state =
            RuntimeConfigData(
                info = Nip11RelayInformation(name = "x", description = "y"),
                bannedPubkeys = listOf(BannedEntry("aa", "spam"), BannedEntry("bb", null)),
                allowedPubkeys = listOf(BannedEntry("cc", "trusted")),
                bannedEvents = listOf(BannedEntry("dd", "off-topic")),
                allowedKinds = listOf(1, 7),
                disallowedKinds = listOf(4, 1059),
            )
        rc.save(state)
        val loaded = rc.load()!!
        assertEquals("x", loaded.info!!.name)
        assertEquals(2, loaded.bannedPubkeys.size)
        assertEquals(listOf(1, 7), loaded.allowedKinds)
        assertEquals(listOf(4, 1059), loaded.disallowedKinds)
    }

    @Test
    fun effectiveFallsBackToStaticSeedWhenNothingPersisted() {
        // Fresh file → no override → static seed wins.
        val seed =
            RuntimeConfigData(
                info = Nip11RelayInformation(name = "from-toml", description = "seeded"),
                allowedKinds = listOf(1, 7),
                bannedPubkeys = listOf(BannedEntry("aa")),
            )
        val rc = RuntimeConfig(stateFile, seed = seed)
        val eff = rc.effective()
        val info = eff.info!!
        assertEquals("from-toml", info.name)
        assertEquals("seeded", info.description)
        assertEquals(listOf(1, 7), eff.allowedKinds)
        assertEquals(listOf("aa"), eff.bannedPubkeys.map { it.key })
    }

    @Test
    fun effectivePrefersPersistedOverrideOverStaticSeed() {
        val seed =
            RuntimeConfigData(
                info = Nip11RelayInformation(name = "from-toml"),
                allowedKinds = listOf(1, 7),
            )
        val rc = RuntimeConfig(stateFile, seed = seed)
        rc.save(
            RuntimeConfigData(
                info = Nip11RelayInformation(name = "admin-renamed"),
                allowedKinds = listOf(42),
            ),
        )
        val eff = rc.effective()
        assertEquals("admin-renamed", eff.info!!.name)
        // Static seed is fully ignored — persisted snapshot wins for
        // every field, not just info.
        assertEquals(listOf(42), eff.allowedKinds)
    }

    @Test
    fun nullFileMakesSaveANoOpAndAlwaysReturnsSeed() {
        // In-memory mode: even after save(), there's nothing on disk to
        // load, so the seed continues to win.
        val seed = RuntimeConfigData(info = Nip11RelayInformation(name = "memory-only"))
        val rc = RuntimeConfig(file = null, seed = seed)
        rc.save(RuntimeConfigData(info = Nip11RelayInformation(name = "ignored")))
        assertNull(rc.load())
        assertEquals("memory-only", rc.effective().info!!.name)
    }

    @Test
    fun authorizationSeedFlowsIntoBanStoreOnFirstBootThenFileTakesOver() {
        val pk = "a".repeat(64)
        // First boot, no file → BanStore is seeded from static
        // authorization.
        val r1 =
            relayPersisted(
                defaultSeed().copy(
                    bannedPubkeys = listOf(BannedEntry(pk)),
                    allowedKinds = listOf(1, 7),
                ),
            )
        try {
            assertTrue(r1.banStore.isBanned(pk), "static-seed pubkey ban must apply on first boot")
            assertEquals(listOf(1, 7), r1.banStore.listAllowedKinds())
        } finally {
            r1.close()
        }
        // First boot wrote nothing to disk yet (we never mutated), so
        // the file is still absent — seed still wins on restart.
        assertTrue(!stateFile.exists(), "no admin mutation yet → no snapshot")

        // Trigger a mutation so the file gets written.
        val r2 = relayPersisted()
        try {
            r2.banStore.banPubkey("b".repeat(64))
        } finally {
            r2.close()
        }
        assertTrue(stateFile.exists())

        // Now restart with a *different* static seed: it must be
        // ignored — the file is authoritative.
        val r3 =
            relayPersisted(
                defaultSeed().copy(
                    bannedPubkeys = listOf(BannedEntry("c".repeat(64))),
                    allowedKinds = listOf(99),
                ),
            )
        try {
            assertTrue(r3.banStore.isBanned("b".repeat(64)), "persisted file ban must survive restart")
            assertTrue(!r3.banStore.isBanned("c".repeat(64)), "new static seed must be ignored once file exists")
            assertTrue(r3.banStore.listAllowedKinds().isEmpty(), "new static kind seed must be ignored once file exists")
        } finally {
            r3.close()
        }
    }

    @Test
    fun seedFromStaticConfigFlowsThroughToNip11Reply() {
        // End-to-end via RelayEngine: a static-derived info reaches the
        // engine's info property without any persisted runtime override.
        val seeded = Nip11RelayInformation(name = "static-name", description = "static-desc")
        val engine = relayPersisted(defaultSeed(info = seeded))
        try {
            assertEquals("static-name", engine.info.document.name)
            assertEquals("static-desc", engine.info.document.description)
        } finally {
            engine.close()
        }
    }

    /**
     * Manual `Nip11RelayInformation.copy` — the class isn't a data
     * class so Kotlin doesn't generate one. We only need `name` here.
     */
    private fun Nip11RelayInformation.copy(name: String? = this.name) =
        Nip11RelayInformation(
            id = id,
            name = name,
            description = description,
            icon = icon,
            pubkey = pubkey,
            self = self,
            contact = contact,
            supported_nips = supported_nips,
            supported_nip_extensions = supported_nip_extensions,
            software = software,
            version = version,
            limitation = limitation,
            relay_countries = relay_countries,
            language_tags = language_tags,
            tags = tags,
            posting_policy = posting_policy,
            privacy_policy = privacy_policy,
            terms_of_service = terms_of_service,
            payments_url = payments_url,
            retention = retention,
            fees = fees,
            nip50 = nip50,
            supported_grasps = supported_grasps,
        )
}
