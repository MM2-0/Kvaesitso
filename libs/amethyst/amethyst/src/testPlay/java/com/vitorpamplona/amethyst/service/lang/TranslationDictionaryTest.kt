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
package com.vitorpamplona.amethyst.service.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TranslationDictionaryTest {
    // ----- isWorthTranslating -----

    @Test
    fun `short text is not worth translating`() {
        assertFalse(TranslationDictionary.isWorthTranslating(""))
        assertFalse(TranslationDictionary.isWorthTranslating("a"))
        assertFalse(TranslationDictionary.isWorthTranslating("ab"))
        assertFalse(TranslationDictionary.isWorthTranslating("abc"))
    }

    @Test
    fun `letterless text is not worth translating`() {
        assertFalse(TranslationDictionary.isWorthTranslating("123456"))
        assertFalse(TranslationDictionary.isWorthTranslating("!!!!!!"))
        assertFalse(TranslationDictionary.isWorthTranslating("       "))
        // Emoji-only.
        assertFalse(TranslationDictionary.isWorthTranslating("😊😊😊"))
    }

    @Test
    fun `text with at least one letter is worth translating`() {
        assertTrue(TranslationDictionary.isWorthTranslating("Hello"))
        assertTrue(TranslationDictionary.isWorthTranslating("a123"))
        assertTrue(TranslationDictionary.isWorthTranslating("你好世界"))
        // Mixed emoji + letters.
        assertTrue(TranslationDictionary.isWorthTranslating("😊 hi"))
    }

    // ----- placeholder -----

    @Test
    fun `placeholder is the ICU MessageFormat positional token for that index`() {
        assertEquals("{0}", TranslationDictionary.placeholder(0))
        assertEquals("{1}", TranslationDictionary.placeholder(1))
        assertEquals("{42}", TranslationDictionary.placeholder(42))
        assertNotEquals(TranslationDictionary.placeholder(0), TranslationDictionary.placeholder(1))
    }

    @Test
    fun `placeholder rejects out of range index`() {
        try {
            TranslationDictionary.placeholder(-1)
            fail("expected IllegalArgumentException for negative index")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        try {
            TranslationDictionary.placeholder(TranslationDictionary.PLACEHOLDER_LIMIT + 1)
            fail("expected IllegalArgumentException for index past limit")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    // ----- build -----

    @Test
    fun `build empty dictionary for plain text`() {
        val dict = TranslationDictionary.build("Just plain text with no special tokens")
        assertTrue(dict.isEmpty())
    }

    @Test
    fun `build picks up a single URL`() {
        val text = "Have you seen this https://t.me/mygroup yet?"
        val dict = TranslationDictionary.build(text)
        assertEquals(1, dict.size)
        assertTrue("dict should contain the URL value", dict.containsValue("https://t.me/mygroup"))
    }

    @Test
    fun `build picks up nostr NIP-19 references`() {
        val text = "see nostr:nevent1qqs0tsw8hjacs4fppgdg7f5yhgwwfkyua4xcs3re9wwkpkk2qeu6mhql22rcy here"
        val dict = TranslationDictionary.build(text)
        assertEquals(1, dict.size)
        assertTrue(dict.containsValue("nostr:nevent1qqs0tsw8hjacs4fppgdg7f5yhgwwfkyua4xcs3re9wwkpkk2qeu6mhql22rcy"))
    }

    @Test
    fun `build picks up Lightning invoices`() {
        val invoice =
            "lnbc12u1p3lvjeupp5a5ecgp45k6pa8tu7rnkgzfuwdy3l5ylv3k5tdzrg4cr8rj2f364sdq5g9kxy7fqd9h8vmmfvdjs"
        val dict = TranslationDictionary.build("Pay me: $invoice please")
        assertEquals(1, dict.size)
        assertTrue(dict.containsValue(invoice))
    }

    @Test
    fun `build picks up legacy NIP-08 positional references`() {
        val text = "Have you seen this, #[0]"
        val dict = TranslationDictionary.build(text)
        assertEquals(1, dict.size)
        assertTrue(dict.containsValue("#[0]"))
    }

    @Test
    fun `build deduplicates repeated occurrences of the same value`() {
        val text = "https://a.com and again https://a.com"
        val dict = TranslationDictionary.build(text)
        assertEquals(1, dict.size)
    }

    @Test
    fun `build collects multiple distinct tokens`() {
        val text =
            "ln: lnbc12u1p3lvjeupp5a5ecgp45k6pa8tu7rnkgzfuwdy3l5ylv3 url: https://a.com " +
                "ref: nostr:nevent1qqsabcdefghjklmnpqrstuvwxyz023456789 nip08: #[0]"
        val dict = TranslationDictionary.build(text)
        // We expect at least one entry per category. Exact count depends on the regexes' bech32-charset
        // truncation behaviour; the contract we care about is that each distinct kind is captured.
        assertTrue(dict.values.any { it.startsWith("lnbc") })
        assertTrue("https://a.com" in dict.values)
        assertTrue(dict.values.any { it.startsWith("nostr:nevent1") })
        assertTrue("#[0]" in dict.values)
    }

    @Test
    fun `build rejects URLs with Chinese full-width punctuation false-positives`() {
        // The URL detector greedily includes ， and 。 — those substrings are not real URLs.
        val text = "看 http://x.com，再见。"
        val dict = TranslationDictionary.build(text)
        for (value in dict.values) {
            assertFalse("URL with ， or 。 should be skipped: $value", value.contains('，') || value.contains('。'))
        }
    }

    // ----- encode / decode round-trip -----

    @Test
    fun `encode replaces dictionary values with placeholders and decode restores them`() {
        val text = "Have you seen this https://t.me/mygroup ?"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)

        assertFalse("URL must be removed from encoded text", encoded.contains("https://t.me/mygroup"))
        assertTrue("encoded text must contain a {N} placeholder", Regex("\\{\\d+}").containsMatchIn(encoded))

        val decoded = TranslationDictionary.decode(encoded, dict)
        assertEquals(text, decoded)
    }

    @Test
    fun `round-trip preserves nostr references through a simulated translation`() {
        val text = "Have you seen this, #[0] and nostr:nevent1qqsabcdefgh023456?"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)

        // Simulate a translator: rewrite the surrounding English to Portuguese, but pass placeholders through unchanged.
        val translated = encoded.replace("Have you seen this", "Você já viu isso").replace("and", "e")

        val decoded = TranslationDictionary.decode(translated, dict)!!
        assertTrue("decoded must contain #[0]", decoded.contains("#[0]"))
        assertTrue("decoded must contain the nostr ref", decoded.contains("nostr:nevent1qqsabcdefgh023456"))
        // The dictionary picks indices above any user-supplied {N}; user text here has none, so the
        // dict starts at {0}. After decode, no {N} placeholder we issued should remain.
        for (token in dict.keys) {
            assertFalse("decoded leaked placeholder $token", decoded.contains(token))
        }
    }

    @Test
    fun `round-trip preserves multiple URLs of differing lengths`() {
        val text =
            "short https://a.co and " +
                "long https://i.imgur.com/asdEZ3QPswadfj2389rioasdjf9834riofaj9834aKLL.jpg end"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)
        val decoded = TranslationDictionary.decode(encoded, dict)
        assertEquals(text, decoded)
    }

    @Test
    fun `encode replaces longer values first to avoid prefix collisions`() {
        // If "https://a.co" was replaced before "https://a.co/long", the longer URL would be partially clobbered.
        val text = "long https://a.co/long short https://a.co end"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)
        // Both URLs must be fully replaced — no leftover http:// fragments.
        assertFalse("no leftover URL fragment in encoded text", encoded.contains("https://"))
        val decoded = TranslationDictionary.decode(encoded, dict)
        assertEquals(text, decoded)
    }

    @Test
    fun `decode does not corrupt user text containing the old B0 C0 A0 placeholders`() {
        // Regression for a pre-rewrite bug where placeholders were "B0", "C0", "A0" — those tokens
        // collided with arbitrary user content. Today's {N} tokens can't collide with B0/C0/A0
        // strings, but this case is cheap to keep as a guard.
        val text = "Pricing tier B0 vs C0 vs A0 — see https://docs.example.com/tiers"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)
        val decoded = TranslationDictionary.decode(encoded, dict)!!
        assertTrue(decoded.contains("B0"))
        assertTrue(decoded.contains("C0"))
        assertTrue(decoded.contains("A0"))
        assertEquals(text, decoded)
    }

    @Test
    fun `case sensitive replacement preserves user text that differs only in case`() {
        // The pre-rewrite implementation used ignoreCase=true, which could mangle user text that looked
        // like a URL placeholder in a different case. With case-sensitive replacement this can't happen.
        val text = "Visit HTTPS://A.COM/Path then revisit https://a.com/Path"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)
        val decoded = TranslationDictionary.decode(encoded, dict)
        assertEquals(text, decoded)
    }

    @Test
    fun `encode is no-op when dictionary is empty`() {
        val text = "Plain text without anything special"
        assertEquals(text, TranslationDictionary.encode(text, emptyMap()))
    }

    @Test
    fun `decode handles null input`() {
        assertNull(TranslationDictionary.decode(null, mapOf("a" to "b")))
    }

    @Test
    fun `decode is no-op when dictionary is empty`() {
        val text = "anything goes"
        assertEquals(text, TranslationDictionary.decode(text, emptyMap()))
    }

    @Test
    fun `mixed content from real-world test cases round-trips`() {
        val text =
            "Hi there! \n How are you doing? \n https://i.imgur.com/asdEZ3QPswadfj2389rioasdjf9834riofaj9834aKLL.jpg"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)
        val decoded = TranslationDictionary.decode(encoded, dict)
        assertEquals(text, decoded)
    }

    @Test
    fun `complex real-world post round-trips`() {
        // Mirrors TranslationsTest#testHttp: URL + emoji + multiple NIP-19 references.
        val text =
            "https://m.primal.net/MdDd.png \nRunning... 😁   " +
                "nostr:npub126ntw5mnermmj0znhjhgdk8lh2af72sm8qfzq48umdlnhaj9kuns3le9ll  " +
                "nostr:npub1getal6ykt05fsz5nqu4uld09nfj3y3qxmv8crys4aeut53unfvlqr80nfm"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)
        val decoded = TranslationDictionary.decode(encoded, dict)
        assertEquals(text, decoded)
        // And every special token must have been replaced in the encoded form.
        assertFalse(encoded.contains("https://m.primal.net/MdDd.png"))
        assertFalse(encoded.contains("nostr:npub126ntw5mnermmj0znhjhgdk8lh2af72sm8qfzq48umdlnhaj9kuns3le9ll"))
        assertFalse(encoded.contains("nostr:npub1getal6ykt05fsz5nqu4uld09nfj3y3qxmv8crys4aeut53unfvlqr80nfm"))
    }

    @Test
    fun `build allocates indices above any user-supplied placeholders`() {
        // User's note contains literal {0} and {3} (e.g. a code snippet). Our dictionary must not
        // reuse those indices, or decode would clobber the user's own text.
        val text = "Code is printf(\"%s\", arg{0}) or fmt(\"%s\", arg{3}); see https://docs.example.com"
        val dict = TranslationDictionary.build(text)
        assertTrue("dict should include the URL", dict.containsValue("https://docs.example.com"))
        for (token in dict.keys) {
            assertFalse("$token collides with user-supplied {0}", token == "{0}")
            assertFalse("$token collides with user-supplied {3}", token == "{3}")
        }
    }

    @Test
    fun `build is resilient to adversarial user placeholders beyond the table limit`() {
        // Adversarial note carries `{99999}` — well past PLACEHOLDER_LIMIT. If we naively started
        // our counter at max+1, the very next call to placeholder() would throw and translation
        // would fail entirely. Counter must stay within the allowed range.
        val text = "weird {99999} placeholder; see https://docs.example.com"
        val dict = TranslationDictionary.build(text)
        assertTrue("dict should include the URL", dict.containsValue("https://docs.example.com"))
        // {99999} is out of range and never enters our dict, so it survives encode/decode untouched.
        val encoded = TranslationDictionary.encode(text, dict)
        assertTrue("user's {99999} must survive encode", encoded.contains("{99999}"))
        val decoded = TranslationDictionary.decode(encoded, dict)
        assertEquals(text, decoded)
    }

    @Test
    fun `round-trip preserves user-supplied placeholders in source text`() {
        // The user wrote {0} and {3}; after encode/decode they must round-trip intact alongside our
        // own placeholder for the URL.
        val text = "Code: printf(\"%s\", arg{0}) and fmt(\"%s\", arg{3}); see https://docs.example.com"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)
        // Encoded form still contains the user's {0} and {3} verbatim — we never touched them.
        assertTrue(encoded.contains("arg{0}"))
        assertTrue(encoded.contains("arg{3}"))
        val decoded = TranslationDictionary.decode(encoded, dict)
        assertEquals(text, decoded)
    }
}
