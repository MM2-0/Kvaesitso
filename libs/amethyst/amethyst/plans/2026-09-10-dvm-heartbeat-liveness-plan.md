# DVM Heartbeat Liveness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Only show DVMs (NIP-89 kind 31990 announcements) that have a fresh kind-11998 heartbeat — at most 420 seconds old — in the Discover list, with offline states on pinned feeds, the detail screen, and the manage screen.

**Architecture:** A new quartz event class `DvmHeartbeatEvent` (kind 11998, `BaseAddressableEvent` — the codebase convention for 10xxx events with real `d` tags) is stored replaceably in `LocalCache.addressables` at `Address(11998, dvmPubkey, dTag)`, mirroring the announcement's address. One shared freshness lookup gates the Discover feed; a composable helper + one extra filter in the existing Discover subscription assembler feed it. Staleness transitions are silent, so both the feed and the UI re-check on timers.

**Tech Stack:** Kotlin, KMP (quartz → commons → amethyst), Compose, kotlinx.coroutines, kotlinx.serialization not needed. Spec: `amethyst/plans/2026-09-10-dvm-heartbeat-liveness.md`.

## Global Constraints

- Heartbeat max age: **420 seconds** (`DvmHeartbeatEvent.MAX_AGE_SECONDS`), defined once in quartz; exactly 420s old counts as fresh.
- Kind: **11998** — no other kind number anywhere.
- The wire contract (operator-side builder, no NIP yet): content `"Alive and kicking"` (plain text), tags `d` (= the NIP-89 DTAG), `status` (free text), `expiration` (= createdAt + 300, NIP-40).
- `commons` must not depend on `amethyst` — the constant and `isFreshAt` live in quartz; the cache lookup helper lives in amethyst.
- No new Gradle dependencies. No new icons (the offline marker is a text bullet `"\u2022"`, so **no font regeneration is needed**).
- New user-facing strings: English only in `commons/src/commonMain/composeResources/values/strings.xml` (translations flow via Crowdin).
- Run `./gradlew spotlessApply` before every commit; conventional commits (`feat:`, `test:`, `docs:`); never `--no-verify`.
- `LocalCache` is a process-wide object shared by all JVM tests — every test must use unique ids/pubkeys/dTags.

---

### Task 1: Quartz event class `DvmHeartbeatEvent` (kind 11998)

**Files:**
- Create: `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip90Dvms/dvmHeartbeat/DvmHeartbeatEvent.kt`
- Modify: `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/EventFactory.kt` (import ~line 357–424 block, dispatch ~line 736–774 block)
- Modify: `quartz/src/commonTest/kotlin/com/vitorpamplona/quartz/utils/EventFactoryKindRangeTest.kt:52-76` (`knownDTagReaders`)
- Modify: `amethyst/plans/2026-09-10-dvm-heartbeat-liveness.md` (§2 wording)
- Test: `quartz/src/commonTest/kotlin/com/vitorpamplona/quartz/nip90Dvms/dvmHeartbeat/DvmHeartbeatEventTest.kt`

**Interfaces:**
- Consumes: `BaseAddressableEvent`, `eventTemplate` (quartz `nip01Core`), `TagArray.expiration()` (NIP-40).
- Produces (used by every later task):
  - `DvmHeartbeatEvent.KIND = 11998`
  - `DvmHeartbeatEvent.MAX_AGE_SECONDS = 420`
  - `DvmHeartbeatEvent.CONTENT = "Alive and kicking"`
  - `fun status(): String?`, `fun expiration(): Long?`, `fun isFreshAt(now: Long = TimeUtils.now()): Boolean`
  - `fun build(dTag: String, status: String, expiration: Long, createdAt: Long): EventTemplate<DvmHeartbeatEvent>` — documents the wire contract; the client never signs heartbeats.

- [ ] **Step 1: Write the failing test**

Create `quartz/src/commonTest/kotlin/com/vitorpamplona/quartz/nip90Dvms/dvmHeartbeat/DvmHeartbeatEventTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DvmHeartbeatEventTest {
    private val pubKey = "11".repeat(32)
    private val dTag = "my-dvm"
    private val beatTime = 1_760_000_000L

    private fun heartbeat(createdAt: Long = beatTime) =
        DvmHeartbeatEvent(
            id = "00".repeat(32),
            pubKey = pubKey,
            createdAt = createdAt,
            tags =
                arrayOf(
                    arrayOf("d", dTag),
                    arrayOf("status", "My heart keeps beating like a hammer"),
                    arrayOf("expiration", (createdAt + 300).toString()),
                ),
            content = "Alive and kicking",
            sig = "22".repeat(64),
        )

    @Test
    fun addressIncludesTheDTag() {
        val event = heartbeat()
        assertEquals(dTag, event.dTag())
        assertEquals(Address(11998, pubKey, dTag), event.address())
        assertEquals("11998:$pubKey:$dTag", event.addressTag())
    }

    @Test
    fun missingDTagFallsBackToEmptyAddress() {
        val event =
            DvmHeartbeatEvent(
                id = "00".repeat(32),
                pubKey = pubKey,
                createdAt = beatTime,
                tags = emptyArray(),
                content = "Alive and kicking",
                sig = "22".repeat(64),
            )
        assertEquals("", event.dTag())
        assertEquals(Address(11998, pubKey, ""), event.address())
    }

    @Test
    fun readsStatusAndExpiration() {
        val event = heartbeat()
        assertEquals("My heart keeps beating like a hammer", event.status())
        assertEquals(beatTime + 300, event.expiration())
    }

    @Test
    fun statusIsOptional() {
        val event = DvmHeartbeatEvent("00".repeat(32), pubKey, beatTime, arrayOf(arrayOf("d", dTag)), "", "22".repeat(64))
        assertNull(event.status())
        assertNull(event.expiration())
    }

    @Test
    fun freshnessBoundary() {
        val event = heartbeat()
        assertTrue(event.isFreshAt(beatTime + 420))
        assertFalse(event.isFreshAt(beatTime + 421))
    }

    @Test
    fun buildWritesAllTags() {
        val template =
            DvmHeartbeatEvent.build(
                dTag = dTag,
                status = "My heart keeps beating like a hammer",
                expiration = beatTime + 300,
                createdAt = beatTime,
            )
        assertEquals(11998, template.kind)
        assertEquals("Alive and kicking", template.content)
        assertEquals(
            arrayOf(
                arrayOf("d", dTag),
                arrayOf("status", "My heart keeps beating like a hammer"),
                arrayOf("expiration", (beatTime + 300).toString()),
            ),
            template.tags,
        )
    }

    @Test
    fun factoryBuildsDvmHeartbeatForKind11998() {
        val event: Event =
            EventFactory.create(
                id = "00".repeat(32),
                pubKey = pubKey,
                createdAt = beatTime,
                kind = DvmHeartbeatEvent.KIND,
                tags = arrayOf(arrayOf("d", dTag)),
                content = "",
                sig = "22".repeat(64),
            )
        assertIs<DvmHeartbeatEvent>(event)
        assertTrue(EventFactory.isKnownKind(DvmHeartbeatEvent.KIND), "kind 11998 should be a known kind")
    }
}
```

Note: import `Event` (`com.vitorpamplona.quartz.nip01Core.core.Event`) if the compiler needs the explicit type annotation to resolve `assertIs`; keep the import list tight.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :quartz:jvmTest --tests "com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEventTest"`
Expected: COMPILATION ERROR ("unresolved reference: dvmHeartbeat" — the class does not exist yet).

- [ ] **Step 3: Write the event class**

Create `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip90Dvms/dvmHeartbeat/DvmHeartbeatEvent.kt`:

```kotlin
/*
 * Copyright (c) 2026 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * DVM heartbeat (kind 11998, experimental — no NIP yet): a beat a DVM publishes every 300s to
 * prove it is alive. The operator contract is plain-text content with `d` (the DVM's NIP-89
 * DTAG), `status` (free text) and `expiration` (createdAt + 300, NIP-40) tags.
 *
 * The kind sits in the replaceable range (10000–19999), so relays keep only the latest beat
 * per author. The `d` tag participates in the client-side address so each announced DVM has
 * its own cache slot: `Address(11998, dvmPubKey, dTag)` mirrors the announcement's
 * `Address(31990, dvmPubKey, dTag)`.
 */
@Stable
@Immutable
class DvmHeartbeatEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun status(): String? = tags.firstOrNull { it.size > 1 && it[0] == STATUS_TAG }?.get(1)

    fun expiration(): Long? = tags.expiration()

    /** True while this beat still proves liveness at [now]. */
    fun isFreshAt(now: Long = TimeUtils.now()): Boolean = createdAt >= now - MAX_AGE_SECONDS

    companion object {
        const val KIND = 11998
        const val STATUS_TAG = "status"
        const val CONTENT = "Alive and kicking"

        /** A beat older than this no longer proves liveness (one missed 300s beat + slack). */
        const val MAX_AGE_SECONDS = 420

        fun build(
            dTag: String,
            status: String,
            expiration: Long,
            createdAt: Long = TimeUtils.now(),
        ): EventTemplate<DvmHeartbeatEvent> =
            eventTemplate(KIND, CONTENT, createdAt) {
                add(arrayOf("d", dTag))
                add(arrayOf(STATUS_TAG, status))
                add(arrayOf("expiration", expiration.toString()))
            }
    }
}
```

Imports (after the package): `androidx.compose.runtime.Immutable`, `androidx.compose.runtime.Stable`, `com.vitorpamplona.quartz.nip01Core.core.EventTemplate`, `com.vitorpamplona.quartz.nip01Core.core.HexKey`, `com.vitorpamplona.quartz.nip01Core.signers.eventTemplate`, `com.vitorpamplona.quartz.nip40Expiration.expiration`, `com.vitorpamplona.quartz.utils.TimeUtils`. Signatures use `Array<Array<String>>` directly (the repo style — see `BlossomServersEvent.createTagArray`).

- [ ] **Step 4: Register the kind in EventFactory**

In `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/EventFactory.kt`:

1. Add the import in the NIP-90 import block (alphabetical, near line 358):
```kotlin
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
```
2. Add a dispatch branch inside the `when (kind)` near the other NIP-90 kinds (next to line 736 `DvmStatusEvent.KIND -> ...`):
```kotlin
DvmHeartbeatEvent.KIND -> DvmHeartbeatEvent(id, pubKey, createdAt, tags, content, sig)
```

- [ ] **Step 5: Add 11998 to the kind-range guard allowlist**

In `quartz/src/commonTest/kotlin/com/vitorpamplona/quartz/utils/EventFactoryKindRangeTest.kt`, add `11998` to `knownDTagReaders` (lines 52–76). This is mandatory in the same change as Step 4 — the `plainReplaceableKindsIgnoreStrayDTags` test probes every registered 10000–19999 kind with a stray `d` tag and fails for any class that reads it.

```kotlin
private val knownDTagReaders =
    setOf(
        10004, 10005, 10006, 10007, 10009, 10012, 10013, 10015, 10017, 10018,
        10020, 10023, 10040, 10054, 10081, 10086, 10087, 10088, 10089, 10090,
        10101, 10102,
        // DVM heartbeat: the d tag is the DVM's NIP-89 DTAG and keys its client-side
        // cache address (relay storage stays plain-replaceable per the kind range).
        11998,
    )
```

- [ ] **Step 6: Update the design doc §2**

In `amethyst/plans/2026-09-10-dvm-heartbeat-liveness.md`, replace the §2 bullet that says the class "extends `BaseReplaceableEvent`" / "Overrides `dTag()`" with the as-built wording: extends `BaseAddressableEvent` (the codebase convention for 10xxx events with real `d` tags, e.g. `StarterPackEvent`), so `dTag()`/`address()`/`addressTag()` come from the base; note `MAX_AGE_SECONDS`, `isFreshAt`, and the `knownDTagReaders` entry live in quartz too (commons imports them).

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :quartz:jvmTest --tests "com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEventTest" --tests "com.vitorpamplona.quartz.utils.EventFactoryKindRangeTest"`
Expected: PASS (both classes).

- [ ] **Step 8: Format and commit**

```bash
./gradlew spotlessApply
git add quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip90Dvms/dvmHeartbeat/DvmHeartbeatEvent.kt \
  quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/EventFactory.kt \
  quartz/src/commonTest/kotlin/com/vitorpamplona/quartz/nip90Dvms/dvmHeartbeat/DvmHeartbeatEventTest.kt \
  quartz/src/commonTest/kotlin/com/vitorpamplona/quartz/utils/EventFactoryKindRangeTest.kt \
  amethyst/plans/2026-09-10-dvm-heartbeat-liveness.md
git commit -m "feat: add DvmHeartbeatEvent (kind 11998) for DVM liveness"
```

---

### Task 2: Cache routing + freshness lookup in amethyst

**Files:**
- Create: `amethyst/src/main/java/com/vitorpamplona/amethyst/model/DvmHeartbeat.kt`
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/model/LocalCache.kt:3744` (the replaceable-consume group)
- Test: `amethyst/src/test/java/com/vitorpamplona/amethyst/model/DvmHeartbeatTest.kt`

**Interfaces:**
- Consumes: `DvmHeartbeatEvent` (Task 1: `KIND`, `isFreshAt`, `MAX_AGE_SECONDS`).
- Produces (used by Tasks 3–5):
  - `fun LocalCache.dvmHeartbeatOf(appDef: AppDefinitionEvent): DvmHeartbeatEvent?`
  - `fun LocalCache.hasFreshDvmHeartbeat(appDef: AppDefinitionEvent, now: Long = TimeUtils.now()): Boolean`
- Behavior: consuming a kind-11998 event stores it in `LocalCache.addressables` keyed by `Address(11998, author, dTag)`; the newest per address wins; `hasFreshDvmHeartbeat` is the single gate every consumer uses.

- [ ] **Step 1: Write the failing tests**

Create `amethyst/src/test/java/com/vitorpamplona/amethyst/model/DvmHeartbeatTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Vitor Pamplona
 * (standard MIT license header — copy from ReportNamingIndexIngestionTest.kt)
 */
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `LocalCache` is a process-wide object and JUnit 4 runs methods in hash order, so every test
 * uses its own pubkeys/dTags/ids (same discipline as ReportNamingIndexIngestionTest).
 */
class DvmHeartbeatTest {
    private val appDefPubKey = "aa".repeat(32)

    private fun appDef(
        dTag: String,
        pubKey: String = appDefPubKey,
    ) = AppDefinitionEvent(
        id = "b0".repeat(32),
        pubKey = pubKey,
        createdAt = 1_760_000_000L,
        tags = arrayOf(arrayOf("d", dTag), arrayOf("k", "5300")),
        content = """{"name":"Test DVM"}""",
        sig = "cc".repeat(64),
    )

    private fun beat(
        dTag: String,
        pubKey: String = appDefPubKey,
        createdAt: Long,
        id: String,
    ) = DvmHeartbeatEvent(
        id = id,
        pubKey = pubKey,
        createdAt = createdAt,
        tags =
            arrayOf(
                arrayOf("d", dTag),
                arrayOf("status", "My heart keeps beating like a hammer"),
                arrayOf("expiration", (createdAt + 300).toString()),
            ),
        content = "Alive and kicking",
        sig = "dd".repeat(64),
    )

    @Test
    fun aConsumedHeartbeatLandsAtTheAnnouncementMirrorAddress() {
        val app = appDef("dvm-one")
        LocalCache.consume(beat("dvm-one", createdAt = 1_760_000_100L, id = "e0".repeat(32)), null, true)

        val found = LocalCache.dvmHeartbeatOf(app)
        assertTrue("heartbeat should be found via the announcement's address", found != null)
        assertEquals(1_760_000_100L, found?.createdAt)
        assertEquals(Address(11998, appDefPubKey, "dvm-one"), found?.address())
    }

    @Test
    fun aFreshHeartbeatPassesTheGateAndAStaleOneDoesNot() {
        val now = 1_760_000_000L
        // Separate dTags: consumeBaseReplaceable only accepts NEWER beats per address, so a
        // 421s-old beat could never supersede the 420s one within a single address slot.
        val freshApp = appDef("dvm-two-fresh")
        val staleApp = appDef("dvm-two-stale")
        assertNull(LocalCache.dvmHeartbeatOf(freshApp), "no beat yet")

        LocalCache.consume(beat("dvm-two-fresh", createdAt = now - 420, id = "e1".repeat(32)), null, true)
        LocalCache.consume(beat("dvm-two-stale", createdAt = now - 421, id = "e2".repeat(32)), null, true)

        assertTrue(LocalCache.hasFreshDvmHeartbeat(freshApp, now), "exactly 420s old counts as fresh")
        assertFalse(LocalCache.hasFreshDvmHeartbeat(staleApp, now), "421s old is stale")
    }

    @Test
    fun theNewestBeatPerAddressWins() {
        val now = 1_760_000_000L
        val app = appDef("dvm-three")
        LocalCache.consume(beat("dvm-three", createdAt = now - 600, id = "e3".repeat(32)), null, true)
        LocalCache.consume(beat("dvm-three", createdAt = now - 60, id = "e4".repeat(32)), null, true)

        assertEquals(now - 60, LocalCache.dvmHeartbeatOf(app)?.createdAt)
    }

    @Test
    fun beatsAreKeyedByDTagSoDifferentDvmsDoNotCollide() {
        val now = 1_760_000_000L
        val appA = appDef("dvm-a")
        val appB = appDef("dvm-b")
        LocalCache.consume(beat("dvm-a", createdAt = now - 60, id = "e5".repeat(32)), null, true)

        assertTrue(LocalCache.hasFreshDvmHeartbeat(appA, now))
        assertFalse(LocalCache.hasFreshDvmHeartbeat(appB, now), "no beat for dvm-b")
    }

    @Test
    fun noHeartbeatMeansNoLiveness() {
        assertFalse(LocalCache.hasFreshDvmHeartbeat(appDef("dvm-never"), TimeUtils.now()))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :amethyst:testPlayDebugUnitTest --tests "com.vitorpamplona.amethyst.model.DvmHeartbeatTest"`
Expected: COMPILATION ERROR (`dvmHeartbeatOf`/`hasFreshDvmHeartbeat` unresolved).

- [ ] **Step 3: Add the freshness helper**

Create `amethyst/src/main/java/com/vitorpamplona/amethyst/model/DvmHeartbeat.kt`:

```kotlin
/*
 * Copyright (c) 2026 Vitor Pamplona
 * (standard MIT license header — copy from a sibling file in this package)
 */
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/** The cache slot a DVM's heartbeat lives in: the announcement's own address, kind 11998. */
fun LocalCache.dvmHeartbeatOf(appDef: AppDefinitionEvent): DvmHeartbeatEvent? =
    getAddressableNoteIfExists(Address(DvmHeartbeatEvent.KIND, appDef.pubKey, appDef.dTag()))?.event as? DvmHeartbeatEvent

/** A DVM counts as alive only if its latest heartbeat is at most 420s old. */
fun LocalCache.hasFreshDvmHeartbeat(
    appDef: AppDefinitionEvent,
    now: Long = TimeUtils.now(),
): Boolean = dvmHeartbeatOf(appDef)?.isFreshAt(now) == true
```

- [ ] **Step 4: Route the kind through the replaceable consume path**

In `amethyst/src/main/java/com/vitorpamplona/amethyst/model/LocalCache.kt`, inside the big replaceable group in `justConsumeInnerInner` (the branch ending `-> consumeBaseReplaceable(event, relay, wasVerified)` at line 3832), insert after `is ContactListEvent,` (line 3744):

```kotlin
// DVM heartbeat (11998): stored per Address(11998, author, d) so liveness checks find the
// beat at the announcement's mirror address (amethyst/plans/2026-09-10-dvm-heartbeat-liveness.md).
is DvmHeartbeatEvent,
```

and add the import `com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent` in the quartz import block.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :amethyst:testPlayDebugUnitTest --tests "com.vitorpamplona.amethyst.model.DvmHeartbeatTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Format and commit**

```bash
./gradlew spotlessApply
git add amethyst/src/main/java/com/vitorpamplona/amethyst/model/DvmHeartbeat.kt \
  amethyst/src/main/java/com/vitorpamplona/amethyst/model/LocalCache.kt \
  amethyst/src/test/java/com/vitorpamplona/amethyst/model/DvmHeartbeatTest.kt
git commit -m "feat: store DVM heartbeats in LocalCache and expose the freshness gate"
```

---

### Task 3: Gate the Discover list + make staleness recompute

**Files:**
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/discover/nip90DVMs/DiscoverNIP89FeedFilter.kt:88-97`
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/AccountFeedContentStates.kt` (two spots: `updateFeedsWith` ~line 338, `init` block ~line 315)

**Interfaces:**
- Consumes: `LocalCache.hasFreshDvmHeartbeat` (Task 2), `DvmHeartbeatEvent` (Task 1).
- Produces: the Discover "Content" list only contains DVMs with a fresh beat; a new beat or the 60s timer triggers a full rebuild of `discoverDVMs` (the additive path can never re-evaluate a 31990 that a heartbeat just validated, and expiry emits no event).

No unit tests here: `DiscoverNIP89FeedFilter` and `AccountFeedContentStates` both require a full `Account` (no test constructs one — verified). The gate logic itself is already covered by Task 2's tests; this task is wiring.

- [ ] **Step 1: Gate `acceptApp` on heartbeat freshness**

In `DiscoverNIP89FeedFilter.kt`, change `acceptApp` (lines 88–97) to:

```kotlin
open fun acceptApp(
    noteEvent: AppDefinitionEvent,
    relays: List<NormalizedRelayUrl>,
): Boolean {
    val filterParams = buildFilterParams(account)
    return noteEvent.appMetaData()?.subscription != true &&
        filterParams.match(noteEvent, relays) &&
        noteEvent.includeKind(targetKind) &&
        noteEvent.createdAt > lastAnnounced &&
        LocalCache.hasFreshDvmHeartbeat(noteEvent)
}
```

Add import: `com.vitorpamplona.amethyst.model.hasFreshDvmHeartbeat`. (`LocalCache` is already imported in this file.)

- [ ] **Step 2: Full-rebuild branch when heartbeats arrive**

In `AccountFeedContentStates.kt`, `updateFeedsWith` (line 338), replace the single line `discoverDVMs.updateFeedWith(newNotes)` with:

```kotlin
if (newNotes.any { it.event is DvmHeartbeatEvent }) {
    // A heartbeat is never a feed row, so the additive path would graft nothing and never
    // re-evaluate the announcement it just validated. Rebuild instead.
    discoverDVMs.invalidateData()
} else {
    discoverDVMs.updateFeedWith(newNotes)
}
```

Add import: `com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent`.

- [ ] **Step 3: 60s staleness timer**

In `AccountFeedContentStates.kt`'s `init` block, after the `account.hiddenUsers.flow.collect { ... }` launcher (ends line 315), add:

```kotlin
// Heartbeat staleness produces no cache event (a beat just ages past 420s), so re-check
// the DVM discovery feed on a timer. refreshSuspended() no-ops when nothing changed.
scope.launch(Dispatchers.IO) {
    while (isActive) {
        delay(60_000)
        discoverDVMs.invalidateData()
    }
}
```

Add imports: `kotlinx.coroutines.delay`, `kotlinx.coroutines.isActive` (both usually present already — check before adding).

- [ ] **Step 4: Build and format**

Run: `./gradlew :amethyst:compilePlayDebugKotlin` — Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/discover/nip90DVMs/DiscoverNIP89FeedFilter.kt \
  amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/AccountFeedContentStates.kt
git commit -m "feat: hide DVMs without a fresh heartbeat from the Discover list"
```

---

### Task 4: Discover heartbeat REQ + shared UI helper + strings

**Files:**
- Modify: `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayClient/discover/nip90DVMs/SubAssemblyHelper.kt:43-58`
- Create: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/dvms/DvmHeartbeatObservation.kt`
- Modify: `commons/src/commonMain/composeResources/values/strings.xml` (~line 2228, after `dvm_home_retry`)

**Interfaces:**
- Consumes: `DvmHeartbeatEvent.KIND`/`MAX_AGE_SECONDS` (Task 1 — commons reads quartz, never amethyst).
- Produces (used by Task 5):
  - `fun makeContentDVMsFilter(...)` now also emits one kind-11998 REQ per discovery relay.
  - `@Composable fun rememberDvmHeartbeatFresh(appDefinitionAddress: Address, accountViewModel: AccountViewModel): State<Boolean>`
  - `@Composable fun DvmOfflineBanner(modifier: Modifier = Modifier)`
  - `Res.string.dvm_offline`, `Res.string.dvm_offline_banner`

- [ ] **Step 1: Append the heartbeat filter to the DVM discover assembly**

In `SubAssemblyHelper.kt`, change `makeContentDVMsFilter` to:

```kotlin
fun makeContentDVMsFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long?,
): List<RelayBasedFilter> =
    when (feedSettings) {
        is AllCommunitiesTopNavPerRelayFilterSet -> filterContentDVMsByAllCommunities(feedSettings, since, defaultSince)
        is AllFollowsTopNavPerRelayFilterSet -> filterContentDVMsByFollows(feedSettings, since, defaultSince)
        is AuthorsTopNavPerRelayFilterSet -> filterContentDVMsByAuthors(feedSettings, since, defaultSince)
        is GlobalTopNavPerRelayFilterSet -> filterContentDVMsGlobal(feedSettings, since, defaultSince)
        is HashtagTopNavPerRelayFilterSet -> filterContentDVMsByHashtag(feedSettings, since, defaultSince)
        is LocationTopNavPerRelayFilterSet -> filterContentDVMsByGeohash(feedSettings, since, defaultSince)
        is MutedAuthorsTopNavPerRelayFilterSet -> filterContentDVMsByAuthors(feedSettings, since, defaultSince)
        is SingleCommunityTopNavPerRelayFilterSet -> filterContentDVMsByCommunity(feedSettings, since, defaultSince)
        else -> emptyList()
    }
        .plusHeartbeatFilter()
        .scopedTo(feedSettings)

/**
 * The 31990 announcements say what a DVM advertises; kind-11998 heartbeats say whether it is
 * still alive (amethyst/plans/2026-09-10-dvm-heartbeat-liveness.md). Ask on the same relays the
 * announcements were asked on, with a rolling window instead of the announcement cursor: beats
 * expire (NIP-40) every 5 minutes, so a stored `since` would miss beats on re-opened tabs.
 */
private fun List<RelayBasedFilter>.plusHeartbeatFilter(): List<RelayBasedFilter> {
    if (isEmpty()) return this
    val heartbeatFilter =
        ExplainedFilter(
            purpose = SubPurpose.DISCOVER_FEED,
            kinds = listOf(DvmHeartbeatEvent.KIND),
            limit = 100,
            since = TimeUtils.now() - DvmHeartbeatEvent.MAX_AGE_SECONDS,
        )
    return this +
        map { it.relay }.distinct().map { relay ->
            RelayBasedFilter(relay = relay, filter = heartbeatFilter)
        }
}
```

Add imports: `com.vitorpamplona.quartz.nip01Core.relay.filters.Filter` is NOT needed (ExplainedFilter carries kinds); add `com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent`, `com.vitorpamplona.quartz.utils.TimeUtils`, and `com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter` / `...subscriptions.SubPurpose` if not already imported. (`RelayBasedFilter` is already imported.)

- [ ] **Step 2: Add the strings**

In `commons/src/commonMain/composeResources/values/strings.xml`, after `<string name="dvm_home_retry">Retry</string>` (line ~2228):

```xml
<string name="dvm_offline">Offline</string>
<string name="dvm_offline_banner">This feed algorithm has not sent a heartbeat recently and may be down</string>
```

- [ ] **Step 3: Create the shared observation helper + offline banner**

Create `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/dvms/DvmHeartbeatObservation.kt`:

```kotlin
/*
 * Copyright (c) 2026 Vitor Pamplona
 * (standard MIT license header — copy from DvmContentDiscoveryScreen.kt)
 */
package com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteAndMap
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val HEARTBEAT_RECHECK_MILLIS = 30_000L

/**
 * True while the DVM announced at [appDefinitionAddress] has a heartbeat (kind 11998) at most
 * 420s old. Resolves the beat's cache note by its mirror address, opens a composable-scoped
 * relay subscription (the event-finder assembler fetches the beat by kind/author/d while it is
 * missing), and re-checks staleness on a timer — an expired beat produces no cache event.
 */
@Composable
fun rememberDvmHeartbeatFresh(
    appDefinitionAddress: Address,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    val heartbeatAddressTag =
        remember(appDefinitionAddress) {
            Address.assemble(DvmHeartbeatEvent.KIND, appDefinitionAddress.pubKeyHex, appDefinitionAddress.dTag)
        }

    var heartbeatNote by
        remember(heartbeatAddressTag) {
            mutableStateOf(accountViewModel.getNoteIfExists(heartbeatAddressTag))
        }
    LaunchedEffect(heartbeatAddressTag) {
        if (heartbeatNote == null) {
            heartbeatNote = accountViewModel.checkGetOrCreateNote(heartbeatAddressTag)
        }
    }

    val resolved = heartbeatNote ?: return remember(heartbeatAddressTag) { mutableStateOf(false) }

    val heartbeat by observeNoteAndMap(resolved, accountViewModel) { it.event as? DvmHeartbeatEvent }

    var now by remember(resolved) { mutableLongStateOf(TimeUtils.now()) }
    LaunchedEffect(resolved) {
        while (isActive) {
            delay(HEARTBEAT_RECHECK_MILLIS)
            now = TimeUtils.now()
        }
    }

    val beat = heartbeat
    return rememberUpdatedState(beat != null && beat.isFreshAt(now))
}

/** Floating "DVM is offline" banner, mirroring the Home status banner's card style. */
@Composable
fun DvmOfflineBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Text(
                text = stringRes(Res.string.dvm_offline_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
```

Verify `accountViewModel.getNoteIfExists(...)` / `checkGetOrCreateNote(...)` exist on `AccountViewModel` (they are the exact two calls `LoadNote` makes in `amethyst/.../ui/components/RichTextViewer.kt:875-891`; if they are `AccountViewModel` extension functions rather than members, import them from the same file `LoadNote` imports them from).

- [ ] **Step 4: Build and format**

Run: `./gradlew :commons:compileKotlinJvm :amethyst:compilePlayDebugKotlin` — Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayClient/discover/nip90DVMs/SubAssemblyHelper.kt \
  commons/src/commonMain/composeResources/values/strings.xml \
  amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/dvms/DvmHeartbeatObservation.kt
git commit -m "feat: subscribe to DVM heartbeats from the Discover screen and add liveness helper"
```

---

### Task 5: Wire the four surfaces

**Files:**
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/navigation/topbars/FeedFilterSpinner.kt` (collapsed text ~lines 179–185; `RenderOption` lines 370–375)
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/home/AlgoFeedStatusBanner.kt` (`SingleAlgoFeedBanner` lines 80–91, `AllFavoriteAlgoFeedsBanner` lines 178–199)
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/dvms/DvmContentDiscoveryScreen.kt` (inner screen, lines 126–172)
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/dvms/favorites/FavoriteAlgoFeedsListScreen.kt` (`FavoriteAlgoFeedRow`, lines 235–311)

**Interfaces:**
- Consumes: `rememberDvmHeartbeatFresh`, `DvmOfflineBanner`, `Res.string.dvm_offline` (Task 4).

- [ ] **Step 1: Pinned chips — collapsed spinner text**

In `FeedFilterSpinner.kt`, replace the plain `Text` in the non-Geohash branch (lines 179–185) with:

```kotlin
} else {
    val favoriteAlgoFeedAddress = (selected?.name as? FavoriteAlgoFeedName)?.note?.address
    if (favoriteAlgoFeedAddress != null) {
        val heartbeatFresh by rememberDvmHeartbeatFresh(favoriteAlgoFeedAddress, accountViewModel)
        Text(
            text = if (heartbeatFresh) currentText else "$currentText \u2022",
            color = if (heartbeatFresh) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Text(
            text = currentText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

Add imports: `androidx.compose.ui.graphics.Color`, `com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms.rememberDvmHeartbeatFresh`, `com.vitorpamplona.amethyst.ui.screen.TopNavFilterState.FavoriteAlgoFeedName` (check its actual package — `TopNavFilterState.kt` line 586).

- [ ] **Step 2: Pinned chips — dialog option rows**

In `FeedFilterSpinner.kt`, change the `is PeopleListName, is CommunityName, is FavoriteAlgoFeedName ->` branch of `RenderOption` (lines 370–375) to:

```kotlin
is PeopleListName, is CommunityName, is FavoriteAlgoFeedName -> {
    val backed = option as NoteBackedName
    val noteState by observeNote(backed.note, accountViewModel)
    val name = remember(noteState) { option.name(context) }
    val appDefAddress = (option as? FavoriteAlgoFeedName)?.note?.address
    val heartbeatFresh =
        if (appDefAddress != null) {
            rememberDvmHeartbeatFresh(appDefAddress, accountViewModel).value
        } else {
            true
        }
    Text(
        text = if (heartbeatFresh) name else "$name \u2022",
        fontSize = Font14SP,
        color =
            if (heartbeatFresh) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
    )
}
```

- [ ] **Step 3: Home status banner — offline supersedes**

In `AlgoFeedStatusBanner.kt`:

a) `SingleAlgoFeedBanner` (lines 80–91) — observe freshness and short-circuit before the existing early return:

```kotlin
@Composable
private fun SingleAlgoFeedBanner(
    favFeed: TopFilter.FavoriteAlgoFeed,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val snapshot by accountViewModel.account.favoriteAlgoFeedsOrchestrator
        .observe(favFeed.address)
        .collectAsStateWithLifecycle()

    val heartbeatFresh by rememberDvmHeartbeatFresh(favFeed.address, accountViewModel)

    // The DVM is down (or its beats never reach us): the offline banner supersedes the
    // requesting/error/payment banner, and shows even when last-known content is on screen.
    if (!heartbeatFresh) {
        BannerCard(modifier) {
            BannerMessageRow(
                message = stringRes(Res.string.dvm_offline_banner),
                showSpinner = false,
            )
        }
        return
    }

    // Hide the banner when the feed is already populated.
    if (snapshot.ids.isNotEmpty() || snapshot.addresses.isNotEmpty()) return

    // ... rest of the existing body unchanged ...
}
```

b) `AllFavoriteAlgoFeedsBanner` (lines 178–199) — after the `if (addresses.isEmpty()) return` and before the snapshots loop, insert:

```kotlin
val anyHeartbeatFresh =
    addresses.any { address ->
        rememberDvmHeartbeatFresh(address, accountViewModel).value
    }
if (!anyHeartbeatFresh) {
    BannerCard(modifier) {
        BannerMessageRow(
            message = stringRes(Res.string.dvm_offline_banner),
            showSpinner = false,
        )
    }
    return
}
```

(When at least one pinned DVM is alive, the existing requesting/error logic governs — the dead ones simply contribute nothing new.)

Add imports to `AlgoFeedStatusBanner.kt`: `com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms.rememberDvmHeartbeatFresh`, and `androidx.compose.runtime.getValue` (already present).

- [ ] **Step 4: Detail screen — offline banner**

In `DvmContentDiscoveryScreen.kt`, inner `DvmContentDiscoveryScreen(appDefinition: Note, ...)` (lines 126–172). After `val noteAuthor = ...` (line 128), add:

```kotlin
val appDef = appDefinition.event as? AppDefinitionEvent
val heartbeatFresh = if (appDef != null) rememberDvmHeartbeatFresh(appDef.address(), accountViewModel) else null
```

and wrap the body of `RefresheableBox` in a `Box` so the banner floats over the loading/content view:

```kotlin
RefresheableBox(onRefresh = onRefresh) {
    Box(modifier = Modifier.fillMaxSize()) {
        val myRequestEventID = requestEventID
        if (myRequestEventID != null) {
            ObserverContentDiscoveryResponse(appDefinition, myRequestEventID, onRefresh, accountViewModel, nav)
        } else {
            FeedEmptyWithStatus(appDefinition, stringRes(Res.string.dvm_requesting_job), accountViewModel, nav)
        }
        if (heartbeatFresh?.value == false) {
            DvmOfflineBanner(modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}
```

Notes: `heartbeatFresh?.value == false` exists only because `appDef` itself may be null. Per human ruling (uniform-strict), unresolved heartbeat beats count as offline on every surface — chips, home banners, and the detail screen all show the offline indicator once the beat is unresolved/stale, rather than showing nothing. `AppDefinitionEvent` is already imported in this file; add `androidx.compose.foundation.layout.Box`, `androidx.compose.foundation.layout.fillMaxSize`, `androidx.compose.ui.Alignment`, `com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms.DvmOfflineBanner` (same package — no import needed) and `rememberDvmHeartbeatFresh` (same package — no import needed).

- [ ] **Step 5: Manage screen rows**

In `FavoriteAlgoFeedsListScreen.kt`'s `FavoriteAlgoFeedRow` (row body ~lines 282–302), inside the `Column(Modifier.weight(1f))`, change the name `Text` (lines 285–291) to:

```kotlin
val heartbeatFresh by rememberDvmHeartbeatFresh(feedNote.address, accountViewModel)
val displayName = card.name.ifBlank { feedNote.dTag() }
Text(
    text = if (heartbeatFresh) displayName else "$displayName \u2022",
    fontWeight = FontWeight.Bold,
    // keep the existing fontSize / maxLines / overflow parameters from the original Text
)
```

(preserve whatever style/color parameters the original `Text` already carries — only the `text` argument changes; add the `rememberDvmHeartbeatFresh` import).

- [ ] **Step 6: Build and format**

Run: `./gradlew :amethyst:compilePlayDebugKotlin` — Expected: BUILD SUCCESSFUL.

```bash
./gradlew spotlessApply
git add amethyst/src/main/java/com/vitorpamplona/amethyst/ui/navigation/topbars/FeedFilterSpinner.kt \
  amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/home/AlgoFeedStatusBanner.kt \
  amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/dvms/DvmContentDiscoveryScreen.kt \
  amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/dvms/favorites/FavoriteAlgoFeedsListScreen.kt
git commit -m "feat: show DVM liveness (offline dot, offline banners) on pinned feeds and detail screens"
```

---

### Task 6: Full verification + doc status

- [ ] **Step 1: Run the affected test suites**

```bash
./gradlew :quartz:jvmTest :amethyst:testPlayDebugUnitTest
```
Expected: BUILD SUCCESSFUL (all suites green, including the new `DvmHeartbeatEventTest` and `DvmHeartbeatTest`).

- [ ] **Step 2: Build the app**

Run: `./gradlew :amethyst:assemblePlayDebug` — Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Update the design doc status**

In `amethyst/plans/2026-09-10-dvm-heartbeat-liveness.md`, change the status line to `_Status: **implemented** (Android; desktop wiring deliberately out of scope — §8)._`

- [ ] **Step 4: Commit**

```bash
./gradlew spotlessApply
git add amethyst/plans/2026-09-10-dvm-heartbeat-liveness.md
git commit -m "docs: mark DVM heartbeat liveness as implemented"
```
