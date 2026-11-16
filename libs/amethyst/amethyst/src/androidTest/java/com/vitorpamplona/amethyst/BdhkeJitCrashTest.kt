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

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip44Encryption.Nip44v2
import com.vitorpamplona.quartz.nip60Cashu.bdhke.Bdhke
import com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression suite for the ART JIT optimizer SIGSEGV that hit Cashu's
 * cryptographic hot path on Android 15+ (Jit thread pool SIGSEGV at
 * `HBasicBlock::RemoveInstruction +32`, fault addr `0x48`, inside
 * `InstructionSimplifierVisitor::Run`).
 *
 * Root cause: `U256.uLtInline` was `internal inline`, so its body
 * `(a xor MIN_VALUE) < (b xor MIN_VALUE)` was duplicated at every call
 * site. With 199 sites across U256 / ScalarN / FieldP — and 12 sequential
 * `if (uLtInline(...)) 1L else 0L` patterns inside `ScalarN.reduceWideTo`
 * alone — ART's InstructionSimplifier tried to fold them as a group and
 * null-deref'd. Fix: drop `inline`, so the function-call boundary blocks
 * the fold path.
 *
 * Each test runs 2048 iterations to force ART tier-1 JIT (threshold for
 * this code is ~48 invocations). Crash-mode tests check for `Jit thread
 * pool` SIGSEGV; correctness-mode tests cross-check against
 * `fr.acinq.secp256k1` JNI or do roundtrip verification.
 *
 * Run all:
 *   ./gradlew :amethyst:connectedPlayDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.vitorpamplona.amethyst.BdhkeJitCrashTest
 *
 * Run single variant:
 *   adb shell am instrument -w -e class \
 *     com.vitorpamplona.amethyst.BdhkeJitCrashTest#a_warmupOnly \
 *     com.vitorpamplona.amethyst.debug.test/androidx.test.runner.AndroidJUnitRunner
 *
 * On crash:
 *   adb logcat -d | grep -E "SIGSEGV|tombstone|BdhkeJitCrashTest"
 *   adb pull /data/tombstones/tombstone_NN /tmp/
 */
@RunWith(AndroidJUnit4::class)
class BdhkeJitCrashTest {
    private val tag = "BdhkeJitCrashTest"

    /** Generator point G in 33-byte compressed form — always parses, never touches wallet state. */
    private val mintPubKey =
        byteArrayOf(
            0x02.toByte(),
            0x79.toByte(),
            0xBE.toByte(),
            0x66.toByte(),
            0x7E.toByte(),
            0xF9.toByte(),
            0xDC.toByte(),
            0xBB.toByte(),
            0xAC.toByte(),
            0x55.toByte(),
            0xA0.toByte(),
            0x62.toByte(),
            0x95.toByte(),
            0xCE.toByte(),
            0x87.toByte(),
            0x0B.toByte(),
            0x07.toByte(),
            0x02.toByte(),
            0x9B.toByte(),
            0xFC.toByte(),
            0xDB.toByte(),
            0x2D.toByte(),
            0xCE.toByte(),
            0x28.toByte(),
            0xD9.toByte(),
            0x59.toByte(),
            0xF2.toByte(),
            0x81.toByte(),
            0x5B.toByte(),
            0x16.toByte(),
            0xF8.toByte(),
            0x17.toByte(),
            0x98.toByte(),
        )

    /**
     * The startup-shape regression. An earlier branch had a `Bdhke.warmup()`
     * that ran 2048 blind+unblind cycles eagerly from
     * `CashuWalletState.start()` to dodge what was thought to be a load-
     * sensitive JIT crash. The real bug was [uLtInline][com.vitorpamplona.quartz.utils.secp256k1.uLtInline]
     * inline-expansion (see `U256.kt`), and the warmup is gone — but the
     * workload it ran is exactly what the unblind path stresses, so we
     * keep it as a regression test under the same name.
     */
    @Test
    fun a_warmupOnly() {
        runBlindUnblind(2048)
    }

    /**
     * Raw loop — bypasses the at-most-once guard so it always actually runs.
     * Lets you bisect the iteration count where the JIT crash kicks in.
     */
    @Test
    fun b_blindUnblindLoop_2048() {
        runBlindUnblind(2048)
    }

    /**
     * Original commit history blamed [Bdhke.unblind] specifically. Isolate it:
     * mint one B' up front, then unblind it N times against the same fixed
     * mintPubKey / r. Same allocation profile as the real restore loop.
     */
    @Test
    fun e_unblindOnly_2048() {
        val r = Bdhke.randomScalar()
        val secret = Bdhke.randomScalar()
        val bTick = Bdhke.blind(secret, r)
        Log.i(tag, "unblindOnly_2048: priming done, beginning loop")
        repeat(2048) { i ->
            Bdhke.unblind(bTick, r, mintPubKey)
            if (i % 256 == 255) Log.i(tag, "unblindOnly_2048: ${i + 1} done")
        }
        Log.i(tag, "unblindOnly_2048: survived")
    }

    /**
     * Multiple coroutines on Dispatchers.Default hammering blind+unblind in
     * parallel — closer to the real startup shape (two accounts each kicking
     * off CashuWalletState.start concurrently) and a higher-stress JIT load.
     */
    @Test
    fun f_blindUnblindLoop_concurrent_x4() {
        runBlocking {
            Log.i(tag, "concurrent_x4: 4 workers × 512 cycles on Dispatchers.Default")
            val workers =
                (0 until 4).map { id ->
                    async(Dispatchers.Default) {
                        repeat(512) { i ->
                            val secret = Bdhke.randomScalar()
                            val r = Bdhke.randomScalar()
                            val bTick = Bdhke.blind(secret, r)
                            Bdhke.unblind(bTick, r, mintPubKey)
                            if (i % 128 == 127) Log.i(tag, "concurrent_x4: worker=$id ${i + 1} done")
                        }
                    }
                }
            workers.awaitAll()
            Log.i(tag, "concurrent_x4: survived")
        }
    }

    private fun runBlindUnblind(iterations: Int) {
        Log.i(tag, "runBlindUnblind: $iterations cycles")
        repeat(iterations) { i ->
            val secret = Bdhke.randomScalar()
            val r = Bdhke.randomScalar()
            val bTick = Bdhke.blind(secret, r)
            Bdhke.unblind(bTick, r, mintPubKey)
            if (i % 256 == 255) Log.i(tag, "runBlindUnblind: ${i + 1} / $iterations done")
        }
        Log.i(tag, "runBlindUnblind: $iterations survived")
    }

    // ==================== Isolation tests ====================
    //
    // Each variant hammers ONE public entry point so the JIT can only compile
    // that entry's call tree. If a variant crashes with the same `Jit thread
    // pool` SIGSEGV signature, the offending method is reachable from that
    // entry and not from any narrower one. Order from leaf to deep:
    //
    //   g_hashToCurveOnly      → tests hashToCurveInto (SHA256 + KeyCodec.liftX + Int-side shift+mask)
    //   h_blindOnly            → adds ECPoint.mulG + ECPoint.addPoints
    //   i_signOnly             → tests ONLY Secp256k1.pubKeyTweakMul (JNI, control)
    //   j_secp256k1PubkeyOnly  → tests ONLY Secp256k1.pubkeyCreate (JNI, control)
    //
    // Run order: g, h, i, j.

    @Test
    fun g_hashToCurveOnly() {
        Log.i(tag, "hashToCurveOnly: 2048 calls to Bdhke.hashToCurveCompressed")
        repeat(2048) { i ->
            val secret = Bdhke.randomScalar()
            Bdhke.hashToCurveCompressed(secret)
            if (i % 256 == 255) Log.i(tag, "hashToCurveOnly: ${i + 1} done")
        }
        Log.i(tag, "hashToCurveOnly: survived")
    }

    @Test
    fun h_blindOnly() {
        Log.i(tag, "blindOnly: 2048 calls to Bdhke.blind")
        repeat(2048) { i ->
            val secret = Bdhke.randomScalar()
            val r = Bdhke.randomScalar()
            Bdhke.blind(secret, r)
            if (i % 256 == 255) Log.i(tag, "blindOnly: ${i + 1} done")
        }
        Log.i(tag, "blindOnly: survived")
    }

    @Test
    fun i_signOnly() {
        Log.i(tag, "signOnly: 2048 calls to Bdhke.sign (Secp256k1.pubKeyTweakMul, JNI)")
        val r = Bdhke.randomScalar()
        val bTick = Bdhke.blind(Bdhke.randomScalar(), r)
        val mintPriv = Bdhke.randomScalar()
        repeat(2048) { i ->
            Bdhke.sign(bTick, mintPriv)
            if (i % 256 == 255) Log.i(tag, "signOnly: ${i + 1} done")
        }
        Log.i(tag, "signOnly: survived")
    }

    @Test
    fun j_secp256k1PubkeyOnly() {
        Log.i(tag, "secp256k1PubkeyOnly: 2048 calls to Secp256k1.pubkeyCreate (pure Kotlin)")
        val priv = Bdhke.randomScalar()
        repeat(2048) { i ->
            Secp256k1.pubkeyCreate(priv)
            if (i % 256 == 255) Log.i(tag, "secp256k1PubkeyOnly: ${i + 1} done")
        }
        Log.i(tag, "secp256k1PubkeyOnly: survived")
    }

    // ==================== Cashu cryptographic coverage ====================
    //
    // Two-axis regression net for the rest of the crypto surface Cashu actually
    // touches (beyond blind/unblind). Each test does both:
    //
    //   1. CRASH: 2048 iterations to force ART tier-1 JIT compile (the same
    //      threshold that surfaced the InstructionSimplifier bug on
    //      ECPoint.mul → uLtInline).
    //   2. CORRECTNESS: every iteration cross-checks against
    //      fr.acinq.secp256k1 JNI, or self-verifies via roundtrip / sign+
    //      verify, depending on whether the primitive is standard secp256k1
    //      (ACINQ-equivalent) or Cashu-specific (no native reference).
    //
    // A JIT miscompile that produces wrong-but-non-crashing code would fail
    // here even though no SIGSEGV fires.

    /** NIP-44 ECDH path. Our [Secp256k1.ecdhXOnly] vs acinq's pubKeyTweakMul. */
    @Test
    fun n_ecdhXOnly_2048() {
        val acinq =
            fr.acinq.secp256k1.Secp256k1
                .get()
        val scalar = Bdhke.randomScalar()
        val peerPriv = Bdhke.randomScalar()
        val peerPubUncompressed = Secp256k1.pubkeyCreate(peerPriv)
        val peerPubCompressed = byteArrayOf(0x02.toByte()) + peerPubUncompressed.copyOfRange(1, 33)
        val peerXOnly = peerPubUncompressed.copyOfRange(1, 33)
        // ACINQ reference: x-coordinate of (scalar · peerPubCompressed)
        val acinqOut = acinq.pubKeyTweakMul(peerPubCompressed, scalar)
        val acinqX = acinqOut.copyOfRange(1, 33)
        Log.i(tag, "ecdhXOnly: 2048 calls, comparing X coord vs acinq")
        repeat(2048) { i ->
            val ours = Secp256k1.ecdhXOnly(peerXOnly, scalar)
            check(ours.contentEquals(acinqX)) {
                "iter $i: ecdhXOnly diverged from acinq.pubKeyTweakMul X"
            }
            if (i % 256 == 255) Log.i(tag, "ecdhXOnly: ${i + 1} done")
        }
    }

    /** NIP-01 / NUT-20 Schnorr sign. Deterministic — bytes must match acinq. */
    @Test
    fun o_signSchnorr_2048() {
        val acinq =
            fr.acinq.secp256k1.Secp256k1
                .get()
        val sk = Bdhke.randomScalar()
        val msg = Bdhke.randomScalar() // 32 bytes
        val aux = Bdhke.randomScalar()
        val acinqSig = acinq.signSchnorr(msg, sk, aux)
        Log.i(tag, "signSchnorr: 2048 calls, comparing 64-byte sig vs acinq")
        repeat(2048) { i ->
            val oursSig = Secp256k1.signSchnorr(msg, sk, aux)
            check(oursSig.contentEquals(acinqSig)) {
                "iter $i: signSchnorr diverged from acinq"
            }
            if (i % 256 == 255) Log.i(tag, "signSchnorr: ${i + 1} done")
        }
    }

    /** Every consumed Nostr event (Cashu or otherwise) verifies through this. */
    @Test
    fun p_verifySchnorr_2048() {
        val acinq =
            fr.acinq.secp256k1.Secp256k1
                .get()
        val sk = Bdhke.randomScalar()
        val msg = Bdhke.randomScalar()
        val aux = Bdhke.randomScalar()
        val sig = acinq.signSchnorr(msg, sk, aux)
        val pubXOnly = Secp256k1.pubkeyCreate(sk).copyOfRange(1, 33)
        // Sanity: acinq must accept its own signature.
        check(acinq.verifySchnorr(sig, msg, pubXOnly))
        Log.i(tag, "verifySchnorr: 2048 calls, must return true each time")
        repeat(2048) { i ->
            val ok = Secp256k1.verifySchnorr(sig, msg, pubXOnly)
            check(ok) { "iter $i: verifySchnorr returned false on valid sig" }
            if (i % 256 == 255) Log.i(tag, "verifySchnorr: ${i + 1} done")
        }
    }

    /** NUT-13 deterministic-secret derivation tweaks scalars; must match acinq byte-for-byte. */
    @Test
    fun q_privKeyTweakAdd_2048() {
        val acinq =
            fr.acinq.secp256k1.Secp256k1
                .get()
        val sk = Bdhke.randomScalar()
        val tweak = Bdhke.randomScalar()
        val acinqOut = acinq.privKeyTweakAdd(sk.copyOf(), tweak)
        Log.i(tag, "privKeyTweakAdd: 2048 calls, comparing 32 bytes vs acinq")
        repeat(2048) { i ->
            val ours = Secp256k1.privKeyTweakAdd(sk, tweak)
            check(ours.contentEquals(acinqOut)) {
                "iter $i: privKeyTweakAdd diverged from acinq"
            }
            if (i % 256 == 255) Log.i(tag, "privKeyTweakAdd: ${i + 1} done")
        }
    }

    /**
     * NUT-12 mint-side DLEQ verification (Alice). Cashu-specific — no JNI
     * reference exists. Self-consistency: a freshly signed DLEQ tuple must
     * verify; corrupting any byte must reject.
     */
    @Test
    fun r_verifyDleq_2048() {
        val mintPriv = Bdhke.randomScalar()
        val mintPubCompressed = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(mintPriv))
        val r = Bdhke.randomScalar()
        val bTick = Bdhke.blind(Bdhke.randomScalar(), r)
        val (cTick, e, s) = Bdhke.signFull(bTick, mintPriv)
        check(Bdhke.verifyDleq(e, s, bTick, cTick, mintPubCompressed)) {
            "setup: signFull→verifyDleq must roundtrip"
        }
        val tampered = e.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
        check(!Bdhke.verifyDleq(tampered, s, bTick, cTick, mintPubCompressed)) {
            "setup: tampered e must be rejected"
        }
        Log.i(tag, "verifyDleq: 2048 calls — accept-valid + reject-tampered each iter")
        repeat(2048) { i ->
            check(Bdhke.verifyDleq(e, s, bTick, cTick, mintPubCompressed)) {
                "iter $i: valid DLEQ rejected"
            }
            check(!Bdhke.verifyDleq(tampered, s, bTick, cTick, mintPubCompressed)) {
                "iter $i: tampered DLEQ accepted"
            }
            if (i % 256 == 255) Log.i(tag, "verifyDleq: ${i + 1} done")
        }
    }

    /**
     * NIP-44 v2 cipher roundtrip. Cashu wallet/token/history events
     * (kind:17375, 7375, 7376, 17376) are all encrypted with NIP-44 v2.
     * The pipeline: secp256k1 ECDH (pure Kotlin) → HKDF-SHA256 → ChaCha20
     * → HMAC-SHA256. ECDH path is already covered by [n_ecdhXOnly_2048] —
     * this catches a JIT miscompile in the symmetric layer, or a key-
     * derivation drift in the HKDF/HMAC primitives.
     *
     * Each iteration uses a fresh random nonce so ciphertext varies, but
     * decrypt(encrypt(p)) must always return `p`.
     */
    @Test
    fun t_nip44CipherRoundtrip_2048() {
        val nip44 = Nip44v2()
        val alicePriv = Bdhke.randomScalar()
        val bobPriv = Bdhke.randomScalar()
        val bobPub = Nip01Crypto.pubKeyCreate(bobPriv)
        val conversationKey = nip44.getConversationKey(alicePriv, bobPub)
        // ~200 byte plaintext is representative of a NIP-60 kind:7375 token
        // payload (cashu proof array, mint URL, unit, memo).
        val plaintext =
            "Cashu kind:7375 token: mint=https://mint.example/v1 unit=sat proofs=[" +
                "{a:1,id:00aabb,s:" + "deadbeef".repeat(8) + ",C:" + "ab".repeat(33) + "}]"
        Log.i(tag, "nip44Cipher: 2048 encrypt+decrypt roundtrips (plaintext=${plaintext.length}B)")
        repeat(2048) { i ->
            val encrypted = nip44.encrypt(plaintext, conversationKey)
            val decrypted = nip44.decrypt(encrypted, conversationKey)
            check(decrypted == plaintext) {
                "iter $i: nip44 roundtrip diverged — got '${decrypted.take(60)}…'"
            }
            if (i % 256 == 255) Log.i(tag, "nip44Cipher: ${i + 1} done")
        }
        Log.i(tag, "nip44Cipher: survived")
    }

    /**
     * NUT-12 §3 Carol-side DLEQ verification. Composite path: internally
     * calls `blind`, `addRTimesA`, and `verifyDleq`, so covers the private
     * `addRTimesA` (also `ECPoint.mul`-bound) which has no other entry point.
     */
    @Test
    fun s_verifyDleqCarol_2048() {
        val mintPriv = Bdhke.randomScalar()
        val mintPubCompressed = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(mintPriv))
        val r = Bdhke.randomScalar()
        val secret = Bdhke.randomScalar()
        val bTick = Bdhke.blind(secret, r)
        val (cTick, e, s) = Bdhke.signFull(bTick, mintPriv)
        val unblindedC = Bdhke.unblind(cTick, r, mintPubCompressed)
        check(Bdhke.verifyDleqCarol(secret, r, e, s, unblindedC, mintPubCompressed)) {
            "setup: Carol-side DLEQ must roundtrip"
        }
        Log.i(tag, "verifyDleqCarol: 2048 calls — must verify each iter")
        repeat(2048) { i ->
            check(Bdhke.verifyDleqCarol(secret, r, e, s, unblindedC, mintPubCompressed)) {
                "iter $i: Carol-side DLEQ rejected"
            }
            if (i % 256 == 255) Log.i(tag, "verifyDleqCarol: ${i + 1} done")
        }
    }

    /**
     * Same workload as [i_signOnly] but uses ACINQ's libsecp256k1 JNI binding
     * instead of our pure-Kotlin [Secp256k1.pubKeyTweakMul]. If this is clean
     * while `i_signOnly` crashes, we have direct proof the JIT crash lives in
     * Kotlin code that ART tries to optimize — and the JNI swap is the fix.
     */
    @Test
    fun l_signOnlyAcinq() {
        val acinq =
            fr.acinq.secp256k1.Secp256k1
                .get()
        Log.i(tag, "signOnlyAcinq: 2048 calls to acinq.pubKeyTweakMul (JNI libsecp256k1)")
        val r = Bdhke.randomScalar()
        val bTick = Bdhke.blind(Bdhke.randomScalar(), r)
        val mintPriv = Bdhke.randomScalar()
        repeat(2048) { i ->
            acinq.pubKeyTweakMul(bTick, mintPriv)
            if (i % 256 == 255) Log.i(tag, "signOnlyAcinq: ${i + 1} done")
        }
        Log.i(tag, "signOnlyAcinq: survived")
    }
}
