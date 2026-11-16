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
package com.vitorpamplona.geode.membership

import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.geode.RelayIndexingStrategy
import com.vitorpamplona.geode.RelayInfo
import com.vitorpamplona.geode.config.RelayIdentity
import com.vitorpamplona.geode.config.RuntimeConfig
import com.vitorpamplona.geode.config.RuntimeConfigData
import com.vitorpamplona.geode.config.StaticConfig
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.server.RelaySession
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip43RelayMembers.addMember.RelayAddMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.joinRequest.RelayJoinRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.leaveRequest.RelayLeaveRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.RelayMembershipListEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.tags.RelayMember
import com.vitorpamplona.quartz.nip43RelayMembers.removeMember.RelayRemoveMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRole
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRoleEvent
import com.vitorpamplona.quartz.nip70ProtectedEvts.isProtected
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Method
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Response
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * NIP-43 on geode: join / leave requests answered over the wire, NIP-86
 * admin changes republished as relay-signed 13534 / 33534 / 8000 / 8001
 * events, all signed by the NIP-11 `self` key, and the state surviving a
 * restart.
 */
class Nip43MembershipTest {
    private val url = RelayUrlNormalizer.normalize("ws://127.0.0.1:7770/")
    private val relayKey = KeyPair()
    private val admin = NostrSignerSync(KeyPair())
    private val alice = NostrSignerSync(KeyPair())
    private val bob = NostrSignerSync(KeyPair())

    private lateinit var dir: File
    private val engines = mutableListOf<RelayEngine>()

    @BeforeTest
    fun setup() {
        dir = Files.createTempDirectory("geode-nip43-").toFile()
    }

    @AfterTest
    fun teardown() {
        engines.forEach { runCatching { it.close() } }
        dir.deleteRecursively()
    }

    private fun relay(
        membership: Boolean = true,
        stateFile: File? = null,
        store: EventStore? = null,
        key: KeyPair = relayKey,
    ): RelayEngine =
        RelayEngine(
            url = url,
            store = store ?: EventStore(dbName = null, relay = url, indexStrategy = RelayIndexingStrategy),
            runtimeConfig = RuntimeConfig(stateFile, RuntimeConfigData(info = RelayInfo.default(url).document)),
            adminPubkeys = setOf(admin.pubKey),
            relayKey = key,
            membership = membership,
        ).also { engines += it }

    /** One client connection with a queue of the frames the relay sent it. */
    private class Client(
        relay: RelayEngine,
    ) {
        private val inbox = Channel<String>(Channel.UNLIMITED)
        private val session: RelaySession = relay.server.connect { inbox.trySend(it) }

        /** Sends [event] and returns the relay's `OK` as (accepted, message). */
        suspend fun publish(event: Event): Pair<Boolean, String> {
            session.receive("[\"EVENT\",${event.toJson()}]")
            return withTimeout(10_000) {
                while (true) {
                    val frame = Json.parseToJsonElement(inbox.receive()).jsonArray
                    if (frame[0].jsonPrimitive.content == "OK" && frame[1].jsonPrimitive.content == event.id) {
                        return@withTimeout frame[2].jsonPrimitive.boolean to frame[3].jsonPrimitive.content
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                error("unreachable")
            }
        }
    }

    private suspend fun RelayEngine.rpc(req: Nip86Request): Nip86Response = nip86Server.dispatch(admin.pubKey, req)

    private suspend fun RelayEngine.memberList(): RelayMembershipListEvent? =
        store
            .query<RelayMembershipListEvent>(Filter(kinds = listOf(RelayMembershipListEvent.KIND), authors = listOf(relaySigner!!.pubKey)))
            .singleOrNull()

    private suspend fun RelayEngine.roles(): List<RelayRoleEvent> = store.query(Filter(kinds = listOf(RelayRoleEvent.KIND)))

    private suspend fun RelayEngine.added(): List<Event> = store.query(Filter(kinds = listOf(RelayAddMemberEvent.KIND)))

    private suspend fun RelayEngine.removed(): List<Event> = store.query(Filter(kinds = listOf(RelayRemoveMemberEvent.KIND)))

    private fun join(
        signer: NostrSignerSync,
        claim: String,
        createdAt: Long = TimeUtils.now(),
    ) = signer.sign(RelayJoinRequestEvent.build(claim, createdAt))

    private fun leave(
        signer: NostrSignerSync,
        createdAt: Long = TimeUtils.now(),
    ) = signer.sign(RelayLeaveRequestEvent.build(createdAt))

    @Test
    fun nip11SelfIsTheSigningKeyAndAdvertisesNip43() =
        runBlocking {
            val relay = relay()
            val doc = relay.info.document
            assertEquals(relayKey.pubKey.toHexKey(), doc.self)
            assertEquals(relay.relaySigner!!.pubKey, doc.self)
            assertTrue("43" in doc.supported_nips!!)

            // Every relay-authored event is signed by that same key.
            relay.membershipServer!!.sync()
            val list = assertNotNull(relay.memberList())
            assertEquals(doc.self, list.pubKey)
            assertTrue(list.tags.isProtected(), "13534 must carry the NIP-70 - tag")
        }

    @Test
    fun membershipOffKeepsTodaysBehaviour() =
        runBlocking {
            val relay = relay(membership = false)
            assertFalse("43" in relay.info.document.supported_nips!!)
            assertNull(relay.membershipServer)

            val methods = (relay.rpc(Nip86Request.supportedMethods()).result as JsonArray).map { it.jsonPrimitive.content }
            assertFalse(Nip86Method.CREATE_ROLE in methods, "role RPCs must not be advertised without a NIP-43 engine")
            assertFalse(Nip86Method.CREATE_CLAIM in methods)
            assertNotNull(relay.rpc(Nip86Request.createClaim("code")).error)

            // Open relay: anyone writes; a 28934 is just an ephemeral event.
            val client = Client(relay)
            assertTrue(client.publish(alice.sign(TextNoteEvent.build("hi"))).first)
            assertTrue(client.publish(join(alice, "code")).first)
            assertNull(relay.memberList())
        }

    @Test
    fun joinWithValidClaimAddsMemberAndPublishes() =
        runBlocking {
            val relay = relay()
            val client = Client(relay)
            assertNull(relay.rpc(Nip86Request.createClaim("invite-1")).error)

            // Members-only: an outsider can't write before joining.
            val (preOk, preMsg) = client.publish(alice.sign(TextNoteEvent.build("before")))
            assertFalse(preOk)
            assertTrue(preMsg.startsWith("restricted:"), preMsg)

            val request = join(alice, "invite-1")
            val (ok, msg) = client.publish(request)
            assertTrue(ok, msg)
            assertTrue(msg.startsWith("info: welcome"), msg)
            assertTrue(relay.banStore.isAllowedPubkey(alice.pubKey))

            assertEquals(listOf(RelayMember(alice.pubKey)), relay.memberList()!!.membersWithRoles())
            val added = relay.added().single()
            assertEquals(relay.relaySigner!!.pubKey, added.pubKey)
            assertEquals(listOf(alice.pubKey), (added as RelayAddMemberEvent).memberPubKeys())
            // The join request (it carries the invite code) is never stored.
            assertTrue(relay.store.query<Event>(Filter(ids = listOf(request.id))).isEmpty())

            // Now a member: writes go through.
            assertTrue(client.publish(alice.sign(TextNoteEvent.build("after"))).first)

            // Invite codes are reusable until revoked.
            assertTrue(client.publish(join(bob, "invite-1")).first)
            assertEquals(setOf(alice.pubKey, bob.pubKey), relay.memberList()!!.members().toSet())

            // Joining again is a no-op answered as a duplicate.
            val (dupOk, dupMsg) = client.publish(join(alice, "invite-1"))
            assertTrue(dupOk)
            assertTrue(dupMsg.startsWith("duplicate:"), dupMsg)
            assertEquals(2, relay.added().size)
        }

    @Test
    fun joinWithInvalidRevokedOrStaleClaimIsRejected() =
        runBlocking {
            val relay = relay()
            val client = Client(relay)
            relay.rpc(Nip86Request.createClaim("good"))

            val (badOk, badMsg) = client.publish(join(alice, "nope"))
            assertFalse(badOk)
            assertEquals("restricted: that is an invalid invite code.", badMsg)

            // Outside the created_at window ("now, plus or minus a few minutes").
            val (oldOk, oldMsg) = client.publish(join(alice, "good", createdAt = TimeUtils.now() - 3600))
            assertFalse(oldOk)
            assertTrue(oldMsg.startsWith("invalid:"), oldMsg)
            val (futureOk, _) = client.publish(join(alice, "good", createdAt = TimeUtils.now() + 3600))
            assertFalse(futureOk)

            // A revoked (deleteclaim) code no longer admits anyone.
            relay.rpc(Nip86Request.deleteClaim("good"))
            val (revokedOk, revokedMsg) = client.publish(join(alice, "good"))
            assertFalse(revokedOk)
            assertTrue(revokedMsg.startsWith("restricted:"), revokedMsg)

            // A banned pubkey can't join even with a valid code.
            relay.rpc(Nip86Request.createClaim("fresh"))
            relay.rpc(Nip86Request.banPubkey(bob.pubKey, "spam"))
            val (bannedOk, bannedMsg) = client.publish(join(bob, "fresh"))
            assertFalse(bannedOk)
            assertTrue(bannedMsg.startsWith("restricted:"), bannedMsg)

            // A forged signature is refused before anything else.
            val forged = join(alice, "fresh").let { Event(it.id, it.pubKey, it.createdAt, it.kind, it.tags, it.content, "0".repeat(128)) }
            val (forgedOk, forgedMsg) = client.publish(forged)
            assertFalse(forgedOk)
            assertTrue(forgedMsg.startsWith("invalid:"), forgedMsg)

            assertFalse(relay.banStore.isAllowedPubkey(alice.pubKey))
            assertFalse(relay.banStore.isAllowedPubkey(bob.pubKey))
            assertTrue(relay.added().isEmpty())
        }

    @Test
    fun leaveRemovesMemberAndPublishes() =
        runBlocking {
            val relay = relay()
            val client = Client(relay)
            relay.rpc(Nip86Request.createClaim("c"))
            assertTrue(client.publish(join(alice, "c")).first)
            assertTrue(client.publish(join(bob, "c")).first)

            // The NIP-70 "-" tag is mandatory on leave requests.
            val unprotected = alice.sign<Event>(TimeUtils.now(), RelayLeaveRequestEvent.KIND, emptyArray(), "")
            val (noTagOk, noTagMsg) = client.publish(unprotected)
            assertFalse(noTagOk)
            assertTrue(noTagMsg.startsWith("invalid:"), noTagMsg)

            val (staleOk, _) = client.publish(leave(alice, createdAt = TimeUtils.now() - 3600))
            assertFalse(staleOk)
            assertTrue(relay.banStore.isAllowedPubkey(alice.pubKey))

            val (ok, msg) = client.publish(leave(alice))
            assertTrue(ok, msg)
            assertFalse(relay.banStore.isAllowedPubkey(alice.pubKey))
            assertEquals(listOf(bob.pubKey), relay.memberList()!!.members())
            val removed = relay.removed().single() as RelayRemoveMemberEvent
            assertEquals(listOf(alice.pubKey), removed.memberPubKeys())
            assertEquals(relay.relaySigner!!.pubKey, removed.pubKey)

            // Leaving twice is a duplicate, and a non-member can't write any more.
            assertTrue(client.publish(leave(alice)).second.startsWith("duplicate:"))
            assertFalse(client.publish(alice.sign(TextNoteEvent.build("still here?"))).first)
        }

    @Test
    fun adminMembershipChangesRepublish() =
        runBlocking {
            val relay = relay()
            assertNull(relay.rpc(Nip86Request.allowPubkey(alice.pubKey)).error)
            assertEquals(listOf(alice.pubKey), relay.memberList()!!.members())
            assertEquals(1, relay.added().size)

            relay.rpc(Nip86Request.allowPubkey(bob.pubKey))
            relay.rpc(Nip86Request.banPubkey(alice.pubKey, "spam"))
            assertEquals(listOf(bob.pubKey), relay.memberList()!!.members())
            assertEquals(listOf(alice.pubKey), (relay.removed().single() as RelayRemoveMemberEvent).memberPubKeys())

            relay.rpc(Nip86Request.unallowPubkey(bob.pubKey))
            assertEquals(emptyList(), relay.memberList()!!.members())
            assertEquals(2, relay.removed().size)
        }

    @Test
    fun rolesArePublishedAndCarriedByTheMemberList() =
        runBlocking {
            val relay = relay()
            relay.rpc(Nip86Request.allowPubkey(alice.pubKey))

            assertNull(relay.rpc(Nip86Request.createRole("mod", "Moderator", "keeps order", 200, 2)).error)
            val role = relay.roles().single()
            assertEquals(relay.relaySigner!!.pubKey, role.pubKey)
            assertTrue(role.tags.isProtected())
            assertEquals(RelayRole("mod", "Moderator", "keeps order", 200, 2), role.role())

            assertNull(relay.rpc(Nip86Request.assignRole(alice.pubKey, "mod")).error)
            assertEquals(listOf(RelayMember(alice.pubKey, listOf("mod"))), relay.memberList()!!.membersWithRoles())

            // Edits within the same second still supersede (created_at is kept monotonic).
            relay.rpc(Nip86Request.editRole("mod", "Mod", null, 10, 1))
            relay.rpc(Nip86Request.editRole("mod", "Moderators", null, 11, 1))
            assertEquals(RelayRole("mod", "Moderators", null, 11, 1), relay.roles().single().role())

            relay.rpc(Nip86Request.createRole("king"))
            relay.rpc(Nip86Request.assignRole(alice.pubKey, "king"))
            assertEquals(
                listOf("mod", "king"),
                relay
                    .memberList()!!
                    .membersWithRoles()
                    .single()
                    .roles,
            )

            relay.rpc(Nip86Request.unassignRole(alice.pubKey, "king"))
            assertEquals(
                listOf("mod"),
                relay
                    .memberList()!!
                    .membersWithRoles()
                    .single()
                    .roles,
            )

            // deleterole: NIP-09 deletion of the 33534, and the id leaves every member.
            relay.rpc(Nip86Request.deleteRole("mod"))
            assertEquals(listOf("king"), relay.roles().map { it.roleId() })
            assertEquals(listOf(RelayMember(alice.pubKey)), relay.memberList()!!.membersWithRoles())
            val deletion = relay.store.query<Event>(Filter(kinds = listOf(5), authors = listOf(relay.relaySigner.pubKey))).single()
            assertTrue(deletion.tags.any { it[0] == "a" && it[1] == "33534:${relay.relaySigner.pubKey}:mod" })

            // A role re-created under a deleted id is published again, after the tombstone.
            relay.rpc(Nip86Request.createRole("mod", "Back"))
            assertEquals(setOf("king", "mod"), relay.roles().map { it.roleId() }.toSet())
        }

    @Test
    fun membershipSurvivesARestartWithoutRepublishing() =
        runBlocking {
            val stateFile = File(dir, "admin.json")
            val dbFile = File(dir, "events.db").path
            val key = RelayIdentity.loadOrCreate(File(dir, "relay.key"))

            val r1 = relay(stateFile = stateFile, store = EventStore(dbName = dbFile, relay = url), key = key)
            r1.rpc(Nip86Request.createClaim("c"))
            r1.rpc(Nip86Request.createRole("mod", "Moderator"))
            assertTrue(Client(r1).publish(join(alice, "c")).first)
            r1.rpc(Nip86Request.assignRole(alice.pubKey, "mod"))
            val list1 = r1.memberList()!!
            r1.close()
            engines.remove(r1)

            // Same key from disk, same state file, same event store.
            val key2 = RelayIdentity.loadOrCreate(File(dir, "relay.key"))
            assertEquals(key.pubKey.toHexKey(), key2.pubKey.toHexKey())
            val r2 = relay(stateFile = stateFile, store = EventStore(dbName = dbFile, relay = url), key = key2)
            assertEquals(key.pubKey.toHexKey(), r2.info.document.self)
            assertTrue(r2.banStore.isAllowedPubkey(alice.pubKey))
            assertEquals(listOf("mod"), r2.banStore.rolesOf(alice.pubKey))
            assertEquals(listOf("c"), r2.banStore.listClaims())

            r2.membershipServer!!.sync()
            assertEquals(list1.id, r2.memberList()!!.id, "an unchanged member list is not re-signed on boot")
            assertEquals(1, r2.added().size)
            assertEquals(1, r2.roles().size)

            // Still a member after the restart.
            assertTrue(Client(r2).publish(alice.sign(TextNoteEvent.build("back"))).first)
        }

    @Test
    fun relayIdentityResolution() {
        val nsecKey = KeyPair()
        val fromNsec =
            RelayIdentity.resolve(StaticConfig.IdentitySection(secret_key = nsecKey.privKey!!.toNsec()), needed = false, stateFile = null)
        assertEquals(nsecKey.pubKey.toHexKey(), fromNsec!!.pubKey.toHexKey())

        val fromHex =
            RelayIdentity.resolve(StaticConfig.IdentitySection(secret_key = nsecKey.privKey!!.toHexKey()), needed = false, stateFile = null)
        assertEquals(nsecKey.pubKey.toHexKey(), fromHex!!.pubKey.toHexKey())

        // Nothing configured and nothing needs it: no identity.
        assertNull(RelayIdentity.resolve(StaticConfig.IdentitySection(), needed = false, stateFile = null))

        // Membership needs one: generated next to the state file, stable across boots.
        val state = File(dir, "state.json").path
        val first = RelayIdentity.resolve(StaticConfig.IdentitySection(), needed = true, stateFile = state)!!
        val second = RelayIdentity.resolve(StaticConfig.IdentitySection(), needed = true, stateFile = state)!!
        assertEquals(first.pubKey.toHexKey(), second.pubKey.toHexKey())
        assertTrue(File(state + RelayIdentity.KEY_FILE_SUFFIX).exists())

        // No state file either: an in-memory key, with a warning.
        val warnings = mutableListOf<String>()
        assertNotNull(RelayIdentity.resolve(StaticConfig.IdentitySection(), needed = true, stateFile = null, warn = { warnings += it }))
        assertEquals(1, warnings.size)
    }
}
