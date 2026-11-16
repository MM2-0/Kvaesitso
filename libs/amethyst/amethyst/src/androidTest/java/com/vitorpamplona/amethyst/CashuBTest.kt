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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.commons.service.cashu.CashuParser
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.quartz.nip60Cashu.token.CashuToken
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CashuBTest {
    val cashuTokenA = "cashuAeyJ0b2tlbiI6W3sibWludCI6Imh0dHBzOi8vODMzMy5zcGFjZTozMzM4IiwicHJvb2ZzIjpbeyJhbW91bnQiOjIsImlkIjoiMDA5YTFmMjkzMjUzZTQxZSIsInNlY3JldCI6IjQwNzkxNWJjMjEyYmU2MWE3N2UzZTZkMmFlYjRjNzI3OTgwYmRhNTFjZDA2YTZhZmMyOWUyODYxNzY4YTc4MzciLCJDIjoiMDJiYzkwOTc5OTdkODFhZmIyY2M3MzQ2YjVlNDM0NWE5MzQ2YmQyYTUwNmViNzk1ODU5OGE3MmYwY2Y4NTE2M2VhIn0seyJhbW91bnQiOjgsImlkIjoiMDA5YTFmMjkzMjUzZTQxZSIsInNlY3JldCI6ImZlMTUxMDkzMTRlNjFkNzc1NmIwZjhlZTBmMjNhNjI0YWNhYTNmNGUwNDJmNjE0MzNjNzI4YzcwNTdiOTMxYmUiLCJDIjoiMDI5ZThlNTA1MGI4OTBhN2Q2YzA5NjhkYjE2YmMxZDVkNWZhMDQwZWExZGUyODRmNmVjNjlkNjEyOTlmNjcxMDU5In1dfV0sInVuaXQiOiJzYXQiLCJtZW1vIjoiVGhhbmsgeW91LiJ9"
    val cashuTokenB1 = "cashuBo2F0gqJhaUgA_9SLj17PgGFwgaNhYQFhc3hAYWNjMTI0MzVlN2I4NDg0YzNjZjE4NTAxNDkyMThhZjkwZjcxNmE1MmJmNGE1ZWQzNDdlNDhlY2MxM2Y3NzM4OGFjWCECRFODGd5IXVW-07KaZCvuWHk3WrnnpiDhHki6SCQh88-iYWlIAK0mjE0fWCZhcIKjYWECYXN4QDEzMjNkM2Q0NzA3YTU4YWQyZTIzYWRhNGU5ZjFmNDlmNWE1YjRhYzdiNzA4ZWIwZDYxZjczOGY0ODMwN2U4ZWVhY1ghAjRWqhENhLSsdHrr2Cw7AFrKUL9Ffr1XN6RBT6w659lNo2FhAWFzeEA1NmJjYmNiYjdjYzY0MDZiM2ZhNWQ1N2QyMTc0ZjRlZmY4YjQ0MDJiMTc2OTI2ZDNhNTdkM2MzZGNiYjU5ZDU3YWNYIQJzEpxXGeWZN5qXSmJjY8MzxWyvwObQGr5G1YCCgHicY2FtdWh0dHA6Ly9sb2NhbGhvc3Q6MzMzOGF1Y3NhdA"
    val cashuTokenB2 = "cashuBpGFkb01pbmliaXRzIHJ1bGVzIWFteEpodHRwOi8vbGJ1dGxoNWxmZ2dxNXI3eHBpd2hyYWpkbDdzeHB1cGdhZ2F6eGw2NXc0YzVjZzcyd3RvZmFzYWQub25pb246MzMzOGF0gaJhaUgAm7I9OpEuTmFwg6NhYRhAYWNYIQPfWR0mG80XbGnj6DO8q1NIyjHSGGIEkoWTA6H16HTpx2FzeEA3YThkY2Y5YjNlOGEyNDdjZTMzOWU3MzY5ZTliNGExOWYzMWVhY2I2OWQ4YjBjNjVkYWFlYjcyZDFhY2I5YWQzo2FhGCBhY1ghA55SwCFBc46dwnjbkb87Mzo30T2EE9Ws_nemuFneDegGYXN4QDlkODFjMWEyNjE2ODUzYWQ4MDQ5Y2JjZDFjN2MyNDdhZGQ4M2IzNzM4Mjg2MjBiYWMyZmQ3ZjNlNWE1OGFjZWKjYWEEYWNYIQM-yAQQTR2t6pIAmfmGM8Wxy7ajKVLOaUg7TrV8o-EdVWFzeEBmNmViMTI4ZmJlMDM3MTEzZTkzZjM3NjllYTYwMTk1NmY1N2NkZWNhNTYwOGY0NWUzMDhhZDU0ZmQ4YTQxNWVhYXVjc2F0"

    @Test()
    fun parseCashuA() {
        runBlocking {
            val parsed = (CashuParser().parse(cashuTokenA) as GenericLoadable.Loaded<ImmutableList<CashuToken>>).loaded[0]

            assertEquals(cashuTokenA, parsed.token)
            assertEquals("https://8333.space:3338", parsed.mint)
            assertEquals(10, parsed.totalAmount)

            assertEquals(2, parsed.proofs[0].amount)
            assertEquals("407915bc212be61a77e3e6d2aeb4c727980bda51cd06a6afc29e2861768a7837", parsed.proofs[0].secret)
            assertEquals("009a1f293253e41e", parsed.proofs[0].id)
            assertEquals("02bc9097997d81afb2cc7346b5e4345a9346bd2a506eb7958598a72f0cf85163ea", parsed.proofs[0].c)

            assertEquals(8, parsed.proofs[1].amount)
            assertEquals("fe15109314e61d7756b0f8ee0f23a624acaa3f4e042f61433c728c7057b931be", parsed.proofs[1].secret)
            assertEquals("009a1f293253e41e", parsed.proofs[1].id)
            assertEquals("029e8e5050b890a7d6c0968db16bc1d5d5fa040ea1de284f6ec69d61299f671059", parsed.proofs[1].c)
        }
    }

    @Test()
    fun parseCashuB() =
        runBlocking {
            val parsed = (CashuParser().parse(cashuTokenB1) as GenericLoadable.Loaded<ImmutableList<CashuToken>>).loaded

            assertEquals(cashuTokenB1, parsed[0].token)
            assertEquals("http://localhost:3338", parsed[0].mint)
            assertEquals(1, parsed[0].totalAmount)
            assertEquals(1, parsed[0].proofs[0].amount)
            assertEquals("acc12435e7b8484c3cf1850149218af90f716a52bf4a5ed347e48ecc13f77388", parsed[0].proofs[0].secret)
            assertEquals("00ffd48b8f5ecf80", parsed[0].proofs[0].id)
            assertEquals("0244538319de485d55bed3b29a642bee5879375ab9e7a620e11e48ba482421f3cf", parsed[0].proofs[0].c)

            assertEquals(3, parsed[1].totalAmount)
            assertEquals(2, parsed[1].proofs[0].amount)
            assertEquals("1323d3d4707a58ad2e23ada4e9f1f49f5a5b4ac7b708eb0d61f738f48307e8ee", parsed[1].proofs[0].secret)
            assertEquals("00ad268c4d1f5826", parsed[1].proofs[0].id)
            assertEquals("023456aa110d84b4ac747aebd82c3b005aca50bf457ebd5737a4414fac3ae7d94d", parsed[1].proofs[0].c)

            assertEquals(1, parsed[1].proofs[1].amount)
            assertEquals("56bcbcbb7cc6406b3fa5d57d2174f4eff8b4402b176926d3a57d3c3dcbb59d57", parsed[1].proofs[1].secret)
            assertEquals("00ad268c4d1f5826", parsed[1].proofs[1].id)
            assertEquals("0273129c5719e599379a974a626363c333c56cafc0e6d01abe46d5808280789c63", parsed[1].proofs[1].c)
        }

    @Test()
    fun parseCashuB2() =
        runBlocking {
            val parsed = (CashuParser().parse(cashuTokenB2) as GenericLoadable.Loaded<ImmutableList<CashuToken>>).loaded

            assertEquals(cashuTokenB2, parsed[0].token)
            assertEquals("http://lbutlh5lfggq5r7xpiwhrajdl7sxpupgagazxl65w4c5cg72wtofasad.onion:3338", parsed[0].mint)
            assertEquals(100, parsed[0].totalAmount)
            assertEquals(64, parsed[0].proofs[0].amount)
            assertEquals("7a8dcf9b3e8a247ce339e7369e9b4a19f31eacb69d8b0c65daaeb72d1acb9ad3", parsed[0].proofs[0].secret)
            assertEquals("009bb23d3a912e4e", parsed[0].proofs[0].id)
            assertEquals("03df591d261bcd176c69e3e833bcab5348ca31d218620492859303a1f5e874e9c7", parsed[0].proofs[0].c)

            assertEquals(32, parsed[0].proofs[1].amount)
            assertEquals("9d81c1a2616853ad8049cbcd1c7c247add83b373828620bac2fd7f3e5a58aceb", parsed[0].proofs[1].secret)
            assertEquals("009bb23d3a912e4e", parsed[0].proofs[1].id)
            assertEquals("039e52c02141738e9dc278db91bf3b333a37d13d8413d5acfe77a6b859de0de806", parsed[0].proofs[1].c)
        }
}
