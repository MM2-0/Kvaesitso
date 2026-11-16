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
import com.google.android.gms.tasks.Tasks
import com.vitorpamplona.amethyst.commons.model.AccountLanguagePreferences
import com.vitorpamplona.amethyst.commons.state.ModelDownloads
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CancellationException

@RunWith(AndroidJUnit4::class)
class TranslationsTest {
    fun translateTo(
        text: String,
        translateTo: String,
    ): String? {
        val task =
            LanguageTranslatorService.autoTranslate(
                text,
                emptySet(),
                translateTo,
                ModelDownloads.ANY_NETWORK,
                AccountLanguagePreferences(MutableStateFlow(emptySet()), MutableStateFlow(emptyMap()), MutableStateFlow(translateTo)),
            )
        return Tasks.await(task).result
    }

    fun assertTranslate(
        expected: String,
        input: String,
        translateTo: String,
    ) {
        assertEquals(null, expected, translateTo(input, translateTo))
    }

    fun assertTranslateContains(
        expected: String,
        input: String,
        translateTo: String,
    ) {
        val translated = translateTo(input, translateTo)!!
        assertTrue("'$translated' does not contain '$expected'", translated.contains(expected))
    }

    @Test
    fun testTranslation() {
        assertTranslate("Olá mundo", "Hello World", "pt")
    }

    @Test
    fun testTranslationName() {
        assertTranslate("Olá Vitor, como você está?", "Hello Vitor, how are you doing?", "pt")
    }

    @Test
    fun testTranslationTag() {
        assertTranslate("Você já viu isso, #[0]", "Have you seen this, #[0]", "pt")
    }

    @Test
    fun testTranslationUrl() {
        assertTranslateContains("https://t.me/mygroup", "Have you seen this https://t.me/mygroup", "pt")
        assertTranslateContains("http://bananas.com", "Have you seen this http://bananas.com", "pt")
        assertTranslateContains(
            "http://bananas.com/myimage.jpg",
            "Have you seen this http://bananas.com/myimage.jpg",
            "pt",
        )
        assertTranslateContains(
            "http://bananas.com?search=true&image=myimage.jpg",
            "Have you seen this http://bananas.com?search=true&image=myimage.jpg",
            "pt",
        )
        assertTranslate("https://i.imgur.com/EZ3QPsw.jpg", "https://i.imgur.com/EZ3QPsw.jpg", "pt")
        assertTranslate("https://HaveYouSeenThis.com", "https://HaveYouSeenThis.com", "pt")
        assertTranslate("https://haveyouseenthis.com", "https://haveyouseenthis.com", "pt")
        assertTranslate(
            "https://i.imgur.com/asdEZ3QPsw.jpg",
            "https://i.imgur.com/asdEZ3QPsw.jpg",
            "pt",
        )
        assertTranslateContains(
            "https://i.imgur.com/asdEZ3QPswadfj2389rioasdjf9834riofaj9834aKLL.jpg",
            "Hi there! \n How are you doing? \n https://i.imgur.com/asdEZ3QPswadfj2389rioasdjf9834riofaj9834aKLL.jpg",
            "pt",
        )
    }

    @Test
    fun testChineseWithUrlDetector() {
        assertTranslate(
            "I entered your home page is very carton, perhaps your attention or other data is too much, and the homepage of others is not so carton. From aMethyst client",
            "我进入你的主页很卡顿，也许是你的关注人数或者其他数据太多了，其他人主页没有这么卡顿。来自amethyst客户端",
            "en",
        )
    }

    @Test
    fun testTranslationEmail() {
        assertTranslateContains(
            "vitor@amethyst.social",
            "Have you seen this vitor@amethyst.social",
            "pt",
        )
    }

    @Test
    fun testTranslationLnInvoice() {
        assertTranslateContains(
            "lnbc12u1p3lvjeupp5a5ecgp45k6pa8tu7rnkgzfuwdy3l5ylv3k5tdzrg4cr8rj2f364sdq5g9kxy7fqd9h8vmmfvdjscqzpgxqyz5vqsp5zuzyetf33aphetf0e80w7tztw6dfsjs4lmvya4cyk8umfsx00qts9qyyssqke9hphcr36zvcav8wr502g0mhfhxpy8m9tt36zttg8vldm2qxw039ulccr8nwy3hjg2sw5vk65e99lwuhrhw0nuya2u57qszltvx7egp74jydn",
            "Have you seen this: lnbc12u1p3lvjeupp5a5ecgp45k6pa8tu7rnkgzfuwdy3l5ylv3k5tdzrg4cr8rj2f364sdq5g9kxy7fqd9h8vmmfvdjscqzpgxqyz5vqsp5zuzyetf33aphetf0e80w7tztw6dfsjs4lmvya4cyk8umfsx00qts9qyyssqke9hphcr36zvcav8wr502g0mhfhxpy8m9tt36zttg8vldm2qxw039ulccr8nwy3hjg2sw5vk65e99lwuhrhw0nuya2u57qszltvx7egp74jydn I think I have to pay",
            "pt",
        )
    }

    @Test(expected = CancellationException::class)
    fun testTranslationLnInvoice2() {
        assertTranslateContains(
            "lnbc10u1p3l0wg0pp5y5y3vxt3429m28uuq56uqhwxadftn67yaarq06h3y9nqapz72n6sdqqxqyjw5q9q7sqqqqqqqqqqqqqqqqqqqqqqqqq9qsqsp5y2tazp42xde3c0tdsz30zqcekrt0lzrneszdtagy2qn7vs0d3p5qrzjqwryaup9lh50kkranzgcdnn2fgvx390wgj5jd07rwr3vxeje0glcll7jdvcln4lhw5qqqqlgqqqqqeqqjqdau9jzseecmvmh03h88xyf5f980xx45fmn0cej654v5jr79ye36pww90jwdda38damlmgt54v8rn6q9kywtw057rh4v3wwrmn8fajagqnssr7v",
            "Test lnbc10u1p3l0wg0pp5y5y3vxt3429m28uuq56uqhwxadftn67yaarq06h3y9nqapz72n6sdqqxqyjw5q9q7sqqqqqqqqqqqqqqqqqqqqqqqqq9qsqsp5y2tazp42xde3c0tdsz30zqcekrt0lzrneszdtagy2qn7vs0d3p5qrzjqwryaup9lh50kkranzgcdnn2fgvx390wgj5jd07rwr3vxeje0glcll7jdvcln4lhw5qqqqlgqqqqqeqqjqdau9jzseecmvmh03h88xyf5f980xx45fmn0cej654v5jr79ye36pww90jwdda38damlmgt54v8rn6q9kywtw057rh4v3wwrmn8fajagqnssr7v",
            "pt",
        )
    }

    @Test(expected = CancellationException::class)
    fun testNostrEvents() {
        assertTranslateContains(
            "nostr:nevent1qqs0tsw8hjacs4fppgdg7f5yhgwwfkyua4xcs3re9wwkpkk2qeu6mhql22rcy",
            "sure, nostr:nevent1qqs0tsw8hjacs4fppgdg7f5yhgwwfkyua4xcs3re9wwkpkk2qeu6mhql22rcy",
            "en",
        )
    }

    @Test
    fun testJapaneseTranslationsOfUrl() {
        assertTranslateContains(
            "https://youtu.be/wMYFmCDy_Eg",
            "うちの会社の小さい先輩の話 第1話「うちの会社の先輩は小さくて可愛い」\n" +
                "\n" +
                "https://youtu.be/wMYFmCDy_Eg\n" +
                "\n" +
                "先輩がうざい後輩の話と似たような話かと思ったけど、もっとオタクの妄想あるある的なものを詰め込んだやつだ。ワードとかシチュエーションとか、ヒロインのサイズ感とか。知らんけど",
            "en",
        )
    }

    @Test
    fun testEmoji() {
        assertTranslateContains(
            "https://cdn.nostr.build/i/df3783dcdf7dd289ba02ba538dc039c8fe1d4db055e580b81604ed88c6af4ee0.jpg",
            "\uD83E\uDD23 https://cdn.nostr.build/i/df3783dcdf7dd289ba02ba538dc039c8fe1d4db055e580b81604ed88c6af4ee0.jpg ",
            "pt",
        )
    }

    @Test
    fun testJapaneseImageUrlIssue1180() {
        // Regression for https://github.com/vitorpamplona/amethyst/issues/1180 — image URL at the end
        // of a Japanese post must survive ja→en auto-translation intact.
        assertTranslateContains(
            "https://image.nostr.build/3cd07f308c13dd9197d03430a75bf5326f86d2ab68674af971e915f55a638ecd.jpg",
            "「正方形のカードを並べて盤面にして、その上でカードゲームとボードゲーム同時にやったらおもろくね？」" +
                "という誰でも思いつきはしそうなアイデアを、ちゃんとルール練り上げて実装まで漕ぎ着けてしまったやつ。" +
                "ブースの人が「こんなところに気を遣ったんですわ〜」っていうのに全部ウンウンわかるそれそれと思わされたので買った " +
                "https://image.nostr.build/3cd07f308c13dd9197d03430a75bf5326f86d2ab68674af971e915f55a638ecd.jpg ",
            "en",
        )
    }

    @Test
    fun testHttp() {
        val text = "https://m.primal.net/MdDd.png \nRunning... \uD83D\uDE01   nostr:npub126ntw5mnermmj0znhjhgdk8lh2af72sm8qfzq48umdlnhaj9kuns3le9ll  nostr:npub1getal6ykt05fsz5nqu4uld09nfj3y3qxmv8crys4aeut53unfvlqr80nfm"

        assertTranslateContains(
            "https://m.primal.net/MdDd.png",
            text,
            "pt",
        )
    }
}
