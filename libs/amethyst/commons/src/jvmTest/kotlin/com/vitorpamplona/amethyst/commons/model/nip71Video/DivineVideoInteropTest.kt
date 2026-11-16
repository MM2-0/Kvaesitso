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
package com.vitorpamplona.amethyst.commons.model.nip71Video

import com.vitorpamplona.amethyst.commons.relayClient.video.SUPPORTED_VIDEO_FEED_MIME_TYPES_SET
import com.vitorpamplona.amethyst.commons.richtext.MediaContentKind
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.quartz.experimental.videoCollaboration.VideoCollaborationEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip51Lists.videoCurationSet.VideoCurationSetEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.credits.CreditTarget
import com.vitorpamplona.quartz.nip71Video.credits.VideoCredit
import com.vitorpamplona.quartz.nip71Video.textTrack.TextTrackEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Interop guard for divine.video (<https://github.com/divinevideo>), the biggest publisher of
 * NIP-71 kind-34236 short videos on the network. Every fixture below is a real event pulled from
 * `wss://relay.divine.video` — only the oversized `proofmode`/`device_attestation`/
 * `identity_binding` blobs were stripped, which is why the signatures are not re-verified here.
 *
 * Divine's events exercise two shapes Amethyst used to get wrong and can silently regress on:
 *
 *  1. **Extension-less media URLs.** Videos live on Blossom, so the URL is
 *     `https://media.divine.video/<sha256>` with no `.mp4`. Only the `imeta` `m` field says it is
 *     a video, so every extension-driven branch (feed admission, image-vs-player classification)
 *     has to consult the declared MIME first.
 *  2. **Dual pointers on every interaction.** Reactions, reposts and comments carry BOTH the
 *     addressable coordinate (`a`/`A`) and the specific version's event id (`e`/`E`), so both
 *     have to resolve or the counters land on a note nobody renders.
 */
class DivineVideoInteropTest {
    private val video =
        """{"id":"fa5a793b24edfe109f8d02ad6aa6cde77b0a923566f891df403049721b46e8d9","pubkey":"4d7dccc0a5116daa057348ef79c573873cddd9eff066fc6a5f3d37e8264afbeb","created_at":1789661586,"kind":34236,"tags":[["d","c855df3d07ba963e9097d5a151b0c14a0a3d494e4ff9b04a389bc0b5f5c16862"],["text-track","https://media.divine.video/283a420202b620a9a326598b85bfcf0e8cb4ab4d52b947c628d8c29a1313006b","wss://relay.divine.video","captions","en"],["text-track","39307:4d7dccc0a5116daa057348ef79c573873cddd9eff066fc6a5f3d37e8264afbeb:subtitles:c855df3d07ba963e9097d5a151b0c14a0a3d494e4ff9b04a389bc0b5f5c16862","wss://relay.divine.video","captions","en"],["imeta","url https://media.divine.video/c855df3d07ba963e9097d5a151b0c14a0a3d494e4ff9b04a389bc0b5f5c16862","m video/mp4","image https://media.divine.video/e1d22b85609cb105dff64b4a402f13982a942dc59aee67218e6e3652c8597d2d","dim 1080x1920","x c855df3d07ba963e9097d5a151b0c14a0a3d494e4ff9b04a389bc0b5f5c16862","size 6579464","blurhash TQHxc{D%X7_Nw{nio#RiRjaJX8oz"],["title","Xmas Already??"],["summary","In the words of Jack Skellington from The Nightmare Before Christmas, \"What's this?\" 😭"],["t","retail"],["published_at","1789661611"],["duration","6"],["alt","Xmas Already??"],["allow_audio_reuse","true"],["e","218af5fc90d66a16ce273f00a4e412a71443441c04c67d1e34ff99c654871d3d","wss://relay.divine.video","audio"],["c2pa_manifest_id","urn:c2pa:b1d12836-a60f-4398-a081-c8f047483ab4"],["verification","verified_mobile"],["client","Divine","31990:d95aa8fc0eff8e488952495b8064991d27fb96ed8652f12cdedc5a4e8b5ae540:divine-mobile","wss://relay.divine.video"]],"content":"In the words of Jack Skellington from The Nightmare Before Christmas, \"What's this?\" 😭","sig":"c32a6486480465d3c4c695d8eee52868eb9c5e085fe54827478f7e183fea0602e969fa82476053136ae766536ec9ed7f0d4c9d009c5fb6991a2099696c66fc33"}"""

    private val videoWithCredits =
        """{"id":"085e909ec9acecdc0833b56162d358f057539bd56a12dabb3c4a76eba13ab176","pubkey":"62e04946ab28d27866f9c29a2c1b88e5ddca31a87110208249fd88693bc406f1","created_at":1789666628,"kind":34236,"tags":[["d","1de7ec53cfc7db7f4e0490aa76ac26408bc9052d287cd5565b96881085929972"],["imeta","url https://media.divine.video/1de7ec53cfc7db7f4e0490aa76ac26408bc9052d287cd5565b96881085929972","m video/mp4"],["title","ShowHole"],["a","34236:0db19e8f1b13e03cd07379edd37a71f07b08a4fb5e7eac222ba1d2c823807075:f11e7f9e2f0e11e80056893152f1850897d4f9164515e567ce13e443b953e305","wss://relay.divine.video","mention"],["p","0db19e8f1b13e03cd07379edd37a71f07b08a4fb5e7eac222ba1d2c823807075","wss://relay.divine.video","inspired-by"],["e","6a8edc14380fdd347ae9c8861b008e4771b885bfb55f1899979bd7a9515b3221","wss://relay.divine.video","audio"],["client","Divine","31990:d95aa8fc0eff8e488952495b8064991d27fb96ed8652f12cdedc5a4e8b5ae540:divine-mobile","wss://relay.divine.video"]],"content":"","sig":""}"""

    private val subtitleEvent =
        """{"id":"5a1c96f3f24b18ee1a9dbb5be04b60e4d7bb8f5e2dd1f2a9e9cf9f5f14b0e1f1","pubkey":"4d7dccc0a5116daa057348ef79c573873cddd9eff066fc6a5f3d37e8264afbeb","created_at":1789661600,"kind":39307,"tags":[["d","subtitles:c855df3d07ba963e9097d5a151b0c14a0a3d494e4ff9b04a389bc0b5f5c16862"],["a","34236:4d7dccc0a5116daa057348ef79c573873cddd9eff066fc6a5f3d37e8264afbeb:c855df3d07ba963e9097d5a151b0c14a0a3d494e4ff9b04a389bc0b5f5c16862"],["url","https://media.divine.video/283a420202b620a9a326598b85bfcf0e8cb4ab4d52b947c628d8c29a1313006b"],["m","text/vtt"],["l","en"],["client","Divine","31990:d95aa8fc0eff8e488952495b8064991d27fb96ed8652f12cdedc5a4e8b5ae540:divine-mobile","wss://relay.divine.video"]],"content":"WEBVTT\n\n1\n00:00:00.000 --> 00:00:02.500\nWe just started putting out Christmas stuff\n\n","sig":""}"""

    private val repost =
        """{"id":"20a0663382290c769b1fbd5e6ad84593d1f848fa75689e4ee4d6263aad5c8760","pubkey":"f67d985c0bfbf87eaa33b056f1d38ad991a6aa625b138bddb2a838bdbac29f40","created_at":1789666347,"kind":16,"tags":[["k","34236"],["a","34236:5ab67f7d7fed4f781008c0ec0d26c8113f9fb46094a8346246c70c75e75db9fb:8de0dcf06982b86aca7189ae50a8c3fe8605917433044ad62c146b723b877025"],["p","5ab67f7d7fed4f781008c0ec0d26c8113f9fb46094a8346246c70c75e75db9fb"],["e","ab074d5a577635b9d34281b31ea32170d54b09cfb4cbfcc4ae6a28f52653fca1"],["client","Divine","31990:d95aa8fc0eff8e488952495b8064991d27fb96ed8652f12cdedc5a4e8b5ae540:divine-mobile","wss://relay.divine.video"]],"content":"","sig":"4513526b886a1d9e1fb12a181a48811dc8765afd901458959a674d3e9d234b761a5cd2551c75cbe4210543f30c5a74b16720281acf88e11e09a67b0b219e3d3d"}"""

    private val reaction =
        """{"id":"88847c819aa18d9294de9ebc1156d6200becdd2fadad6bc6a0248b24f22d3c42","pubkey":"34257350449d357c37e93eb8aef387ff1fee8879d794da664462346a4b540aa8","created_at":1789666900,"kind":7,"tags":[["e","a75d3c1a2fb824544ae51d3d20b1a8280aea9647d13f87c06418233883e80890"],["a","34236:03c49dd3d68fdd15fc0bc7dff669d652af313408bfd6dde10daf27b02f54eb50:a983212b6a82e0d6f6a41efc085ddd0176e1cd3ebd8dcb4cac16219db0a283ef"],["p","03c49dd3d68fdd15fc0bc7dff669d652af313408bfd6dde10daf27b02f54eb50"],["k","34236"],["client","Divine","31990:d95aa8fc0eff8e488952495b8064991d27fb96ed8652f12cdedc5a4e8b5ae540:divine-mobile","wss://relay.divine.video"]],"content":"+","sig":"99d515ef57cb8425e26b85051202e305fb6f1f4265ea38bb041da24044c8ac3e95339bdcae01400307afa26417fb056b6aad1a34e826c4e267df7b4dc6c29e94"}"""

    private val comment =
        """{"id":"dac3c50f5a3c22f185dc8587a2eae3357087aa0ccfd1f1230f8308d7a047515a","pubkey":"f67d985c0bfbf87eaa33b056f1d38ad991a6aa625b138bddb2a838bdbac29f40","created_at":1789666353,"kind":1111,"tags":[["E","ab074d5a577635b9d34281b31ea32170d54b09cfb4cbfcc4ae6a28f52653fca1","","5ab67f7d7fed4f781008c0ec0d26c8113f9fb46094a8346246c70c75e75db9fb"],["A","34236:5ab67f7d7fed4f781008c0ec0d26c8113f9fb46094a8346246c70c75e75db9fb:8de0dcf06982b86aca7189ae50a8c3fe8605917433044ad62c146b723b877025",""],["K","34236"],["P","5ab67f7d7fed4f781008c0ec0d26c8113f9fb46094a8346246c70c75e75db9fb"],["e","ab074d5a577635b9d34281b31ea32170d54b09cfb4cbfcc4ae6a28f52653fca1","","5ab67f7d7fed4f781008c0ec0d26c8113f9fb46094a8346246c70c75e75db9fb"],["a","34236:5ab67f7d7fed4f781008c0ec0d26c8113f9fb46094a8346246c70c75e75db9fb:8de0dcf06982b86aca7189ae50a8c3fe8605917433044ad62c146b723b877025",""],["k","34236"],["p","5ab67f7d7fed4f781008c0ec0d26c8113f9fb46094a8346246c70c75e75db9fb"],["client","Divine","31990:d95aa8fc0eff8e488952495b8064991d27fb96ed8652f12cdedc5a4e8b5ae540:divine-mobile","wss://relay.divine.video"]],"content":"⚫️","sig":"8fa4fcb27882b0622d48e21f1323e4b16c2df24f6a127c49bc30a6e277ef6069f5cfb7b13ddf9788ac18c4cde031091b1d39e349cedde812a6fc8fd517dc3d1f"}"""

    private val videoList =
        """{"id":"70118cbfdd6ed7a0b774d083eb7e788a04f39660ad1aecb01cc645902d06cb9f","pubkey":"34257350449d357c37e93eb8aef387ff1fee8879d794da664462346a4b540aa8","created_at":1789666571,"kind":30005,"tags":[["d","my_vine_list"],["title","My List"],["description","My favorite vines and videos"],["playorder","chronological"],["client","Divine","31990:d95aa8fc0eff8e488952495b8064991d27fb96ed8652f12cdedc5a4e8b5ae540:divine-mobile","wss://relay.divine.video"]],"content":"Ai9Jcq6da7mTNMTcnDh+I4ZUwQcX5///rkV6ouuE7ZqaBnRIgg+rCqul8ykw4KKj6O7wwTm3jzxmTD7ZeEjD7LplcaH8kYDPnzAJWriiLC6tJ1THmXwx0SYYNYrtLs+t1cBR","sig":"7db79697f4adf9ff8b6aba918e5760508fe96fd8227b600ba41edc84d4efbf5a37af15ed7980da5e6106b9a7241583e3e4dce473728f215a0d70b0b28dbf52b2"}"""

    private val collabResponse =
        """{"id":"70cd826d52989fbfd6a12f7e62e4f9aa3c01c902c1bdeec61955d0271920a248","pubkey":"ddfdea0a598ec89f1383ca83b71993540c99ea5a3734b4d5aab23719ea6bde80","created_at":1789653321,"kind":34238,"tags":[["d","34236:32d84a21bb7702c538d5ddd3f9086e86e8b73e5d5cb2e67861eaca36708597e6:d67650d7e97d50a35f491ef24f463b4b408026b5b08c0bc9dbc8359cf4b8aa05"],["a","34236:32d84a21bb7702c538d5ddd3f9086e86e8b73e5d5cb2e67861eaca36708597e6:d67650d7e97d50a35f491ef24f463b4b408026b5b08c0bc9dbc8359cf4b8aa05","wss://relay.divine.video","root"],["p","32d84a21bb7702c538d5ddd3f9086e86e8b73e5d5cb2e67861eaca36708597e6"],["role","Collaborator"],["status","accepted"],["client","Divine","31990:d95aa8fc0eff8e488952495b8064991d27fb96ed8652f12cdedc5a4e8b5ae540:divine-mobile","wss://relay.divine.video"]],"content":"","sig":"65e476c6023de6e443810e630450e6f2c3bca0201a970adcb89f18788dc9bf2ab8e5431a68ff716d411aaa05caa83288549efed8814750c2a23cec62c69bab18"}"""

    @Test
    fun aShortVideoIsAnAddressableVerticalVideo() {
        val event = Event.fromJson(video)

        assertTrue(event is AddressableShortVideoEvent)
        // The address must come from the `d` tag: an `a` tag elsewhere on the network points at
        // `34236:<pubkey>:<d>`, and a class on the wrong base would split the cache in two.
        assertEquals(
            "34236:4d7dccc0a5116daa057348ef79c573873cddd9eff066fc6a5f3d37e8264afbeb:c855df3d07ba963e9097d5a151b0c14a0a3d494e4ff9b04a389bc0b5f5c16862",
            (event as AddressableEvent).address().toValue(),
        )
        assertEquals("Xmas Already??", (event as AddressableShortVideoEvent).title())
        assertEquals(6, event.duration())
    }

    @Test
    fun theBlossomUrlIsPlayedAsAVideoDespiteHavingNoFileExtension() {
        val track = (Event.fromJson(video) as AddressableShortVideoEvent).selectVideoTrack()

        assertNotNull(track)
        assertEquals("https://media.divine.video/c855df3d07ba963e9097d5a151b0c14a0a3d494e4ff9b04a389bc0b5f5c16862", track!!.url)
        assertEquals("video/mp4", track.mimeType)
        assertEquals("1080x1920", track.dimension.toString())
        assertEquals(
            "https://media.divine.video/e1d22b85609cb105dff64b4a402f13982a942dc59aee67218e6e3652c8597d2d",
            track.image.firstOrNull(),
        )

        // VideoDisplay diverts to the image viewer only when classifyMedia says IMAGE. The URL has
        // no extension at all, so the declared MIME is the only thing keeping this in the player.
        assertEquals(MediaContentKind.VIDEO, RichTextParser.classifyMedia(track.url, track.mimeType))

        // ...and the same MIME is what admits it into the Shorts/Video feeds, whose
        // SupportedContent matcher would otherwise fall through to the extension list.
        assertTrue(track.mimeType in SUPPORTED_VIDEO_FEED_MIME_TYPES_SET)
    }

    @Test
    fun theAudioSourceETagIsNotAReplyPointer() {
        // Divine marks the reused soundtrack as `["e", <id>, <relay>, "audio"]` on the video
        // itself. LocalCache.computeReplyTo has no branch for video events, so this never turns
        // the post into a reply — which would drop it out of the home feed and thread it under
        // whatever video the audio came from.
        val event = Event.fromJson(video) as AddressableShortVideoEvent
        val audio = event.tags.first { it[0] == "e" }

        assertEquals("218af5fc90d66a16ce273f00a4e412a71443441c04c67d1e34ff99c654871d3d", audio[1])
        assertEquals("audio", audio[3])
    }

    @Test
    fun captionsComeFromTheTextTrackTagsAndNotTheAudioPointer() {
        // textTrack() used to run ETag::parse, so it returned the `e` tags instead — on this
        // event, the reused-soundtrack pointer, which is not a caption track at all.
        val tracks = (Event.fromJson(video) as AddressableShortVideoEvent).textTrack()

        assertEquals(2, tracks.size)
        // Divine publishes the same track twice: the WebVTT file on Blossom, and the addressable
        // kind-39307 subtitle event that wraps it.
        assertEquals("https://media.divine.video/283a420202b620a9a326598b85bfcf0e8cb4ab4d52b947c628d8c29a1313006b", tracks[0].ref)
        assertEquals("39307:4d7dccc0a5116daa057348ef79c573873cddd9eff066fc6a5f3d37e8264afbeb:subtitles:c855df3d07ba963e9097d5a151b0c14a0a3d494e4ff9b04a389bc0b5f5c16862", tracks[1].ref)
        tracks.forEach {
            assertEquals("wss://relay.divine.video", it.relay)
            assertEquals("captions", it.type)
            assertEquals("en", it.language)
        }
        // No renderer consumes these yet — Amethyst plays divine.video shorts without captions.
    }

    @Test
    fun theTwoFormsOfTheSameCaptionTrackCollapseIntoOne() {
        val video = Event.fromJson(video) as AddressableShortVideoEvent
        val refs = video.captionTracks()

        // One `text-track` is directly loadable, the other has to be fetched first.
        assertEquals(
            listOf("https://media.divine.video/283a420202b620a9a326598b85bfcf0e8cb4ab4d52b947c628d8c29a1313006b"),
            refs.direct.map { it.url },
        )
        assertEquals(1, refs.pending.size)
        assertEquals(
            TextTrackEvent.KIND,
            refs.pending
                .first()
                .address.kind,
        )
        assertEquals(
            "en",
            refs.pending
                .first()
                .tag.language,
        )

        // The pending one resolves to the very same file, so the player gets one configuration,
        // not two identical entries in its track menu.
        val resolved = (Event.fromJson(subtitleEvent) as TextTrackEvent).toCaptionTrack(refs.pending.first().tag)
        assertEquals("text/vtt", resolved?.mimeType)
        assertEquals("en", resolved?.language)
        assertEquals(1, mergeCaptionTracks(refs.direct, listOfNotNull(resolved)).size)
    }

    @Test
    fun aRepostResolvesBothTheCoordinateAndTheVersionId() {
        val event = Event.fromJson(repost)

        assertTrue(event is GenericRepostEvent)
        val boost = event as GenericRepostEvent
        assertEquals(34236, boost.boostedKind())
        assertEquals(
            "34236:5ab67f7d7fed4f781008c0ec0d26c8113f9fb46094a8346246c70c75e75db9fb:8de0dcf06982b86aca7189ae50a8c3fe8605917433044ad62c146b723b877025",
            boost.boostedAddress()?.toValue(),
        )
        assertEquals("ab074d5a577635b9d34281b31ea32170d54b09cfb4cbfcc4ae6a28f52653fca1", boost.boostedEventId())
    }

    @Test
    fun aReactionResolvesBothTheCoordinateAndTheVersionId() {
        val event = Event.fromJson(reaction)

        assertTrue(event is ReactionEvent)
        val like = event as ReactionEvent
        assertEquals("+", like.content)
        assertEquals(listOf("a75d3c1a2fb824544ae51d3d20b1a8280aea9647d13f87c06418233883e80890"), like.originalPost())
        assertEquals(
            listOf("34236:03c49dd3d68fdd15fc0bc7dff669d652af313408bfd6dde10daf27b02f54eb50:a983212b6a82e0d6f6a41efc085ddd0176e1cd3ebd8dcb4cac16219db0a283ef"),
            like.linkedAddressIds(),
        )
    }

    @Test
    fun aTopLevelCommentRootsAtTheVideoCoordinate() {
        val event = Event.fromJson(comment)

        assertTrue(event is CommentEvent)
        val reply = event as CommentEvent
        val coordinate = "34236:5ab67f7d7fed4f781008c0ec0d26c8113f9fb46094a8346246c70c75e75db9fb:8de0dcf06982b86aca7189ae50a8c3fe8605917433044ad62c146b723b877025"

        assertEquals(listOf(coordinate), reply.rootAddressIds())
        assertEquals(listOf("ab074d5a577635b9d34281b31ea32170d54b09cfb4cbfcc4ae6a28f52653fca1"), reply.rootEventIds())
        // A top-level comment repeats the root as its direct parent (lowercase a/e), so both the
        // version note and the addressable note collect the reply. Note.addReply() dedupes, and
        // consumeBaseReplaceable migrates the version's references onto the address.
        assertEquals(listOf(coordinate), reply.replyAddressIds())
    }

    @Test
    fun creditsReadTheMarkerWhicheverSlotItIsIn() {
        // divine-mobile: ["p", <pubkey>, <relay>, "inspired-by"] and ["e", <id>, <relay>, "audio"].
        val credits = (Event.fromJson(videoWithCredits) as AddressableShortVideoEvent).credits()

        val person = credits.filterIsInstance<VideoCredit>().first { it.target is CreditTarget.Person }
        assertEquals("0db19e8f1b13e03cd07379edd37a71f07b08a4fb5e7eac222ba1d2c823807075", (person.target as CreditTarget.Person).pubKey)
        assertEquals("inspired-by", person.label)

        val video = credits.first { it.target is CreditTarget.Video }
        assertEquals("mention", video.label)
        assertEquals(34236, (video.target as CreditTarget.Video).address.kind)

        val audio = credits.first { it.target is CreditTarget.Event }
        assertEquals("audio", audio.label)
        assertEquals("6a8edc14380fdd347ae9c8861b008e4771b885bfb55f1899979bd7a9515b3221", (audio.target as CreditTarget.Event).eventId)
    }

    @Test
    fun aRoleInTheRelaySlotIsReadAsTheLabelAndNotAsAHint() {
        // divine-web's collaborator invite writes ["p", <pubkey>, "<role>"] — no relay hint at all,
        // so the slot PTag reads for a hint holds the label instead.
        val invite =
            AddressableShortVideoEvent(
                id = "a".repeat(64),
                pubKey = "b".repeat(64),
                createdAt = 1789666628,
                tags = arrayOf(arrayOf("d", "vid"), arrayOf("p", "c".repeat(64), "Collaborator")),
                content = "",
                sig = "",
            )

        val credit = invite.credits().single()
        assertEquals("Collaborator", credit.label)
        assertEquals("c".repeat(64), (credit.target as CreditTarget.Person).pubKey)
    }

    @Test
    fun aBareETagOnAVideoIsNotACredit() {
        // No marker means nothing is being credited — printing "references <id>" for a stray
        // threading tag would invent an attribution the publisher never made.
        val stray =
            AddressableShortVideoEvent(
                id = "a".repeat(64),
                pubKey = "b".repeat(64),
                createdAt = 1789666628,
                tags = arrayOf(arrayOf("d", "vid"), arrayOf("e", "d".repeat(64)), arrayOf("e", "e".repeat(64), "", "root")),
                content = "",
                sig = "",
            )

        assertTrue(stray.credits().isEmpty())
    }

    @Test
    fun aCollaborationResponseNamesTheVideoItAccepts() {
        val event = Event.fromJson(collabResponse)

        assertTrue(event is VideoCollaborationEvent)
        val response = event as VideoCollaborationEvent
        assertEquals(
            "34236:32d84a21bb7702c538d5ddd3f9086e86e8b73e5d5cb2e67861eaca36708597e6:d67650d7e97d50a35f491ef24f463b4b408026b5b08c0bc9dbc8359cf4b8aa05",
            response.video()?.toValue(),
        )
        assertEquals("Collaborator", response.role())
        assertTrue(response.isAccepted())
        // divine-mobile happens to key this one by the video coordinate, but the `a` tag is what
        // the lookup matches on — see [bothCollaborationShapesAreFoundByTheirATag].
        assertEquals(response.video()?.toValue(), response.dTag())
    }

    @Test
    fun bothCollaborationShapesAreFoundByTheirATag() {
        // The `d` tag is the one thing the two publishers disagree on: divine-mobile writes the
        // video coordinate, divine-web writes `crypto.randomUUID()`
        // (divine-web/src/hooks/useApproveCollab.ts). Matching on `a` + author is what makes the
        // credit's check mark appear for both — and is the same filter divine-web itself uses in
        // useVideoCollaboratorStatus.ts.
        val mobile = Event.fromJson(collabResponse) as VideoCollaborationEvent
        val coord = mobile.video()!!.toValue()

        val web =
            VideoCollaborationEvent(
                id = "a".repeat(64),
                pubKey = mobile.pubKey,
                createdAt = mobile.createdAt,
                tags = arrayOf(arrayOf("a", coord), arrayOf("d", "0e1cbb2c-1b5a-4f1f-9f0e-2c6b2c8a9f11")),
                content = "",
                sig = "",
            )

        val byATag =
            Filter(
                kinds = listOf(VideoCollaborationEvent.KIND),
                authors = listOf(mobile.pubKey),
                tags = mapOf("a" to listOf(coord)),
            )

        assertTrue(byATag.match(mobile))
        assertTrue(byATag.match(web))

        // What the coordinate-addressed lookup used to do: it can only ever name one `d`, so the
        // other publisher's answer was invisible however long you waited for it.
        val byCoordinate =
            Filter(
                kinds = listOf(VideoCollaborationEvent.KIND),
                authors = listOf(mobile.pubKey),
                tags = mapOf("d" to listOf(coord)),
            )

        assertTrue(byCoordinate.match(mobile))
        assertFalse(byCoordinate.match(web))
    }

    @Test
    fun aResponseWithoutAStatusStillCounts() {
        // divine-web publishes no `status` — it only emits the event on approval at all.
        val webShape =
            VideoCollaborationEvent(
                id = "a".repeat(64),
                pubKey = "b".repeat(64),
                createdAt = 1789666628,
                tags = arrayOf(arrayOf("a", "34236:${"c".repeat(64)}:vid"), arrayOf("d", "0e1cbb2c-1b5a-4f1f-9f0e-2c6b2c8a9f11")),
                content = "",
                sig = "",
            )

        assertTrue(webShape.isAccepted())
        assertEquals("34236:${"c".repeat(64)}:vid", webShape.video()?.toValue())
    }

    @Test
    fun aVideoListParsesButCarriesItsItemsEncrypted() {
        val event = Event.fromJson(videoList)

        assertTrue(event is VideoCurationSetEvent)
        val list = event as VideoCurationSetEvent
        assertEquals("my_vine_list", list.dTag())
        assertEquals("My List", list.title())
        assertEquals("My favorite vines and videos", list.description())
        // Divine keeps every member in the NIP-51 encrypted `content`, so nothing is public and
        // only the list's owner can decrypt the members; RenderVideoCurationSet says so rather
        // than rendering an empty strip as though the list were broken.
        assertTrue(list.publicItems().isEmpty())
    }
}
