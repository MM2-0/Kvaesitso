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

import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class StaticConfigTest {
    @Test
    fun emptyTomlYieldsAllDefaults() {
        val c = StaticConfig.fromToml("")
        assertEquals("0.0.0.0", c.network.host)
        assertEquals(7447, c.network.port)
        assertEquals("/", c.network.path)
        assertEquals(true, c.database.in_memory)
        assertEquals(false, c.options.require_auth)
        // Verify is on by default — operators have to opt out explicitly.
        assertEquals(true, c.options.verify_signatures)
        assertTrue(c.authorization.pubkey_whitelist.isEmpty())
    }

    @Test
    fun verifySignaturesCanBeExplicitlyDisabled() {
        val c = StaticConfig.fromToml("[options]\nverify_signatures = false")
        assertEquals(false, c.options.verify_signatures)
    }

    @Test
    fun parsesDatabaseTuningKnobs() {
        val toml =
            """
            [database]
            in_memory = false
            file = "/tmp/x.db"
            readers = 8
            mmap_size = 268435456
            temp_store_memory = true
            optimize_interval_seconds = 3600
            """.trimIndent()

        val c = StaticConfig.fromToml(toml)

        assertEquals(8, c.database.readers)
        assertEquals(268435456L, c.database.mmap_size)
        assertEquals(true, c.database.temp_store_memory)
        assertEquals(3600L, c.database.optimize_interval_seconds)

        // And all knobs default to off/null so plain configs are untouched.
        val d = StaticConfig.fromToml("")
        assertEquals(null, d.database.readers)
        assertEquals(null, d.database.mmap_size)
        assertEquals(false, d.database.temp_store_memory)
        assertEquals(null, d.database.optimize_interval_seconds)
    }

    @Test
    fun backendDefaultsToSqliteAndParses() {
        // Unset → SQLite, so existing configs keep the current backend.
        assertEquals("sqlite", StaticConfig.fromToml("").database.backend)
        // Explicit values round-trip verbatim (case/keyword resolution is
        // StoreFactory's job, not the parser's).
        assertEquals("fs", StaticConfig.fromToml("[database]\nbackend = \"fs\"").database.backend)
        assertEquals(
            "com.example.MyStore",
            StaticConfig.fromToml("[database]\nbackend = \"com.example.MyStore\"").database.backend,
        )
    }

    @Test
    fun mirrorSectionDefaultsToEmpty() {
        assertTrue(StaticConfig.fromToml("").mirror.isEmpty())
    }

    @Test
    fun validateRejectsNonPositiveReaders() {
        assertFailsWith<IllegalArgumentException> {
            StaticConfig.fromToml("[database]\nreaders = 0").validate()
        }
        assertFailsWith<IllegalArgumentException> {
            StaticConfig.fromToml("[database]\nreaders = -1").validate()
        }
        // A sane pool passes.
        StaticConfig.fromToml("[database]\nreaders = 1").validate()
        // Unset passes (quartz default applies).
        StaticConfig.fromToml("").validate()
    }

    @Test
    fun validateRejectsNonPositiveOptimizeInterval() {
        assertFailsWith<IllegalArgumentException> {
            StaticConfig.fromToml("[database]\noptimize_interval_seconds = 0").validate()
        }
        assertFailsWith<IllegalArgumentException> {
            StaticConfig.fromToml("[database]\noptimize_interval_seconds = -5").validate()
        }
        StaticConfig.fromToml("[database]\noptimize_interval_seconds = 3600").validate()
    }

    @Test
    fun mirrorFilterValidatorRejectsTyposAndScalars() {
        val url = "wss://up.example/"

        // Unknown key (a typo) — must fail, not silently widen scope.
        assertFailsWith<IllegalArgumentException> {
            MirrorFilterValidator.validate(url, """{"kindss":[4]}""")
        }
        // List field given a scalar.
        assertFailsWith<IllegalArgumentException> {
            MirrorFilterValidator.validate(url, """{"authors":"abc"}""")
        }
        // Not an object.
        assertFailsWith<IllegalArgumentException> {
            MirrorFilterValidator.validate(url, """["kinds",1]""")
        }
        // Malformed JSON.
        assertFailsWith<IllegalArgumentException> {
            MirrorFilterValidator.validate(url, """{"kinds":[1,}""")
        }

        // Valid shapes pass: recognized scalar + array + tag keys.
        MirrorFilterValidator.validate(url, """{"kinds":[0,1,3],"#t":["nostr"],"since":123,"limit":5,"search":"x"}""")
        MirrorFilterValidator.validate(url, """{"&p":["abc"]}""")
        MirrorFilterValidator.validate(url, "{}")
    }

    @Test
    fun mirrorFilterJsonParsesToANip01Filter() {
        // The exact parse Main.kt runs on [[mirror]].filter at boot.
        val f = OptimizedJsonMapper.fromJsonTo<Filter>("""{"kinds":[0,1],"#t":["nostr"],"since":123,"limit":5}""")
        assertEquals(listOf(0, 1), f.kinds)
        assertEquals(listOf("nostr"), f.tags?.get("t"))
        assertEquals(123L, f.since)
        assertEquals(5, f.limit)
    }

    @Test
    fun parsesMirrorUpstreams() {
        val toml =
            """
            [[mirror]]
            url = "wss://trusted.upstream.example/"
            trusted = true
            backfill_seconds = 3600
            filter = '{"kinds":[0,1,3],"#t":["nostr"]}'

            [[mirror]]
            url = "wss://public.upstream.example/"
            """.trimIndent()

        val c = StaticConfig.fromToml(toml)

        assertEquals(2, c.mirror.size)
        assertEquals("wss://trusted.upstream.example/", c.mirror[0].url)
        assertEquals(true, c.mirror[0].trusted)
        assertEquals(3600L, c.mirror[0].backfill_seconds)
        assertEquals("""{"kinds":[0,1,3],"#t":["nostr"]}""", c.mirror[0].filter)
        // Trust and scoping are opt-in per upstream: the default is
        // mirror-everything-but-verify.
        assertEquals("wss://public.upstream.example/", c.mirror[1].url)
        assertEquals(false, c.mirror[1].trusted)
        assertEquals(0L, c.mirror[1].backfill_seconds)
        assertEquals(null, c.mirror[1].filter)
    }

    @Test
    fun parsesAllSectionsTogether() {
        val toml =
            """
            [info]
            relay_url = "wss://relay.example.com/"
            name = "Example"
            contact = "ops@example.com"
            supported_nips = [1, 9, 11, 42]

            [network]
            host = "127.0.0.1"
            port = 9988
            path = "/relay"

            [database]
            in_memory = false
            file = "/var/lib/quartz-relay/events.db"

            [options]
            verify_signatures = true
            require_auth = true
            reject_future_seconds = 1800

            [authorization]
            pubkey_blacklist = ["aaaa", "bbbb"]
            kind_blacklist = [4, 1059]
            """.trimIndent()

        val c = StaticConfig.fromToml(toml)

        assertEquals("wss://relay.example.com/", c.info.relay_url)
        assertEquals("Example", c.info.name)
        assertEquals(listOf("1", "9", "11", "42"), c.info.supported_nips)

        assertEquals("127.0.0.1", c.network.host)
        assertEquals(9988, c.network.port)
        assertEquals("/relay", c.network.path)

        assertEquals(false, c.database.in_memory)
        assertEquals("/var/lib/quartz-relay/events.db", c.database.file)

        assertEquals(true, c.options.verify_signatures)
        assertEquals(true, c.options.require_auth)
        assertEquals(1800, c.options.reject_future_seconds)

        assertEquals(listOf("aaaa", "bbbb"), c.authorization.pubkey_blacklist)
        assertEquals(listOf(4, 1059), c.authorization.kind_blacklist)
    }

    @Test
    fun supportedNipsRenderedAsStringsInNip11Doc() {
        val c =
            StaticConfig.fromToml(
                """
                [info]
                supported_nips = [1, 11, 42]
                """.trimIndent(),
            )
        val info = c.resolveInfo()
        assertEquals(listOf("1", "11", "42"), info.document.supported_nips)
    }

    @Test
    fun supportedNipsTakeHexNamedNipsAsStrings() {
        val c =
            StaticConfig.fromToml(
                """
                [info]
                supported_nips = [1, 11, "FE"]
                """.trimIndent(),
            )
        assertEquals(listOf("1", "11", "FE"), c.resolveInfo().document.supported_nips)
    }

    @Test
    fun httpCommandsAreOnByDefaultAndAdvertised() {
        val c = StaticConfig.fromToml("")
        assertTrue(c.http.enabled)
        assertNotNull(c.http.toSettings())
        assertTrue("FE" in c.resolveInfo().document.supported_nips!!)
    }

    @Test
    fun httpSectionParsesAndTurningItOffDropsFE() {
        val c =
            StaticConfig.fromToml(
                """
                [http]
                enabled = false
                max_concurrent_requests = 10
                max_requests_per_client = 2
                deadline_seconds = 5
                alternate_urls = ["ws://2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid.onion/"]
                trusted_proxies = ["127.0.0.1"]
                """.trimIndent(),
            )
        assertEquals(10, c.http.max_concurrent_requests)
        assertEquals(2, c.http.max_requests_per_client)
        assertEquals(listOf("127.0.0.1"), c.http.trusted_proxies)
        assertEquals(null, c.http.toSettings())
        assertTrue("FE" !in c.resolveInfo().document.supported_nips!!)
        val on = c.copy(http = c.http.copy(enabled = true)).http.toSettings()!!
        assertEquals(5.seconds, on.deadline)
        assertEquals(1, on.alternateUrls.size)
    }

    @Test
    fun httpLimitsMustBeSane() {
        assertFailsWith<IllegalArgumentException> { StaticConfig.fromToml("[http]\ndeadline_seconds = 0").validate() }
        assertFailsWith<IllegalArgumentException> { StaticConfig.fromToml("[http]\nmax_requests_per_client = -1").validate() }
    }

    @Test
    fun loadsTheBundledExampleConfigCleanly() {
        // The example file lives at the module root so operators have a
        // canonical reference. Read it via a relative path resolved
        // against the working directory (gradle runs tests from the
        // module dir).
        val candidates =
            listOf(
                File("config.example.toml"),
                File("geode/config.example.toml"),
            )
        val example =
            candidates.firstOrNull { it.exists() }
                ?: error(
                    "config.example.toml not found in any of: ${candidates.joinToString { it.absolutePath }}",
                )

        val c = StaticConfig.fromFile(example)

        assertEquals("wss://relay.example.com/", c.info.relay_url)
        assertEquals(true, c.options.verify_signatures)
        assertEquals(false, c.database.in_memory)
        assertNotNull(c.database.file)
    }

    @Test
    fun missingSectionsAreOptional() {
        val c = StaticConfig.fromToml("[info]\nname = \"only-info\"")
        assertEquals("only-info", c.info.name)
        // Defaults preserved for unspecified sections.
        assertEquals(7447, c.network.port)
        assertEquals(true, c.database.in_memory)
    }

    @Test
    fun rateLimitsAreOffByDefault() {
        assertNull(StaticConfig.fromToml("").rate_limits.toRateLimits())
    }

    @Test
    fun rateLimitsParseAndAZeroBurstMeansTheRate() {
        val limits =
            StaticConfig
                .fromToml(
                    """
                    [rate_limits]
                    events_per_minute = 120
                    reqs_per_minute = 30
                    req_burst = 5
                    """.trimIndent(),
                ).rate_limits
                .toRateLimits()
        assertNotNull(limits)
        assertEquals(120, limits.events?.perMinute)
        assertEquals(120, limits.events?.burst)
        assertEquals(30, limits.reqs?.perMinute)
        assertEquals(5, limits.reqs?.burst)
    }

    @Test
    fun oneRateCanBeOnWithoutTheOther() {
        val limits = assertNotNull(StaticConfig.fromToml("[rate_limits]\nreqs_per_minute = 30").rate_limits.toRateLimits())
        assertNull(limits.events)
        assertNotNull(limits.reqs)
    }

    @Test
    fun negativeRateLimitsFailValidation() {
        listOf("events_per_minute", "event_burst", "reqs_per_minute", "req_burst").forEach { key ->
            assertFailsWith<IllegalArgumentException>(key) {
                StaticConfig.fromToml("[rate_limits]\n$key = -1").validate()
            }
        }
    }
}
