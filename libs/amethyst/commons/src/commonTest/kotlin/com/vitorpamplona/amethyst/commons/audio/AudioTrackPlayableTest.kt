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
package com.vitorpamplona.amethyst.commons.audio

import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Kind 31337 events as served by wss://search.brainstorm.world (2026-10). */
class AudioTrackPlayableTest {
    private fun track(
        vararg tags: Array<String>,
        content: String = "",
    ) = AudioTrackEvent(
        id = "a".repeat(64),
        pubKey = "c2f1ab4e6a57".padEnd(64, '0'),
        createdAt = 1791913269,
        tags = arrayOf(*tags),
        content = content,
        sig = "0".repeat(128),
    )

    @Test
    fun aMediaFileNamedAsAudioPlays() {
        val mp3 = "https://renaissancemachine.ai/music/ball-lightning.mp3"
        assertEquals(PlayableAudio(mp3, null), track(arrayOf("d", "ball-lightning"), arrayOf("media", mp3), arrayOf("url", mp3)).playableAudio())

        val mpga = "https://blossom.yakihonne.com/952ae9b52c8494b000c40050ffd1793ce47e137eac9c802d883898a88ad8efa7.mpga"
        assertEquals(mpga, track(arrayOf("d", "x"), arrayOf("media", mpga)).playableAudio()?.url)
    }

    @Test
    fun anExtensionlessBlossomFilePlaysByItsImetaMime() {
        val blob = "https://blossom.yakihonne.com/7224210c45dbdcfcc6afbb6d7e9d81c98349cb474395e5e1044d5eeafd2bb8d0"
        val event =
            track(
                arrayOf("d", "x"),
                arrayOf("media", blob),
                arrayOf("imeta", "url $blob", "m audio/mpeg", "x 7224210c45dbdcfcc6afbb6d7e9d81c98349cb474395e5e1044d5eeafd2bb8d0"),
                arrayOf("t", "BBSnostr"),
            )
        assertEquals(PlayableAudio(blob, "audio/mpeg"), event.playableAudio())
    }

    @Test
    fun anExtensionlessFilePlaysByTheEventsMimeTag() {
        // lightning.fm: `url` and `m` side by side, NIP-94 style.
        val file = "https://media.lightning.fm/250be958d3be2bd621b5c788f3226c7969394e99f5accad9b98580bb51141157"
        val event = track(arrayOf("d", "cypherpunk-lullaby"), arrayOf("title", "Cypherpunk Lullaby"), arrayOf("url", file), arrayOf("m", "audio/mpeg"))
        assertEquals(PlayableAudio(file, "audio/mpeg"), event.playableAudio())
    }

    @Test
    fun aWebPageInUrlGivesWayToTheImetaFile() {
        // A podcast episode that names the show's page in `url` and the episode's file in its imeta.
        val file = "https://dts.podtrac.com/redirect.mp3/pdst.fm/e/nyt.simplecastaudio.com/episodes/audio/128/default.mp3?aid=rss_feed"
        val event =
            track(
                arrayOf("title", "Nixon Dreamed of Breaking the Media. Trump Is Doing It."),
                arrayOf("url", "https://www.nytimes.com/the-daily"),
                arrayOf("imeta", "url $file", "size 43638028", "type audio/mpeg"),
                arrayOf("d", "Nixon-Dreamed-of-Breaking-the-Media-Trump-Is-Doing-It-m1lmhz"),
            )
        assertEquals(file, event.playableAudio()?.url)
    }

    @Test
    fun anMp4StaysForTheCardToProbe() {
        // The audio card reads an MP4's track list to tell an AAC upload from a music video.
        val mp4 = "https://blossom.band/727dbd3c23b2f6c1de63fd87083ecadae5782963bb94cefe81c9f3de361bbee0.mp4"
        assertEquals(mp4, track(arrayOf("d", "x"), arrayOf("media", mp4)).playableAudio()?.url)
    }

    @Test
    fun recordsThatOnlyBorrowTheKindHaveNothingToPlay() {
        // AntennaPod's ad-skip data.
        val adSkip =
            track(
                arrayOf("d", "ba4448828f27ea93323a347f1809e460"),
                arrayOf("t", "antennapod-adskip"),
                content = "{\"status\":\"complete\",\"ads\":[{\"startMs\":0,\"endMs\":30390}]}",
            )
        assertNull(adSkip.playableAudio())

        // A file-sync index: a path that ends in `.mp3`, but in `d`, with no file to fetch.
        val index =
            track(
                arrayOf("d", "music/dj/dnb/2023/Sub Focus - Do It To It.mp3"),
                arrayOf("x", "842ed8913e623a24213638d4483d47936217711b839e7c3ac95e2e09aa333420"),
                arrayOf("size", "12042188"),
            )
        assertNull(index.playableAudio())

        // Links to a streaming site's page, and a bare extensionless file nothing types.
        assertNull(track(arrayOf("d", "x"), arrayOf("url", "https://www.youtube.com/watch?v=dQw4w9WgXcQ")).playableAudio())
        assertNull(track(arrayOf("d", "x"), arrayOf("media", "https://void.cat/d/AbCdEf")).playableAudio())
        assertNull(track(arrayOf("d", "x"), arrayOf("media", "not a url.mp3")).playableAudio())
    }
}
