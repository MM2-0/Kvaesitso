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
package com.vitorpamplona.amethyst.commons.richtext

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlParserTest {
    val parser = UrlParser()

    fun test(
        text: String,
        expected: Urls,
    ) {
        val urlSet = parser.parseValidUrls(text)
        assertEquals(expected.withScheme, urlSet.withScheme)
        assertEquals(expected.withoutScheme, urlSet.withoutScheme)
        assertEquals(expected.emails, urlSet.emails)
        assertEquals(expected.bech32s, urlSet.bech32s)
        assertEquals(expected.blossomUris, urlSet.blossomUris)
        assertEquals(expected.relayUrls, urlSet.relayUrls)
    }

    @Test
    fun testSimpleText() =
        test(
            "test. com",
            Urls(),
        )

    @Test
    fun testBasicUrl() =
        test(
            "http://test.com",
            Urls(withScheme = setOf("http://test.com")),
        )

    @Test
    fun testNoSchemaUrl() =
        test(
            "test.com",
            Urls(withoutScheme = setOf("test.com")),
        )

    @Test
    fun testNoSchemaUrlPrefixMultibyte() =
        test(
            "ほtest.com",
            Urls(withoutScheme = setOf("test.com")),
        )

    @Test
    fun testNoSchemaUrlSuffixMultibyte() =
        test(
            "test.comほ",
            Urls(withoutScheme = setOf("test.com")),
        )

    @Test
    fun testNoSchemaUrlWithParams() =
        test(
            "test.com/some/me/hey?param=value#some=value",
            Urls(withoutScheme = setOf("test.com/some/me/hey?param=value#some=value")),
        )

    @Test
    fun testNoSchemaUrlWithParamsWithOtherWords() =
        test(
            "Hi there, check my website test.com/some/me/hey?param=value#some=value .",
            Urls(withoutScheme = setOf("test.com/some/me/hey?param=value#some=value")),
        )

    @Test
    fun testBasicUrlWithoutSpaceBefore() =
        test(
            "ahttp://test.com",
            Urls(withScheme = setOf("http://test.com")),
        )

    @Test
    fun testBasicUrlWithoutSpaceBeforeMultiByte() =
        test(
            "ほhttp://test.com",
            Urls(withScheme = setOf("http://test.com")),
        )

    @Test
    fun testBasicUrlWithoutSpaceAfter() =
        test(
            "http://test.comほ",
            Urls(withScheme = setOf("http://test.com")),
        )

    @Test
    fun testBasicUrlWithMultibytePath() =
        test(
            "http://test.com/ほ",
            Urls(withScheme = setOf("http://test.com/ほ")),
        )

    @Test
    fun testBasicUrls() =
        test(
            "http://test.com http://test2.com",
            Urls(withScheme = setOf("http://test.com", "http://test2.com")),
        )

    @Test
    fun testEmail() =
        test(
            "vitor@vitorpamplona.com",
            Urls(emails = setOf("vitor@vitorpamplona.com")),
        )

    @Test
    fun testEmailWithMultibytePrefix() =
        test(
            "ほvitor@vitorpamplona.com",
            Urls(emails = setOf("vitor@vitorpamplona.com")),
        )

    @Test
    fun testEmailWithMultibyteSuffix() =
        test(
            "vitor@vitorpamplona.comほ",
            Urls(emails = setOf("vitor@vitorpamplona.com")),
        )

    @Test
    fun testEmailWithMultibyteBoth() =
        test(
            "ほvitor@vitorpamplona.comほ",
            Urls(emails = setOf("vitor@vitorpamplona.com")),
        )

    @Test
    fun testUrlWithUserAndMultibyteSuffix() =
        test(
            "http://vitor@vitorpamplona.comほ",
            Urls(withScheme = setOf("http://vitor@vitorpamplona.com")),
        )

    @Test
    fun testUrlWithUserAndMultibytePrefix() =
        test(
            "ほhttp://vitor@vitorpamplona.com",
            Urls(withScheme = setOf("http://vitor@vitorpamplona.com")),
        )

    @Test
    fun testNostrUrls() =
        test(
            "nostr:npub142gywvjkq0dv6nupggyn2euhx4nduwc7yz5f24ah9rpmunr2s39se3xrj0",
            Urls(bech32s = setOf("nostr:npub142gywvjkq0dv6nupggyn2euhx4nduwc7yz5f24ah9rpmunr2s39se3xrj0")),
        )

    @Test
    fun testUrlsWithUsernameAndPath() =
        test(
            "miceliomad@miceliomad.github.io/nostr/",
            Urls(withoutScheme = setOf("miceliomad@miceliomad.github.io/nostr/")),
        )

    @Test
    fun testUrlsWithQuery() =
        test(
            " universe.nostrich.land?lang=zh ",
            Urls(withoutScheme = setOf("universe.nostrich.land?lang=zh")),
        )

    @Test
    fun testUrlsWithSchemaPathAndQuery() =
        test(
            "https://miceliomad.github.io/nostr/test?me=you",
            Urls(withScheme = setOf("https://miceliomad.github.io/nostr/test?me=you")),
        )

    @Test
    fun testUrlsWithPathAndQuery() =
        test(
            "miceliomad.github.io/nostr/test?me=you",
            Urls(withoutScheme = setOf("miceliomad.github.io/nostr/test?me=you")),
        )

    @Test
    fun testAvifFileNameComplete() =
        test(
            "https://bae.st/media/66b08dde784287ed8f92c455bc62076a04671ccb44097550626a532185a5d3ed.avif?name=81ca16-b665-4f57-80cb-11a58461fb61.avif",
            Urls(withScheme = setOf("https://bae.st/media/66b08dde784287ed8f92c455bc62076a04671ccb44097550626a532185a5d3ed.avif?name=81ca16-b665-4f57-80cb-11a58461fb61.avif")),
        )

    @Test
    fun testAvifFileName() =
        test(
            "81ca16-b665-4f57-80cb-11a58461fb61.avif",
            Urls(withoutScheme = setOf("81ca16-b665-4f57-80cb-11a58461fb61.avif")),
        )

    @Test
    fun testMultiLine() =
        test(
            """
            22.8K (3.2%) nos.lol
            22.7K (3.1%) universe.nostrich.land?lang=zh
            22.5K (3.1%) universe.nostrich.land?lang=en
            """.trimIndent(),
            Urls(withoutScheme = setOf("nos.lol", "universe.nostrich.land?lang=zh", "universe.nostrich.land?lang=en")),
        )

    @Test
    fun testEmailWithDashes() =
        test(
            "freeverification@Nostr-Check.com",
            Urls(emails = setOf("freeverification@Nostr-Check.com")),
        )

    @Test
    fun testEmailWithManyDashes() =
        test(
            "free-veri-fica-tion@No-str-Ch-eck.com",
            Urls(emails = setOf("free-veri-fica-tion@No-str-Ch-eck.com")),
        )

    @Test
    fun testEmailWithUnderscore() =
        test(
            "vi_t_or@vitorpamplona.com",
            Urls(emails = setOf("vi_t_or@vitorpamplona.com")),
        )

    @Test
    fun testEmailsWithPeriod() =
        test(
            "john.smith@gmail.com",
            Urls(emails = setOf("john.smith@gmail.com")),
        )

    @Test
    fun testStrangeError() =
        test(
            "neomobius_at_mstdn.jp@mostr.pub",
            Urls(emails = setOf("neomobius_at_mstdn.jp@mostr.pub")),
        )

    @Test
    fun testRelayUrl() =
        test(
            "wss://test.com",
            Urls(relayUrls = setOf("wss://test.com")),
        )

    @Test
    fun testBech12() =
        test(
            "nostr:npub142gywvjkq0dv6nupggyn2euhx4nduwc7yz5f24ah9rpmunr2s39se3xrj0",
            Urls(bech32s = setOf("nostr:npub142gywvjkq0dv6nupggyn2euhx4nduwc7yz5f24ah9rpmunr2s39se3xrj0")),
        )

    @Test
    fun testJapaneseUrls() =
        test(
            "我进入你的主页很卡顿，也许是你的关注人数或者其他数据太多了，其他人主页没有这么卡顿。来自amethyst客户端",
            Urls(withScheme = emptySet()),
        )

    /**
     * Regression test for PR #1907: parsing a note whose content is only the Japanese phrase
     * "今北産業" (a common internet abbreviation) must not throw a StringIndexOutOfBoundsException
     * from Url.getPart() and must produce no detected URLs.
     */
    @Test
    fun testImakitaSangyo() =
        test(
            "今北産業",
            Urls(),
        )

    @Test
    fun testHour() =
        test(
            "10.00hr,",
            Urls(withScheme = emptySet()),
        )

    @Test
    fun testIPv6UrlWithScheme() =
        test(
            "http://[302:68d0:f0d5:b88d::bdb]/08e31992c51b06d8f8ee4e40207ede11d4c69b0db2d268fb2783d45d69094c2b",
            Urls(withScheme = setOf("http://[302:68d0:f0d5:b88d::bdb]/08e31992c51b06d8f8ee4e40207ede11d4c69b0db2d268fb2783d45d69094c2b")),
        )

    @Test
    fun testIPv6UrlWithPort() =
        test(
            "http://[2001:db8::1]:8080/path",
            Urls(withScheme = setOf("http://[2001:db8::1]:8080/path")),
        )

    @Test
    fun testIPv6UrlInSentence() =
        test(
            "check this http://[2a01:5cc0:1:2::4] out",
            Urls(withScheme = setOf("http://[2a01:5cc0:1:2::4]")),
        )

    @Test
    fun testUrlWithBalancedParenthesis() =
        test(
            "https://en.wikipedia.org/wiki/Bitcoin_(disambiguation)",
            Urls(withScheme = setOf("https://en.wikipedia.org/wiki/Bitcoin_(disambiguation)")),
        )

    @Test
    fun testUrlWithCommaAndBalancedParenthesis() =
        test(
            "https://memory-alpha.fandom.com/wiki/Scorpion,_Part_II_(episode)",
            Urls(withScheme = setOf("https://memory-alpha.fandom.com/wiki/Scorpion,_Part_II_(episode)")),
        )

    @Test
    fun testUrlWithBalancedParenthesisInSentence() =
        test(
            "Read https://en.wikipedia.org/wiki/Bitcoin_(disambiguation) for context.",
            Urls(withScheme = setOf("https://en.wikipedia.org/wiki/Bitcoin_(disambiguation)")),
        )

    @Test
    fun testUrlWrappedInParenthesisDropsCloser() =
        test(
            "(see https://test.com)",
            Urls(withScheme = setOf("https://test.com")),
        )

    @Test
    fun testQuotedRelayName() =
        test(
            "It seems like this bridge-relay \"relay.momostr.pink\" doesn't appear in the feed at all",
            Urls(withoutScheme = setOf("relay.momostr.pink")),
        )

    @Test
    fun testSingleQuotedRelayName() =
        test(
            "It seems like this bridge-relay 'relay.momostr.pink' doesn't appear in the feed at all",
            Urls(withoutScheme = setOf("relay.momostr.pink")),
        )

    @Test
    fun testQuotedUrlWithScheme() =
        test(
            "the docs are at \"https://example.com/some/page?a=b\" if you need them",
            Urls(withScheme = setOf("https://example.com/some/page?a=b")),
        )

    @Test
    fun testQuotedRelayUrl() =
        test(
            "add \"wss://relay.momostr.pink\" to your list",
            Urls(relayUrls = setOf("wss://relay.momostr.pink")),
        )

    @Test
    fun testBlossom() {
        val blossom = "blossom:b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553.pdf?xs=cdn.satellite.earth"
        test(blossom, Urls(blossomUris = setOf(blossom)))
    }

    @Test
    fun testBlossomComplete() {
        val blossom = "blossom:a7b3c2d1e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1.png?xs=cdn.example.com&xs=media.nostr.build&as=781208004e09102d7da3b7345e64fd193cd1bc3fce8fdae6008d77f9cabcd036&as=b53185b9f27962ebdf76b8a9b0a84cd8b27f9f3d4abd59f715788a3bf9e7f75e&sz=2547831"
        test(blossom, Urls(blossomUris = setOf(blossom)))
    }
}
