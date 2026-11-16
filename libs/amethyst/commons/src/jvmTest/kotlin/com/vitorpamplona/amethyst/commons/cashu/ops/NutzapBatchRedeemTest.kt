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
package com.vitorpamplona.amethyst.commons.cashu.ops

import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip60Cashu.bdhke.Bdhke
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.mintApi.CheckStateRequestDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpResponse
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpTransport
import com.vitorpamplona.quartz.nip60Cashu.mintApi.SwapRequestDto
import com.vitorpamplona.quartz.nip60Cashu.p2pk.P2PK
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CashuWalletOps.redeemNutzaps] against a fake mint that really signs outputs, charges NUT-02
 * fees like mint.coinos.io (100 ppk) and refuses what real mints refuse: an empty `outputs`
 * ("no outputs provided"), unbalanced amounts, and proofs it has already seen.
 */
class NutzapBatchRedeemTest {
    private val keysetId = "01182e5f5e35aecd"
    private val mintA = "https://mint-a.example.com"
    private val mintB = "https://mint-b.example.com"
    private val walletMints = listOf(mintA, mintB)

    private val json = Json { ignoreUnknownKeys = true }

    private class FakeMint(
        private val keysetId: String,
        private val feePpk: Long,
    ) {
        val privKey = RandomInstance.bytes(32)
        val pubKeyHex = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(privKey)).toHexKey()
        var keysGets = 0
        private val spent = HashSet<String>()
        private val json = Json { ignoreUnknownKeys = true }

        /** Swap requests accepted or refused, in order. */
        val swapInputCounts = mutableListOf<Int>()

        /** When set, every swap answers with this status, as an overloaded or broken mint would. */
        var forcedSwapStatus: Int? = null

        /** NUT-00 error code sent with [forcedSwapStatus]. */
        var forcedSwapCode: Int = 0

        /** Proofs this mint refuses as invalid without their being spent (a forged signature). */
        val invalid = HashSet<String>()
        val checkStateCalls = mutableListOf<Int>()

        fun markSpent(secret: String) {
            spent += secret
        }

        private fun y(secret: String) = Bdhke.hashToCurveCompressed(secret.encodeToByteArray()).toHexKey()

        fun handleCheckState(body: String): MintHttpResponse {
            val ys = json.decodeFromString<CheckStateRequestDto>(body).ys
            checkStateCalls += ys.size
            val spentYs = spent.map { y(it) }.toSet()
            val rows = ys.joinToString(",") { """{"Y":"$it","state":"${if (it in spentYs) "SPENT" else "UNSPENT"}"}""" }
            return MintHttpResponse(200, """{"states":[$rows]}""")
        }

        fun handleGet(path: String): MintHttpResponse {
            val keys = (0..20).joinToString(",") { "\"${1L shl it}\":\"$pubKeyHex\"" }
            if (path == "/v1/keys") keysGets++
            return when (path) {
                "/v1/keys" -> MintHttpResponse(200, """{"keysets":[{"id":"$keysetId","unit":"sat","keys":{$keys},"input_fee_ppk":$feePpk}]}""")
                "/v1/keysets" -> MintHttpResponse(200, """{"keysets":[{"id":"$keysetId","unit":"sat","active":true,"input_fee_ppk":$feePpk}]}""")
                else -> MintHttpResponse(404, """{"detail":"not found"}""")
            }
        }

        fun handleSwap(body: String): MintHttpResponse {
            val request = json.decodeFromString<SwapRequestDto>(body)
            swapInputCounts += request.inputs.size
            forcedSwapStatus?.let { return MintHttpResponse(it, """{"detail":"unavailable","code":$forcedSwapCode}""") }
            if (request.outputs.isEmpty()) return MintHttpResponse(400, """{"detail":"no outputs provided","code":11000}""")
            if (request.inputs.any { it.secret in spent }) return MintHttpResponse(400, """{"detail":"Token already spent.","code":11001}""")
            if (request.inputs.any { it.secret in invalid }) return MintHttpResponse(400, """{"detail":"could not verify proofs.","code":10003}""")
            // NUT-11: a P2PK-locked input needs a Schnorr signature over sha256(secret) by the lock's key.
            val badWitness =
                request.inputs.any { input ->
                    val lock = P2PK.parseSecret(input.secret)?.pubKeyHex ?: return@any false
                    val sig =
                        input.witness?.let {
                            json
                                .parseToJsonElement(it)
                                .jsonObject["signatures"]
                                ?.jsonArray
                                ?.firstOrNull()
                                ?.jsonPrimitive
                                ?.content
                        }
                    sig == null || !Secp256k1.verifySchnorr(sig.hexToByteArray(), sha256(input.secret.encodeToByteArray()), lock.takeLast(64).hexToByteArray())
                }
            if (badWitness) return MintHttpResponse(400, """{"detail":"Witness is missing or invalid.","code":10003}""")
            val fee = (request.inputs.size * feePpk + 999) / 1000
            val inputTotal = request.inputs.sumOf { it.amount }
            if (inputTotal - fee != request.outputs.sumOf { it.amount }) {
                return MintHttpResponse(400, """{"detail":"inputs and outputs not balanced","code":11002}""")
            }
            request.inputs.forEach { spent += it.secret }
            val signatures =
                request.outputs.joinToString(",") { out ->
                    val cTick = Bdhke.sign(out.bTick.hexToByteArray(), privKey).toHexKey()
                    """{"amount":${out.amount},"id":"${out.id}","C_":"$cTick"}"""
                }
            return MintHttpResponse(200, """{"signatures":[$signatures]}""")
        }
    }

    private class Mints(
        val byHost: Map<String, FakeMint>,
    ) : MintHttpTransport {
        /** Every URL requested, known mint or not. */
        val requested = mutableListOf<String>()

        private fun route(url: String): Pair<FakeMint, String> {
            requested += url
            val host = url.removePrefix("https://").substringBefore('/')
            return byHost.getValue(host) to "/" + url.removePrefix("https://").substringAfter('/')
        }

        override suspend fun get(url: String): MintHttpResponse = route(url).let { (mint, path) -> mint.handleGet(path) }

        override suspend fun postJson(
            url: String,
            json: String,
        ): MintHttpResponse =
            route(url).let { (mint, path) ->
                when (path) {
                    "/v1/swap" -> mint.handleSwap(json)
                    "/v1/checkstate" -> mint.handleCheckState(json)
                    else -> MintHttpResponse(404, """{"detail":"not found"}""")
                }
            }
    }

    private val walletPrivKey = RandomInstance.bytes(32)
    private val walletPubKey = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(walletPrivKey)).toHexKey()
    private val me = NostrSignerInternal(KeyPair())
    private val sender = NostrSignerInternal(KeyPair())
    private val published = mutableListOf<Event>()

    private fun walletOps(transport: MintHttpTransport) = CashuWalletOps(signer = me, publish = { published += it }, mintTransport = transport)

    /** A nutzap to [me] whose proofs (one per amount) are locked to [lockTo]. Returns it and its proof secrets. */
    private fun nutzap(
        mintUrl: String,
        vararg amounts: Long,
        lockTo: String = walletPubKey,
        dleqFrom: FakeMint? = null,
        tamperDleq: Boolean = false,
        /** Write the lock's key x-only, as some senders do; [P2PK.lockedSecret] always writes it compressed. */
        xOnlyLock: Boolean = false,
    ): Pair<NutzapEvent, List<String>> {
        val secrets =
            amounts.map {
                if (xOnlyLock) {
                    """["P2PK",{"nonce":"${RandomInstance.bytes(16).toHexKey()}","data":"${lockTo.takeLast(64)}"}]"""
                } else {
                    P2PK.lockedSecret(lockTo)
                }
            }
        val proofs =
            amounts.zip(secrets).map { (amount, secret) ->
                if (dleqFrom == null) {
                    // The fake mint doesn't verify input signatures, so any valid point works as C.
                    val c = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(RandomInstance.bytes(32))).toHexKey()
                    """{"id":"$keysetId","amount":$amount,"secret":${Json.encodeToString(secret)},"C":"$c"}"""
                } else {
                    // Really signed by [dleqFrom], with a NUT-12 Carol DLEQ the sender passes along.
                    val r = Bdhke.randomScalar()
                    val (cPrime, e, sig) = Bdhke.signFull(Bdhke.blind(secret.encodeToByteArray(), r), dleqFrom.privKey)
                    val c = Bdhke.unblind(cPrime, r, dleqFrom.pubKeyHex.hexToByteArray()).toHexKey()
                    val eHex = if (tamperDleq) Bdhke.randomScalar().toHexKey() else e.toHexKey()
                    """{"id":"$keysetId","amount":$amount,"secret":${Json.encodeToString(secret)},"C":"$c","dleq":{"e":"$eHex","s":"${sig.toHexKey()}","r":"${r.toHexKey()}"}}"""
                }
            }
        val template = NutzapEvent.buildToUser("zap", proofs, mintUrl, "sat", me.pubKey)
        return runBlocking { sender.sign(template) } to secrets
    }

    private fun history() = published.filterIsInstance<CashuSpendingHistoryEvent>()

    @Test
    fun `ten 1-sat nutzaps at 100 ppk redeem in one swap and keep 9 sats`() {
        val mint = FakeMint(keysetId, feePpk = 100)
        val zaps = List(10) { nutzap(mintA, 1).first }

        val result = runBlocking { walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(zaps, walletPrivKey.toHexKey(), walletMints) }

        assertEquals(listOf(10), mint.swapInputCounts)
        assertEquals(1, result.redeemed.size)
        assertEquals(9L, result.redeemed[0].completed.amount)
        assertEquals(
            zaps.map { it.id }.toSet(),
            result.redeemed[0]
                .nutzaps
                .map { it.id }
                .toSet(),
        )
        assertTrue(result.belowFee.isEmpty() && result.failed.isEmpty() && result.unredeemable.isEmpty())
        // One history event marks every covered nutzap redeemed.
        assertEquals(1, history().size)
        assertEquals(zaps.map { it.id }.toSet(), history()[0].redeemedReferences().map { it.eventId }.toSet())
    }

    @Test
    fun `a lone 1-sat nutzap is below the fee and never reaches swap`() {
        val mint = FakeMint(keysetId, feePpk = 100)
        val zap = nutzap(mintA, 1).first

        val result = runBlocking { walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(listOf(zap), walletPrivKey.toHexKey(), walletMints) }

        assertEquals(emptyList<Int>(), mint.swapInputCounts)
        assertEquals(listOf(zap.id), result.belowFee.map { it.id })
        assertTrue(result.failed.isEmpty() && result.redeemed.isEmpty())
        assertTrue(published.isEmpty())
    }

    @Test
    fun `one spent nutzap does not hold back the rest of its mint`() {
        val mint = FakeMint(keysetId, feePpk = 100)
        val good1 = nutzap(mintA, 4).first
        val (spentZap, spentSecrets) = nutzap(mintA, 4)
        val good2 = nutzap(mintA, 4).first
        spentSecrets.forEach { mint.markSpent(it) }

        val result =
            runBlocking {
                walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(listOf(good1, spentZap, good2), walletPrivKey.toHexKey(), walletMints)
            }

        // The batch of 3 is refused; one checkstate finds the spent one, and the other two go together.
        assertEquals(listOf(3, 2), mint.swapInputCounts)
        assertEquals(listOf(3), mint.checkStateCalls)
        assertEquals(
            setOf(good1.id, good2.id),
            result.redeemed
                .single()
                .nutzaps
                .map { it.id }
                .toSet(),
        )
        assertEquals(listOf(spentZap.id), result.unredeemable.keys.map { it.id })
        assertTrue(result.failed.isEmpty())
    }

    @Test
    fun `nutzaps are batched per mint`() {
        val a = FakeMint(keysetId, feePpk = 100)
        val b = FakeMint(keysetId, feePpk = 0)
        val zaps = listOf(nutzap(mintA, 2).first, nutzap(mintB, 1).first, nutzap(mintA, 2).first, nutzap(mintB, 1).first)

        val result =
            runBlocking {
                walletOps(Mints(mapOf("mint-a.example.com" to a, "mint-b.example.com" to b))).redeemNutzaps(zaps, walletPrivKey.toHexKey(), walletMints)
            }

        assertEquals(listOf(2), a.swapInputCounts)
        assertEquals(listOf(2), b.swapInputCounts)
        assertEquals(setOf(3L, 2L), result.redeemed.map { it.completed.amount }.toSet())
    }

    @Test
    fun `a nutzap locked to another key is refused without any mint call`() {
        val mint = FakeMint(keysetId, feePpk = 100)
        val otherKey = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(RandomInstance.bytes(32))).toHexKey()
        val zap = nutzap(mintA, 8, lockTo = otherKey).first

        val result = runBlocking { walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(listOf(zap), walletPrivKey.toHexKey(), walletMints) }

        assertEquals(listOf(zap.id), result.unredeemable.keys.map { it.id })
        assertEquals(emptyList<Int>(), mint.swapInputCounts)
    }

    @Test
    fun `swaps are chunked so none carries more than the input cap`() {
        val mint = FakeMint(keysetId, feePpk = 0)
        val zaps = List(2 * CashuWalletOps.MAX_PROOFS_PER_SWAP + 1) { nutzap(mintA, 1).first }

        val result = runBlocking { walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(zaps, walletPrivKey.toHexKey(), walletMints) }

        assertEquals(listOf(67, 67, 67), mint.swapInputCounts)
        assertEquals(zaps.size, result.redeemed.sumOf { it.nutzaps.size })
    }

    // --- Audit: failure paths ---

    @Test
    fun `a mint outage does not multiply into one swap per nutzap`() {
        val mint = FakeMint(keysetId, feePpk = 0).apply { forcedSwapStatus = 503 }
        val zaps = List(5) { nutzap(mintA, 2).first }

        val result = runBlocking { walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(zaps, walletPrivKey.toHexKey(), walletMints) }

        assertEquals("a 503 says nothing about any one nutzap", listOf(5), mint.swapInputCounts)
        assertEquals(5, result.failed.size)
    }

    @Test
    fun `a publish failure after the swap does not re-swap the spent proofs`() {
        val mint = FakeMint(keysetId, feePpk = 0)
        val zaps = List(3) { nutzap(mintA, 2).first }
        var publishes = 0
        val ops =
            CashuWalletOps(
                signer = me,
                publish = { if (publishes++ == 0) throw IllegalStateException("relay down") },
                mintTransport = Mints(mapOf("mint-a.example.com" to mint)),
            )

        val result = runBlocking { ops.redeemNutzaps(zaps, walletPrivKey.toHexKey(), walletMints) }

        assertEquals("the proofs are already spent at the mint; retrying them is futile", listOf(3), mint.swapInputCounts)
        assertEquals(
            zaps.map { it.id },
            result.notSaved
                .single()
                .nutzaps
                .map { it.id },
        )
        assertEquals(6L, result.notSaved.single().amount)
        assertTrue(result.failed.isEmpty() && result.redeemed.isEmpty())
    }

    @Test
    fun `a spent nutzap among small ones does not strand the rest below the fee`() {
        val mint = FakeMint(keysetId, feePpk = 100)
        val small = List(10) { nutzap(mintA, 1).first }
        val (spentZap, spentSecrets) = nutzap(mintA, 1)
        spentSecrets.forEach { mint.markSpent(it) }

        val result =
            runBlocking {
                walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(small + spentZap, walletPrivKey.toHexKey(), walletMints)
            }

        // Together the ten clear the fee (10 in, 1 fee, 9 out); retried one by one each is below it.
        assertEquals(small.map { it.id }.toSet(), result.redeemed.flatMap { r -> r.nutzaps.map { it.id } }.toSet())
        assertTrue("an already-spent nutzap is final, not worth retrying", spentZap in result.unredeemable)
    }

    @Test
    fun `101 small nutzaps split evenly instead of leaving one below the fee`() {
        val mint = FakeMint(keysetId, feePpk = 100)
        val zaps = List(CashuWalletOps.MAX_PROOFS_PER_SWAP + 1) { nutzap(mintA, 1).first }

        val result = runBlocking { walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(zaps, walletPrivKey.toHexKey(), walletMints) }

        assertEquals(listOf(51, 50), mint.swapInputCounts)
        assertTrue(result.belowFee.isEmpty())
        assertEquals(zaps.size, result.redeemed.sumOf { it.nutzaps.size })
    }

    @Test
    fun `a refusal with nothing spent falls back to one swap per nutzap`() {
        val mint = FakeMint(keysetId, feePpk = 0)
        val good1 = nutzap(mintA, 2).first
        val (forged, forgedSecrets) = nutzap(mintA, 2)
        val good2 = nutzap(mintA, 2).first
        mint.invalid += forgedSecrets

        val result =
            runBlocking {
                walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(listOf(good1, forged, good2), walletPrivKey.toHexKey(), walletMints)
            }

        // One checkstate for the batch, none for the singles.
        assertEquals(listOf(3, 1, 1, 1), mint.swapInputCounts)
        assertEquals(listOf(3), mint.checkStateCalls)
        assertEquals(setOf(good1.id, good2.id), result.redeemed.flatMap { r -> r.nutzaps.map { it.id } }.toSet())
        assertEquals(listOf(forged.id), result.unredeemable.keys.map { it.id })
        assertTrue(result.failed.isEmpty())
    }

    // --- Audit 2 ---

    @Test
    fun `a rate-limited mint is not answered with one swap per nutzap`() {
        val mint = FakeMint(keysetId, feePpk = 0).apply { forcedSwapStatus = 429 }
        val zaps = List(5) { nutzap(mintA, 2).first }

        val result = runBlocking { walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(zaps, walletPrivKey.toHexKey(), walletMints) }

        assertEquals("429 is about us, not the inputs", listOf(5), mint.swapInputCounts)
        assertEquals(emptyList<Int>(), mint.checkStateCalls)
        assertEquals(5, result.failed.size)
    }

    @Test
    fun `a forged nutzap the mint refuses is final, so the next sweep does not repeat the fallback`() {
        val mint = FakeMint(keysetId, feePpk = 0)
        val good = nutzap(mintA, 2).first
        val (forged, forgedSecrets) = nutzap(mintA, 2)
        mint.invalid += forgedSecrets

        val result =
            runBlocking { walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(listOf(good, forged), walletPrivKey.toHexKey(), walletMints) }

        assertTrue("anyone can send a junk nutzap; retrying it costs a batch, a checkstate and a swap per nutzap every sweep", forged in result.unredeemable)
    }

    @Test
    fun `a stranger's nutzap naming a local mint is never contacted`() {
        val transport = Mints(emptyMap())
        val zap = nutzap("http://127.0.0.1:8080", 8).first

        runBlocking { walletOps(transport).redeemNutzaps(listOf(zap), walletPrivKey.toHexKey(), walletMints) }

        assertEquals(emptyList<String>(), transport.requested)
    }

    // --- Audit 2 fixes ---

    @Test
    fun `a 408 is transient too`() {
        val mint = FakeMint(keysetId, feePpk = 0).apply { forcedSwapStatus = 408 }
        val zaps = List(3) { nutzap(mintA, 2).first }

        val result = runBlocking { walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(zaps, walletPrivKey.toHexKey(), walletMints) }

        assertEquals(listOf(3), mint.swapInputCounts)
        assertEquals(3, result.failed.size)
    }

    @Test
    fun `two nutzaps carrying the same proof redeem the first and drop the copy`() {
        val mint = FakeMint(keysetId, feePpk = 0)
        val original = nutzap(mintA, 4).first
        // A second nutzap event re-publishing the exact same proof tags.
        val copy = runBlocking { sender.sign(NutzapEvent.buildToUser("again", original.proofs(), mintA, "sat", me.pubKey)) }

        val result = runBlocking { walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(listOf(original, copy), walletPrivKey.toHexKey(), walletMints) }

        assertEquals(listOf(1), mint.swapInputCounts)
        assertEquals(
            listOf(original.id),
            result.redeemed
                .single()
                .nutzaps
                .map { it.id },
        )
        assertEquals(listOf(copy.id), result.unredeemable.keys.map { it.id })
    }

    @Test
    fun `mint URLs differing only in case or a trailing slash share one swap`() {
        val mint = FakeMint(keysetId, feePpk = 100)
        val zaps =
            listOf(
                nutzap("https://mint-a.example.com", 1).first,
                nutzap("https://MINT-A.example.com/", 1).first,
                nutzap("HTTPS://Mint-A.Example.com", 1).first,
            )

        val result = runBlocking { walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(zaps, walletPrivKey.toHexKey(), walletMints) }

        // Three 1-sat proofs in one swap clear the fee (3 in, 1 out after ceil(300/1000)).
        assertEquals(listOf(3), mint.swapInputCounts)
        assertEquals(
            2L,
            result.redeemed
                .single()
                .completed.amount,
        )
    }

    @Test
    fun `a bad DLEQ is narrowed down with a fixed number of key fetches`() {
        val mint = FakeMint(keysetId, feePpk = 0)
        val good = List(6) { nutzap(mintA, 2, dleqFrom = mint).first }
        val bad = nutzap(mintA, 2, dleqFrom = mint, tamperDleq = true).first

        val result =
            runBlocking { walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(good + bad, walletPrivKey.toHexKey(), walletMints) }

        assertEquals(listOf(bad.id), result.unredeemable.keys.map { it.id })
        assertEquals(
            good.map { it.id }.toSet(),
            result.redeemed
                .single()
                .nutzaps
                .map { it.id }
                .toSet(),
        )
        // Batch check, re-check of the survivors, the swap's own keyset fetch: not one per nutzap.
        assertEquals(3, mint.keysGets)
    }

    // --- Audit 3: only a NUT-00 input error narrows the batch down ---

    private fun assertNoFanOut(
        status: Int,
        code: Int,
    ) {
        val mint =
            FakeMint(keysetId, feePpk = 0).apply {
                forcedSwapStatus = status
                forcedSwapCode = code
            }
        val zaps = List(4) { nutzap(mintA, 2).first }

        val result = runBlocking { walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(zaps, walletPrivKey.toHexKey(), walletMints) }

        assertEquals("HTTP $status / code $code is not about any one nutzap", listOf(4), mint.swapInputCounts)
        assertEquals(emptyList<Int>(), mint.checkStateCalls)
        assertEquals("transient: retried later, not pinned", 4, result.failed.size)
    }

    @Test
    fun `an auth-required mint (403) is not answered with one swap per nutzap`() = assertNoFanOut(403, 0)

    @Test
    fun `a 404 is not a refusal of the inputs`() = assertNoFanOut(404, 0)

    @Test
    fun `a NUT-22 blind-auth error (400, 31001) is not a refusal of the inputs`() = assertNoFanOut(400, 31001)

    @Test
    fun `an unbalanced swap (400, 11002) is our fee disagreeing with the mint, not a bad nutzap`() = assertNoFanOut(400, 11002)

    // --- Nutzaps locked to the recipient's Nostr identity key ---

    private val identityPubKey = me.pubKey
    private val identityPrivKey = me.keyPair.privKey!!.toHexKey()

    @Test
    fun `with a local key, identity-locked nutzaps redeem in the same swap as wallet-locked ones`() {
        val mint = FakeMint(keysetId, feePpk = 0)
        val toWallet = nutzap(mintA, 4).first
        val toIdentity = nutzap(mintA, 8, lockTo = identityPubKey).first
        // Compressed (02-prefixed) form of the same identity key, as some senders write it.
        val toIdentityCompressed = nutzap(mintA, 2, lockTo = "02$identityPubKey").first

        val result =
            runBlocking {
                walletOps(Mints(mapOf("mint-a.example.com" to mint)))
                    .redeemNutzaps(listOf(toWallet, toIdentity, toIdentityCompressed), walletPrivKey.toHexKey(), walletMints, identityPrivKey)
            }

        // One swap; the fake mint verifies every witness against its lock's key.
        assertEquals(listOf(3), mint.swapInputCounts)
        assertEquals(
            setOf(toWallet.id, toIdentity.id, toIdentityCompressed.id),
            result.redeemed
                .single()
                .nutzaps
                .map { it.id }
                .toSet(),
        )
        assertEquals(
            14L,
            result.redeemed
                .single()
                .completed.amount,
        )
    }

    @Test
    fun `without the identity key, an identity-locked nutzap is refused with a clear reason and no mint call`() {
        val transport = Mints(mapOf("mint-a.example.com" to FakeMint(keysetId, feePpk = 0)))
        val zap = nutzap(mintA, 8, lockTo = identityPubKey).first

        val result = runBlocking { walletOps(transport).redeemNutzaps(listOf(zap), walletPrivKey.toHexKey(), walletMints) }

        assertTrue(result.unredeemable.getValue(zap).contains("identity key"))
        assertEquals(emptyList<String>(), transport.requested)
    }

    @Test
    fun `the identity key does not unlock nutzaps locked to anyone else`() {
        val transport = Mints(mapOf("mint-a.example.com" to FakeMint(keysetId, feePpk = 0)))
        val stranger = NostrSignerInternal(KeyPair()).pubKey
        val zap = nutzap(mintA, 8, lockTo = stranger).first

        val result = runBlocking { walletOps(transport).redeemNutzaps(listOf(zap), walletPrivKey.toHexKey(), walletMints, identityPrivKey) }

        assertTrue(result.unredeemable.getValue(zap).contains("different pubkey"))
        assertEquals(emptyList<String>(), transport.requested)
    }

    // --- Locks that name their key x-only ---

    @Test
    fun `an x-only lock to our own key is signed like any other`() {
        val mint = FakeMint(keysetId, feePpk = 0)
        val xOnly = nutzap(mintA, 16, lockTo = identityPubKey, xOnlyLock = true).first

        val result =
            runBlocking {
                walletOps(Mints(mapOf("mint-a.example.com" to mint))).redeemNutzaps(listOf(xOnly), walletPrivKey.toHexKey(), walletMints, identityPrivKey)
            }

        assertEquals(listOf(1), mint.swapInputCounts)
        assertEquals(
            listOf(xOnly.id),
            result.redeemed
                .single()
                .nutzaps
                .map { it.id },
        )
    }

    @Test
    fun `an x-only lock to someone else is still refused before any mint call`() {
        val transport = Mints(mapOf("mint-a.example.com" to FakeMint(keysetId, feePpk = 0)))
        val stranger = NostrSignerInternal(KeyPair()).pubKey
        val zap = nutzap(mintA, 16, lockTo = stranger, xOnlyLock = true).first

        val result = runBlocking { walletOps(transport).redeemNutzaps(listOf(zap), walletPrivKey.toHexKey(), walletMints, identityPrivKey) }

        assertTrue(zap in result.unredeemable)
        assertEquals(emptyList<String>(), transport.requested)
    }
}
