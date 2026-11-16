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

import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RichTextParserTest {
    private val textToParse =
        """
        📰 24 Hour NostrInspector Report 🕵 (TEXT ONLY VERSION)

        Generated Friday June 30 2023 03:59:01 UTC-6 (CST)

        Network statistics

        New events witnessed (top 110 relays)

        Kind, count, (% count), size, (% size)
        1, 207.9K, (28.8%), 458.02MB, (9.2%)
        7, 158.3K, (22%), 280.83MB, (5.7%)
        0, 84.1K, (11.7%), 192.89MB, (3.9%)
        9735, 57.2K, (7.9%), 353.16MB, (7.1%)
        3, 54.7K, (7.6%), 2.75GB, (56.7%)
        6, 31.6K, (4.4%), 111.27MB, (2.2%)
        4, 30.8K, (4.3%), 89.79MB, (1.8%)
        30000, 29.1K, (4%), 115.33MB, (2.3%)
        30078, 12.1K, (1.7%), 317.25MB, (6.4%)
        5, 11K, (1.5%), 16.86MB, (0.3%)
        10002, 8.6K, (1.2%), 16.59MB, (0.3%)
        1311, 7.7K, (1.1%), 12.71MB, (0.3%)
        1984, 6.3K, (0.9%), 10.93MB, (0.2%)
        9734, 3.7K, (0.5%), 10.88MB, (0.2%)
        30001, 3.1K, (0.4%), 66.91MB, (1.3%)
        1000, 2.8K, (0.4%), 13.43MB, (0.3%)
        20100, 1.4K, (0.2%), 2.32MB, (0%)
        42, 1.1K, (0.2%), 2.30MB, (0%)
        13194, 1K, (0.1%), 1.22MB, (0%)
        1063, 875, (0.1%), 1.96MB, (0%)

        New events by relay (top 50%)

        Events (%) Relay
        33.4K (4.6%) relay.shitforce.one
        32.9K (4.6%) relayable.org
        26.6K (3.7%) universe.nostrich.land
        22.8K (3.2%) nos.lol
        22.7K (3.1%) universe.nostrich.land?lang=zh
        22.5K (3.1%) universe.nostrich.land?lang=en
        21.2K (2.9%) relay.damus.io
        20.6K (2.9%) relay.nostr.wirednet.jp
        20.1K (2.8%) offchain.pub
        19.9K (2.8%) nostr.rocks
        19.5K (2.7%) relay.wellorder.net
        19.4K (2.7%) nostr.oxtr.dev
        19K (2.6%) universe.nostrich.land?lang=ja
        18.4K (2.6%) relay.mostr.pub
        17.5K (2.4%) universe.nostrich.land?lang=zh
        16.3K (2.3%) nostr.bitcoiner.social

        30 day global new events

        23-05-29 1M
        23-05-30 861.9K
        23-05-31 752.5K
        23-06-01 0.9M
        23-06-02 808.9K
        23-06-03 683.8K
        23-06-04 0.9M
        23-06-05 890.6K
        23-06-06 839.4K
        23-06-07 827K
        23-06-08 804.8K
        23-06-09 736.7K
        23-06-10 709.7K
        23-06-11 772.2K
        23-06-12 882K
        23-06-13 794.9K
        23-06-14 842.2K
        23-06-15 812.1K
        23-06-16 839.6K
        23-06-17 730.2K
        23-06-18 811.9K
        23-06-19 721.9K
        23-06-20 786.2K
        23-06-21 756.6K
        23-06-22 736K
        23-06-23 723.5K
        23-06-24 703.9K
        23-06-25 734.9K
        23-06-26 742.4K
        23-06-27 707.8K
        23-06-28 747.7K

        Social Network Statistics

        Top 30 hashtags found today

        #hashtag, mentions today, days in top 30

        #bitcoin, 1.7K, 109
        #concussion, 1.1K, 25
        #press, 0.9K, 65
        #france, 492, 46
        #presse, 480, 42
        #covid19, 465, 65
        #nostr, 414, 109
        #zapathon, 386, 76
        #rssfeed, 309, 53
        #btc, 299, 109
        #news, 294, 91
        #zap, 283, 109
        #linux, 253, 88
        #respond, 246, 90
        #kompost, 240, 31
        #plebchain, 236, 109
        #gardenaward, 236, 31
        #start, 236, 31
        #unicef, 233, 32
        #coronavirus, 233, 33
        #bew, 229, 31
        #balkon, 229, 31
        #terrasse, 229, 31
        #braininjuryawareness, 229, 24
        #garten, 220, 21
        #smart, 220, 21
        #nsfw, 211, 85
        #protoncalendar, 206, 31
        #stacksats, 195, 99
        #nokyc, 179, 98

        Emoji sentiment today

        ⚡ (1.6K) 👉 (1.4K) 🇪🇺 (1.2K) 🫂 (1.2K) 🇺🇸 (1.1K) 💜 (875) 🧠 (858) 😂 (830) 🔥 (690) 🤣 (566) 🤙 (525) ☕ (444) 👇 (443) 🙌🏻 (425) ☀ (307) 😎 (305) 🥳 (301) 🤔 (276) 🌻 (270) 🧡 (270) 🥇 (269) 🗓 (269) 🙏 (268) 🏆 (267) 🌱 (264) 📰 (230) 🏉 (221) 😭 (220) 💰 (219) 🔗 (209) 👀 (201) 😅 (199) ✨ (193) 🇷🇺 (182) 💪 (167) ✅ (164) 💤 (163) 🐶 (151) 🇨🇭 (141) 📝 (137) 😁 (136) 🌞 (136) 🐾 (136) ❤ (132) 💻 (126) 🚀 (125) 👍 (125) 🇧🇷 (125) 😊 (121) 📚 (120) ➡ (120) 👏 (118) 🎉 (117) 🎮 (115) 🤷 (113) 👋 (112) 💃 (108) 🕺🏻 (106) 💡 (104) 🚨 (99) 😆 (97) 💯 (95) ⚠ (92) 📢 (92) 🤗 (89) 😴 (87) 🔁 (83) 🐰 (81) 😀 (79) 🎟 (78) ⛏ (78) 🐦 (76) 💸 (76) ✌🏻 (75) 🤍 (73) 🇬🇧 (73) 🌽 (70) 🤡 (69) 🤮 (69) ❗ (66) 🤝 (65) 😉 (65) 🙇 (65) 🍻 (64) 🌍 (64) 💕 (63) 🌸 (62) 💬 (61) ☺ (61) 🇦🇷 (59) 🇮🇩 (57) 😳 (57) 😄 (57) 🎶 (57) 🥷🏻 (56) 🎵 (56) 😃 (56) 🔍 (55) 💥 (55) 🎲 (54) ✍ (54) 🕒 (53) ⬇ (53) 💙 (51) 🔒 (50) 📈 (50) 🪙 (50) 🌧 (50) 🥰 (50) 🕸 (50) 🌐 (50) 💭 (49) 🌙 (49) 😍 (49) 📱 (48) 🌟 (48) 🤩 (48) 💔 (47) 🔌 (47) 😋 (47) 🎖 (47) 🍣 (46) 📷 (46) 💼 (45) ⭐ (45) 🥔 (45) 🥺 (45) 👌 (44) 👷🏼 (43) 😱 (43) 📅 (43) 🤖 (43) 📸 (42) 📊 (42) 🦑 (40) 💵 (40) 🤦 (39) ❣ (38) 💎 (38) 🖤 (38) 📺 (37) 🇵🇱 (37) 🇯🇵 (36) 🔧 (36) 🤘 (36) 💖 (36) ‼ (35) 😢 (35) 😺 (34) 🔊 (34) 😏 (34) 🇸🇰 (34) 🏃 (34) 👩‍👧 (34) ⏰ (33) 👨‍💻 (33) 👑 (33) 👥 (32) 🖥 (32) 💨 (32) 💗 (31) 🇲🇽 (31) 📖 (31) 🚫 (31) 👊🏻 (31) 😡 (31) 🌎 (31) 👁 (30) 🗞 (30) 🍀 (30) 🍽 (29) 🐸 (29) 🥚 (29) 💩 (29) ✊🏾 (29) 😮 (29) 🌡 (29) 🙃 (28) 🔔 (28) 🇻🇪 (28) 💦 (28) 🎯 (28) 🎨 (28) 🍛 (28) 🖼 (27) ☝🏻 (27) 🛑 (27) 🙄 (27) 🧑🏻‍🤝‍🧑🏽 (27) 🌈 (27) 🥂 (26) 🇫🇮 (26) 🎥 (26) 😬 (26) 🥲 (25) 🦾 (24) 🤜 (24) 🙂 (24) 🖕 (24) 😩 (24)

        Zap economy

        ⚡41.7M sats (₿0.417)
        1,816 zappers & 920 zapped (unique pubkeys)
        🌩️ 33,248 zaps, 1,253 sats per zap (avg)

        Most followed

        #1 30% jb55, jb55@jb55.com - 32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245
        #2 19% Snowden, Snowden@Nostr-Check.com - 84dee6e676e5bb67b4ad4e042cf70cbd8681155db535942fcc6a0533858a7240
        #3 18% cameri, cameri@elder.nostr.land - 00000000827ffaa94bfea288c3dfce4422c794fbb96625b6b31e9049f729d700
        #4 11% Natalie, natalie@NostrVerified.com - edcd20558f17d99327d841e4582f9b006331ac4010806efa020ef0d40078e6da
        #5 11% saifedean,  - 4379e76bfa76a80b8db9ea759211d90bb3e67b2202f8880cc4f5ffe2065061ad
        #6 11% alanbwt, alanbwt@nostrplebs.com - 1bd32a386a7be6f688b3dc7c480efc21cd946b43eac14ba4ba7834ac77a23e69
        #7 10% rick, rick@no.str.cr - 978c8f26ea9b3c58bfd4c8ddfde83741a6c2496fab72774109fe46819ca49708
        #8 9% shawn, shawn@shawnyeager.com - c7eda660a6bc8270530e82b4a7712acdea2e31dc0a56f8dc955ac009efd97c86
        #9 9% 0xtr, 0xtr@oxtr.dev - b2d670de53b27691c0c3400225b65c35a26d06093bcc41f48ffc71e0907f9d4a
        #10 9% stick, pavol@rusnak.io - d7f0e3917c466f1e2233e9624fbd6d4bd1392dbcfcaf3574f457569d496cb731
        #11 9% caitlinlong, caitlin@nostrverified.com - e1055729d51e037b3c14e8c56e2c79c22183385d94aadb32e5dc88092cd0fef4
        #12 9% ralf, ralf@snort.social - c89cf36deea286da912d4145f7140c73495d77e2cfedfb652158daa7c771f2f8
        #13 9% StackSats, stacksats@nostrplebs.com - b93049a6e2547a36a7692d90e4baa809012526175546a17337454def9ab69d30
        #14 9% MrHodl, MrHodl@nostrpurple.com - 29fbc05acee671fb579182ca33b0e41b455bb1f9564b90a3d8f2f39dee3f2779
        #15 9% mikedilger, _@mikedilger.com - ee11a5dff40c19a555f41fe42b48f00e618c91225622ae37b6c2bb67b76c4e49
        #16 9% jascha, jascha@relayable.org - 2479739594ed5802a96703e5a870b515d986982474a71feae180e8ecffa302c6
        #17 8% Nakadaimon, Nakadaimon@nostrplebs.com - 803a613997a26e8714116f99aa1f98e8589cb6116e1aaa1fc9c389984fcd9bb8
        #18 8% KeithMukai, KeithMukai@nostr.seedsigner.com - 5b0e8da6fdfba663038690b37d216d8345a623cc33e111afd0f738ed7792bc54
        #19 8% TheGuySwann, theguyswann@NostrVerified.com - b0b8fbd9578ac23e782d97a32b7b3a72cda0760761359bd65661d42752b4090a
        #20 8% dk, dk@stacker.news - b708f7392f588406212c3882e7b3bc0d9b08d62f95fa170d099127ece2770e5e
        #21 7% zerohedge, npub1z7eqn5603ltuxr77w70t3sasep8hyngzr6lxqpa9hfcqjwe9wmdqhw0qhv@nost.vip - 17b209d34f8fd7c30fde779eb8c3b0c84f724d021ebe6007a5ba70093b2576da
        #22 7% miljan, miljan@primal.net - d61f3bc5b3eb4400efdae6169a5c17cabf3246b514361de939ce4a1a0da6ef4a
        #23 7% jared, jared@nostrplebs.com - 92e3aac668edb25319edd1d87cadef0b189557fdd13b123d82a19d67fd211909
        #24 7% radii, radii@orangepill.dev - acedd3597025cb13b84f9a89643645aeb61a3b4a3af8d7ac01f8553171bf17c5
        #25 7% katie, _@katieannbaker.com - 07eced8b63b883cedbd8520bdb3303bf9c2b37c2c7921ca5c59f64e0f79ad2a6
        #26 7% giacomozucco, giacomozucco@BitcoinNostr.com - ef151c7a380f40a75d7d1493ac347b6777a9d9b5fa0aa3cddb47fc78fab69a8b
        #27 7% kr, kr@stacker.news - 08b80da85ba68ac031885ea555ab42bb42231fde9b690bbd0f48c128dfbf8009
        #28 7% phil, phil@nostrpurple.com - e07773a92a610a28da20748fdd98bfb5af694b0cad085224801265594a98108a
        #29 7% angela, angela@nostr.world - 2b1964b885de3fcbb33777874d06b05c254fecd561511622ce86e3d1851949fa
        #30 7% mason 𓄀 𓅦, mason@lacosanostr.com - 5ef92421b5df0ed97df6c1a98fc038ea7962a29e7f33a060f7a8ddeb9ee587e9
        #31 7% Lau, lau@nostr.report - 5a9c48c8f4782351135dd89c5d8930feb59cb70652ffd37d9167bf922f2d1069
        #32 7% Rex Damascus , damascusrex@iris.to - 50c5c98ccc31ca9f1ef56a547afc4cb48195fe5603d4f7874a221db965867c8e
        #33 6% nym, nym@nostr.fan - 9936a53def39d712f886ac7e2ed509ce223b534834dd29d95caba9f6bc01ef35
        #34 6% nico, nico@nostrplebs.com - 0000000033f569c7069cdec575ca000591a31831ebb68de20ed9fb783e3fc287
        #35 6% anna, seekerdreamer1@stacker.news - 6f2347c6fc4cbcc26d66e74247abadd4151592277b3048331f52aa3a5c244af9
        #36 6% TheSameCat, thesamecat@iris.to - 72f9755501e1a4464f7277d86120f67e7f7ec3a84ef6813cc7606bf5e0870ff3
        #37 6% nitesh_btc, nitesh@noderunner.wtf - 021d7ef7aafc034a8fefba4de07622d78fd369df1e5f9dd7d41dc2cffa74ae02
        #38 6% gpt3, gpt3@jb55.com - 5c10ed0678805156d39ef1ef6d46110fe1e7e590ae04986ccf48ba1299cb53e2
        #39 6% Byzantine, byzantine@stacker.news - 5d1d83de3ee5edde157071d5091a6d03ead8cce1d46bc585a9642abdd0db5aa0
        #40 6% wealththeory, wealththeory@nostrplebs.com - 3004d45a0ab6352c61a62586a57c50f11591416c29db1143367a4f0623b491ca
        #41 6% IshBit, gug@nostrplebs.com - 8e27ffb5c9bb8cdd0131ade6efa49d56d401b5424d9fdf9a63e074d527b0715c
        #42 5% Lana, lana@b.tc - e8795f9f4821f63116572ed4998924c6f0e01682945bf7a3d9d6132f1c7dace7
        #43 5% Shevacai, shevacai@nostrplebs.com - 2f175fe4348f4da2da157e84d119b5165c84559158e64729ff00b16394718bbf
        #44 5% joe, joe@nostrpurple.com - 907a5a23635ea02be052c31f465b1982aefb756710ccc9f628aa31b70d2e262e
        #45 5% SimplestBitcoinBook, simplestbitcoinbook@nostrplebs.com - 6867d899ce6b677b89052602cfe04a165f26bb6a1a6390355f497f9ee5cb0796
        #46 5% knutsvanholm, knutsvanholm@iris.to - 92cbe5861cfc5213dd89f0a6f6084486f85e6f03cfeb70a13f455938116433b8
        #47 5% rajwinder, rs@zbd.ai - 1c9d368fc24e8549ce2d95eba63cb34b82b363f3036d90c12e5f13afe2981fba
        #48 5% Vlad,  - 50054d07e2cdf32b1035777bd9cf73992a4ae22f91c14a762efdaa5bf61f4755
        #49 5% GRANTGILLIAM, GRANTGILLIAM@grantgilliam.com - 874db6d2db7b39035fe7aac19e83a48257915e37d4f2a55cb4ca66be2d77aa88
        #50 5% LifeLoveLiberty, lifeloveliberty@iris.to - c07a2ea48b6753d11ad29d622925cb48bab48a8f38e954e85aec46953a0752a2
        #51 5% hackernews, npub1s9c53smfq925qx6fgkqgw8as2e99l2hmj32gz0hjjhe8q67fxdvs3ga9je@nost.vip - 817148c3690155401b494580871fb0564a5faafb9454813ef295f2706bc93359
        #52 5% arbedout, arbedout@granddecentral.com - a67e98faf32f2520ae574d84262534e7b94625ce0d4e14a50c97e362c06b770e
        #53 5% nobody,  - 5f735049528d831f544b49a585e6f058c1655dfaed9fc338374cd4f3a5a06bf7
        #54 5% glowleaf, glowleaf@nostrplebs.com - 34c0a53283bacd5cb6c45f9b057bea05dfb276333dcf14e9b167680b5d3638e4
        #55 5% Modus, modus@lacosanostr.com - 547fcc5c7e655fe7c83da5a812e6332f0a4779c87bf540d8e75a4edbbf36fe4a
        #56 5% Melvin Carvalho Old Key DO NOT USE, USE npub1melv683fw6n2mvhl5h6dhqd8mqfv3wmxnz4qph83ua4dk4006ezsrt5c24,  - ed1d0e1f743a7d19aa2dfb0162df73bacdbc699f67cc55bb91a98c35f7deac69
        #57 5% anil, anil@bitcoinnostr.com - ade7a0c6acca095c5b36f88f20163bccda4d97b071c4acc8fe329dc724eec8fb
        #58 4% DocumentingBTC, documentingbtc@uselessshit.co - 641ac8fea1478c27839fb7a0850676c2873c22aa70c6216996862c98861b7e2f
        #59 4% wolfbearclaw, wolfbearclaw@nostr.messagepush.io - 0b963191ab21680a63307aedb50fd7b01392c9c6bef79cd0ceb6748afc5e7ffd
        #60 4% Amboss, _@amboss.space - 2af01e0d6bd1b9fbb9e3d43157d64590fb27dcfbcabe28784a5832e17befb87b
        #61 4% k3tan, k3tan@k3tan.com - 599c4f2380b0c1a9a18b7257e107cf9e6d8b4f8dea06c18c84538d311ff2b28c
        #62 4% wolzie , wolzie@BitcoinNostr.com - aabedc1f237853aeeb22bd985556036f262f8507842d64f3ecce01adbd7207e2
        #63 4% trey, trey@nostrplebs.com - d5415a313d38461ff93a8c170f941b2cd4a66a5cfdbb093406960f6cb317849f
        #64 4% sillystev,  - d541ef2e4830f2e1543c8bdc40128ceceb062b08c7e3f53d141552d5f5bc0cfc
        #65 4% sovereignmox, woody@fountain.fm - 1c4123b2431c60be030d641b4b68300eb464415405035b199428c0913b879c0c
        #66 4% CosmicDimension, cosmicdimension@nostrplebs.com - 4afec6c875e81dc28a760cc828345c0c5b61ec464ba20224148f9fd854a868ff
        #67 4% Mir, mirbtc@getalby.com - 234c45ff85a31c19bf7108a747fa7be9cd4af95c7d621e07080ca2d663bb47d2
        #68 4% Tacozilla,  - 5f70f80ddcf4f6a022467bd5196a1fdfc53d59f1e735a90443e7f7c980564c88
        #69 4% marks, marks@nostrplebs.com - 8ea485266b2285463b13bf835907161c22bb3da1e652b443db14f9cee6720a43
        #70 4% blacktomcat, barrensatin40@walletofsatoshi.com - 16b7e4b067cba8c86bda96a8d932e7593f398118d24bd8060da39ccfd7315f5c
        #71 4% Alex Emidio, alexemidio@alexemidio.github.io - 4ba8e86d2d97896dc9337c3e500691893d7317572fd81f8b41ddda5d89d32de4
        #72 4% Jenn, Jenn@mintgreen.co - e0f59d89047b868a188c5efd6b93dd8c16b65643b8718884dad8542386c60ddd
        #73 4% spacemonkey, spacemonkey@nostrich.love - 23b26fea28700cd1e2e3a8acca5c445c37ab89acaad549a36d50e9c0eb0f5806
        #74 4% ishak, ishak@nostrplebs.com - 052466631c6c0aed84171f83ef3c95cb81848d4dcdc1d1ee9dfdf75b850c1cb4
        #75 4% nakamoto_army,  - 62f6c5ff12fd24251f0bfb3b7eb1e512d7f1f577a1a97a595db01c66b52ad04f
        #76 4% GrassFedBitcoin, GrassFedBitcoin@start9.com - 74ffc51cc30150cf79b6cb316d3a15cf332ab29a38fec9eb484ab1551d6d1856
        #77 4% NinoHodls, ninoholds@nostrplebs.com - 43ccdbcb1e4dff7e3dea2a91b851ca0e22f50e3c560364a12b64b8c6587924f0
        #78 4% satcap, satcap@nostr.satcap.io - 11dfaa43ae0faa0a06d8c67f89759214c58b60a021521627bc76cb2d3ad0b2e8
        #79 4% DuneMessias,  - 96a578f6b504646de141ba90bec5651965aa01df0605928b3785a1372504e93d
        #80 4% Idaeus,  - eb473e8fd55ced7af32abaf89578647ddba75e38a860b1c41682bbfb774f5579
        #81 4% tpmoreira, tpmoreira@nostrplebs.com - f514ef7d18da12ecfce55c964add719ce00a1392c187f20ccb57d99290720e03
        #82 4% force2B, force2b@nostrplebs.com - d411848a42a11ad2747c439b00fc881120a4121e04917d38bebd156212e2f4ad
        #83 4% Hendrix, hendrix@nostrplebs.com - cbd92008e1fe949072cbea02e54228140c43d14d14519108b1d7a32d9102665b
        #84 4% TXMC, TXMC@alphabetasoup.tv - 37359e92ece5c6fc8d5755de008ceb6270808b814ddd517d38ebeab269836c96
        #85 4% norman188,  - 662a4476a9c15a5778f379ce41ceb2841ac72dfa1829b492d67796a8443ac2ca
        #86 4% pipleb, pipleb@iris.to - 3c4280ef3b792fa919b1964460d34ca6af93b83fa55f633a3b0eb8fde556235a
        #87 4% reallhex, reallhex@terranostr.com - 29630aed66aeec73b6519a11547f40ca15c3f6aa79907e640f1efcf5a2ee9dc8
        #88 4% 374324…ef9f78,  - 3743244390be53473a7e3b3b8d04dce83f6c9514b81a997fb3b123c072ef9f78
        #89 4% Nostradamus,  - 7acce9b3da22ceedc511a15cb730c898235ab551623955314b003e9f33e8b10c
        #90 4% Nic₿, nicb@nicb.me - 000000002d4f4733f1ee417a405637fd0d81dbfbc6dbd8c0d1c95f04ec3db973
        #91 4% NabismoPrime, NabismoPrime@BostonBTC.com - 4503baa127bdfd0b054384dc5ba82cb0e2a8367cbdb0629179f00db1a34caacc
        #92 4% paco, paco@iris.to - 66bd8fed3590f2299ef0128f58d67879289e6a99a660e83ead94feab7606fd17
        #93 3% globalstatesmen, globalstatesmen@nostrplebs.com - 237506ca399e5b1b9ce89455fe960bc98dfab6a71936772a89c5145720b681f4
        #94 3% Nostryfied, _@NostrNet.work - c2c20ec0a555959713ca4c404c4d2cc80e6cb906f5b64217070612a0cae29c62
        #95 3% crayonsmell, crayonsmell@habel.net - 3ef3be9db1e3f268f84e937ad73c68772a58c6ffcec1d42feeef5f214ad1eaf9
        #96 3% Toxikat27, ToxiKat27@Bitcoiner.social - 12cfc2ec5a39a39d02f921f77e701dbc175b6287f22ddf0247af39706967f1d9
        #97 3% James Trageser, jtrag@BitcoinNostr.com - d29bc58353389481e302569835661c95838bee076137533eb365bca752c38316
        #98 3% Joe Martin Music, joemartinmusic@nostrplebs.com - 28ca019b78b494c25a9da2d645975a8501c7e99b11302e5cbe748ee593fcb2cc
        #99 3% Fundamentals, ph@nostrplebs.com - 5677fa5b6b1cb6d5bee785d088a904cd08082552bf75df3e4302cea015a5d3e1
        #100 3% bb,  - 1f254ae909a36b0000c3b68f36b92aad168f4532725d7cd9b67f5b09088f2125
        #101 3% 李子柒,  - c70c8e55e0228c3ce171ae0d357452e386489f3a2d14e6deca174c2fbfc8da52
        #102 3% Horse 🐴, horse@iris.to - e4d3420c0b77926cfbf107f9cb606238efaf5524af39ff1c86e6d6fdd1515a57
        #103 3% KP, kp@no.str.cr - b2e777c827e20215e905ab90b6d81d5b84be5bf66c944ce34943540b462ea362
        #104 3% Azarakhsh, rebornbitcoiner@getalby.com - c734992a115c2ad9b4df40dd7c14d153695b29081a995df39b4fc8e6f1dcfb14
        #105 3% Toshi, toshi@nostr-check.com - 79d434176b64745d2793cf307f20967e27912994f6e81632de18da3106c2cbb4
        #106 3% FreeBorn, freeborn@nostrplebs.com - 408e04e9a5b02ef6d82edb9ecb2cca1d5a3121cb26b0ca5e6511800a0269b069
        #107 3% blee, blee@bitcoiner.social - 69a0a0910b49a1dbfbc4e4f10df22b5806af5403a228267638f2e908c968228d
        #108 3% SatsTonight, SatsTonight@BitcoinNostr.com - eb3b94533dafeb8ebd58a4947a3dce11d83a9931c622bdf30a4257d3347ee1bf
        #109 3% Nostr-Check.com, freeverification@Nostr-Check.com - ddfbb06a722e51933cd37e4ecdb30b1864f262f9bb5bd6c2d95cbeefc728f096
        #110 3% cowmaster, cowmaster@getalby.com - 6af9411d742c74611e149d19037e7a2ba4d44bbceb429b209c451902b6740bb8
        #111 3% Hacker, hacker818@iris.to - 40e10350fed534e5226b73761925030134d9f85306ee1db5cfbd663118034e84
        #112 3% BitcasaHomes, amandabitcasa@nostrplebs.com - f96a2a2552c08f99c30b9e2441d64ca4c6b3d761735e7cd74580bafe549326e0
        #113 3% footstr,  - aa1aa6af6be3a2903e2fb18690d7df128a10eec0f3a015157daf371c688b4cff
        #114 3% tiago, tiago@nostrplebs.com - 780ab38a843423c61502550474b016e006f2b56f2f7d18e9cd02737e11113262
        #115 3% Sepehr, sepehr@nostribe.com - 3e294d2fd339bb16a5403a86e3664947dd408c4d87a0066524f8a573ae53ca8e
        #116 3% dhruv,  - 297bc16357b314be291c893755b25d66999c1525bbf3537fbc637a0c767f14bb
        #117 3% b310ed…4f793a,  - b310ed0a54a71ccf8a8368032dd3b4b83b7aca2840bb10a4d5e6ef4b6a4f793a
        #118 3% MichZ 🧘🏻‍♀️,  - 9349d012686caab46f6bfefd2f4c361c52e14b1cde1cd027476e0ae6d3e98946
        #119 3% gfy, gfy@stacker.news - 01e4fc2adc0ff7a0465d3e70b3267d375ebe4292828fa3888f972313f3a1248e
        #120 3% Dude,  - 67cbb3d83800cc1af6f5d2821f1c911f033ea21e1269ff2ad613ab3ae099b1f3
        #121 3% HODL_MFER,  - 7c6a9e6231570a6773e608d1c0a709acb9c21193a5c2df9cebfa9e9db09411a3
        #122 3% renatarodrigues,  - aa116590cf23dc761a8a9e38ff224a3d07db45c66be3035b9f87144bda0eeaa5
        #123 3% CryptoJournaal, cryptojournaal@iris.to - fb649213b88e9927a5c8f470d7affe88441de995deaccf283bf60a78f771b825
        #124 3% Bon, bon@nostrplebs.com - b2722dd1e13ff9b82ff2f432186019045fee39911d5652d6b4263562061af908
        #125 3% binarywatch, bot@binarywatch.org - 0095c837e8ed370de6505c2c631551af08c110853b519055d0cdf3d981da5ac3
        #126 3% Moritz, moritz@getalby.com - 0521db9531096dff700dcf410b01db47ab6598de7e5ef2c5a2bd7e1160315bf6
        #127 3% hodlish, hodlish@Nostr-Check.com - 3575a3a7a6b5236443d6af03606aa9297c3177a45cf5314b9fd57bff894ee3ae
        #128 3% HolgerHatGarKeineNode, HolgerHatGarKeineNode@nip05.easify.de - 0adf67475ccc5ca456fd3022e46f5d526eb0af6284bf85494c0dd7847f3e5033
        #129 3% joe, joe@jaxo.github.io - 6827ef2b75ee652dcc83958b83aea0bc6580705b56041a9ee70a4178e1046cdb
        #130 3% hahattpro, hahattpro@iris.to - 53ac90ebaef84b0439cdf4f1d955ff1f1e98febc04fb789eff4a08fe53316483
        #131 3% bensima, bensima@simatime.com - 2fa4b9ba71b6dab17c4723745bb7850dfdafcb6ae1a8642f76f9c64fa5f43436
        #132 3% satan, satan@nostrcheck.me - d6b44ef322f6d67806ff06aaa9623b22ff5c2b0f0705c5e7a5a35684af9e5101
        #133 3% RadVladdy, radvladdy@nostrplebs.com - 7933ea1abdb329139b4eb37157649229b41d0ae445907238b07926182f717924
        #134 3% horacio,  - f61abb9886e1f4cd5d20419c197d5d7f3649addab24b6a32a2367124ca3194b4
        #135 3% yidneth, yidneth@getalby.com - f28be20326c6779b2f8bfa75a865d0fa4af384e9c6c99dc6a803e542f9d2085e
        #136 3% JonO,  - edecf91d15e03c921806ae6ebff86771c79e1641e899787e4d7689f68314d447
        #137 3% bellatrix, bellatrix@iris.to - f9d7f0b271b5bb19ed400d8baeee1c22ac3a5be5cf20da55219c4929e523987a
        #138 3% SecureCoop, securecoop@iris.to - d244e3cd0842d514a0725e0e0a00b712b7f2ed515a1d7ef362fd12c957b95549
        #139 3% charliesurf, charliesurf@ln.tips - a396e36e962a991dac21731dd45da2ee3fd9265d65f9839c15847294ec991f1c
        #140 3% Bitcoin ATM, bitcoinatm@Nostr-Check.com - 01a69fa5a7cbb4a185904bdc7cae6137ff353889bba95619c619debe9e3b8b09
        #141 3% lnstallone, lnstallone@allmysats.com - 84fe3febc748470ff1a363db8a375ffa1ff86603f2653d1c3c311ad0a70b5d0c
        #142 3% a652f6…9124f3,  - a652f66df4ddb5280ff466b6ff444fbc310b8e83238660473d5ccffa9e9124f3
        #143 3% hmichellerose,  - 5b29255d5eaaaeb577552bf0d11030376f477d19a009c5f5a80ddc73d49359f6
        #144 3% L0la L33tz, L0laL33tz@cashu.me - d8a6ecf0c396eaa8f79a4497fe9b77dc977633451f3ca5c634e208659116647b
        #145 3% Lommy, Lommy@nostrplebs.com - 014b9837dabb358fc0f416ceb58f72c4e6ed8fc6d317f0578dd704fc879f16f8
        #146 3% jgmontoya, jgmontoya@nostrplebs.com - 9236f9ac521be2ee0a54f1cfffdf2df7f4982df4e6eb992867d733debcf95b35
        #147 3% bavarianledger, bavarianledger@iris.to - f27c20bc6e64407f805a92c3190089060f9d85efa67ccc80b85f007c3323c221
        #148 3% operator, operator@brb.io - 3c1ba7d42c873c2f89caf1ca79b4ead6513385de53743fa6eb98c3705655695c
        #149 3% awaremoma,  - 44313b79dfc3303e3bd0c4aee0c872e96a84f23a2a45624b3ab630f24f43012f
        #150 3% Tío Tito, tiotito@nostriches.net - dc6e531596c52a218a6fae2e1ea359a1365d5eda02ec176c945ed06a9400ec72
        #151 3% javi, javi@www.javiergonzalez.io - 2eab634b27a78107c98599a982849b4f71c605316c8f4994861f83dc565df5c8
        #152 3% NathanCPerry,  - cec9808bbb00bc9c3eab4c2f23e9440a5ea775201b65a18462bc77080e39e336
        #153 3% Jason Hodlers ♾️/2099999997690000🏴, geekigai@nostrplebs.com - d162a53c3b0bfb5c3ebd787d7b08feab206b112362eca25aa291251cd70fe225
        #154 3% MR.Rabbit, Mr.Rabbit@BitcoinNostr.com - 42af69b2384071f31e55cb2d368c8a3351c8f2da03207e1fb6885991ac2522bf
        #155 3% kilicl, kilicl@nostr-check.com - 48a94f890f4dc3625b9926cdccded61e353ad1fe76600bc6acea44bdb9efceb7
        #156 3% retired,  - 82ba83731adcfe5a65ced992fde81efc756d10670c56a58cb8870210f859d3c1
        #157 3% Alex Bit, alexbit@nostrbr.online - 9db334a465cc3f6107ed847eec0bc6c835e76ba50625f4c1900cbcb9df808d91
        #158 3% freeeedom21, william@nostrplebs.com - fd254541619b6d4baa467412058321f70cf108d773adcda69083bd500e502033
        #159 3% OneEzra, oneezra@nostrplebs.com - 0078d4cb1652552475ba61ec439cd50c37c3a3a439853d830d7c9d338826ade2
        #160 3% lightsats,  - 88185e27e96cfcfc3c58c625cf70c4dba757f8d2e9ab7cab80f5012a343eb7d2
        #161 3% IceAndFireBTC, iceandfirebtc@nostrplebs.com - edb50fd8286e36878f8dd9346c138598052e5d914f0c3c6072f12eb152f307d8
        #162 3% Nostr Gang, nostrgang@nostrplebs.com - 91aeab23b5664edaa57dbe00b041ccb50544f89d7d956345bbd78b7dbaa48660
        #163 3% kexkey,  - 436456869bdd7fcb3aaaa91bed05173ea1510879004250b9f69b2c4370d58cf7
        #164 3% freebitcoin, npub1vez5zekuzc3qk989q5gtly2zg9k2gz4l3wuplv5xs8y3se09yussg4vp7p@carteclip.com - 66454166dc16220b14e50510bf9142416ca40abf8bb81fb28681c91865e52721
        #165 3% Sqvaznyak, Sqvaznyak@uselessshit.co - 056d6999f3283778d50aa85c25985716857cfeaffdbad92e73cf8aeaf394a5cd
        #166 3% koba,  - b5926366f9ac01d8ed427c9bb4cdcb86b7b4a44aaad00d262ef436621e30ea5a
        #167 3% braj, braj@nostrplebs.com - 5921b801183f10b0143c2e48c22c8192fa38d27ac614a20251cac30ab729d3a5
        #168 3% Libertus, libertus@getalby.com - 2154d20dace7b28018621edf9c3a56ab842b901db0d9b02616dbed3d15fc5490
        #169 3% ZoeBoudreault, ZoeBoudreault@id.nostrfy.me - 3c43dc2a4c996832ae3a1830250d5f0917476783132969db4e14955b6e394047
        #170 3% Saiga,  - 8f5f3a60edc875315d9c1348d6ad5dddbca806d02400049632589cb32b3f0493
        #171 3% n,  - aceff8abf70a60d7b378469ab80513c83c5d70a4f82872bac7bd619acbc71ff1
        #172 3% dnilso, dnilso@iris.to - 5ae325f930f53fad2a1a9ebefdb943bba1bef7b411e7712d2173bf3c38a49b17
        #173 3% Shroom, shroom@nostrplebs.com - a4ee688a599c9493b8641cc61987ef42b7556ba1e79d35bca92a1dce186dac85
        #174 3% 0a92e7…bc2d3d,  - 0a92e765595bbf3368c44338479df5351cf5b0028215ba95e1c9e8de99bc2d3d
        #175 3% olegaba, olegaba@olegaba.com - 7fb2a29bd1a41d9a8ca43a19a7dcf3a8522f1bc09b4086253539190e9c29c51a
        #176 3% CJButcher,  - 15fdc4596019e2b9b702ae229d5c7a17d9527226f8cf5526006908901612b200
        #177 3% wasabi-pea, wasabi@nostrplebs.com - abe1c8a87aca21e9b6a32a8c2fae5acbaf3212a01d9ccc13a80981c853e8fa02
        #178 3% 045a6f…f32334,  - 045a6fa0da5d278ac1c3aee79df23b7372ea03ee4da04ad4b8db9a5967f32334
        #179 3% Artur, artur@getalby.com - 762a3c15c6fa90911bf13d50fc3a29f1663dc1f04b4397a89eef604f622ecd60
        #180 3% ihsanmd💀, ihsanmd@getalby.com - d030bd233a1347e510c372b1878e00204b228072814361451623707896435da9
        #181 2% Satoshee, satoshee@vida.page - 0e88aac7368d5f2582437826042b3fb3a26a126f3d857618c6b6652a9f5bfa0a
        #182 2% 39ed0a…60271a,  - 39ed0aea2338477103e0b5a820532ded27dbfe4f203e7270392d55f63e60271a
        #183 2% Ancap.su, ancapsu@getalby.com - 2fe5292a2df25047a392fceead75458875c775c31cc28f4be04cef3e8db15291
        #184 2% NiceAction, niceaction@www.niceaction.com - 32891ace6802507077035ba6064f7e1db29667002165b9bf5c1c9b3f84e2303c
        #185 2% seak, seak@nostrplebs.com - d70f1bca430a2158f0e4c88b158ae18efffe8a91d436edbeee27acf2d9012cf5
        #186 2% twochickshomestead,  - 5bf5ab367f45b01b1cac72d73703fb30c704f3dbd5d376396fc0b6f39cac456b
        #187 2% Andy, andy@nodeless.io - 08cd52a46ab37a9894b3333785c2ff50e068d1b01fb03d702608da83e9817d82
        #188 2% coinbitstwitterfollows,  - 1341010418f272ed6db469d77dffdf1d946dd0701e33bdc84bb72269cef5bfed
        #189 2% Annonymal,  - 5c7794d47115a1b133a19673d57346ca494d367379458d8e98bf24a498abc46b
        #190 2% lindsey,  - f81d7cbdfe99ff2b11932fb4cdcd94f18e629e3fedafcd25ee0a4ddc0967f0f9
        #191 2% pinkyjay, pinkyjay@nostrplebs.com - b0dbac368a5ac474bc19ab11a0b3fd4260cf56b40c60944c4a331b8ad8ced926
        #192 2% criptobastardo, criptobastardo@nostrplebs.com - 311262ac14efb7011f23223b662aa1f18b3bb7c238206cb1c07424f051a11cce
        #193 2% lacosanostr, lacosanostr@lacosanostr.com - 6ce2001e7f070fade19d4817006747e4164089886a0faca950a6b0ab2a3b58b2
        #194 2% teeJem, teejem@nostrplebs.com - 36f7bc3a3f40b11095f546a86b11ff1babc7ca7111c8498d6b6950cfc7663694
        #195 2% BiancaBtcArt,  - 1f2c17bd3bcaf12f9c7e78fe798eeea59c1b22e1ee036694d5dc2886ddfa35d7
        #196 2% ruto,  - 2888961a564e080dfe35ad8fc6517b920d2fcd2b7830c73f7c3f9f2abae90ea9
        #197 2% Pocketcows,  - e462fd4f25682164bdb7c51fc1b2cd3c7e6ddba13a1d7094b06f6f4fe47f9ae3
        #198 2% mewj, mewj@elder.nostr.land - 489ac583fc30cfbee0095dd736ec46468faa8b187e311fda6269c4e18284ed0c
        #199 2% nostr,  - 2bd053345e10aed28bd0e97c311aab3470f6d7f405dc588b056bce1e3797d2f0
        #200 2% Bobolo,  - ca7799f00a9d792f9bba6947b32e3142e6c6c4733e52906cbaf92a2961216b46
        #201 2% InsolentBitcoin,  - 6484df04c9403a64c3039f5f00d24ac0535f497cdfa1f187bc6a2d34cf017b97
        #202 2% Monero Directory,  - 1abdef52155dc52a21a2ac9ed19e444317f6cf83500df139fbe73c2a7ac78e2a
        #203 2% thetonewrecker, thetonewrecker@nostrplebs.com - 3762d3159bfd9d8acb56677eec9a6f8a5a05ea86636186ca6ed6714a69975fed
        #204 2% yodatravels, yodatravels@iris.to - 67eb726f7bb8e316418cd46cfa170d580345e51adbc186f8f7aa0d4380579350
        #205 2% Bitcoin Bandit, bitcoin69@iris.to - 907842aa7b5d00054473d261e814c011c5d8e13bf8a585cc76121b1e6c51900f
        #206 2% Zzar, Zzar@nostrplebs.com - ca1dd2422cb94874c1666c9c76b7961bbaea432632643f7a2dc9d4d2bfb35db9
        #207 2% vidalBidi, vidalbidi@getalby.com - 0c28a25357c76ac5ac3714eddc25d81fe98134df13351ab526fc2479cc306e65
        #208 2% 994e89…f75447,  - 994e892582261fd933af25bcc9672f2fbd5e769e3d1c889ecd292a7a92f75447
        #209 2% juangalt, juangalt@current.ninja - 372da077d6353430f343d5853d85311b3fd27018d5a83b8c1b397b92518ec7ac
        #210 2% Dean, dean@nostrplebs.com - 83f018060171dfee116b077f0f455472b6b6de59abf4730994022bf6f27d16be
        #211 2% alexli, alex2@nostrverified.com - 8083df6081d91b42bcf1042215e4bfc894af893cd07ea472e801bc0794da3934
        #212 2% Khidthungban,  - 8d5cf93afb8d9ef1d08acee4e7147348d0c573bf7e5f57886a8a9a137cbe890c
        #213 2% Trooper, trooper@iris.to - 2c8d81a4e5cd9a99caba73f14c087ca7c05e554bb9988a900ccd76dbd828407d
        #214 2% Satscoinsv, ⚡️satscoinsv@getalby.com - 80db64657ea0358c5332c5cca01565eeddd4b8799688b1c46d3cb2d7c966671f
        #215 2% AARBTC, aarbtc@iris.to - 6d23993803386c313b7d4dcdfffdbe4e1be706c2f0c89cb5afaa542bf2be1b90
        #216 2% yogsite, _@gue.yogsite.com - d3ab705ec57f3ea963fc7c467bddc7b17bf01b85acc4fbb14eed87df794a116c
        #217 2% NostrMemes, nostrmemes@iris.to - 6399694ca3b8c40d8be9762f50c9c420bf0bd73fb7d7d244a195814c9ab8fb7e
        #218 2% btcpavao, btcpavao@iris.to - 1a8ed3216bd2b81768363b4326e1ae270a7cd6fe570bafeda2dc070f34f3aedc
        #219 2% Anonymous, Anonymous@BitcoinNostr.com - ac076f8f80ee4a49f22c2ce258dcfe6e105de0bf029a048fa3a8de4b51c1b957
        #220 2% zoltanAB, zoltanab@iris.to - 42aafd1217089d68c757671a251507a194587dd3adfc3a3a76bb1e38a78a3453
        #221 2% katsu, katsu@onsats.org - 76f64475795661961801389aeaa7869a005735266c9e3df9bc93d127fad04154
        #222 2% bryan, bryan@nonni.io - 9ddf6fe3a194d330a6c6e278a432ae1309e52cc08587254b337d0f491f7ff642
        #223 2% pedromvpg, pedromvpg@pedromvpg.com - 8cd2d0f8310f7009e94f50231870756cb39ba68f37506044910e2f71482b1788
        #224 2% Nellie, sonicstudio@getalby.com - 37fbbf7707e70a8a7787e5b1b75f3e977e70aab4f41ddf7b3c0f38caedd875d4
        #225 2% nicknash,  - 636b4e6f5a594893c544b49a5742f0a90f109b70d659585e0427a1c0361c0b09
        #226 2% dlegal, kounsellor@nostrplebs.com - 201e51e71a753af3699cf684d7f4113c59a73c4b7bd26ef3f4c187a6173fbf06
        #227 2% BitcoinLake,  - 5babddf98277e3db6c88ae1d322bc63fd637764370e1d5e4fe5226104d82034f
        #228 2% BitcoinKeegan,  - b457120b6cfb2589d48718f2ab71362dd0db43e13266771725129d35cc602dbe
        #229 2% KatieRoss, katieross@nostrplebs.com - 90f09238f3514f249e2b333e6119eef49697020f956fd7b6732ce118dd1b53cb
        #230 2% efcfa6…e3f485,  - efcfa63ac0324e37fb138c2b9dbbf9372f64ec857c923c5c1f713d3592e3f485
        #231 2% bc9e89…b519d3,  - bc9e89110e6e7ec5540b8ad0467d8a39554a7527c27e7af4cd45b2b8c4b519d3
        #232 2% Ilj, iamlj@iris.to - fa3e7bcc5e588a8111ffb9d9eb8bf62c87d8a0ef6e1e5e0c74311b61f6ced8e7
        #233 2% ayelen,  - 1c31ccda2709fc6cf5db0a0b0873613e25646c4a944779dfb5e8d6cbbcd2ee1c
        #234 2% zach, Zach@BitcoinNostr.com - d99211aeeb643695ee1aad0517696bbc822e2fb443afe2dc9dadc0ca50b040e2
        #235 2% Yi,  - 248caad2f8392c7f72502da41ee62bbe256ea66fb365e395c988198660562ff7
        #236 2% Amouranth, amouranth@nostrcheck.me - be5aa097ad9f4d872c70e432ad8c09565ee7dc1aee24a50b683ddca771b14901
        #237 2% hss5qy, hss5qy@getalby.com - bc21401161327647e0bbd31f2dec1be168ef7fa5d05689fca0d063b114ed9b46
        #238 2% dpc, dpcpw@iris.to - 274611b4728b0c40be1cf180d8f3427d7d3eebc55645d869a002e8b657f8cd61
        #239 2% pred,  - 3946adbb2fc7c95f75356d8f3952c8e2705ee2431f8bd33f5cae0f9ede0298e2
        #240 2% jamesgore,  - a94921403ac0ccf1a150ccac3679b11adcb3c3bb78b490452db43a8b6964a5c7
        #241 2% bitcoinfinity, bitcoinfinity@nostrplebs.com - afbda6a942f975ddf8728bda3e6e5c9e440f067fcde719c6f57512f0f7ed4bf2
        #242 2% tonyseries, TonySeries@BitcoinNostr.com - ba5a614a48719361f515f6efa62c3e213da4bcddbb78dafd3121daa839192275
        #243 2% kuobano, kuobano@nostrplebs.com - 3f6d0bbb073839671f4c7f1e23452c6c3080f6c5f4cbc2f56c17e2b57ee01442
        #244 2% kitakripto, kitakripto@BitcoinNostr.com - 0b11a45bf4ff7f000886b2227e43404d212bf585f71514d54ae5ae685f4c8fbb
        #245 2% Bashy, _@localhost.re - 566516663d91d4fef824eaeccbf9c2631a8d8a2efee8048ca5ee6095e6e5c843
        #246 2% alxc, alxc@uselessshit.co - c13cb9426a4f85aff08019d246d1240a6cbf49ab9525a06d54fb496b9a3592b0
        #247 2% Kukryr, kukryr@orangepill.dev - 3f03ab6555d2e36ba970d83b8dfe1a9c09d1b89048cf7db0c85d40850f406e54
        #248 2% Saidah, saidah@nostrplebs.com - 909efa6667b28627f107764ce3c28895c46fffd1811b7415dcab03f48c44b597
        #249 2% micmad, miceliomad@miceliomad.github.io/nostr/ - cd806edcf8ff40ea94fa574ea9cd97da16e5beb2b85aac6e1d648b8388504343
        #250 2% Zack Wynne,  - 9156e62c7d2f49a91b55effec6c111d3fb343e9de6ff05650e7fd89a039a9dce
        #251 2% Sharon21M, sharon21m@nostr.fan - 66b5c5be6cec2b4a124c532e97d8342f8d763d6b507caced9185168603751f25
        #252 2% bitcoinheirodomanto,  - 93d16b6fcd11199cc113e28976999ff94137ded02ddf6b84bf671daf9358c54a
        #253 2% tyler,  - 272fe1597e8d938b9a7ae5eb23aa50c5048aabbf68f27a428afe3aecd08192da
        #254 2% DMN, dmn@noderunners.org - 176d6e6ceef73b3c66e1cb1ed19b9f2473eaa514678159bc41361b3f29ddb065
        #255 2% Nela@Nostrica2023, nela_at_nostrica2023@Nostr-Check.com - 4b0bcab460adda31fad5a326fb0c04f6ec821fb24be85dbdc03c04cc0e12fc07
        #256 2% xbolo, xbologg@nanostr.deno.dev - 7aabf4a15df15074deeffdb597e6be54be4a211cbd6303436cb1ccea6c9cf87b
        #257 2% btcurenas, btcurenas@nostr.fan - 206a1264c89e8f29355e792782e83ca62331ca3d70169327cb315171b4a7ce2c
        #258 2% amaluenda, amaluenda@getalby.com - 129a80a580a0cb88d5eae9d3924d7bb8a29e0c03ef9fb723091de69c22eaaff8
        #259 2% DeveRoSt,  - f838b6a03d8d0127a9a98e87c0142b528916a4336ba537e14131a2f513becc17
        #260 2% phoenixpyro,  - 5122cee9af93a36be4bb9b08ee7897ef88fe446c0a5d2f8db60da9faa0f72f27
        #261 2% Queen ₿, queenb@nostrplebs.com - 735e573b24b78138e86c96aaf37cf47547d6287c9acbd4eda173e01826b6647a
        #262 2% L., ezekiel@Nostr-Check.com - 83663cd936892679cbd1ccdf22e017cb9fee11aef494713192c93ad6a155e287
        #263 2% dolu (compromised),  - e668a111aa647e63ef587c17fb0e2513d5c2859cd8d389563c7640ffea1fc216
        #264 2% Marakesh 𓅦, marakesh@getalby.com - dace63b00c42e6e017d00dd190a9328386002ff597b841eb5ef91de4f1ce8491
        #265 2% Storm, storm@reddirtmining.io - eaba072268fbb5409bdd2e8199e2878cf5d0b51ce3493122d03d7c69585d17f2
        #266 2% fiore,  - 155fd584b69fea049a428935cef11c093b6b80ca067fe4362eab0564d0774f10
        #267 2% .b.o.n.e.s., _b_o_n_e_s_@stacker.news - b91257b518ee7226972fc7b726e96d8a63477750a1b40589e36a090735a4f92f
        #268 2% btchodl, bdichdbd@stacker.news - d3ca4d0144b7608eceb214734a098d50dd6c728eb72e47b0e5b1e04480db1009
        #269 2% Rosie,  - caf0d967570ab0702c3402d50c4ab12dc6855ea062519b1ac048708cb663b0c8
        #270 2% j9, j9@nostrplebs.com - c2797c4c633d3005d60a469d154b85766277454b648252d927660d41ecec4163
        #271 2% nokyctranslate, nokyctranslate@iris.to - 794366f1f67b7bc5604fd47e21a27e6fcbff7ec7e7a72c6d4c386d50fd5d2f04
        #272 2% Neomobius, Neomobius_at_mstdn.jp@mostr.pub - 9134bd35097c03abdcd9d61819aa8948880b6e49fc548d8a751b719dced7f7da
        #273 2% dojomaster,  - 30be56daec34e8b319d730f2c2f1cba28ef076660be33d7811dd385698a9cb40
        #274 2% paddepadde ⚡️, paddepadde@getcurrent.io - 430169631f2f0682c60cebb4f902d68f0c71c498fd1711fd982f052cf1fd4279
        #275 2% Val, val@nostrplebs.com - e2004cb6f21a23878f0000131363e557638e47a804bcfc200103dd653fc9b7dc
        #276 2% Nickfost_,  - a3e4cba409d392a81521d8714578948979557c8b2d56994b2026a06f6b7e97d2
        #277 2% dishwasher_iot, dishwasher_iot@wlvs.space - 5c6c25b7ef18d8633e97512159954e1aa22809c6b763e94b9f91071836d00217
        #278 2% 𝕬𝖓𝖔𝖓𝖞𝖒𝖔𝖚𝖘, zapper.lol - 96aceca84aa381eeda084167dd317e1bf7a45d874cd14147f0a9e0df86fb44c2
        #279 2% Peter,  - b649ca5743312176174cbe76cf81d3eec493b21a52b822b6aa12bd4473da0d01
        #280 2% justin, 1@justinrezvani.com - 84d535055542132100ea22e96e33349844422e6e698cc98bd8fb5eae08d76752
        #281 2% vikeymehta,  - 1a3d05e13fa38543b3d45f31c638e94e113b35c0e1db7371cdfa69861e150830
        #282 2% sshh, sshh@nostrplebs.com - b0f86106d59d2ce292a4d89e70ff4057d7adf4b1b42bb913f37ceb9159bb2aea
        #283 2% Red_Eye_Jedi,  - 3603dbbea53ee52ab34e0f96a8d42aa55486cf5e2e05483533613e97274155f5
        #284 2% jim, mk05@iris.to - 2ed67b778522bfa0245ee57306dea40d6fd9b023db5fff43e2de0419cfe2164e
        #285 2% pniraj007,  - 99f7ba6cfb2fcd60853446b45cec2a467f65faa3245a95513bcf372eec4fbb0e
        #286 2% b676eb…7c389b,  - b676ebe5ebd490523dda7db35407b7370974b4df25be32335f0652a1f07c389b
        #287 2% herald, herald@bitcoin-herald.org - 7e7224cfe0af5aaf9131af8f3e9d34ff615ff91ce2694640f1f1fee5d8febb7d
        #288 2% Giuseppe Atorino, nostr@pos.btcpayserver.it - e6eaf2368767307b45fcbea2d96dcb34a93af8877147203fadc10b8f741b71c9
        #289 2% a8b7b0…d90ac2,  - a8b7b07222485f8b845961dd4ca4d8b63c575e060b4d9386e32463e513d90ac2
        #290 2% genosonic,  - 05ffbdf4b71930d0e93ae0caa8f34bcfb5100cfba71f07b9fad4d8b5a80e4df3
        #291 2% JohnnyG, thumpgofast@NostrVerified.com - 241d6b169d62fa3d673fccf66ab62d49c0a1147ab6ab81f7a526d890e1d68a2b
        #292 2% neoop, neo@elder.nostr.land - ea64386dba380b76c86f671f2f3c5b2a93febe8d3e2e968ac26f33569da36f87
        #293 2% Alchemist, alchemist@electronalchemy.com - 734aac327175cb770b9aa75c8816156ea439a79c6f87a16801248c1c793a8bfc
        #294 2% timp, timp@iris.to - 24cf74e1125833e9752b4843e2887dedddf6910896e6e82a2def68c8527d0814
        #295 2% ken, ken@BitcoinNostr.com - 3505b759f075da83e9d503530d3238361b1603c28e0ee309d928174e87341713
        #296 2% Shea,  - 8dc289f2b5896057e23edc6b806407dc09162147164f4cae1d00dcb1bcd3f084
        #297 2% Devcat,  - 7f1052e59569dee4c6587507c69032af5d6883d2aa659a55bbfe1cb2e8233daf
        #298 2% 173a2e…36436a,  - 173a2e04860656e9bab4a62cd5ec2b46ac8814e240c183e47b6badf7b936436a
        #299 2% Irebus, irebus@nostr.red - 1aaaa8e2a2094e2fdd70def09eae4e329ceb01a6a29473cb0b5e0c118f85bd35
        #300 2% b720b6…e48a8f,  - b720b63c47b3292dcb3339782c612462a7a42c9eece06d609a49cf951de48a8f
        #301 2% theflywheel,  - 57dcc9ed500a26a465ddb12c51de05963d4dec8a596708629558495c4acacab3
        #302 2% 223597…002c18,  - 22359794c50e2945aa768ee500ffb2ddb388696ad078a350ae570152ff002c18
        #303 2% gratitude,  - 4686358c60bae7694e8b39dad26d1c834d5dd27726a56e2501fc06dec6942be1
        #304 2% stim4444, stim4444@no.str.cr - 0aeaec333bf9a0638de51ea837590ca64522ec590ed160ce87cb6e30d10df537
        #305 2% 756240…265fc2,  - 756240d3be0d553b0cd174b3499cffa37fbe8394ee06b9ab50652e314c265fc2
        #306 2% 4d38ed…d26aad,  - 4d38ed26a6d1080806534818a668c71381bcb04bc4ca1083d9d9572977d26aad
        #307 2% Kwinten,  - c29da265739bc3886c76d84b0a351849fa45a31a64fcb72f47c600ab2623f90c
        #308 2% b36506…7ca32c,  - b365069ada41fc7190f8b11e8342f7f66f9777eaaa9882722d0be863c27ca32c
        #309 2% Cole Albon,  - c3ff9a851ca965ed266ba54c9263f680be91e2465628c64bab6a5992521d5c5d
        #310 2% Onecoin,  - b23ce47262373574d6653fad2da09db1fb20bb2919f3e697b8edd1966fffd8ec
        #311 2% Disabled,  - 7d706eaefb905ea9b3af885879fb5911b50b39db539c319438703373424204ec
        #312 2% xdamman,  - 340254e011abda2e82585cbfee4f91b3f07549a6c468fe009bf3ec7665a2e31b
        #313 2% jmrichner,  - 797750041d1366a80d45e130c831f0562b5f7266662b07acef50dd541bfa2535
        #314 2% pentoshi,  - db6ad1e2a4cbbacbbdf79377a9ebb2fc30eb417ce9b061003771cb40b8e00d56
        #315 2% 35453d…45d10b,  - 35453d2e49a0282c4dd694e5a364bf29600a9b5443e4712cfc86a0495345d10b
        #316 2% LayerLNW, layerlnw@nostr.fan - 33c9edf7ade19188685997136e6ffb4ed89939178fa5f2259428de1cd3301380
        #317 2% Bitcoincouch,  - fbd3c6eb5ef06e82583d3b533663ba86036462a02e686881d8cb2de5aaa9fa4a
        #318 2% BritishHodl,  - 22fb17c6657bb317be84421335ef6b0f9f1777617aa220cf27dc06fb5788f438
        #319 2% enhickman, enhickman@enhickman.net - 0cf08d280aa5fcfaf340c269abcf66357526fdc90b94b3e9ff6d347a41f090b7
        #320 2% 4d6e72…219298,  - 4d6e72aba0e8a033c973acd7e42f915d5fa1708be7229d477869e91136219298
        #321 2% f75326…af65e0,  - f7532615471b029a34e41e080b2af4bad2b80f8105c008378d0095991eaf65e0
        #322 2% LiveFreeBTC, LiveFreeBTC@livefreebtc.org - 49f586188679af2b31e8d62ff153baf767fd0c586939f4142ef65606d236ff75
        #323 2% aptx4869, aptx4869@aptx4869.app - 64aaa73189af814977ff5dedbbab022df030f1d7df3e6307aceb1fddb30df847
        #324 2% khalil, khalil@klouche.com - 5a03bdb5448b440428d8459d4afe9b553e705737ef8cd7a0d25569ccead4d6ce
        #325 2% nsec1wnppl0xqw2lysecymwmz3hgxuzk60dgyur6mqtgexln20qp4xv9sugxghg, nsec@ittybitty.tips - f1ea91eeab7988ed00e3253d5d50c66837433995348d7d97f968a0ceb81e0929
        #326 2% BTC_P2P,  - ecf468164bd743b75683db3870ce01cb9a1d4b8ec203ed26de50f96255bbc75a
        #327 2% Big FISH, bigfish@iris.to - 963100cf40967a70cdea802c6b4b97956cf8c5e3b09e492b24a847d4c535a794
        #328 2% 9e93fb…2483b6,  - 9e93fb0012a6177faddf2fd324fb61eafbe8b142b31c5e89fd85bfafd12483b6
        #329 2% Mynameis,  - 6bec23b4a17da33d0a2f44e258371e869ff124775e8e38b9581dcd49c8d1d4a6
        #330 2% 3f2342…d689b8,  - 3f23426af245168f8112e441c046ecdb29aca56a6d33d21e276b8ac00bd689b8
        #331 2% 865c92…136ced,  - 865c92a207a156a2d48404694a2eed5ceca5c163b7a845b86a6c75e142136ced
        #332 2% 95d4d6…fe1673,  - 95d4d60e643f283cef8d70ab7a9c09ab5a85924f97e11b22cf99779c4ffe1673
        #333 2% verse,  - 0ff7a93751d37ffcca05579c59ac69053d8d0c6f2c57ed9101ba8758eebc0d6b
        #334 2% oldschool, oldschool@iris.to - 19dba8f974322c7345d3b491925896d19e7f432a4f41223c5daf96e31fae338d
        #335 2% Danton🇨🇭, danton@nostrplebs.com - dbe693bc2d16c52e18e75f2cb76401cb7d74132cc956f7315ea5ebee1adfc966
        #336 2% BitcoinZavior, bitcoinzavior@nostrplebs.com - c6e86c9b95ef289600800b855b9a6ca42019cc9453937020289d8b3e01dab865
        #337 2% BitcoinSermons, BitcoinSermons@BitcoinNostr.com - 615f40fae8f2e08da81b5c76a0143cb04b4e9e044bf6047efe15c56c7cc1a6b2
        #338 2% skreep,  - a4992688b449c2bdd6fa9c39a880d7fe27d5f5e3e9fd4c47d65d824588fd660f
        #339 2% db830b…4bb85c,  - db830b864876a0f3109ae3447e43715711250d53f310092052aabb5bdc4bb85c
        #340 2% UKNW22LINUX, uknwlinux@plebs.place - ab1ef3f15fc29b3da324eb401122382ceb5ea9c61adaad498192879fd9a5d057
        #341 2% Satoshism, satoshism@nostrplebs.com - e262ed3a22ad8c478b077ef5d7c56b2c3c7a530519ed696ed2e57c65e147fbcb
        #342 2% William,  - 8c55174d8fc29d4da650b273fdd18ad4dda478faa4b0ea14726d81ac6c7bef48
        #343 2% thebitcoinyogi, jon@nostrplebs.com - 59c2e15ad7bc0b5c97b8438b2763a5c409ff76ab985ab5f1f47c4bcdd25e6e8d
        #344 2% vake,  - 547f45b91c1e6b4137917cde4fa1da867c8cdfe43d0f646c836a622769795a14
        #345 2% hobozakki, hobozakki@nostrplebs.com - 29e31c4103b85fab499132fa71870bd5446de8f7e2ac040ec0372aa61ae22f98
        #346 2% SirGalahodl, sirgalahodl@satstream.me - 25ee676190e2b6145ad8dd137630eca55fc503dde715ce8af4c171815d018797
        #347 2% 1f6c76…ebb9c9,  - 1f6c76ddbab213cdd43db2695b1474605639862302c7cfae35362be8caebb9c9
        #348 2% greencandleit,  - 3d4b358b50d20c3e4d855f273ff06c49bc6b3f6e62c42aed44f278742fd579da
        #349 2% ichigo,  - 477e0b3c0c6029e31562b39650efa8f871d52e3ab09145d72e99b9b74dd384d7
        #350 2% Niko,  - 636fdb4de194bca39ab30ab5793a38b8d15c1b1c0a968d04f7fe14eb1a6a8c42
        #351 2% afa, victor@lnmarkets.com - 8f6945b4726112826ac6abd56ec041c87d8bdc4ec02e86bb388a97481f372b97
        #352 2% BushBrook,  - a39fd86ed75c654550bf813430877819beb77a3b670e01a9680a84a844db9620
        #353 2% naoise,  - c4a9caef93e93f484274c04cd981d1de1424902451aca2f5602bd0835fe4393d
        #354 2% smies.me, jacksmies@iris.to - cdecbc48e35a351582e3e030fd8cf5d5f44681613d2949353d9c6644d32d451f
        #355 2% Chemaclass, chemaclass@snort.social - c5d4815c26e18e2c178133004a6ddba9a96a5f7af795a3ab606d11aa1055146a
        #356 2% BTCingularity,  - aa1f96f685d0ac3e28a52feb87a20399a91afb3ac3137afeb7698dfcc99bc454
        #357 2% the_man,  - dad77f3814964b5cdcd120a3a8d7b40c6218d413ae6328801b9929ed90123687
        #358 2% jayson, jayson@tautic.com - 7be5d241f3cc10922545e31aeb8d5735be2bc3230480e038c7fd503e7349a2cc
        #359 2% jesterhodl, jesterhodl@jesterhodl.com - 3c285d830bf433135ae61c721b750ce11ae5b2e187712d7a171afa7cda649e50
        #360 2% 06d694…c3ab96,  - 06d6946fd1ff1fba6ac530e0b5683db4c73cdc11d6c42324246e10f4f2c3ab96
        #361 2% sardin,  - f26470570bcb67a18a90890dbe02d565eadc6c955912977c64c99d4b9a7fd29f
        #362 2% Bitcoin_Gamer_21, Bitcoin_Gamer_21@bitcoin-21.org - 021df4103ede2cdc32de4058d4bdb29ffcbfd13070f05c4688f6974bd9a67176
        #363 2% water-bot, water-bot@gourcetools.github.io - 000000dd7a2e54c77a521237a516eefb1d41df39047a9c64882d05bc84c9d666
        #364 1% ondorevillager,  - 5d7b460173010efd682c0d7bc8cc36ca9bf7dcc7990288f642c04b8e05713c83
        #365 1% Tomfantasia,  - d856af932000c292ad723dee490ebcf908a1031b486dea05267ee50b473349b2
        #366 1% W3crypto, w3crypto@iris.to - d001bca923ab56b1c759fc9471fbe6baadac50aeba7d963155772ac7b6779027
        #367 1% bradjpn,  - c4da3be8e10fa86128530885d18e455900cccff39d7a24c4a6ac12b0284f62b3
        #368 1% @discretelog,  - 03e4804b4a28c051f43185d6bf5b4643cb3f0d9632c4394b60a2ffad0f852340
        #369 1% makaveli, makaveli@nostrplebs.com - 570469cbc969ea6c7e94c41c6496a2951f52d3399011992bf45f4b2216d99119
        #370 1% JamieAnders, jamieanders@ln.tips - 7601e743ad432d78471ac57178402a57cd3f3a92fb208be7de788af2d6a57669
        #371 1% LightningVentures,  - 37de18e08cdc01ce7ced1808b241ec0b4a69e754d576ce0e08f0cf3375bb0a6b
        #372 1% Colorado Craig, cball@nostrplebs.com - a2c20d6856545b145bc76cdfaffd04ddad4e58d73b2352dcc5de86aa4ba38e7b
        #373 1% 21fadb…3d8f6f,  - 21fadb45755a5f41d1b84ecf4610657dd9336d24419d61efffb947aeec3d8f6f
        #374 1% castaway,  - 0cbde76a61cc539059f7da7b4fb19c0197f9f781674d307b52264cbb0144c739
        #375 1% chames,  - a721f4370afd51fcbc7e2a685f24a454f14fea84448e1c2aa4a9a94b89f3ea7d
        #376 1% laura, laura@nostrich.zone - ac2250f83aaa7c4a8503f9c15c0cc11ac992315e5ac3e634541223a8deb6c09c
        #377 1% Kaz, kaz@reddirtmining.io - 826d71153f4938c43b930f90cc3130f33430d1e069d43a2f705f9538450b9369
        #378 1% Verismus, verismus@nostrplebs.com - 9e79aed207461f0d5ebc2c8b94e6875e2a6d5dd15990f8ea3ad2540786d07528
        #379 1% cafc4f…107e85,  - cafc4fbaa558e466bba6c667fcf14506728ff70975f2817c8e5b6fb062107e85
        #380 1% bitpetro, bitpetro@nostrplebs.com - 22470b963e71fa04e1f330ce55f66ff9783c7a9c4851b903d332a59f2327891e
        #381 1% nossence, nossence@nossence.xyz - 56899e6a55c14771a45a88cb90a802623a0e3211ea1447057e2c9871796ce57c
        #382 1% The Progressive Bitcoiner,  - 4870d5500a121e5187544a3e6e5c2fee1d0a03e1b85073f27edb710b110d6208
        #383 1% orangepillstacker,  - affe861d3e4c42bb956a35d8f9d2c76a99ba16581f3d0dbf762d807e1de8e234
        #384 1% Nostrdamus, manbearpig@nostrplebs.com - 84a42d3efa48018e187027e2bbdd013285a27d8faf970f83a35691d7e2e1a310
        #385 1% JohnSmith, johnsmith@nostrplebs.com - 7c939a7211f1b818567d10b7e65bb03e2830420acf3d6f4f65a7320e2e66d97e
        #386 1% Matty,  - 1cb599e80e7933a7144bbebfb39168c6ee75a27bacd6d8a67e80c442a32a52a8
        #387 1% epodrulz, bitcoin@bitcoinedu.com - a249234ba07c832c8ee99915f145c02838245499589a6ab8a7461f2ef3eec748
        #388 1% paul,  - 52b9e1aca3df269710568d1caa051abf40fbdf8c2489afb8d2b7cdb1d1d0ce6f
        #389 1% 0ec37a…ba5855,  - 0ec37a784c894b8c8f96a0ccb6055d4ce7b8420482bc41d00e235723a9ba5855
        #390 1% jor, knggolf@nostrplebs.com - 7c765d407d3a9d5ea117cb8b8699628560787fc084a0c76afaa449bfbd121d84
        #391 1% Nighthaven, nighthaven@iris.to - 510e0096e4e622e9f2877af7e7af979ac2fdf50702b9cd77021658344d1a682c
        #392 1% 00f454…929254,  - 00f45459dcd6c6e04706ddafd03a9f52a28833efc04b3ff0a66b89146b929254
        #393 1% XBT_fi, xbt_fi@iris.to - 6e1bee4bdfc34056ffcde2c0685ae6468867aedd0843ed5d0cfcde41f64bfda8
        #394 1% e9f332…6474aa,  - e9f33272af64080287624176253ed2b468d17cec5f2a3d927a3ee36c356474aa
        #395 1% ulrichard,  - cd0ea239c10e2dbe12e5171537ff0b8619747bfcd8dcf939f4bceed340b38c87
        #396 1% 54ff28…d7090d,  - 54ff28f1abbceddea50cf35cac69e5df32b982c3e872d40aa9ec035431d7090d
        #397 1% GeneralCarlosQ17, gencarlosq17@iris.to - b13cc2d0b7b70ba41c13f09cc78dc6ce7f72049b1fe59a8194a237e23e37216e
        #398 1% BitcoinIslandPH,  - b4ab403c8215e0606f11be21670126a501d85ea2027b6d15bf4b54c3236d0994
        #399 1% rotciv, rotciv@plebs.place - b70c9bfb254b6072804212643beb077b6ba941609ed40515d9b10961d7767899
        #400 1% Alfa,  - 0575bc052fed6c729a0ab828efa45da77e28685da91bdfebc7a7640cb0728d12
        #401 1% ben_dewaal,  - aac02781318dfc8c3d7ed0978ef9a7e8154a6b8ae6c910b3a52b42fd56875002
        #402 1% cguida,  - 2895c330c23f383196c0ef988de6da83b83b4583ed5f9c1edb0a559cecd1f900
        #403 1% nout,  - 52cb4b34775fa781b6a964bda0432dbcdfede7a59bf8dfc279cbff0ad8fb09ff
        #404 1% Merlin, Merlin@bitcoinnostr.com - 76dd32f31619b8e35e9f32e015224b633a0df8be8d5613c25b8838a370407698
        #405 1% millymischiefx,  - 868d9200af6e6fe1604a28d587b30c2712100b0edab76982551d56ebc6ae061f
        #406 1% yegorpetrov(alternative), yeg0rpetrov@iris.to - 2650f1f87e1dc974ffcc7b5813a234f6f1b1c92d56732f7db4fef986c80a31f7
        #407 1% baloo, baloo@nostrpurple.com - c49f8402ef410fce5a1e5b2e6da06f351804988dd2e0bad18ae17a50fc76c221
        #408 1% jamesgospodyn, jamesgospodyn@nostr.theorangepillapp.com - 11edfa8182cf3d843ef36aa2fa270137d1aee9e4f0cd2add67707c8fc5ff2a0d
        #409 1% Mysterious_Minx,  - 381dbcc7138eab9a71e814c57837c9d623f4036ec0240ef302330684ffc8b38f
        #410 1% 878bf5…f7cb86,  - 878bf5d63ed5b13d2dac3f463e1bd73d0502bd3462ebf2ea3a0825ca11f7cb86
        #411 1% carl, carl@armadalabs.studio - cd1197bede3b3c0cdc7412d076228e3f48b5b66e88760f53142e91485d128e07
        #412 1% NIMBUS,  - c48a8ced6dfcc450056bb069b4007607c68a3e93cf3ae6e62b75bf3509f78178
        #413 1% btcportal, btcportal@nostrplebs.com - 9fc1e0ef750dba8cdb3b360b8a00ccad6dcef6b7ad7644f628e952ed8b7eebfb
        #414 1% 9652ba…ccd3f1,  - 9652ba74b6981f69a3ffad088aa0f16c8af7fe38a72e5d82176878acdcccd3f1
        #415 1% mjbonham, mjb@nostrplebs.com - 802afdddebfb60a516b39d649ea35401749622e394f85a687674907c4588dc7a
        #416 1% ⌜Jan⌝,  - fca142a3a900fed71d831aa0aa9c21bb86a5917a9e1183659857b684f25ae1ce
        #417 1% DontTraceMeBruh,  - 3fef59378dce7726d3ef35d4699f57becf76d3be0a13187677126a66c9ade3b8
        #418 1% 9a73c0…1707f2,  - 9a73c0ecd5049ae38b50d0d9eaaabd49390cdd08c3d3d666d0d8476c411707f2
        #419 1% esbewolkt, esbewolkt@nostr.fan - 50ea483ddffeeed3231c6f41fddfe8fb71f891fa736de46e3e06f748bbdeb307
        #420 1% morningstar,  - 82671c61fa007b0f70496dec2420238efd3df2f76cdaf6c1f810def8ce95ba45
        #421 1% Sweedgraffixx,  - ee5f4a67cb434317dd7b931d9d23cb2978ab728a008e4c4dcca9cc781d3ae576
        #422 1% 878492…165b4f,  - 878492807168be8dfbae71d721a9b7f6833a9928fcf9acc3274dfdb113165b4f
        #423 1% koukos, koukos@iris.to - 4260122b8a141e888413082dea2d93568488bae4726358e9e6b7da741852dfc8
        #424 1% nopara73,  - 001892e9b48b430d7e37c27051ff7bf414cbc52a7f48f451d857409ce7839dde
        #425 1% Be⚡BANK,  - fbfb3855d50c37866af00484a6476680ae1e2ff04ceb9dd8936465f70d39150b
        #426 1% davekrock, davekrock@NostrVerified.com - e26b5f261cb29354def8a8ba6af49b137e3144388a81ef78eed8e77cfb18fd44
        #427 1% BitcoinLoveLife, Bitcoinlovelife@BitcoinNostr.com - 3c08d854ef6c86b1dc11159fdabc09209eaeba01790ce96690c55787daf3c415
        #428 1% Steam,  - 111a1ae50a7e30a465126b0ab10c3eac6ddaa3cca016a4117470e6715a2dfdef
        #429 1% xolag, xolagl2@getalby.com - fb64b9c3386a9ababaf8c4f80b47c071c4a38f7b8acdc4dafb009875a64f8c37
        #430 1% relay9may,  - 1e7fd2177d20c97f326cda699551f085b8e7f93650b48b6e87a0bebcdfeebc8b
        #431 1% f2c817…8a2f3b,  - f2c817a3bbf07517a38beac228a12e3460d18f1ec2ed928d2e6d2e67308a2f3b
        #432 1% remoney, remoney@nostrplebs.com - 3939a929101b17f4782171b5e0e49996fbe2215b226bd847bd76be3c2de80e9a
        #433 1% 387eb9…a6f87f,  - 387eb9a5c4f43e40e6abd1f6fe953477464ae5830d104e325f362209c2a6f87f
        #434 1% 846b76…539eca,  - 846b763b1234c5652f1e327e59570dcb6535d2d20589c67c2a9a90b323539eca
        #435 1% Shawn C.,  - 83ea7cb5a3ab517f24eb2948b23f39466dd5f200fd4e6951fed43ba34e9a4a83
        #436 1% roberto, roberto@bitcoiner.chat - 319a588a77cd798b358724234b534bff3f3c294b4f6512bde94d070da93237c9
        #437 1% LazyNinja, cryptolazyninja@stacker.news - ff444d454bc6ba2c16abdfd843124e6ad494297cf424fa81fb0604a24ee188e2
        #438 1% e5ae7b…c8b2ef,  - e5ae7b9cc5177675654400db194878601ee8ff5c355acb85daa50f7551c8b2ef
        #439 1% kimymt, kimymt@getalby.com - 3009318aa9544a2caf401ece529fd772e26cdd7e60349ec175423b302dafd521
        #440 1% z_hq,  - 215e2d416a8663d5b2e44f30d6c46750db7254cdbd2cf87fea4c1549d97486d4
        #441 1% Reza,  - e7c0d1e42929695b972e90e88fb2210b3567af45206aac51fff85ba011f79093
        #442 1% benderlogic, benderlogic@rogue.earth - d656ffcaf523f15899db0ea3289d04d00528714651d624814695cabe9cb34114
        #443 1% maestro, MAESTRO@BitcoinNostr.com - 8c3e08bbc47297021be7e6e2c59dab237fab9056b3a5302a8cd2fc2959037466
        #444 1% travis, travis@west.report - 3dc0b75592823507f5f625f889d36ba2607487550b4f38335a603eda010f2bc2
        #445 1% Coffee Lover, coffeelover@nostrplebs.com - 9ecbaa6dc307291c3cf205c8a79ad8174411874cf244ca06f58a5a73e491222c
        #446 1% shadowysuperstore, shadowysuperstore@shadowysuperstore.com - 7abbf3067536c6b70fbc8ac1965e485dce6ebb3d5c125aac248bc0fe906c6818
        #447 1% bhaskar,  - 5beb5d04939db36498e0736003771294317c1c018953d18433276a042bf9a39d
        #448 1% kylum 🟣,  - e651489d08a27970aac55b222b8a3ea5f3c00419f2976a3cf4006f3add2b6f3c
        #449 1% 特立独行的李员外, npub1wg2dsjnh0g7phheq23v288k0mj8x75fffmq7rghtkhv53027hnassf4w8t@nost.vip - 7214d84a777a3c1bdf205458a39ecfdc8e6f51294ec1e1a2ebb5d948bd5ebcfb
        #450 1% eynhaender, eynhaender@nostrplebs.com - a21babb54929f10164ca8f8fcca5138d25a892c32fabc8df7d732b8b52b68d82
        #451 1% 8340fd…8c7a30,  - 8340fd16fb4414765af8f59192ed68814920e7d33522709de2457490c28c7a30
        #452 1% B1ackSwan, b1ackswan@nostrplebs.com - 1f695a6883cef577dcebf9c60041111772a64e3490cb299c3b97fc81ad3901f4
        #453 1% 91dac4…599398,  - 91dac44e3f9d0e3b839aaf7fd81e6c19cf2ce02356fca5096af9e92f58599398
        #454 1% 356e99…fc3ba8,  - 356e99a0f75e973c0512873cbdce0385df39712653020af825556ceb4afc3ba8
        #455 1% mcdean,  - 54def063abe1657a22cc886eaba75f6636845c601efe9ad56709b4cb3dcc62f1
        #456 1% mrbitcoin, mrbitc0in@nostrplebs.com - da41332116804e9c4396f6dbb77ec9ad338197993e9d8af18f332e53dcc1bfeb
        #457 1% Jedi, jedi@nostrplebs.com - 246498aa79542482499086f9ab0134750a23047dad0cca38b696750f9ed8072c
        #458 1% CloudNull, cloudnull@nostrplebs.com - 5f53baca8cb88a18320a032957bf0b6f8dc8b33db007310b0e2f573edf2703a3
        #459 1% Mrwh0, Mrwh0@Mrwh0.github.io - d8dd77e3dff24bd8c2da9b4c4fb321f5f99e8713bad40dd748ab59656b5ed27d
        #460 1% shinohai, shinohai@iris.to - 4bc7982c4ee4078b2ada5340ae673f18d3b6a664b1f97e8d6799e6074cb5c39d
        #461 1% awoi, awoi@iris.to - edc083016d344679566ae8205b362530ecbafc6e064e224a0c2df1850cecfb4a
        #462 1% TheShopRat,  - 8362e77d9fd268720a15840af33fd9ab5cdf13fabc66f0910111580960cd297a
        #463 1% Dajjal,  - 614aee83d7eaffc7bc6bbf02feda0cc53e7f97eeceac08a897c4cea3c023b804
        #464 1% felipe,  - 0ee8894f1f663fd76b682c16e6a92db0fe14ada98db35b4a4cfa5f9068be0b3a
        #465 1% crypt0-j3sus,  - 9a7b7cbe37b2caa703062c51b207eb6ec4c42d06bfa909d979aa2d5005ac3d65
        #466 1% Just J, jcope101@nostrplebs.com - 5f6f376733b1a8682a0f330e07b6a6064d738fdd8159db6c8df44c6c9419ff88
        #467 1% mmasnick,  - 4d53de27a24feb84d6383962e350219fc09e572c22a17c542545a69cd35b067f
        #468 1% Murmur, murmur@nostrplebs.com - f7e84b92a5457546894daedaff9abd66f3d289f92435d6ac068a33cb170b01a4
        #469 1% JD,  - 1a9ba80629e2f8f77340ac13e67fdb4fcc66f4bb4124f9beff6a8c75e4ce29b0
        #470 1% dario, dario@nostrplebs.com - d9987652d3cbb2c0fa39b6305cc0f2d03ca987afc1e56bc97a81c79e138152a8
        #471 1% leonwankum, @leonawankum@BitcoinNostr.com  - 652d58acafa105af8475c0fe8029a52e7ddbc337b2bd9c98bb17a111dc4cde60
        #472 1% phil, phil@iris.to - 8352b55a828a60bb0e86b0ac9ef1928999ebe636c905dcbe0cd3c0f95c61b83b
        #473 1% hkmccullough, thatirdude@nostrplebs.com - 836059a05aeb8498dd53a0d422e04aced6b4b71eb3621d312626c46715d259d8
        #474 1% BitBox,  - 5a3de28ffd09d7506cff0a2672dbdb1f836307bcff0217cc144f48e19eea3fff
        #475 1% 5eff6c…60bd07,  - 5eff6c1205c9db582863978b5b2e9c9aa73a57e6c1df526fddc2b9996060bd07
        #476 1% nobody,  - 2e472c6d072c0bcc28f1b260e0fc309f1f919667d238f4e703f8f1db0f0eb424
        #477 1% K_hole, K_hole@ketamine.com - 5ac74532e23b7573f8f6f3248fe5174c0b7230aec0b653c0ec8f11d540209fd7
        #478 1% bitcoinIllustrated,  - 90fb6b9607bba40686fe70aad74a07e5af96d152778f3a09fcda5967dcb0daba
        #479 1% kingfisher,  - 33d4c61d7354e1d5872e26218eda73170646d12a8e7b9cb6d3069a7058ebabfd
        #480 1% cfc11e…b4f6e4,  - cfc11ef4b31e2ab18261a71b79097c60199f532605a0c3aa73ad36acc6b4f6e4
        #481 1% d06848…2f86b3,  - d06848a9ea53f9e9c15cafaf41b1729d6d7b84083cfbac2c76a0506dd72f86b3
        #482 1% nostrceo,  - 3159e1a148ca235cb55365a2ffde608b17e84c4c3bff6ed309f3e320307d5ab3
        #483 1% Lokuyow2, 2@lokuyow.github.io - f5f02030cb4b22ed15c3d7cc35ae616e6ce6bb3fa537f6e9e91aaa274b9cd716
        #484 1% fatushi,  - 49a458319060806221990e90e6bf2b1654201f08a40828d1a5d215a85f449df0
        #485 1% Omnia,  - 026d2251aa211684ef63e7a28e21c611c087bb3131a9c90b11dff6c16d68ce77
        #486 1% joey,  - 5f8a5bbf8d26104547a3942e82d7a5159554b3a5a3bc1275c47674b5e8c4c1d7
        #487 1% Hazey, hazey@iris.to - 800e0fe3d8638ce3f75a56ed865df9d96fc9d9cd2f75550df0d7f5c1d8468b0b
        #488 1% Milad Younis,  - 64c24e0991f9bb6f59f9da486ba29242bc562b09ce051882f7b3bcc7fd055227
        #489 1% jlgalley,  - 920535dd1487975ccc75ed82b7b4753260ec4041dcf9ce24657623164f6586e3
        #490 1% paulgallo28,  - 690af9eed15cc3a7439c39b228bf194da134f75d64f40114a41d77bff6a60699
        #491 1% HeineNon, HeineNon@tomottodx.github.io - 64c66c231ea1c25ebd66b14fe4a0b1b39a6928d6824ad43e035f54aa667bc650
        #492 1% a9b9ad…2b9f4c,  - a9b9ad000e2ada08326bbcc1836effcdfa4e64b9c937e406fe5912dc562b9f4c
        #493 1% legxxi,  - 8476d0dcdb53f1cc67efc8d33f40104394da2d33e61369a8a8ade288036977c6
        #494 1% 99f1b7…559c31,  - 99f1b7b39201d0e142f9ec3c8101b6be0eee8a389d16d53667ca4f57b1559c31
        #495 1% mbz,  - e5195850d4fed08183f0b274ca30777094daad67be235a5cd15548b9b0341031
        #496 1% Titan, titan@nostrplebs.com - 672b1637bd65b6206c7a603158c2ecee15599648e10dd15a82f2fcb4e47735bf
        #497 1% Highlandhodl,  - f0c74190cd05d85d843cdc5f355afe0fbac6d30d18da91243d6cae30a69713f7
        #498 1% CodeWarrior,  - 21a7014db2ba17acc8bbb9496645084866b46e1ba0062a80513afda405450183
        #499 1% baller.hodl,  - d8150dc0631f834a004f231f0747d5ec8409b1a9214d246f675dfef39807a224
        #500 1% Now Playing on GM₿,  - 9c6907de72e59daf5272103a34649bf7ca01050a68f402955520fc53dba9730d

        Inspector monitor

        New events inspected today: 720.71K (4.85GB)
        Average events inspected per second: 8.34
        Uptime: Server 99.93%, NostrInspector: 99.93%
        Spam estimate:
        74.12 %

        About the NostrInspector Report

        ✅ The 24 Hour NostrInspector Report is generated by listening for new events on the top relays using the Nostr Protocol. The statistics report that
        it generates includes de data layer as well as the social layer.
        💜 To support this free effort share, like, comment or zap.
        🫂 Thank you 🙏

        🕵️ @nostrin "The Nostr Inspector"
        npub17m7f7q08k4x746s2v45eyvwppck32dcahw7uj2mu5txuswldgqkqw9zms7
        """.trimIndent()

    @Test
    fun testUrlsToParse() {
        val state =
            RichTextParser()
                .parseText(textToParse, EmptyTagList, null)
        assertEquals(
            "relay.shitforce.one, relayable.org, universe.nostrich.land, nos.lol, universe.nostrich.land?lang=zh, universe.nostrich.land?lang=en, relay.damus.io, relay.nostr.wirednet.jp, offchain.pub, nostr.rocks, relay.wellorder.net, nostr.oxtr.dev, universe.nostrich.land?lang=ja, relay.mostr.pub, nostr.bitcoiner.social, Nostr-Check.com, MR.Rabbit, Ancap.su, miceliomad@miceliomad.github.io/nostr/, zapper.lol, smies.me, baller.hodl",
            state.urlSet.withoutScheme.joinToString(", "),
        )

        assertEquals(
            "jb55@jb55.com, Snowden@Nostr-Check.com, cameri@elder.nostr.land, natalie@NostrVerified.com, alanbwt@nostrplebs.com, rick@no.str.cr, shawn@shawnyeager.com, 0xtr@oxtr.dev, pavol@rusnak.io, caitlin@nostrverified.com, ralf@snort.social, stacksats@nostrplebs.com, MrHodl@nostrpurple.com, _@mikedilger.com, jascha@relayable.org, Nakadaimon@nostrplebs.com, KeithMukai@nostr.seedsigner.com, theguyswann@NostrVerified.com, dk@stacker.news, npub1z7eqn5603ltuxr77w70t3sasep8hyngzr6lxqpa9hfcqjwe9wmdqhw0qhv@nost.vip, miljan@primal.net, jared@nostrplebs.com, radii@orangepill.dev, _@katieannbaker.com, giacomozucco@BitcoinNostr.com, kr@stacker.news, phil@nostrpurple.com, angela@nostr.world, mason@lacosanostr.com, lau@nostr.report, damascusrex@iris.to, nym@nostr.fan, nico@nostrplebs.com, seekerdreamer1@stacker.news, thesamecat@iris.to, nitesh@noderunner.wtf, gpt3@jb55.com, byzantine@stacker.news, wealththeory@nostrplebs.com, gug@nostrplebs.com, lana@b.tc, shevacai@nostrplebs.com, joe@nostrpurple.com, simplestbitcoinbook@nostrplebs.com, knutsvanholm@iris.to, rs@zbd.ai, GRANTGILLIAM@grantgilliam.com, lifeloveliberty@iris.to, npub1s9c53smfq925qx6fgkqgw8as2e99l2hmj32gz0hjjhe8q67fxdvs3ga9je@nost.vip, arbedout@granddecentral.com, glowleaf@nostrplebs.com, modus@lacosanostr.com, anil@bitcoinnostr.com, documentingbtc@uselessshit.co, wolfbearclaw@nostr.messagepush.io, _@amboss.space, k3tan@k3tan.com, wolzie@BitcoinNostr.com, trey@nostrplebs.com, woody@fountain.fm, cosmicdimension@nostrplebs.com, mirbtc@getalby.com, marks@nostrplebs.com, barrensatin40@walletofsatoshi.com, alexemidio@alexemidio.github.io, Jenn@mintgreen.co, spacemonkey@nostrich.love, ishak@nostrplebs.com, GrassFedBitcoin@start9.com, ninoholds@nostrplebs.com, satcap@nostr.satcap.io, tpmoreira@nostrplebs.com, force2b@nostrplebs.com, hendrix@nostrplebs.com, TXMC@alphabetasoup.tv, pipleb@iris.to, reallhex@terranostr.com, nicb@nicb.me, NabismoPrime@BostonBTC.com, paco@iris.to, globalstatesmen@nostrplebs.com, _@NostrNet.work, crayonsmell@habel.net, ToxiKat27@Bitcoiner.social, jtrag@BitcoinNostr.com, joemartinmusic@nostrplebs.com, ph@nostrplebs.com, horse@iris.to, kp@no.str.cr, rebornbitcoiner@getalby.com, toshi@nostr-check.com, freeborn@nostrplebs.com, blee@bitcoiner.social, SatsTonight@BitcoinNostr.com, freeverification@Nostr-Check.com, cowmaster@getalby.com, hacker818@iris.to, amandabitcasa@nostrplebs.com, tiago@nostrplebs.com, sepehr@nostribe.com, gfy@stacker.news, cryptojournaal@iris.to, bon@nostrplebs.com, bot@binarywatch.org, moritz@getalby.com, hodlish@Nostr-Check.com, HolgerHatGarKeineNode@nip05.easify.de, joe@jaxo.github.io, hahattpro@iris.to, bensima@simatime.com, satan@nostrcheck.me, radvladdy@nostrplebs.com, yidneth@getalby.com, bellatrix@iris.to, securecoop@iris.to, charliesurf@ln.tips, bitcoinatm@Nostr-Check.com, lnstallone@allmysats.com, L0laL33tz@cashu.me, Lommy@nostrplebs.com, jgmontoya@nostrplebs.com, bavarianledger@iris.to, operator@brb.io, tiotito@nostriches.net, javi@www.javiergonzalez.io, geekigai@nostrplebs.com, Mr.Rabbit@BitcoinNostr.com, kilicl@nostr-check.com, alexbit@nostrbr.online, william@nostrplebs.com, oneezra@nostrplebs.com, iceandfirebtc@nostrplebs.com, nostrgang@nostrplebs.com, npub1vez5zekuzc3qk989q5gtly2zg9k2gz4l3wuplv5xs8y3se09yussg4vp7p@carteclip.com, Sqvaznyak@uselessshit.co, braj@nostrplebs.com, libertus@getalby.com, ZoeBoudreault@id.nostrfy.me, dnilso@iris.to, shroom@nostrplebs.com, olegaba@olegaba.com, wasabi@nostrplebs.com, artur@getalby.com, ihsanmd@getalby.com, satoshee@vida.page, ancapsu@getalby.com, niceaction@www.niceaction.com, seak@nostrplebs.com, andy@nodeless.io, pinkyjay@nostrplebs.com, criptobastardo@nostrplebs.com, lacosanostr@lacosanostr.com, teejem@nostrplebs.com, mewj@elder.nostr.land, thetonewrecker@nostrplebs.com, yodatravels@iris.to, bitcoin69@iris.to, Zzar@nostrplebs.com, vidalbidi@getalby.com, juangalt@current.ninja, dean@nostrplebs.com, alex2@nostrverified.com, trooper@iris.to, satscoinsv@getalby.com, aarbtc@iris.to, _@gue.yogsite.com, nostrmemes@iris.to, btcpavao@iris.to, Anonymous@BitcoinNostr.com, zoltanab@iris.to, katsu@onsats.org, bryan@nonni.io, pedromvpg@pedromvpg.com, sonicstudio@getalby.com, kounsellor@nostrplebs.com, katieross@nostrplebs.com, iamlj@iris.to, Zach@BitcoinNostr.com, amouranth@nostrcheck.me, hss5qy@getalby.com, dpcpw@iris.to, bitcoinfinity@nostrplebs.com, TonySeries@BitcoinNostr.com, kuobano@nostrplebs.com, kitakripto@BitcoinNostr.com, _@localhost.re, alxc@uselessshit.co, kukryr@orangepill.dev, saidah@nostrplebs.com, sharon21m@nostr.fan, dmn@noderunners.org, nela_at_nostrica2023@Nostr-Check.com, xbologg@nanostr.deno.dev, btcurenas@nostr.fan, amaluenda@getalby.com, queenb@nostrplebs.com, ezekiel@Nostr-Check.com, marakesh@getalby.com, storm@reddirtmining.io, _b_o_n_e_s_@stacker.news, bdichdbd@stacker.news, j9@nostrplebs.com, nokyctranslate@iris.to, Neomobius_at_mstdn.jp@mostr.pub, paddepadde@getcurrent.io, val@nostrplebs.com, dishwasher_iot@wlvs.space, 1@justinrezvani.com, sshh@nostrplebs.com, mk05@iris.to, herald@bitcoin-herald.org, nostr@pos.btcpayserver.it, thumpgofast@NostrVerified.com, neo@elder.nostr.land, alchemist@electronalchemy.com, timp@iris.to, ken@BitcoinNostr.com, irebus@nostr.red, stim4444@no.str.cr, layerlnw@nostr.fan, enhickman@enhickman.net, LiveFreeBTC@livefreebtc.org, aptx4869@aptx4869.app, khalil@klouche.com, nsec@ittybitty.tips, bigfish@iris.to, oldschool@iris.to, danton@nostrplebs.com, bitcoinzavior@nostrplebs.com, BitcoinSermons@BitcoinNostr.com, uknwlinux@plebs.place, satoshism@nostrplebs.com, jon@nostrplebs.com, hobozakki@nostrplebs.com, sirgalahodl@satstream.me, victor@lnmarkets.com, jacksmies@iris.to, chemaclass@snort.social, jayson@tautic.com, jesterhodl@jesterhodl.com, Bitcoin_Gamer_21@bitcoin-21.org, water-bot@gourcetools.github.io, w3crypto@iris.to, makaveli@nostrplebs.com, jamieanders@ln.tips, cball@nostrplebs.com, laura@nostrich.zone, kaz@reddirtmining.io, verismus@nostrplebs.com, bitpetro@nostrplebs.com, nossence@nossence.xyz, manbearpig@nostrplebs.com, johnsmith@nostrplebs.com, bitcoin@bitcoinedu.com, knggolf@nostrplebs.com, nighthaven@iris.to, xbt_fi@iris.to, gencarlosq17@iris.to, rotciv@plebs.place, Merlin@bitcoinnostr.com, yeg0rpetrov@iris.to, baloo@nostrpurple.com, jamesgospodyn@nostr.theorangepillapp.com, carl@armadalabs.studio, btcportal@nostrplebs.com, mjb@nostrplebs.com, esbewolkt@nostr.fan, koukos@iris.to, davekrock@NostrVerified.com, Bitcoinlovelife@BitcoinNostr.com, xolagl2@getalby.com, remoney@nostrplebs.com, roberto@bitcoiner.chat, cryptolazyninja@stacker.news, kimymt@getalby.com, benderlogic@rogue.earth, MAESTRO@BitcoinNostr.com, travis@west.report, coffeelover@nostrplebs.com, shadowysuperstore@shadowysuperstore.com, npub1wg2dsjnh0g7phheq23v288k0mj8x75fffmq7rghtkhv53027hnassf4w8t@nost.vip, eynhaender@nostrplebs.com, b1ackswan@nostrplebs.com, mrbitc0in@nostrplebs.com, jedi@nostrplebs.com, cloudnull@nostrplebs.com, Mrwh0@Mrwh0.github.io, shinohai@iris.to, awoi@iris.to, jcope101@nostrplebs.com, murmur@nostrplebs.com, dario@nostrplebs.com, leonawankum@BitcoinNostr.com, phil@iris.to, thatirdude@nostrplebs.com, K_hole@ketamine.com, 2@lokuyow.github.io, hazey@iris.to, HeineNon@tomottodx.github.io, titan@nostrplebs.com",
            state.urlSet.emails.joinToString(", "),
        )

        assertEquals(
            "",
            state.urlSet.withScheme.joinToString(", "),
        )
    }

    @Test
    fun testTextToParse() {
        val state =
            RichTextParser()
                .parseText(textToParse, EmptyTagList, null)

        // printStateForDebug(state)

        val expectedResult =
            listOf(
                "RegularText(📰 24 Hour NostrInspector Report 🕵 (TEXT ONLY VERSION))",
                "RegularText()",
                "RegularText(Generated Friday June 30 2023 03:59:01 UTC-6 (CST))",
                "RegularText()",
                "RegularText(Network statistics)",
                "RegularText()",
                "RegularText(New events witnessed (top 110 relays))",
                "RegularText()",
                "RegularText(Kind, count, (% count), size, (% size))",
                "RegularText(1, 207.9K, (28.8%), 458.02MB, (9.2%))",
                "RegularText(7, 158.3K, (22%), 280.83MB, (5.7%))",
                "RegularText(0, 84.1K, (11.7%), 192.89MB, (3.9%))",
                "RegularText(9735, 57.2K, (7.9%), 353.16MB, (7.1%))",
                "RegularText(3, 54.7K, (7.6%), 2.75GB, (56.7%))",
                "RegularText(6, 31.6K, (4.4%), 111.27MB, (2.2%))",
                "RegularText(4, 30.8K, (4.3%), 89.79MB, (1.8%))",
                "RegularText(30000, 29.1K, (4%), 115.33MB, (2.3%))",
                "RegularText(30078, 12.1K, (1.7%), 317.25MB, (6.4%))",
                "RegularText(5, 11K, (1.5%), 16.86MB, (0.3%))",
                "RegularText(10002, 8.6K, (1.2%), 16.59MB, (0.3%))",
                "RegularText(1311, 7.7K, (1.1%), 12.71MB, (0.3%))",
                "RegularText(1984, 6.3K, (0.9%), 10.93MB, (0.2%))",
                "RegularText(9734, 3.7K, (0.5%), 10.88MB, (0.2%))",
                "RegularText(30001, 3.1K, (0.4%), 66.91MB, (1.3%))",
                "RegularText(1000, 2.8K, (0.4%), 13.43MB, (0.3%))",
                "RegularText(20100, 1.4K, (0.2%), 2.32MB, (0%))",
                "RegularText(42, 1.1K, (0.2%), 2.30MB, (0%))",
                "RegularText(13194, 1K, (0.1%), 1.22MB, (0%))",
                "RegularText(1063, 875, (0.1%), 1.96MB, (0%))",
                "RegularText()",
                "RegularText(New events by relay (top 50%))",
                "RegularText()",
                "RegularText(Events (%) Relay)",
                "RegularText(33.4K)",
                "RegularText((4.6%))",
                "SchemelessUrl(relay.shitforce.one)",
                "RegularText(32.9K)",
                "RegularText((4.6%))",
                "SchemelessUrl(relayable.org)",
                "RegularText(26.6K)",
                "RegularText((3.7%))",
                "SchemelessUrl(universe.nostrich.land)",
                "RegularText(22.8K)",
                "RegularText((3.2%))",
                "SchemelessUrl(nos.lol)",
                "RegularText(22.7K)",
                "RegularText((3.1%))",
                "SchemelessUrl(universe.nostrich.land?lang=zh)",
                "RegularText(22.5K)",
                "RegularText((3.1%))",
                "SchemelessUrl(universe.nostrich.land?lang=en)",
                "RegularText(21.2K)",
                "RegularText((2.9%))",
                "SchemelessUrl(relay.damus.io)",
                "RegularText(20.6K)",
                "RegularText((2.9%))",
                "SchemelessUrl(relay.nostr.wirednet.jp)",
                "RegularText(20.1K)",
                "RegularText((2.8%))",
                "SchemelessUrl(offchain.pub)",
                "RegularText(19.9K)",
                "RegularText((2.8%))",
                "SchemelessUrl(nostr.rocks)",
                "RegularText(19.5K)",
                "RegularText((2.7%))",
                "SchemelessUrl(relay.wellorder.net)",
                "RegularText(19.4K)",
                "RegularText((2.7%))",
                "SchemelessUrl(nostr.oxtr.dev)",
                "RegularText(19K)",
                "RegularText((2.6%))",
                "SchemelessUrl(universe.nostrich.land?lang=ja)",
                "RegularText(18.4K)",
                "RegularText((2.6%))",
                "SchemelessUrl(relay.mostr.pub)",
                "RegularText(17.5K)",
                "RegularText((2.4%))",
                "SchemelessUrl(universe.nostrich.land?lang=zh)",
                "RegularText(16.3K)",
                "RegularText((2.3%))",
                "SchemelessUrl(nostr.bitcoiner.social)",
                "RegularText()",
                "RegularText(30 day global new events)",
                "RegularText()",
                "RegularText(23-05-29 1M)",
                "RegularText(23-05-30 861.9K)",
                "RegularText(23-05-31 752.5K)",
                "RegularText(23-06-01 0.9M)",
                "RegularText(23-06-02 808.9K)",
                "RegularText(23-06-03 683.8K)",
                "RegularText(23-06-04 0.9M)",
                "RegularText(23-06-05 890.6K)",
                "RegularText(23-06-06 839.4K)",
                "RegularText(23-06-07 827K)",
                "RegularText(23-06-08 804.8K)",
                "RegularText(23-06-09 736.7K)",
                "RegularText(23-06-10 709.7K)",
                "RegularText(23-06-11 772.2K)",
                "RegularText(23-06-12 882K)",
                "RegularText(23-06-13 794.9K)",
                "RegularText(23-06-14 842.2K)",
                "RegularText(23-06-15 812.1K)",
                "RegularText(23-06-16 839.6K)",
                "RegularText(23-06-17 730.2K)",
                "RegularText(23-06-18 811.9K)",
                "RegularText(23-06-19 721.9K)",
                "RegularText(23-06-20 786.2K)",
                "RegularText(23-06-21 756.6K)",
                "RegularText(23-06-22 736K)",
                "RegularText(23-06-23 723.5K)",
                "RegularText(23-06-24 703.9K)",
                "RegularText(23-06-25 734.9K)",
                "RegularText(23-06-26 742.4K)",
                "RegularText(23-06-27 707.8K)",
                "RegularText(23-06-28 747.7K)",
                "RegularText()",
                "RegularText(Social Network Statistics)",
                "RegularText()",
                "RegularText(Top 30 hashtags found today)",
                "RegularText()",
                "HashTag(#hashtag,)",
                "RegularText(mentions)",
                "RegularText(today,)",
                "RegularText(days)",
                "RegularText(in)",
                "RegularText(top)",
                "RegularText(30)",
                "RegularText()",
                "HashTag(#bitcoin,)",
                "RegularText(1.7K,)",
                "RegularText(109)",
                "HashTag(#concussion,)",
                "RegularText(1.1K,)",
                "RegularText(25)",
                "HashTag(#press,)",
                "RegularText(0.9K,)",
                "RegularText(65)",
                "HashTag(#france,)",
                "RegularText(492,)",
                "RegularText(46)",
                "HashTag(#presse,)",
                "RegularText(480,)",
                "RegularText(42)",
                "HashTag(#covid19,)",
                "RegularText(465,)",
                "RegularText(65)",
                "HashTag(#nostr,)",
                "RegularText(414,)",
                "RegularText(109)",
                "HashTag(#zapathon,)",
                "RegularText(386,)",
                "RegularText(76)",
                "HashTag(#rssfeed,)",
                "RegularText(309,)",
                "RegularText(53)",
                "HashTag(#btc,)",
                "RegularText(299,)",
                "RegularText(109)",
                "HashTag(#news,)",
                "RegularText(294,)",
                "RegularText(91)",
                "HashTag(#zap,)",
                "RegularText(283,)",
                "RegularText(109)",
                "HashTag(#linux,)",
                "RegularText(253,)",
                "RegularText(88)",
                "HashTag(#respond,)",
                "RegularText(246,)",
                "RegularText(90)",
                "HashTag(#kompost,)",
                "RegularText(240,)",
                "RegularText(31)",
                "HashTag(#plebchain,)",
                "RegularText(236,)",
                "RegularText(109)",
                "HashTag(#gardenaward,)",
                "RegularText(236,)",
                "RegularText(31)",
                "HashTag(#start,)",
                "RegularText(236,)",
                "RegularText(31)",
                "HashTag(#unicef,)",
                "RegularText(233,)",
                "RegularText(32)",
                "HashTag(#coronavirus,)",
                "RegularText(233,)",
                "RegularText(33)",
                "HashTag(#bew,)",
                "RegularText(229,)",
                "RegularText(31)",
                "HashTag(#balkon,)",
                "RegularText(229,)",
                "RegularText(31)",
                "HashTag(#terrasse,)",
                "RegularText(229,)",
                "RegularText(31)",
                "HashTag(#braininjuryawareness,)",
                "RegularText(229,)",
                "RegularText(24)",
                "HashTag(#garten,)",
                "RegularText(220,)",
                "RegularText(21)",
                "HashTag(#smart,)",
                "RegularText(220,)",
                "RegularText(21)",
                "HashTag(#nsfw,)",
                "RegularText(211,)",
                "RegularText(85)",
                "HashTag(#protoncalendar,)",
                "RegularText(206,)",
                "RegularText(31)",
                "HashTag(#stacksats,)",
                "RegularText(195,)",
                "RegularText(99)",
                "HashTag(#nokyc,)",
                "RegularText(179,)",
                "RegularText(98)",
                "RegularText()",
                "RegularText(Emoji sentiment today)",
                "RegularText()",
                "RegularText(⚡ (1.6K) 👉 (1.4K) 🇪🇺 (1.2K) 🫂 (1.2K) 🇺🇸 (1.1K) 💜 (875) 🧠 (858) 😂 (830) " +
                    "🔥 (690) 🤣 (566) 🤙 (525) ☕ (444) 👇 (443) 🙌🏻 (425) ☀ (307) 😎 (305) 🥳 (301) " +
                    "🤔 (276) 🌻 (270) 🧡 (270) 🥇 (269) 🗓 (269) 🙏 (268) 🏆 (267) 🌱 (264) 📰 (230)" +
                    " 🏉 (221) 😭 (220) 💰 (219) 🔗 (209) 👀 (201) 😅 (199) ✨ (193) 🇷🇺 (182) 💪 (167)" +
                    " ✅ (164) 💤 (163) 🐶 (151) 🇨🇭 (141) 📝 (137) 😁 (136) 🌞 (136) 🐾 (136) ❤ (132) " +
                    "💻 (126) 🚀 (125) 👍 (125) 🇧🇷 (125) 😊 (121) 📚 (120) ➡ (120) 👏 (118) 🎉 (117) " +
                    "🎮 (115) 🤷 (113) 👋 (112) 💃 (108) 🕺🏻 (106) 💡 (104) 🚨 (99) 😆 (97) 💯 (95) ⚠ (92) " +
                    "📢 (92) 🤗 (89) 😴 (87) 🔁 (83) 🐰 (81) 😀 (79) 🎟 (78) ⛏ (78) 🐦 (76) 💸 (76) " +
                    "✌🏻 (75) 🤍 (73) 🇬🇧 (73) 🌽 (70) 🤡 (69) 🤮 (69) ❗ (66) 🤝 (65) 😉 (65) 🙇 (65) " +
                    "🍻 (64) 🌍 (64) 💕 (63) 🌸 (62) 💬 (61) ☺ (61) 🇦🇷 (59) 🇮🇩 (57) 😳 (57) 😄 (57) " +
                    "🎶 (57) 🥷🏻 (56) 🎵 (56) 😃 (56) 🔍 (55) 💥 (55) 🎲 (54) ✍ (54) 🕒 (53) ⬇ (53) " +
                    "💙 (51) 🔒 (50) 📈 (50) 🪙 (50) 🌧 (50) 🥰 (50) 🕸 (50) 🌐 (50) 💭 (49) 🌙 (49) " +
                    "😍 (49) 📱 (48) 🌟 (48) 🤩 (48) 💔 (47) 🔌 (47) 😋 (47) 🎖 (47) 🍣 (46) 📷 (46) " +
                    "💼 (45) ⭐ (45) 🥔 (45) 🥺 (45) 👌 (44) 👷🏼 (43) 😱 (43) 📅 (43) 🤖 (43) 📸 (42) " +
                    "📊 (42) 🦑 (40) 💵 (40) 🤦 (39) ❣ (38) 💎 (38) 🖤 (38) 📺 (37) 🇵🇱 (37) 🇯🇵 (36) " +
                    "🔧 (36) 🤘 (36) 💖 (36) ‼ (35) 😢 (35) 😺 (34) 🔊 (34) 😏 (34) 🇸🇰 (34) 🏃 (34) " +
                    "👩‍👧 (34) ⏰ (33) 👨‍💻 (33) 👑 (33) 👥 (32) 🖥 (32) 💨 (32) 💗 (31) 🇲🇽 (31) 📖 (31) " +
                    "🚫 (31) 👊🏻 (31) 😡 (31) 🌎 (31) 👁 (30) 🗞 (30) 🍀 (30) 🍽 (29) 🐸 (29) 🥚 (29) " +
                    "💩 (29) ✊🏾 (29) 😮 (29) 🌡 (29) 🙃 (28) 🔔 (28) 🇻🇪 (28) 💦 (28) 🎯 (28) 🎨 (28) " +
                    "🍛 (28) 🖼 (27) ☝🏻 (27) 🛑 (27) 🙄 (27) 🧑🏻‍🤝‍🧑🏽 (27) 🌈 (27) 🥂 (26) 🇫🇮 (26) 🎥 (26) " +
                    "😬 (26) 🥲 (25) 🦾 (24) 🤜 (24) 🙂 (24) 🖕 (24) 😩 (24))",
                "RegularText()",
                "RegularText(Zap economy)",
                "RegularText()",
                "RegularText(⚡41.7M sats (₿0.417))",
                "RegularText(1,816 zappers & 920 zapped (unique pubkeys))",
                "RegularText(🌩️ 33,248 zaps, 1,253 sats per zap (avg))",
                "RegularText()",
                "RegularText(Most followed)",
                "RegularText()",
                "HashTag(#1)",
                "RegularText(30%)",
                "RegularText(jb55,)",
                "Email(jb55@jb55.com)",
                "RegularText(-)",
                "RegularText(32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245)",
                "HashTag(#2)",
                "RegularText(19%)",
                "RegularText(Snowden,)",
                "Email(Snowden@Nostr-Check.com)",
                "RegularText(-)",
                "RegularText(84dee6e676e5bb67b4ad4e042cf70cbd8681155db535942fcc6a0533858a7240)",
                "HashTag(#3)",
                "RegularText(18%)",
                "RegularText(cameri,)",
                "Email(cameri@elder.nostr.land)",
                "RegularText(-)",
                "RegularText(00000000827ffaa94bfea288c3dfce4422c794fbb96625b6b31e9049f729d700)",
                "HashTag(#4)",
                "RegularText(11%)",
                "RegularText(Natalie,)",
                "Email(natalie@NostrVerified.com)",
                "RegularText(-)",
                "RegularText(edcd20558f17d99327d841e4582f9b006331ac4010806efa020ef0d40078e6da)",
                "HashTag(#5)",
                "RegularText(11%)",
                "RegularText(saifedean,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(4379e76bfa76a80b8db9ea759211d90bb3e67b2202f8880cc4f5ffe2065061ad)",
                "HashTag(#6)",
                "RegularText(11%)",
                "RegularText(alanbwt,)",
                "Email(alanbwt@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(1bd32a386a7be6f688b3dc7c480efc21cd946b43eac14ba4ba7834ac77a23e69)",
                "HashTag(#7)",
                "RegularText(10%)",
                "RegularText(rick,)",
                "Email(rick@no.str.cr)",
                "RegularText(-)",
                "RegularText(978c8f26ea9b3c58bfd4c8ddfde83741a6c2496fab72774109fe46819ca49708)",
                "HashTag(#8)",
                "RegularText(9%)",
                "RegularText(shawn,)",
                "Email(shawn@shawnyeager.com)",
                "RegularText(-)",
                "RegularText(c7eda660a6bc8270530e82b4a7712acdea2e31dc0a56f8dc955ac009efd97c86)",
                "HashTag(#9)",
                "RegularText(9%)",
                "RegularText(0xtr,)",
                "Email(0xtr@oxtr.dev)",
                "RegularText(-)",
                "RegularText(b2d670de53b27691c0c3400225b65c35a26d06093bcc41f48ffc71e0907f9d4a)",
                "HashTag(#10)",
                "RegularText(9%)",
                "RegularText(stick,)",
                "Email(pavol@rusnak.io)",
                "RegularText(-)",
                "RegularText(d7f0e3917c466f1e2233e9624fbd6d4bd1392dbcfcaf3574f457569d496cb731)",
                "HashTag(#11)",
                "RegularText(9%)",
                "RegularText(caitlinlong,)",
                "Email(caitlin@nostrverified.com)",
                "RegularText(-)",
                "RegularText(e1055729d51e037b3c14e8c56e2c79c22183385d94aadb32e5dc88092cd0fef4)",
                "HashTag(#12)",
                "RegularText(9%)",
                "RegularText(ralf,)",
                "Email(ralf@snort.social)",
                "RegularText(-)",
                "RegularText(c89cf36deea286da912d4145f7140c73495d77e2cfedfb652158daa7c771f2f8)",
                "HashTag(#13)",
                "RegularText(9%)",
                "RegularText(StackSats,)",
                "Email(stacksats@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(b93049a6e2547a36a7692d90e4baa809012526175546a17337454def9ab69d30)",
                "HashTag(#14)",
                "RegularText(9%)",
                "RegularText(MrHodl,)",
                "Email(MrHodl@nostrpurple.com)",
                "RegularText(-)",
                "RegularText(29fbc05acee671fb579182ca33b0e41b455bb1f9564b90a3d8f2f39dee3f2779)",
                "HashTag(#15)",
                "RegularText(9%)",
                "RegularText(mikedilger,)",
                "Email(_@mikedilger.com)",
                "RegularText(-)",
                "RegularText(ee11a5dff40c19a555f41fe42b48f00e618c91225622ae37b6c2bb67b76c4e49)",
                "HashTag(#16)",
                "RegularText(9%)",
                "RegularText(jascha,)",
                "Email(jascha@relayable.org)",
                "RegularText(-)",
                "RegularText(2479739594ed5802a96703e5a870b515d986982474a71feae180e8ecffa302c6)",
                "HashTag(#17)",
                "RegularText(8%)",
                "RegularText(Nakadaimon,)",
                "Email(Nakadaimon@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(803a613997a26e8714116f99aa1f98e8589cb6116e1aaa1fc9c389984fcd9bb8)",
                "HashTag(#18)",
                "RegularText(8%)",
                "RegularText(KeithMukai,)",
                "Email(KeithMukai@nostr.seedsigner.com)",
                "RegularText(-)",
                "RegularText(5b0e8da6fdfba663038690b37d216d8345a623cc33e111afd0f738ed7792bc54)",
                "HashTag(#19)",
                "RegularText(8%)",
                "RegularText(TheGuySwann,)",
                "Email(theguyswann@NostrVerified.com)",
                "RegularText(-)",
                "RegularText(b0b8fbd9578ac23e782d97a32b7b3a72cda0760761359bd65661d42752b4090a)",
                "HashTag(#20)",
                "RegularText(8%)",
                "RegularText(dk,)",
                "Email(dk@stacker.news)",
                "RegularText(-)",
                "RegularText(b708f7392f588406212c3882e7b3bc0d9b08d62f95fa170d099127ece2770e5e)",
                "HashTag(#21)",
                "RegularText(7%)",
                "RegularText(zerohedge,)",
                "Email(npub1z7eqn5603ltuxr77w70t3sasep8hyngzr6lxqpa9hfcqjwe9wmdqhw0qhv@nost.vip)",
                "RegularText(-)",
                "RegularText(17b209d34f8fd7c30fde779eb8c3b0c84f724d021ebe6007a5ba70093b2576da)",
                "HashTag(#22)",
                "RegularText(7%)",
                "RegularText(miljan,)",
                "Email(miljan@primal.net)",
                "RegularText(-)",
                "RegularText(d61f3bc5b3eb4400efdae6169a5c17cabf3246b514361de939ce4a1a0da6ef4a)",
                "HashTag(#23)",
                "RegularText(7%)",
                "RegularText(jared,)",
                "Email(jared@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(92e3aac668edb25319edd1d87cadef0b189557fdd13b123d82a19d67fd211909)",
                "HashTag(#24)",
                "RegularText(7%)",
                "RegularText(radii,)",
                "Email(radii@orangepill.dev)",
                "RegularText(-)",
                "RegularText(acedd3597025cb13b84f9a89643645aeb61a3b4a3af8d7ac01f8553171bf17c5)",
                "HashTag(#25)",
                "RegularText(7%)",
                "RegularText(katie,)",
                "Email(_@katieannbaker.com)",
                "RegularText(-)",
                "RegularText(07eced8b63b883cedbd8520bdb3303bf9c2b37c2c7921ca5c59f64e0f79ad2a6)",
                "HashTag(#26)",
                "RegularText(7%)",
                "RegularText(giacomozucco,)",
                "Email(giacomozucco@BitcoinNostr.com)",
                "RegularText(-)",
                "RegularText(ef151c7a380f40a75d7d1493ac347b6777a9d9b5fa0aa3cddb47fc78fab69a8b)",
                "HashTag(#27)",
                "RegularText(7%)",
                "RegularText(kr,)",
                "Email(kr@stacker.news)",
                "RegularText(-)",
                "RegularText(08b80da85ba68ac031885ea555ab42bb42231fde9b690bbd0f48c128dfbf8009)",
                "HashTag(#28)",
                "RegularText(7%)",
                "RegularText(phil,)",
                "Email(phil@nostrpurple.com)",
                "RegularText(-)",
                "RegularText(e07773a92a610a28da20748fdd98bfb5af694b0cad085224801265594a98108a)",
                "HashTag(#29)",
                "RegularText(7%)",
                "RegularText(angela,)",
                "Email(angela@nostr.world)",
                "RegularText(-)",
                "RegularText(2b1964b885de3fcbb33777874d06b05c254fecd561511622ce86e3d1851949fa)",
                "HashTag(#30)",
                "RegularText(7%)",
                "RegularText(mason)",
                "RegularText(𓄀)",
                "RegularText(𓅦,)",
                "Email(mason@lacosanostr.com)",
                "RegularText(-)",
                "RegularText(5ef92421b5df0ed97df6c1a98fc038ea7962a29e7f33a060f7a8ddeb9ee587e9)",
                "HashTag(#31)",
                "RegularText(7%)",
                "RegularText(Lau,)",
                "Email(lau@nostr.report)",
                "RegularText(-)",
                "RegularText(5a9c48c8f4782351135dd89c5d8930feb59cb70652ffd37d9167bf922f2d1069)",
                "HashTag(#32)",
                "RegularText(7%)",
                "RegularText(Rex)",
                "RegularText(Damascus)",
                "RegularText(,)",
                "Email(damascusrex@iris.to)",
                "RegularText(-)",
                "RegularText(50c5c98ccc31ca9f1ef56a547afc4cb48195fe5603d4f7874a221db965867c8e)",
                "HashTag(#33)",
                "RegularText(6%)",
                "RegularText(nym,)",
                "Email(nym@nostr.fan)",
                "RegularText(-)",
                "RegularText(9936a53def39d712f886ac7e2ed509ce223b534834dd29d95caba9f6bc01ef35)",
                "HashTag(#34)",
                "RegularText(6%)",
                "RegularText(nico,)",
                "Email(nico@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(0000000033f569c7069cdec575ca000591a31831ebb68de20ed9fb783e3fc287)",
                "HashTag(#35)",
                "RegularText(6%)",
                "RegularText(anna,)",
                "Email(seekerdreamer1@stacker.news)",
                "RegularText(-)",
                "RegularText(6f2347c6fc4cbcc26d66e74247abadd4151592277b3048331f52aa3a5c244af9)",
                "HashTag(#36)",
                "RegularText(6%)",
                "RegularText(TheSameCat,)",
                "Email(thesamecat@iris.to)",
                "RegularText(-)",
                "RegularText(72f9755501e1a4464f7277d86120f67e7f7ec3a84ef6813cc7606bf5e0870ff3)",
                "HashTag(#37)",
                "RegularText(6%)",
                "RegularText(nitesh_btc,)",
                "Email(nitesh@noderunner.wtf)",
                "RegularText(-)",
                "RegularText(021d7ef7aafc034a8fefba4de07622d78fd369df1e5f9dd7d41dc2cffa74ae02)",
                "HashTag(#38)",
                "RegularText(6%)",
                "RegularText(gpt3,)",
                "Email(gpt3@jb55.com)",
                "RegularText(-)",
                "RegularText(5c10ed0678805156d39ef1ef6d46110fe1e7e590ae04986ccf48ba1299cb53e2)",
                "HashTag(#39)",
                "RegularText(6%)",
                "RegularText(Byzantine,)",
                "Email(byzantine@stacker.news)",
                "RegularText(-)",
                "RegularText(5d1d83de3ee5edde157071d5091a6d03ead8cce1d46bc585a9642abdd0db5aa0)",
                "HashTag(#40)",
                "RegularText(6%)",
                "RegularText(wealththeory,)",
                "Email(wealththeory@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(3004d45a0ab6352c61a62586a57c50f11591416c29db1143367a4f0623b491ca)",
                "HashTag(#41)",
                "RegularText(6%)",
                "RegularText(IshBit,)",
                "Email(gug@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(8e27ffb5c9bb8cdd0131ade6efa49d56d401b5424d9fdf9a63e074d527b0715c)",
                "HashTag(#42)",
                "RegularText(5%)",
                "RegularText(Lana,)",
                "Email(lana@b.tc)",
                "RegularText(-)",
                "RegularText(e8795f9f4821f63116572ed4998924c6f0e01682945bf7a3d9d6132f1c7dace7)",
                "HashTag(#43)",
                "RegularText(5%)",
                "RegularText(Shevacai,)",
                "Email(shevacai@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(2f175fe4348f4da2da157e84d119b5165c84559158e64729ff00b16394718bbf)",
                "HashTag(#44)",
                "RegularText(5%)",
                "RegularText(joe,)",
                "Email(joe@nostrpurple.com)",
                "RegularText(-)",
                "RegularText(907a5a23635ea02be052c31f465b1982aefb756710ccc9f628aa31b70d2e262e)",
                "HashTag(#45)",
                "RegularText(5%)",
                "RegularText(SimplestBitcoinBook,)",
                "Email(simplestbitcoinbook@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(6867d899ce6b677b89052602cfe04a165f26bb6a1a6390355f497f9ee5cb0796)",
                "HashTag(#46)",
                "RegularText(5%)",
                "RegularText(knutsvanholm,)",
                "Email(knutsvanholm@iris.to)",
                "RegularText(-)",
                "RegularText(92cbe5861cfc5213dd89f0a6f6084486f85e6f03cfeb70a13f455938116433b8)",
                "HashTag(#47)",
                "RegularText(5%)",
                "RegularText(rajwinder,)",
                "Email(rs@zbd.ai)",
                "RegularText(-)",
                "RegularText(1c9d368fc24e8549ce2d95eba63cb34b82b363f3036d90c12e5f13afe2981fba)",
                "HashTag(#48)",
                "RegularText(5%)",
                "RegularText(Vlad,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(50054d07e2cdf32b1035777bd9cf73992a4ae22f91c14a762efdaa5bf61f4755)",
                "HashTag(#49)",
                "RegularText(5%)",
                "RegularText(GRANTGILLIAM,)",
                "Email(GRANTGILLIAM@grantgilliam.com)",
                "RegularText(-)",
                "RegularText(874db6d2db7b39035fe7aac19e83a48257915e37d4f2a55cb4ca66be2d77aa88)",
                "HashTag(#50)",
                "RegularText(5%)",
                "RegularText(LifeLoveLiberty,)",
                "Email(lifeloveliberty@iris.to)",
                "RegularText(-)",
                "RegularText(c07a2ea48b6753d11ad29d622925cb48bab48a8f38e954e85aec46953a0752a2)",
                "HashTag(#51)",
                "RegularText(5%)",
                "RegularText(hackernews,)",
                "Email(npub1s9c53smfq925qx6fgkqgw8as2e99l2hmj32gz0hjjhe8q67fxdvs3ga9je@nost.vip)",
                "RegularText(-)",
                "RegularText(817148c3690155401b494580871fb0564a5faafb9454813ef295f2706bc93359)",
                "HashTag(#52)",
                "RegularText(5%)",
                "RegularText(arbedout,)",
                "Email(arbedout@granddecentral.com)",
                "RegularText(-)",
                "RegularText(a67e98faf32f2520ae574d84262534e7b94625ce0d4e14a50c97e362c06b770e)",
                "HashTag(#53)",
                "RegularText(5%)",
                "RegularText(nobody,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(5f735049528d831f544b49a585e6f058c1655dfaed9fc338374cd4f3a5a06bf7)",
                "HashTag(#54)",
                "RegularText(5%)",
                "RegularText(glowleaf,)",
                "Email(glowleaf@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(34c0a53283bacd5cb6c45f9b057bea05dfb276333dcf14e9b167680b5d3638e4)",
                "HashTag(#55)",
                "RegularText(5%)",
                "RegularText(Modus,)",
                "Email(modus@lacosanostr.com)",
                "RegularText(-)",
                "RegularText(547fcc5c7e655fe7c83da5a812e6332f0a4779c87bf540d8e75a4edbbf36fe4a)",
                "HashTag(#56)",
                "RegularText(5%)",
                "RegularText(Melvin)",
                "RegularText(Carvalho)",
                "RegularText(Old)",
                "RegularText(Key)",
                "RegularText(DO)",
                "RegularText(NOT)",
                "RegularText(USE,)",
                "RegularText(USE)",
                "Bech(npub1melv683fw6n2mvhl5h6dhqd8mqfv3wmxnz4qph83ua4dk4006ezsrt5c24,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(ed1d0e1f743a7d19aa2dfb0162df73bacdbc699f67cc55bb91a98c35f7deac69)",
                "HashTag(#57)",
                "RegularText(5%)",
                "RegularText(anil,)",
                "Email(anil@bitcoinnostr.com)",
                "RegularText(-)",
                "RegularText(ade7a0c6acca095c5b36f88f20163bccda4d97b071c4acc8fe329dc724eec8fb)",
                "HashTag(#58)",
                "RegularText(4%)",
                "RegularText(DocumentingBTC,)",
                "Email(documentingbtc@uselessshit.co)",
                "RegularText(-)",
                "RegularText(641ac8fea1478c27839fb7a0850676c2873c22aa70c6216996862c98861b7e2f)",
                "HashTag(#59)",
                "RegularText(4%)",
                "RegularText(wolfbearclaw,)",
                "Email(wolfbearclaw@nostr.messagepush.io)",
                "RegularText(-)",
                "RegularText(0b963191ab21680a63307aedb50fd7b01392c9c6bef79cd0ceb6748afc5e7ffd)",
                "HashTag(#60)",
                "RegularText(4%)",
                "RegularText(Amboss,)",
                "Email(_@amboss.space)",
                "RegularText(-)",
                "RegularText(2af01e0d6bd1b9fbb9e3d43157d64590fb27dcfbcabe28784a5832e17befb87b)",
                "HashTag(#61)",
                "RegularText(4%)",
                "RegularText(k3tan,)",
                "Email(k3tan@k3tan.com)",
                "RegularText(-)",
                "RegularText(599c4f2380b0c1a9a18b7257e107cf9e6d8b4f8dea06c18c84538d311ff2b28c)",
                "HashTag(#62)",
                "RegularText(4%)",
                "RegularText(wolzie)",
                "RegularText(,)",
                "Email(wolzie@BitcoinNostr.com)",
                "RegularText(-)",
                "RegularText(aabedc1f237853aeeb22bd985556036f262f8507842d64f3ecce01adbd7207e2)",
                "HashTag(#63)",
                "RegularText(4%)",
                "RegularText(trey,)",
                "Email(trey@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(d5415a313d38461ff93a8c170f941b2cd4a66a5cfdbb093406960f6cb317849f)",
                "HashTag(#64)",
                "RegularText(4%)",
                "RegularText(sillystev,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(d541ef2e4830f2e1543c8bdc40128ceceb062b08c7e3f53d141552d5f5bc0cfc)",
                "HashTag(#65)",
                "RegularText(4%)",
                "RegularText(sovereignmox,)",
                "Email(woody@fountain.fm)",
                "RegularText(-)",
                "RegularText(1c4123b2431c60be030d641b4b68300eb464415405035b199428c0913b879c0c)",
                "HashTag(#66)",
                "RegularText(4%)",
                "RegularText(CosmicDimension,)",
                "Email(cosmicdimension@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(4afec6c875e81dc28a760cc828345c0c5b61ec464ba20224148f9fd854a868ff)",
                "HashTag(#67)",
                "RegularText(4%)",
                "RegularText(Mir,)",
                "Email(mirbtc@getalby.com)",
                "RegularText(-)",
                "RegularText(234c45ff85a31c19bf7108a747fa7be9cd4af95c7d621e07080ca2d663bb47d2)",
                "HashTag(#68)",
                "RegularText(4%)",
                "RegularText(Tacozilla,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(5f70f80ddcf4f6a022467bd5196a1fdfc53d59f1e735a90443e7f7c980564c88)",
                "HashTag(#69)",
                "RegularText(4%)",
                "RegularText(marks,)",
                "Email(marks@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(8ea485266b2285463b13bf835907161c22bb3da1e652b443db14f9cee6720a43)",
                "HashTag(#70)",
                "RegularText(4%)",
                "RegularText(blacktomcat,)",
                "Email(barrensatin40@walletofsatoshi.com)",
                "RegularText(-)",
                "RegularText(16b7e4b067cba8c86bda96a8d932e7593f398118d24bd8060da39ccfd7315f5c)",
                "HashTag(#71)",
                "RegularText(4%)",
                "RegularText(Alex)",
                "RegularText(Emidio,)",
                "Email(alexemidio@alexemidio.github.io)",
                "RegularText(-)",
                "RegularText(4ba8e86d2d97896dc9337c3e500691893d7317572fd81f8b41ddda5d89d32de4)",
                "HashTag(#72)",
                "RegularText(4%)",
                "RegularText(Jenn,)",
                "Email(Jenn@mintgreen.co)",
                "RegularText(-)",
                "RegularText(e0f59d89047b868a188c5efd6b93dd8c16b65643b8718884dad8542386c60ddd)",
                "HashTag(#73)",
                "RegularText(4%)",
                "RegularText(spacemonkey,)",
                "Email(spacemonkey@nostrich.love)",
                "RegularText(-)",
                "RegularText(23b26fea28700cd1e2e3a8acca5c445c37ab89acaad549a36d50e9c0eb0f5806)",
                "HashTag(#74)",
                "RegularText(4%)",
                "RegularText(ishak,)",
                "Email(ishak@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(052466631c6c0aed84171f83ef3c95cb81848d4dcdc1d1ee9dfdf75b850c1cb4)",
                "HashTag(#75)",
                "RegularText(4%)",
                "RegularText(nakamoto_army,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(62f6c5ff12fd24251f0bfb3b7eb1e512d7f1f577a1a97a595db01c66b52ad04f)",
                "HashTag(#76)",
                "RegularText(4%)",
                "RegularText(GrassFedBitcoin,)",
                "Email(GrassFedBitcoin@start9.com)",
                "RegularText(-)",
                "RegularText(74ffc51cc30150cf79b6cb316d3a15cf332ab29a38fec9eb484ab1551d6d1856)",
                "HashTag(#77)",
                "RegularText(4%)",
                "RegularText(NinoHodls,)",
                "Email(ninoholds@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(43ccdbcb1e4dff7e3dea2a91b851ca0e22f50e3c560364a12b64b8c6587924f0)",
                "HashTag(#78)",
                "RegularText(4%)",
                "RegularText(satcap,)",
                "Email(satcap@nostr.satcap.io)",
                "RegularText(-)",
                "RegularText(11dfaa43ae0faa0a06d8c67f89759214c58b60a021521627bc76cb2d3ad0b2e8)",
                "HashTag(#79)",
                "RegularText(4%)",
                "RegularText(DuneMessias,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(96a578f6b504646de141ba90bec5651965aa01df0605928b3785a1372504e93d)",
                "HashTag(#80)",
                "RegularText(4%)",
                "RegularText(Idaeus,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(eb473e8fd55ced7af32abaf89578647ddba75e38a860b1c41682bbfb774f5579)",
                "HashTag(#81)",
                "RegularText(4%)",
                "RegularText(tpmoreira,)",
                "Email(tpmoreira@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(f514ef7d18da12ecfce55c964add719ce00a1392c187f20ccb57d99290720e03)",
                "HashTag(#82)",
                "RegularText(4%)",
                "RegularText(force2B,)",
                "Email(force2b@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(d411848a42a11ad2747c439b00fc881120a4121e04917d38bebd156212e2f4ad)",
                "HashTag(#83)",
                "RegularText(4%)",
                "RegularText(Hendrix,)",
                "Email(hendrix@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(cbd92008e1fe949072cbea02e54228140c43d14d14519108b1d7a32d9102665b)",
                "HashTag(#84)",
                "RegularText(4%)",
                "RegularText(TXMC,)",
                "Email(TXMC@alphabetasoup.tv)",
                "RegularText(-)",
                "RegularText(37359e92ece5c6fc8d5755de008ceb6270808b814ddd517d38ebeab269836c96)",
                "HashTag(#85)",
                "RegularText(4%)",
                "RegularText(norman188,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(662a4476a9c15a5778f379ce41ceb2841ac72dfa1829b492d67796a8443ac2ca)",
                "HashTag(#86)",
                "RegularText(4%)",
                "RegularText(pipleb,)",
                "Email(pipleb@iris.to)",
                "RegularText(-)",
                "RegularText(3c4280ef3b792fa919b1964460d34ca6af93b83fa55f633a3b0eb8fde556235a)",
                "HashTag(#87)",
                "RegularText(4%)",
                "RegularText(reallhex,)",
                "Email(reallhex@terranostr.com)",
                "RegularText(-)",
                "RegularText(29630aed66aeec73b6519a11547f40ca15c3f6aa79907e640f1efcf5a2ee9dc8)",
                "HashTag(#88)",
                "RegularText(4%)",
                "RegularText(374324…ef9f78,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(3743244390be53473a7e3b3b8d04dce83f6c9514b81a997fb3b123c072ef9f78)",
                "HashTag(#89)",
                "RegularText(4%)",
                "RegularText(Nostradamus,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(7acce9b3da22ceedc511a15cb730c898235ab551623955314b003e9f33e8b10c)",
                "HashTag(#90)",
                "RegularText(4%)",
                "RegularText(Nic₿,)",
                "Email(nicb@nicb.me)",
                "RegularText(-)",
                "RegularText(000000002d4f4733f1ee417a405637fd0d81dbfbc6dbd8c0d1c95f04ec3db973)",
                "HashTag(#91)",
                "RegularText(4%)",
                "RegularText(NabismoPrime,)",
                "Email(NabismoPrime@BostonBTC.com)",
                "RegularText(-)",
                "RegularText(4503baa127bdfd0b054384dc5ba82cb0e2a8367cbdb0629179f00db1a34caacc)",
                "HashTag(#92)",
                "RegularText(4%)",
                "RegularText(paco,)",
                "Email(paco@iris.to)",
                "RegularText(-)",
                "RegularText(66bd8fed3590f2299ef0128f58d67879289e6a99a660e83ead94feab7606fd17)",
                "HashTag(#93)",
                "RegularText(3%)",
                "RegularText(globalstatesmen,)",
                "Email(globalstatesmen@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(237506ca399e5b1b9ce89455fe960bc98dfab6a71936772a89c5145720b681f4)",
                "HashTag(#94)",
                "RegularText(3%)",
                "RegularText(Nostryfied,)",
                "Email(_@NostrNet.work)",
                "RegularText(-)",
                "RegularText(c2c20ec0a555959713ca4c404c4d2cc80e6cb906f5b64217070612a0cae29c62)",
                "HashTag(#95)",
                "RegularText(3%)",
                "RegularText(crayonsmell,)",
                "Email(crayonsmell@habel.net)",
                "RegularText(-)",
                "RegularText(3ef3be9db1e3f268f84e937ad73c68772a58c6ffcec1d42feeef5f214ad1eaf9)",
                "HashTag(#96)",
                "RegularText(3%)",
                "RegularText(Toxikat27,)",
                "Email(ToxiKat27@Bitcoiner.social)",
                "RegularText(-)",
                "RegularText(12cfc2ec5a39a39d02f921f77e701dbc175b6287f22ddf0247af39706967f1d9)",
                "HashTag(#97)",
                "RegularText(3%)",
                "RegularText(James)",
                "RegularText(Trageser,)",
                "Email(jtrag@BitcoinNostr.com)",
                "RegularText(-)",
                "RegularText(d29bc58353389481e302569835661c95838bee076137533eb365bca752c38316)",
                "HashTag(#98)",
                "RegularText(3%)",
                "RegularText(Joe)",
                "RegularText(Martin)",
                "RegularText(Music,)",
                "Email(joemartinmusic@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(28ca019b78b494c25a9da2d645975a8501c7e99b11302e5cbe748ee593fcb2cc)",
                "HashTag(#99)",
                "RegularText(3%)",
                "RegularText(Fundamentals,)",
                "Email(ph@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(5677fa5b6b1cb6d5bee785d088a904cd08082552bf75df3e4302cea015a5d3e1)",
                "HashTag(#100)",
                "RegularText(3%)",
                "RegularText(bb,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(1f254ae909a36b0000c3b68f36b92aad168f4532725d7cd9b67f5b09088f2125)",
                "HashTag(#101)",
                "RegularText(3%)",
                "RegularText(李子柒,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(c70c8e55e0228c3ce171ae0d357452e386489f3a2d14e6deca174c2fbfc8da52)",
                "HashTag(#102)",
                "RegularText(3%)",
                "RegularText(Horse)",
                "RegularText(🐴,)",
                "Email(horse@iris.to)",
                "RegularText(-)",
                "RegularText(e4d3420c0b77926cfbf107f9cb606238efaf5524af39ff1c86e6d6fdd1515a57)",
                "HashTag(#103)",
                "RegularText(3%)",
                "RegularText(KP,)",
                "Email(kp@no.str.cr)",
                "RegularText(-)",
                "RegularText(b2e777c827e20215e905ab90b6d81d5b84be5bf66c944ce34943540b462ea362)",
                "HashTag(#104)",
                "RegularText(3%)",
                "RegularText(Azarakhsh,)",
                "Email(rebornbitcoiner@getalby.com)",
                "RegularText(-)",
                "RegularText(c734992a115c2ad9b4df40dd7c14d153695b29081a995df39b4fc8e6f1dcfb14)",
                "HashTag(#105)",
                "RegularText(3%)",
                "RegularText(Toshi,)",
                "Email(toshi@nostr-check.com)",
                "RegularText(-)",
                "RegularText(79d434176b64745d2793cf307f20967e27912994f6e81632de18da3106c2cbb4)",
                "HashTag(#106)",
                "RegularText(3%)",
                "RegularText(FreeBorn,)",
                "Email(freeborn@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(408e04e9a5b02ef6d82edb9ecb2cca1d5a3121cb26b0ca5e6511800a0269b069)",
                "HashTag(#107)",
                "RegularText(3%)",
                "RegularText(blee,)",
                "Email(blee@bitcoiner.social)",
                "RegularText(-)",
                "RegularText(69a0a0910b49a1dbfbc4e4f10df22b5806af5403a228267638f2e908c968228d)",
                "HashTag(#108)",
                "RegularText(3%)",
                "RegularText(SatsTonight,)",
                "Email(SatsTonight@BitcoinNostr.com)",
                "RegularText(-)",
                "RegularText(eb3b94533dafeb8ebd58a4947a3dce11d83a9931c622bdf30a4257d3347ee1bf)",
                "HashTag(#109)",
                "RegularText(3%)",
                "SchemelessUrl(Nostr-Check.com)",
                "RegularText(,)",
                "Email(freeverification@Nostr-Check.com)",
                "RegularText(-)",
                "RegularText(ddfbb06a722e51933cd37e4ecdb30b1864f262f9bb5bd6c2d95cbeefc728f096)",
                "HashTag(#110)",
                "RegularText(3%)",
                "RegularText(cowmaster,)",
                "Email(cowmaster@getalby.com)",
                "RegularText(-)",
                "RegularText(6af9411d742c74611e149d19037e7a2ba4d44bbceb429b209c451902b6740bb8)",
                "HashTag(#111)",
                "RegularText(3%)",
                "RegularText(Hacker,)",
                "Email(hacker818@iris.to)",
                "RegularText(-)",
                "RegularText(40e10350fed534e5226b73761925030134d9f85306ee1db5cfbd663118034e84)",
                "HashTag(#112)",
                "RegularText(3%)",
                "RegularText(BitcasaHomes,)",
                "Email(amandabitcasa@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(f96a2a2552c08f99c30b9e2441d64ca4c6b3d761735e7cd74580bafe549326e0)",
                "HashTag(#113)",
                "RegularText(3%)",
                "RegularText(footstr,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(aa1aa6af6be3a2903e2fb18690d7df128a10eec0f3a015157daf371c688b4cff)",
                "HashTag(#114)",
                "RegularText(3%)",
                "RegularText(tiago,)",
                "Email(tiago@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(780ab38a843423c61502550474b016e006f2b56f2f7d18e9cd02737e11113262)",
                "HashTag(#115)",
                "RegularText(3%)",
                "RegularText(Sepehr,)",
                "Email(sepehr@nostribe.com)",
                "RegularText(-)",
                "RegularText(3e294d2fd339bb16a5403a86e3664947dd408c4d87a0066524f8a573ae53ca8e)",
                "HashTag(#116)",
                "RegularText(3%)",
                "RegularText(dhruv,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(297bc16357b314be291c893755b25d66999c1525bbf3537fbc637a0c767f14bb)",
                "HashTag(#117)",
                "RegularText(3%)",
                "RegularText(b310ed…4f793a,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(b310ed0a54a71ccf8a8368032dd3b4b83b7aca2840bb10a4d5e6ef4b6a4f793a)",
                "HashTag(#118)",
                "RegularText(3%)",
                "RegularText(MichZ)",
                "RegularText(🧘🏻‍♀️,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(9349d012686caab46f6bfefd2f4c361c52e14b1cde1cd027476e0ae6d3e98946)",
                "HashTag(#119)",
                "RegularText(3%)",
                "RegularText(gfy,)",
                "Email(gfy@stacker.news)",
                "RegularText(-)",
                "RegularText(01e4fc2adc0ff7a0465d3e70b3267d375ebe4292828fa3888f972313f3a1248e)",
                "HashTag(#120)",
                "RegularText(3%)",
                "RegularText(Dude,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(67cbb3d83800cc1af6f5d2821f1c911f033ea21e1269ff2ad613ab3ae099b1f3)",
                "HashTag(#121)",
                "RegularText(3%)",
                "RegularText(HODL_MFER,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(7c6a9e6231570a6773e608d1c0a709acb9c21193a5c2df9cebfa9e9db09411a3)",
                "HashTag(#122)",
                "RegularText(3%)",
                "RegularText(renatarodrigues,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(aa116590cf23dc761a8a9e38ff224a3d07db45c66be3035b9f87144bda0eeaa5)",
                "HashTag(#123)",
                "RegularText(3%)",
                "RegularText(CryptoJournaal,)",
                "Email(cryptojournaal@iris.to)",
                "RegularText(-)",
                "RegularText(fb649213b88e9927a5c8f470d7affe88441de995deaccf283bf60a78f771b825)",
                "HashTag(#124)",
                "RegularText(3%)",
                "RegularText(Bon,)",
                "Email(bon@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(b2722dd1e13ff9b82ff2f432186019045fee39911d5652d6b4263562061af908)",
                "HashTag(#125)",
                "RegularText(3%)",
                "RegularText(binarywatch,)",
                "Email(bot@binarywatch.org)",
                "RegularText(-)",
                "RegularText(0095c837e8ed370de6505c2c631551af08c110853b519055d0cdf3d981da5ac3)",
                "HashTag(#126)",
                "RegularText(3%)",
                "RegularText(Moritz,)",
                "Email(moritz@getalby.com)",
                "RegularText(-)",
                "RegularText(0521db9531096dff700dcf410b01db47ab6598de7e5ef2c5a2bd7e1160315bf6)",
                "HashTag(#127)",
                "RegularText(3%)",
                "RegularText(hodlish,)",
                "Email(hodlish@Nostr-Check.com)",
                "RegularText(-)",
                "RegularText(3575a3a7a6b5236443d6af03606aa9297c3177a45cf5314b9fd57bff894ee3ae)",
                "HashTag(#128)",
                "RegularText(3%)",
                "RegularText(HolgerHatGarKeineNode,)",
                "Email(HolgerHatGarKeineNode@nip05.easify.de)",
                "RegularText(-)",
                "RegularText(0adf67475ccc5ca456fd3022e46f5d526eb0af6284bf85494c0dd7847f3e5033)",
                "HashTag(#129)",
                "RegularText(3%)",
                "RegularText(joe,)",
                "Email(joe@jaxo.github.io)",
                "RegularText(-)",
                "RegularText(6827ef2b75ee652dcc83958b83aea0bc6580705b56041a9ee70a4178e1046cdb)",
                "HashTag(#130)",
                "RegularText(3%)",
                "RegularText(hahattpro,)",
                "Email(hahattpro@iris.to)",
                "RegularText(-)",
                "RegularText(53ac90ebaef84b0439cdf4f1d955ff1f1e98febc04fb789eff4a08fe53316483)",
                "HashTag(#131)",
                "RegularText(3%)",
                "RegularText(bensima,)",
                "Email(bensima@simatime.com)",
                "RegularText(-)",
                "RegularText(2fa4b9ba71b6dab17c4723745bb7850dfdafcb6ae1a8642f76f9c64fa5f43436)",
                "HashTag(#132)",
                "RegularText(3%)",
                "RegularText(satan,)",
                "Email(satan@nostrcheck.me)",
                "RegularText(-)",
                "RegularText(d6b44ef322f6d67806ff06aaa9623b22ff5c2b0f0705c5e7a5a35684af9e5101)",
                "HashTag(#133)",
                "RegularText(3%)",
                "RegularText(RadVladdy,)",
                "Email(radvladdy@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(7933ea1abdb329139b4eb37157649229b41d0ae445907238b07926182f717924)",
                "HashTag(#134)",
                "RegularText(3%)",
                "RegularText(horacio,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(f61abb9886e1f4cd5d20419c197d5d7f3649addab24b6a32a2367124ca3194b4)",
                "HashTag(#135)",
                "RegularText(3%)",
                "RegularText(yidneth,)",
                "Email(yidneth@getalby.com)",
                "RegularText(-)",
                "RegularText(f28be20326c6779b2f8bfa75a865d0fa4af384e9c6c99dc6a803e542f9d2085e)",
                "HashTag(#136)",
                "RegularText(3%)",
                "RegularText(JonO,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(edecf91d15e03c921806ae6ebff86771c79e1641e899787e4d7689f68314d447)",
                "HashTag(#137)",
                "RegularText(3%)",
                "RegularText(bellatrix,)",
                "Email(bellatrix@iris.to)",
                "RegularText(-)",
                "RegularText(f9d7f0b271b5bb19ed400d8baeee1c22ac3a5be5cf20da55219c4929e523987a)",
                "HashTag(#138)",
                "RegularText(3%)",
                "RegularText(SecureCoop,)",
                "Email(securecoop@iris.to)",
                "RegularText(-)",
                "RegularText(d244e3cd0842d514a0725e0e0a00b712b7f2ed515a1d7ef362fd12c957b95549)",
                "HashTag(#139)",
                "RegularText(3%)",
                "RegularText(charliesurf,)",
                "Email(charliesurf@ln.tips)",
                "RegularText(-)",
                "RegularText(a396e36e962a991dac21731dd45da2ee3fd9265d65f9839c15847294ec991f1c)",
                "HashTag(#140)",
                "RegularText(3%)",
                "RegularText(Bitcoin)",
                "RegularText(ATM,)",
                "Email(bitcoinatm@Nostr-Check.com)",
                "RegularText(-)",
                "RegularText(01a69fa5a7cbb4a185904bdc7cae6137ff353889bba95619c619debe9e3b8b09)",
                "HashTag(#141)",
                "RegularText(3%)",
                "RegularText(lnstallone,)",
                "Email(lnstallone@allmysats.com)",
                "RegularText(-)",
                "RegularText(84fe3febc748470ff1a363db8a375ffa1ff86603f2653d1c3c311ad0a70b5d0c)",
                "HashTag(#142)",
                "RegularText(3%)",
                "RegularText(a652f6…9124f3,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(a652f66df4ddb5280ff466b6ff444fbc310b8e83238660473d5ccffa9e9124f3)",
                "HashTag(#143)",
                "RegularText(3%)",
                "RegularText(hmichellerose,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(5b29255d5eaaaeb577552bf0d11030376f477d19a009c5f5a80ddc73d49359f6)",
                "HashTag(#144)",
                "RegularText(3%)",
                "RegularText(L0la)",
                "RegularText(L33tz,)",
                "Email(L0laL33tz@cashu.me)",
                "RegularText(-)",
                "RegularText(d8a6ecf0c396eaa8f79a4497fe9b77dc977633451f3ca5c634e208659116647b)",
                "HashTag(#145)",
                "RegularText(3%)",
                "RegularText(Lommy,)",
                "Email(Lommy@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(014b9837dabb358fc0f416ceb58f72c4e6ed8fc6d317f0578dd704fc879f16f8)",
                "HashTag(#146)",
                "RegularText(3%)",
                "RegularText(jgmontoya,)",
                "Email(jgmontoya@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(9236f9ac521be2ee0a54f1cfffdf2df7f4982df4e6eb992867d733debcf95b35)",
                "HashTag(#147)",
                "RegularText(3%)",
                "RegularText(bavarianledger,)",
                "Email(bavarianledger@iris.to)",
                "RegularText(-)",
                "RegularText(f27c20bc6e64407f805a92c3190089060f9d85efa67ccc80b85f007c3323c221)",
                "HashTag(#148)",
                "RegularText(3%)",
                "RegularText(operator,)",
                "Email(operator@brb.io)",
                "RegularText(-)",
                "RegularText(3c1ba7d42c873c2f89caf1ca79b4ead6513385de53743fa6eb98c3705655695c)",
                "HashTag(#149)",
                "RegularText(3%)",
                "RegularText(awaremoma,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(44313b79dfc3303e3bd0c4aee0c872e96a84f23a2a45624b3ab630f24f43012f)",
                "HashTag(#150)",
                "RegularText(3%)",
                "RegularText(Tío)",
                "RegularText(Tito,)",
                "Email(tiotito@nostriches.net)",
                "RegularText(-)",
                "RegularText(dc6e531596c52a218a6fae2e1ea359a1365d5eda02ec176c945ed06a9400ec72)",
                "HashTag(#151)",
                "RegularText(3%)",
                "RegularText(javi,)",
                "Email(javi@www.javiergonzalez.io)",
                "RegularText(-)",
                "RegularText(2eab634b27a78107c98599a982849b4f71c605316c8f4994861f83dc565df5c8)",
                "HashTag(#152)",
                "RegularText(3%)",
                "RegularText(NathanCPerry,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(cec9808bbb00bc9c3eab4c2f23e9440a5ea775201b65a18462bc77080e39e336)",
                "HashTag(#153)",
                "RegularText(3%)",
                "RegularText(Jason)",
                "RegularText(Hodlers)",
                "RegularText(♾️/2099999997690000🏴,)",
                "Email(geekigai@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(d162a53c3b0bfb5c3ebd787d7b08feab206b112362eca25aa291251cd70fe225)",
                "HashTag(#154)",
                "RegularText(3%)",
                "SchemelessUrl(MR.Rabbit)",
                "RegularText(,)",
                "Email(Mr.Rabbit@BitcoinNostr.com)",
                "RegularText(-)",
                "RegularText(42af69b2384071f31e55cb2d368c8a3351c8f2da03207e1fb6885991ac2522bf)",
                "HashTag(#155)",
                "RegularText(3%)",
                "RegularText(kilicl,)",
                "Email(kilicl@nostr-check.com)",
                "RegularText(-)",
                "RegularText(48a94f890f4dc3625b9926cdccded61e353ad1fe76600bc6acea44bdb9efceb7)",
                "HashTag(#156)",
                "RegularText(3%)",
                "RegularText(retired,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(82ba83731adcfe5a65ced992fde81efc756d10670c56a58cb8870210f859d3c1)",
                "HashTag(#157)",
                "RegularText(3%)",
                "RegularText(Alex)",
                "RegularText(Bit,)",
                "Email(alexbit@nostrbr.online)",
                "RegularText(-)",
                "RegularText(9db334a465cc3f6107ed847eec0bc6c835e76ba50625f4c1900cbcb9df808d91)",
                "HashTag(#158)",
                "RegularText(3%)",
                "RegularText(freeeedom21,)",
                "Email(william@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(fd254541619b6d4baa467412058321f70cf108d773adcda69083bd500e502033)",
                "HashTag(#159)",
                "RegularText(3%)",
                "RegularText(OneEzra,)",
                "Email(oneezra@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(0078d4cb1652552475ba61ec439cd50c37c3a3a439853d830d7c9d338826ade2)",
                "HashTag(#160)",
                "RegularText(3%)",
                "RegularText(lightsats,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(88185e27e96cfcfc3c58c625cf70c4dba757f8d2e9ab7cab80f5012a343eb7d2)",
                "HashTag(#161)",
                "RegularText(3%)",
                "RegularText(IceAndFireBTC,)",
                "Email(iceandfirebtc@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(edb50fd8286e36878f8dd9346c138598052e5d914f0c3c6072f12eb152f307d8)",
                "HashTag(#162)",
                "RegularText(3%)",
                "RegularText(Nostr)",
                "RegularText(Gang,)",
                "Email(nostrgang@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(91aeab23b5664edaa57dbe00b041ccb50544f89d7d956345bbd78b7dbaa48660)",
                "HashTag(#163)",
                "RegularText(3%)",
                "RegularText(kexkey,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(436456869bdd7fcb3aaaa91bed05173ea1510879004250b9f69b2c4370d58cf7)",
                "HashTag(#164)",
                "RegularText(3%)",
                "RegularText(freebitcoin,)",
                "Email(npub1vez5zekuzc3qk989q5gtly2zg9k2gz4l3wuplv5xs8y3se09yussg4vp7p@carteclip.com)",
                "RegularText(-)",
                "RegularText(66454166dc16220b14e50510bf9142416ca40abf8bb81fb28681c91865e52721)",
                "HashTag(#165)",
                "RegularText(3%)",
                "RegularText(Sqvaznyak,)",
                "Email(Sqvaznyak@uselessshit.co)",
                "RegularText(-)",
                "RegularText(056d6999f3283778d50aa85c25985716857cfeaffdbad92e73cf8aeaf394a5cd)",
                "HashTag(#166)",
                "RegularText(3%)",
                "RegularText(koba,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(b5926366f9ac01d8ed427c9bb4cdcb86b7b4a44aaad00d262ef436621e30ea5a)",
                "HashTag(#167)",
                "RegularText(3%)",
                "RegularText(braj,)",
                "Email(braj@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(5921b801183f10b0143c2e48c22c8192fa38d27ac614a20251cac30ab729d3a5)",
                "HashTag(#168)",
                "RegularText(3%)",
                "RegularText(Libertus,)",
                "Email(libertus@getalby.com)",
                "RegularText(-)",
                "RegularText(2154d20dace7b28018621edf9c3a56ab842b901db0d9b02616dbed3d15fc5490)",
                "HashTag(#169)",
                "RegularText(3%)",
                "RegularText(ZoeBoudreault,)",
                "Email(ZoeBoudreault@id.nostrfy.me)",
                "RegularText(-)",
                "RegularText(3c43dc2a4c996832ae3a1830250d5f0917476783132969db4e14955b6e394047)",
                "HashTag(#170)",
                "RegularText(3%)",
                "RegularText(Saiga,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(8f5f3a60edc875315d9c1348d6ad5dddbca806d02400049632589cb32b3f0493)",
                "HashTag(#171)",
                "RegularText(3%)",
                "RegularText(n,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(aceff8abf70a60d7b378469ab80513c83c5d70a4f82872bac7bd619acbc71ff1)",
                "HashTag(#172)",
                "RegularText(3%)",
                "RegularText(dnilso,)",
                "Email(dnilso@iris.to)",
                "RegularText(-)",
                "RegularText(5ae325f930f53fad2a1a9ebefdb943bba1bef7b411e7712d2173bf3c38a49b17)",
                "HashTag(#173)",
                "RegularText(3%)",
                "RegularText(Shroom,)",
                "Email(shroom@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(a4ee688a599c9493b8641cc61987ef42b7556ba1e79d35bca92a1dce186dac85)",
                "HashTag(#174)",
                "RegularText(3%)",
                "RegularText(0a92e7…bc2d3d,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(0a92e765595bbf3368c44338479df5351cf5b0028215ba95e1c9e8de99bc2d3d)",
                "HashTag(#175)",
                "RegularText(3%)",
                "RegularText(olegaba,)",
                "Email(olegaba@olegaba.com)",
                "RegularText(-)",
                "RegularText(7fb2a29bd1a41d9a8ca43a19a7dcf3a8522f1bc09b4086253539190e9c29c51a)",
                "HashTag(#176)",
                "RegularText(3%)",
                "RegularText(CJButcher,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(15fdc4596019e2b9b702ae229d5c7a17d9527226f8cf5526006908901612b200)",
                "HashTag(#177)",
                "RegularText(3%)",
                "RegularText(wasabi-pea,)",
                "Email(wasabi@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(abe1c8a87aca21e9b6a32a8c2fae5acbaf3212a01d9ccc13a80981c853e8fa02)",
                "HashTag(#178)",
                "RegularText(3%)",
                "RegularText(045a6f…f32334,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(045a6fa0da5d278ac1c3aee79df23b7372ea03ee4da04ad4b8db9a5967f32334)",
                "HashTag(#179)",
                "RegularText(3%)",
                "RegularText(Artur,)",
                "Email(artur@getalby.com)",
                "RegularText(-)",
                "RegularText(762a3c15c6fa90911bf13d50fc3a29f1663dc1f04b4397a89eef604f622ecd60)",
                "HashTag(#180)",
                "RegularText(3%)",
                "RegularText(ihsanmd💀,)",
                "Email(ihsanmd@getalby.com)",
                "RegularText(-)",
                "RegularText(d030bd233a1347e510c372b1878e00204b228072814361451623707896435da9)",
                "HashTag(#181)",
                "RegularText(2%)",
                "RegularText(Satoshee,)",
                "Email(satoshee@vida.page)",
                "RegularText(-)",
                "RegularText(0e88aac7368d5f2582437826042b3fb3a26a126f3d857618c6b6652a9f5bfa0a)",
                "HashTag(#182)",
                "RegularText(2%)",
                "RegularText(39ed0a…60271a,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(39ed0aea2338477103e0b5a820532ded27dbfe4f203e7270392d55f63e60271a)",
                "HashTag(#183)",
                "RegularText(2%)",
                "SchemelessUrl(Ancap.su)",
                "RegularText(,)",
                "Email(ancapsu@getalby.com)",
                "RegularText(-)",
                "RegularText(2fe5292a2df25047a392fceead75458875c775c31cc28f4be04cef3e8db15291)",
                "HashTag(#184)",
                "RegularText(2%)",
                "RegularText(NiceAction,)",
                "Email(niceaction@www.niceaction.com)",
                "RegularText(-)",
                "RegularText(32891ace6802507077035ba6064f7e1db29667002165b9bf5c1c9b3f84e2303c)",
                "HashTag(#185)",
                "RegularText(2%)",
                "RegularText(seak,)",
                "Email(seak@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(d70f1bca430a2158f0e4c88b158ae18efffe8a91d436edbeee27acf2d9012cf5)",
                "HashTag(#186)",
                "RegularText(2%)",
                "RegularText(twochickshomestead,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(5bf5ab367f45b01b1cac72d73703fb30c704f3dbd5d376396fc0b6f39cac456b)",
                "HashTag(#187)",
                "RegularText(2%)",
                "RegularText(Andy,)",
                "Email(andy@nodeless.io)",
                "RegularText(-)",
                "RegularText(08cd52a46ab37a9894b3333785c2ff50e068d1b01fb03d702608da83e9817d82)",
                "HashTag(#188)",
                "RegularText(2%)",
                "RegularText(coinbitstwitterfollows,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(1341010418f272ed6db469d77dffdf1d946dd0701e33bdc84bb72269cef5bfed)",
                "HashTag(#189)",
                "RegularText(2%)",
                "RegularText(Annonymal,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(5c7794d47115a1b133a19673d57346ca494d367379458d8e98bf24a498abc46b)",
                "HashTag(#190)",
                "RegularText(2%)",
                "RegularText(lindsey,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(f81d7cbdfe99ff2b11932fb4cdcd94f18e629e3fedafcd25ee0a4ddc0967f0f9)",
                "HashTag(#191)",
                "RegularText(2%)",
                "RegularText(pinkyjay,)",
                "Email(pinkyjay@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(b0dbac368a5ac474bc19ab11a0b3fd4260cf56b40c60944c4a331b8ad8ced926)",
                "HashTag(#192)",
                "RegularText(2%)",
                "RegularText(criptobastardo,)",
                "Email(criptobastardo@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(311262ac14efb7011f23223b662aa1f18b3bb7c238206cb1c07424f051a11cce)",
                "HashTag(#193)",
                "RegularText(2%)",
                "RegularText(lacosanostr,)",
                "Email(lacosanostr@lacosanostr.com)",
                "RegularText(-)",
                "RegularText(6ce2001e7f070fade19d4817006747e4164089886a0faca950a6b0ab2a3b58b2)",
                "HashTag(#194)",
                "RegularText(2%)",
                "RegularText(teeJem,)",
                "Email(teejem@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(36f7bc3a3f40b11095f546a86b11ff1babc7ca7111c8498d6b6950cfc7663694)",
                "HashTag(#195)",
                "RegularText(2%)",
                "RegularText(BiancaBtcArt,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(1f2c17bd3bcaf12f9c7e78fe798eeea59c1b22e1ee036694d5dc2886ddfa35d7)",
                "HashTag(#196)",
                "RegularText(2%)",
                "RegularText(ruto,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(2888961a564e080dfe35ad8fc6517b920d2fcd2b7830c73f7c3f9f2abae90ea9)",
                "HashTag(#197)",
                "RegularText(2%)",
                "RegularText(Pocketcows,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(e462fd4f25682164bdb7c51fc1b2cd3c7e6ddba13a1d7094b06f6f4fe47f9ae3)",
                "HashTag(#198)",
                "RegularText(2%)",
                "RegularText(mewj,)",
                "Email(mewj@elder.nostr.land)",
                "RegularText(-)",
                "RegularText(489ac583fc30cfbee0095dd736ec46468faa8b187e311fda6269c4e18284ed0c)",
                "HashTag(#199)",
                "RegularText(2%)",
                "RegularText(nostr,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(2bd053345e10aed28bd0e97c311aab3470f6d7f405dc588b056bce1e3797d2f0)",
                "HashTag(#200)",
                "RegularText(2%)",
                "RegularText(Bobolo,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(ca7799f00a9d792f9bba6947b32e3142e6c6c4733e52906cbaf92a2961216b46)",
                "HashTag(#201)",
                "RegularText(2%)",
                "RegularText(InsolentBitcoin,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(6484df04c9403a64c3039f5f00d24ac0535f497cdfa1f187bc6a2d34cf017b97)",
                "HashTag(#202)",
                "RegularText(2%)",
                "RegularText(Monero)",
                "RegularText(Directory,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(1abdef52155dc52a21a2ac9ed19e444317f6cf83500df139fbe73c2a7ac78e2a)",
                "HashTag(#203)",
                "RegularText(2%)",
                "RegularText(thetonewrecker,)",
                "Email(thetonewrecker@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(3762d3159bfd9d8acb56677eec9a6f8a5a05ea86636186ca6ed6714a69975fed)",
                "HashTag(#204)",
                "RegularText(2%)",
                "RegularText(yodatravels,)",
                "Email(yodatravels@iris.to)",
                "RegularText(-)",
                "RegularText(67eb726f7bb8e316418cd46cfa170d580345e51adbc186f8f7aa0d4380579350)",
                "HashTag(#205)",
                "RegularText(2%)",
                "RegularText(Bitcoin)",
                "RegularText(Bandit,)",
                "Email(bitcoin69@iris.to)",
                "RegularText(-)",
                "RegularText(907842aa7b5d00054473d261e814c011c5d8e13bf8a585cc76121b1e6c51900f)",
                "HashTag(#206)",
                "RegularText(2%)",
                "RegularText(Zzar,)",
                "Email(Zzar@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(ca1dd2422cb94874c1666c9c76b7961bbaea432632643f7a2dc9d4d2bfb35db9)",
                "HashTag(#207)",
                "RegularText(2%)",
                "RegularText(vidalBidi,)",
                "Email(vidalbidi@getalby.com)",
                "RegularText(-)",
                "RegularText(0c28a25357c76ac5ac3714eddc25d81fe98134df13351ab526fc2479cc306e65)",
                "HashTag(#208)",
                "RegularText(2%)",
                "RegularText(994e89…f75447,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(994e892582261fd933af25bcc9672f2fbd5e769e3d1c889ecd292a7a92f75447)",
                "HashTag(#209)",
                "RegularText(2%)",
                "RegularText(juangalt,)",
                "Email(juangalt@current.ninja)",
                "RegularText(-)",
                "RegularText(372da077d6353430f343d5853d85311b3fd27018d5a83b8c1b397b92518ec7ac)",
                "HashTag(#210)",
                "RegularText(2%)",
                "RegularText(Dean,)",
                "Email(dean@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(83f018060171dfee116b077f0f455472b6b6de59abf4730994022bf6f27d16be)",
                "HashTag(#211)",
                "RegularText(2%)",
                "RegularText(alexli,)",
                "Email(alex2@nostrverified.com)",
                "RegularText(-)",
                "RegularText(8083df6081d91b42bcf1042215e4bfc894af893cd07ea472e801bc0794da3934)",
                "HashTag(#212)",
                "RegularText(2%)",
                "RegularText(Khidthungban,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(8d5cf93afb8d9ef1d08acee4e7147348d0c573bf7e5f57886a8a9a137cbe890c)",
                "HashTag(#213)",
                "RegularText(2%)",
                "RegularText(Trooper,)",
                "Email(trooper@iris.to)",
                "RegularText(-)",
                "RegularText(2c8d81a4e5cd9a99caba73f14c087ca7c05e554bb9988a900ccd76dbd828407d)",
                "HashTag(#214)",
                "RegularText(2%)",
                "RegularText(Satscoinsv,)",
                "RegularText(⚡\uFE0F)",
                "Email(satscoinsv@getalby.com)",
                "RegularText(-)",
                "RegularText(80db64657ea0358c5332c5cca01565eeddd4b8799688b1c46d3cb2d7c966671f)",
                "HashTag(#215)",
                "RegularText(2%)",
                "RegularText(AARBTC,)",
                "Email(aarbtc@iris.to)",
                "RegularText(-)",
                "RegularText(6d23993803386c313b7d4dcdfffdbe4e1be706c2f0c89cb5afaa542bf2be1b90)",
                "HashTag(#216)",
                "RegularText(2%)",
                "RegularText(yogsite,)",
                "Email(_@gue.yogsite.com)",
                "RegularText(-)",
                "RegularText(d3ab705ec57f3ea963fc7c467bddc7b17bf01b85acc4fbb14eed87df794a116c)",
                "HashTag(#217)",
                "RegularText(2%)",
                "RegularText(NostrMemes,)",
                "Email(nostrmemes@iris.to)",
                "RegularText(-)",
                "RegularText(6399694ca3b8c40d8be9762f50c9c420bf0bd73fb7d7d244a195814c9ab8fb7e)",
                "HashTag(#218)",
                "RegularText(2%)",
                "RegularText(btcpavao,)",
                "Email(btcpavao@iris.to)",
                "RegularText(-)",
                "RegularText(1a8ed3216bd2b81768363b4326e1ae270a7cd6fe570bafeda2dc070f34f3aedc)",
                "HashTag(#219)",
                "RegularText(2%)",
                "RegularText(Anonymous,)",
                "Email(Anonymous@BitcoinNostr.com)",
                "RegularText(-)",
                "RegularText(ac076f8f80ee4a49f22c2ce258dcfe6e105de0bf029a048fa3a8de4b51c1b957)",
                "HashTag(#220)",
                "RegularText(2%)",
                "RegularText(zoltanAB,)",
                "Email(zoltanab@iris.to)",
                "RegularText(-)",
                "RegularText(42aafd1217089d68c757671a251507a194587dd3adfc3a3a76bb1e38a78a3453)",
                "HashTag(#221)",
                "RegularText(2%)",
                "RegularText(katsu,)",
                "Email(katsu@onsats.org)",
                "RegularText(-)",
                "RegularText(76f64475795661961801389aeaa7869a005735266c9e3df9bc93d127fad04154)",
                "HashTag(#222)",
                "RegularText(2%)",
                "RegularText(bryan,)",
                "Email(bryan@nonni.io)",
                "RegularText(-)",
                "RegularText(9ddf6fe3a194d330a6c6e278a432ae1309e52cc08587254b337d0f491f7ff642)",
                "HashTag(#223)",
                "RegularText(2%)",
                "RegularText(pedromvpg,)",
                "Email(pedromvpg@pedromvpg.com)",
                "RegularText(-)",
                "RegularText(8cd2d0f8310f7009e94f50231870756cb39ba68f37506044910e2f71482b1788)",
                "HashTag(#224)",
                "RegularText(2%)",
                "RegularText(Nellie,)",
                "Email(sonicstudio@getalby.com)",
                "RegularText(-)",
                "RegularText(37fbbf7707e70a8a7787e5b1b75f3e977e70aab4f41ddf7b3c0f38caedd875d4)",
                "HashTag(#225)",
                "RegularText(2%)",
                "RegularText(nicknash,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(636b4e6f5a594893c544b49a5742f0a90f109b70d659585e0427a1c0361c0b09)",
                "HashTag(#226)",
                "RegularText(2%)",
                "RegularText(dlegal,)",
                "Email(kounsellor@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(201e51e71a753af3699cf684d7f4113c59a73c4b7bd26ef3f4c187a6173fbf06)",
                "HashTag(#227)",
                "RegularText(2%)",
                "RegularText(BitcoinLake,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(5babddf98277e3db6c88ae1d322bc63fd637764370e1d5e4fe5226104d82034f)",
                "HashTag(#228)",
                "RegularText(2%)",
                "RegularText(BitcoinKeegan,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(b457120b6cfb2589d48718f2ab71362dd0db43e13266771725129d35cc602dbe)",
                "HashTag(#229)",
                "RegularText(2%)",
                "RegularText(KatieRoss,)",
                "Email(katieross@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(90f09238f3514f249e2b333e6119eef49697020f956fd7b6732ce118dd1b53cb)",
                "HashTag(#230)",
                "RegularText(2%)",
                "RegularText(efcfa6…e3f485,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(efcfa63ac0324e37fb138c2b9dbbf9372f64ec857c923c5c1f713d3592e3f485)",
                "HashTag(#231)",
                "RegularText(2%)",
                "RegularText(bc9e89…b519d3,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(bc9e89110e6e7ec5540b8ad0467d8a39554a7527c27e7af4cd45b2b8c4b519d3)",
                "HashTag(#232)",
                "RegularText(2%)",
                "RegularText(Ilj,)",
                "Email(iamlj@iris.to)",
                "RegularText(-)",
                "RegularText(fa3e7bcc5e588a8111ffb9d9eb8bf62c87d8a0ef6e1e5e0c74311b61f6ced8e7)",
                "HashTag(#233)",
                "RegularText(2%)",
                "RegularText(ayelen,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(1c31ccda2709fc6cf5db0a0b0873613e25646c4a944779dfb5e8d6cbbcd2ee1c)",
                "HashTag(#234)",
                "RegularText(2%)",
                "RegularText(zach,)",
                "Email(Zach@BitcoinNostr.com)",
                "RegularText(-)",
                "RegularText(d99211aeeb643695ee1aad0517696bbc822e2fb443afe2dc9dadc0ca50b040e2)",
                "HashTag(#235)",
                "RegularText(2%)",
                "RegularText(Yi,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(248caad2f8392c7f72502da41ee62bbe256ea66fb365e395c988198660562ff7)",
                "HashTag(#236)",
                "RegularText(2%)",
                "RegularText(Amouranth,)",
                "Email(amouranth@nostrcheck.me)",
                "RegularText(-)",
                "RegularText(be5aa097ad9f4d872c70e432ad8c09565ee7dc1aee24a50b683ddca771b14901)",
                "HashTag(#237)",
                "RegularText(2%)",
                "RegularText(hss5qy,)",
                "Email(hss5qy@getalby.com)",
                "RegularText(-)",
                "RegularText(bc21401161327647e0bbd31f2dec1be168ef7fa5d05689fca0d063b114ed9b46)",
                "HashTag(#238)",
                "RegularText(2%)",
                "RegularText(dpc,)",
                "Email(dpcpw@iris.to)",
                "RegularText(-)",
                "RegularText(274611b4728b0c40be1cf180d8f3427d7d3eebc55645d869a002e8b657f8cd61)",
                "HashTag(#239)",
                "RegularText(2%)",
                "RegularText(pred,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(3946adbb2fc7c95f75356d8f3952c8e2705ee2431f8bd33f5cae0f9ede0298e2)",
                "HashTag(#240)",
                "RegularText(2%)",
                "RegularText(jamesgore,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(a94921403ac0ccf1a150ccac3679b11adcb3c3bb78b490452db43a8b6964a5c7)",
                "HashTag(#241)",
                "RegularText(2%)",
                "RegularText(bitcoinfinity,)",
                "Email(bitcoinfinity@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(afbda6a942f975ddf8728bda3e6e5c9e440f067fcde719c6f57512f0f7ed4bf2)",
                "HashTag(#242)",
                "RegularText(2%)",
                "RegularText(tonyseries,)",
                "Email(TonySeries@BitcoinNostr.com)",
                "RegularText(-)",
                "RegularText(ba5a614a48719361f515f6efa62c3e213da4bcddbb78dafd3121daa839192275)",
                "HashTag(#243)",
                "RegularText(2%)",
                "RegularText(kuobano,)",
                "Email(kuobano@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(3f6d0bbb073839671f4c7f1e23452c6c3080f6c5f4cbc2f56c17e2b57ee01442)",
                "HashTag(#244)",
                "RegularText(2%)",
                "RegularText(kitakripto,)",
                "Email(kitakripto@BitcoinNostr.com)",
                "RegularText(-)",
                "RegularText(0b11a45bf4ff7f000886b2227e43404d212bf585f71514d54ae5ae685f4c8fbb)",
                "HashTag(#245)",
                "RegularText(2%)",
                "RegularText(Bashy,)",
                "Email(_@localhost.re)",
                "RegularText(-)",
                "RegularText(566516663d91d4fef824eaeccbf9c2631a8d8a2efee8048ca5ee6095e6e5c843)",
                "HashTag(#246)",
                "RegularText(2%)",
                "RegularText(alxc,)",
                "Email(alxc@uselessshit.co)",
                "RegularText(-)",
                "RegularText(c13cb9426a4f85aff08019d246d1240a6cbf49ab9525a06d54fb496b9a3592b0)",
                "HashTag(#247)",
                "RegularText(2%)",
                "RegularText(Kukryr,)",
                "Email(kukryr@orangepill.dev)",
                "RegularText(-)",
                "RegularText(3f03ab6555d2e36ba970d83b8dfe1a9c09d1b89048cf7db0c85d40850f406e54)",
                "HashTag(#248)",
                "RegularText(2%)",
                "RegularText(Saidah,)",
                "Email(saidah@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(909efa6667b28627f107764ce3c28895c46fffd1811b7415dcab03f48c44b597)",
                "HashTag(#249)",
                "RegularText(2%)",
                "RegularText(micmad,)",
                "SchemelessUrl(miceliomad@miceliomad.github.io/nostr/)",
                "RegularText(-)",
                "RegularText(cd806edcf8ff40ea94fa574ea9cd97da16e5beb2b85aac6e1d648b8388504343)",
                "HashTag(#250)",
                "RegularText(2%)",
                "RegularText(Zack)",
                "RegularText(Wynne,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(9156e62c7d2f49a91b55effec6c111d3fb343e9de6ff05650e7fd89a039a9dce)",
                "HashTag(#251)",
                "RegularText(2%)",
                "RegularText(Sharon21M,)",
                "Email(sharon21m@nostr.fan)",
                "RegularText(-)",
                "RegularText(66b5c5be6cec2b4a124c532e97d8342f8d763d6b507caced9185168603751f25)",
                "HashTag(#252)",
                "RegularText(2%)",
                "RegularText(bitcoinheirodomanto,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(93d16b6fcd11199cc113e28976999ff94137ded02ddf6b84bf671daf9358c54a)",
                "HashTag(#253)",
                "RegularText(2%)",
                "RegularText(tyler,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(272fe1597e8d938b9a7ae5eb23aa50c5048aabbf68f27a428afe3aecd08192da)",
                "HashTag(#254)",
                "RegularText(2%)",
                "RegularText(DMN,)",
                "Email(dmn@noderunners.org)",
                "RegularText(-)",
                "RegularText(176d6e6ceef73b3c66e1cb1ed19b9f2473eaa514678159bc41361b3f29ddb065)",
                "HashTag(#255)",
                "RegularText(2%)",
                "RegularText(Nela@Nostrica2023,)",
                "Email(nela_at_nostrica2023@Nostr-Check.com)",
                "RegularText(-)",
                "RegularText(4b0bcab460adda31fad5a326fb0c04f6ec821fb24be85dbdc03c04cc0e12fc07)",
                "HashTag(#256)",
                "RegularText(2%)",
                "RegularText(xbolo,)",
                "Email(xbologg@nanostr.deno.dev)",
                "RegularText(-)",
                "RegularText(7aabf4a15df15074deeffdb597e6be54be4a211cbd6303436cb1ccea6c9cf87b)",
                "HashTag(#257)",
                "RegularText(2%)",
                "RegularText(btcurenas,)",
                "Email(btcurenas@nostr.fan)",
                "RegularText(-)",
                "RegularText(206a1264c89e8f29355e792782e83ca62331ca3d70169327cb315171b4a7ce2c)",
                "HashTag(#258)",
                "RegularText(2%)",
                "RegularText(amaluenda,)",
                "Email(amaluenda@getalby.com)",
                "RegularText(-)",
                "RegularText(129a80a580a0cb88d5eae9d3924d7bb8a29e0c03ef9fb723091de69c22eaaff8)",
                "HashTag(#259)",
                "RegularText(2%)",
                "RegularText(DeveRoSt,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(f838b6a03d8d0127a9a98e87c0142b528916a4336ba537e14131a2f513becc17)",
                "HashTag(#260)",
                "RegularText(2%)",
                "RegularText(phoenixpyro,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(5122cee9af93a36be4bb9b08ee7897ef88fe446c0a5d2f8db60da9faa0f72f27)",
                "HashTag(#261)",
                "RegularText(2%)",
                "RegularText(Queen)",
                "RegularText(₿,)",
                "Email(queenb@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(735e573b24b78138e86c96aaf37cf47547d6287c9acbd4eda173e01826b6647a)",
                "HashTag(#262)",
                "RegularText(2%)",
                "RegularText(L.,)",
                "Email(ezekiel@Nostr-Check.com)",
                "RegularText(-)",
                "RegularText(83663cd936892679cbd1ccdf22e017cb9fee11aef494713192c93ad6a155e287)",
                "HashTag(#263)",
                "RegularText(2%)",
                "RegularText(dolu)",
                "RegularText((compromised),)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(e668a111aa647e63ef587c17fb0e2513d5c2859cd8d389563c7640ffea1fc216)",
                "HashTag(#264)",
                "RegularText(2%)",
                "RegularText(Marakesh)",
                "RegularText(𓅦,)",
                "Email(marakesh@getalby.com)",
                "RegularText(-)",
                "RegularText(dace63b00c42e6e017d00dd190a9328386002ff597b841eb5ef91de4f1ce8491)",
                "HashTag(#265)",
                "RegularText(2%)",
                "RegularText(Storm,)",
                "Email(storm@reddirtmining.io)",
                "RegularText(-)",
                "RegularText(eaba072268fbb5409bdd2e8199e2878cf5d0b51ce3493122d03d7c69585d17f2)",
                "HashTag(#266)",
                "RegularText(2%)",
                "RegularText(fiore,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(155fd584b69fea049a428935cef11c093b6b80ca067fe4362eab0564d0774f10)",
                "HashTag(#267)",
                "RegularText(2%)",
                "RegularText(.b.o.n.e.s.,)",
                "Email(_b_o_n_e_s_@stacker.news)",
                "RegularText(-)",
                "RegularText(b91257b518ee7226972fc7b726e96d8a63477750a1b40589e36a090735a4f92f)",
                "HashTag(#268)",
                "RegularText(2%)",
                "RegularText(btchodl,)",
                "Email(bdichdbd@stacker.news)",
                "RegularText(-)",
                "RegularText(d3ca4d0144b7608eceb214734a098d50dd6c728eb72e47b0e5b1e04480db1009)",
                "HashTag(#269)",
                "RegularText(2%)",
                "RegularText(Rosie,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(caf0d967570ab0702c3402d50c4ab12dc6855ea062519b1ac048708cb663b0c8)",
                "HashTag(#270)",
                "RegularText(2%)",
                "RegularText(j9,)",
                "Email(j9@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(c2797c4c633d3005d60a469d154b85766277454b648252d927660d41ecec4163)",
                "HashTag(#271)",
                "RegularText(2%)",
                "RegularText(nokyctranslate,)",
                "Email(nokyctranslate@iris.to)",
                "RegularText(-)",
                "RegularText(794366f1f67b7bc5604fd47e21a27e6fcbff7ec7e7a72c6d4c386d50fd5d2f04)",
                "HashTag(#272)",
                "RegularText(2%)",
                "RegularText(Neomobius,)",
                "Email(Neomobius_at_mstdn.jp@mostr.pub)",
                "RegularText(-)",
                "RegularText(9134bd35097c03abdcd9d61819aa8948880b6e49fc548d8a751b719dced7f7da)",
                "HashTag(#273)",
                "RegularText(2%)",
                "RegularText(dojomaster,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(30be56daec34e8b319d730f2c2f1cba28ef076660be33d7811dd385698a9cb40)",
                "HashTag(#274)",
                "RegularText(2%)",
                "RegularText(paddepadde)",
                "RegularText(⚡️,)",
                "Email(paddepadde@getcurrent.io)",
                "RegularText(-)",
                "RegularText(430169631f2f0682c60cebb4f902d68f0c71c498fd1711fd982f052cf1fd4279)",
                "HashTag(#275)",
                "RegularText(2%)",
                "RegularText(Val,)",
                "Email(val@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(e2004cb6f21a23878f0000131363e557638e47a804bcfc200103dd653fc9b7dc)",
                "HashTag(#276)",
                "RegularText(2%)",
                "RegularText(Nickfost_,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(a3e4cba409d392a81521d8714578948979557c8b2d56994b2026a06f6b7e97d2)",
                "HashTag(#277)",
                "RegularText(2%)",
                "RegularText(dishwasher_iot,)",
                "Email(dishwasher_iot@wlvs.space)",
                "RegularText(-)",
                "RegularText(5c6c25b7ef18d8633e97512159954e1aa22809c6b763e94b9f91071836d00217)",
                "HashTag(#278)",
                "RegularText(2%)",
                "RegularText(𝕬𝖓𝖔𝖓𝖞𝖒𝖔𝖚𝖘,)",
                "SchemelessUrl(zapper.lol)",
                "RegularText(-)",
                "RegularText(96aceca84aa381eeda084167dd317e1bf7a45d874cd14147f0a9e0df86fb44c2)",
                "HashTag(#279)",
                "RegularText(2%)",
                "RegularText(Peter,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(b649ca5743312176174cbe76cf81d3eec493b21a52b822b6aa12bd4473da0d01)",
                "HashTag(#280)",
                "RegularText(2%)",
                "RegularText(justin,)",
                "Email(1@justinrezvani.com)",
                "RegularText(-)",
                "RegularText(84d535055542132100ea22e96e33349844422e6e698cc98bd8fb5eae08d76752)",
                "HashTag(#281)",
                "RegularText(2%)",
                "RegularText(vikeymehta,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(1a3d05e13fa38543b3d45f31c638e94e113b35c0e1db7371cdfa69861e150830)",
                "HashTag(#282)",
                "RegularText(2%)",
                "RegularText(sshh,)",
                "Email(sshh@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(b0f86106d59d2ce292a4d89e70ff4057d7adf4b1b42bb913f37ceb9159bb2aea)",
                "HashTag(#283)",
                "RegularText(2%)",
                "RegularText(Red_Eye_Jedi,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(3603dbbea53ee52ab34e0f96a8d42aa55486cf5e2e05483533613e97274155f5)",
                "HashTag(#284)",
                "RegularText(2%)",
                "RegularText(jim,)",
                "Email(mk05@iris.to)",
                "RegularText(-)",
                "RegularText(2ed67b778522bfa0245ee57306dea40d6fd9b023db5fff43e2de0419cfe2164e)",
                "HashTag(#285)",
                "RegularText(2%)",
                "RegularText(pniraj007,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(99f7ba6cfb2fcd60853446b45cec2a467f65faa3245a95513bcf372eec4fbb0e)",
                "HashTag(#286)",
                "RegularText(2%)",
                "RegularText(b676eb…7c389b,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(b676ebe5ebd490523dda7db35407b7370974b4df25be32335f0652a1f07c389b)",
                "HashTag(#287)",
                "RegularText(2%)",
                "RegularText(herald,)",
                "Email(herald@bitcoin-herald.org)",
                "RegularText(-)",
                "RegularText(7e7224cfe0af5aaf9131af8f3e9d34ff615ff91ce2694640f1f1fee5d8febb7d)",
                "HashTag(#288)",
                "RegularText(2%)",
                "RegularText(Giuseppe)",
                "RegularText(Atorino,)",
                "Email(nostr@pos.btcpayserver.it)",
                "RegularText(-)",
                "RegularText(e6eaf2368767307b45fcbea2d96dcb34a93af8877147203fadc10b8f741b71c9)",
                "HashTag(#289)",
                "RegularText(2%)",
                "RegularText(a8b7b0…d90ac2,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(a8b7b07222485f8b845961dd4ca4d8b63c575e060b4d9386e32463e513d90ac2)",
                "HashTag(#290)",
                "RegularText(2%)",
                "RegularText(genosonic,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(05ffbdf4b71930d0e93ae0caa8f34bcfb5100cfba71f07b9fad4d8b5a80e4df3)",
                "HashTag(#291)",
                "RegularText(2%)",
                "RegularText(JohnnyG,)",
                "Email(thumpgofast@NostrVerified.com)",
                "RegularText(-)",
                "RegularText(241d6b169d62fa3d673fccf66ab62d49c0a1147ab6ab81f7a526d890e1d68a2b)",
                "HashTag(#292)",
                "RegularText(2%)",
                "RegularText(neoop,)",
                "Email(neo@elder.nostr.land)",
                "RegularText(-)",
                "RegularText(ea64386dba380b76c86f671f2f3c5b2a93febe8d3e2e968ac26f33569da36f87)",
                "HashTag(#293)",
                "RegularText(2%)",
                "RegularText(Alchemist,)",
                "Email(alchemist@electronalchemy.com)",
                "RegularText(-)",
                "RegularText(734aac327175cb770b9aa75c8816156ea439a79c6f87a16801248c1c793a8bfc)",
                "HashTag(#294)",
                "RegularText(2%)",
                "RegularText(timp,)",
                "Email(timp@iris.to)",
                "RegularText(-)",
                "RegularText(24cf74e1125833e9752b4843e2887dedddf6910896e6e82a2def68c8527d0814)",
                "HashTag(#295)",
                "RegularText(2%)",
                "RegularText(ken,)",
                "Email(ken@BitcoinNostr.com)",
                "RegularText(-)",
                "RegularText(3505b759f075da83e9d503530d3238361b1603c28e0ee309d928174e87341713)",
                "HashTag(#296)",
                "RegularText(2%)",
                "RegularText(Shea,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(8dc289f2b5896057e23edc6b806407dc09162147164f4cae1d00dcb1bcd3f084)",
                "HashTag(#297)",
                "RegularText(2%)",
                "RegularText(Devcat,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(7f1052e59569dee4c6587507c69032af5d6883d2aa659a55bbfe1cb2e8233daf)",
                "HashTag(#298)",
                "RegularText(2%)",
                "RegularText(173a2e…36436a,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(173a2e04860656e9bab4a62cd5ec2b46ac8814e240c183e47b6badf7b936436a)",
                "HashTag(#299)",
                "RegularText(2%)",
                "RegularText(Irebus,)",
                "Email(irebus@nostr.red)",
                "RegularText(-)",
                "RegularText(1aaaa8e2a2094e2fdd70def09eae4e329ceb01a6a29473cb0b5e0c118f85bd35)",
                "HashTag(#300)",
                "RegularText(2%)",
                "RegularText(b720b6…e48a8f,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(b720b63c47b3292dcb3339782c612462a7a42c9eece06d609a49cf951de48a8f)",
                "HashTag(#301)",
                "RegularText(2%)",
                "RegularText(theflywheel,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(57dcc9ed500a26a465ddb12c51de05963d4dec8a596708629558495c4acacab3)",
                "HashTag(#302)",
                "RegularText(2%)",
                "RegularText(223597…002c18,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(22359794c50e2945aa768ee500ffb2ddb388696ad078a350ae570152ff002c18)",
                "HashTag(#303)",
                "RegularText(2%)",
                "RegularText(gratitude,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(4686358c60bae7694e8b39dad26d1c834d5dd27726a56e2501fc06dec6942be1)",
                "HashTag(#304)",
                "RegularText(2%)",
                "RegularText(stim4444,)",
                "Email(stim4444@no.str.cr)",
                "RegularText(-)",
                "RegularText(0aeaec333bf9a0638de51ea837590ca64522ec590ed160ce87cb6e30d10df537)",
                "HashTag(#305)",
                "RegularText(2%)",
                "RegularText(756240…265fc2,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(756240d3be0d553b0cd174b3499cffa37fbe8394ee06b9ab50652e314c265fc2)",
                "HashTag(#306)",
                "RegularText(2%)",
                "RegularText(4d38ed…d26aad,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(4d38ed26a6d1080806534818a668c71381bcb04bc4ca1083d9d9572977d26aad)",
                "HashTag(#307)",
                "RegularText(2%)",
                "RegularText(Kwinten,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(c29da265739bc3886c76d84b0a351849fa45a31a64fcb72f47c600ab2623f90c)",
                "HashTag(#308)",
                "RegularText(2%)",
                "RegularText(b36506…7ca32c,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(b365069ada41fc7190f8b11e8342f7f66f9777eaaa9882722d0be863c27ca32c)",
                "HashTag(#309)",
                "RegularText(2%)",
                "RegularText(Cole)",
                "RegularText(Albon,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(c3ff9a851ca965ed266ba54c9263f680be91e2465628c64bab6a5992521d5c5d)",
                "HashTag(#310)",
                "RegularText(2%)",
                "RegularText(Onecoin,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(b23ce47262373574d6653fad2da09db1fb20bb2919f3e697b8edd1966fffd8ec)",
                "HashTag(#311)",
                "RegularText(2%)",
                "RegularText(Disabled,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(7d706eaefb905ea9b3af885879fb5911b50b39db539c319438703373424204ec)",
                "HashTag(#312)",
                "RegularText(2%)",
                "RegularText(xdamman,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(340254e011abda2e82585cbfee4f91b3f07549a6c468fe009bf3ec7665a2e31b)",
                "HashTag(#313)",
                "RegularText(2%)",
                "RegularText(jmrichner,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(797750041d1366a80d45e130c831f0562b5f7266662b07acef50dd541bfa2535)",
                "HashTag(#314)",
                "RegularText(2%)",
                "RegularText(pentoshi,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(db6ad1e2a4cbbacbbdf79377a9ebb2fc30eb417ce9b061003771cb40b8e00d56)",
                "HashTag(#315)",
                "RegularText(2%)",
                "RegularText(35453d…45d10b,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(35453d2e49a0282c4dd694e5a364bf29600a9b5443e4712cfc86a0495345d10b)",
                "HashTag(#316)",
                "RegularText(2%)",
                "RegularText(LayerLNW,)",
                "Email(layerlnw@nostr.fan)",
                "RegularText(-)",
                "RegularText(33c9edf7ade19188685997136e6ffb4ed89939178fa5f2259428de1cd3301380)",
                "HashTag(#317)",
                "RegularText(2%)",
                "RegularText(Bitcoincouch,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(fbd3c6eb5ef06e82583d3b533663ba86036462a02e686881d8cb2de5aaa9fa4a)",
                "HashTag(#318)",
                "RegularText(2%)",
                "RegularText(BritishHodl,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(22fb17c6657bb317be84421335ef6b0f9f1777617aa220cf27dc06fb5788f438)",
                "HashTag(#319)",
                "RegularText(2%)",
                "RegularText(enhickman,)",
                "Email(enhickman@enhickman.net)",
                "RegularText(-)",
                "RegularText(0cf08d280aa5fcfaf340c269abcf66357526fdc90b94b3e9ff6d347a41f090b7)",
                "HashTag(#320)",
                "RegularText(2%)",
                "RegularText(4d6e72…219298,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(4d6e72aba0e8a033c973acd7e42f915d5fa1708be7229d477869e91136219298)",
                "HashTag(#321)",
                "RegularText(2%)",
                "RegularText(f75326…af65e0,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(f7532615471b029a34e41e080b2af4bad2b80f8105c008378d0095991eaf65e0)",
                "HashTag(#322)",
                "RegularText(2%)",
                "RegularText(LiveFreeBTC,)",
                "Email(LiveFreeBTC@livefreebtc.org)",
                "RegularText(-)",
                "RegularText(49f586188679af2b31e8d62ff153baf767fd0c586939f4142ef65606d236ff75)",
                "HashTag(#323)",
                "RegularText(2%)",
                "RegularText(aptx4869,)",
                "Email(aptx4869@aptx4869.app)",
                "RegularText(-)",
                "RegularText(64aaa73189af814977ff5dedbbab022df030f1d7df3e6307aceb1fddb30df847)",
                "HashTag(#324)",
                "RegularText(2%)",
                "RegularText(khalil,)",
                "Email(khalil@klouche.com)",
                "RegularText(-)",
                "RegularText(5a03bdb5448b440428d8459d4afe9b553e705737ef8cd7a0d25569ccead4d6ce)",
                "HashTag(#325)",
                "RegularText(2%)",
                "RegularText(nsec1wnppl0xqw2lysecymwmz3hgxuzk60dgyur6mqtgexln20qp4xv9sugxghg,)",
                "Email(nsec@ittybitty.tips)",
                "RegularText(-)",
                "RegularText(f1ea91eeab7988ed00e3253d5d50c66837433995348d7d97f968a0ceb81e0929)",
                "HashTag(#326)",
                "RegularText(2%)",
                "RegularText(BTC_P2P,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(ecf468164bd743b75683db3870ce01cb9a1d4b8ec203ed26de50f96255bbc75a)",
                "HashTag(#327)",
                "RegularText(2%)",
                "RegularText(Big)",
                "RegularText(FISH,)",
                "Email(bigfish@iris.to)",
                "RegularText(-)",
                "RegularText(963100cf40967a70cdea802c6b4b97956cf8c5e3b09e492b24a847d4c535a794)",
                "HashTag(#328)",
                "RegularText(2%)",
                "RegularText(9e93fb…2483b6,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(9e93fb0012a6177faddf2fd324fb61eafbe8b142b31c5e89fd85bfafd12483b6)",
                "HashTag(#329)",
                "RegularText(2%)",
                "RegularText(Mynameis,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(6bec23b4a17da33d0a2f44e258371e869ff124775e8e38b9581dcd49c8d1d4a6)",
                "HashTag(#330)",
                "RegularText(2%)",
                "RegularText(3f2342…d689b8,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(3f23426af245168f8112e441c046ecdb29aca56a6d33d21e276b8ac00bd689b8)",
                "HashTag(#331)",
                "RegularText(2%)",
                "RegularText(865c92…136ced,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(865c92a207a156a2d48404694a2eed5ceca5c163b7a845b86a6c75e142136ced)",
                "HashTag(#332)",
                "RegularText(2%)",
                "RegularText(95d4d6…fe1673,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(95d4d60e643f283cef8d70ab7a9c09ab5a85924f97e11b22cf99779c4ffe1673)",
                "HashTag(#333)",
                "RegularText(2%)",
                "RegularText(verse,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(0ff7a93751d37ffcca05579c59ac69053d8d0c6f2c57ed9101ba8758eebc0d6b)",
                "HashTag(#334)",
                "RegularText(2%)",
                "RegularText(oldschool,)",
                "Email(oldschool@iris.to)",
                "RegularText(-)",
                "RegularText(19dba8f974322c7345d3b491925896d19e7f432a4f41223c5daf96e31fae338d)",
                "HashTag(#335)",
                "RegularText(2%)",
                "RegularText(Danton🇨🇭,)",
                "Email(danton@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(dbe693bc2d16c52e18e75f2cb76401cb7d74132cc956f7315ea5ebee1adfc966)",
                "HashTag(#336)",
                "RegularText(2%)",
                "RegularText(BitcoinZavior,)",
                "Email(bitcoinzavior@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(c6e86c9b95ef289600800b855b9a6ca42019cc9453937020289d8b3e01dab865)",
                "HashTag(#337)",
                "RegularText(2%)",
                "RegularText(BitcoinSermons,)",
                "Email(BitcoinSermons@BitcoinNostr.com)",
                "RegularText(-)",
                "RegularText(615f40fae8f2e08da81b5c76a0143cb04b4e9e044bf6047efe15c56c7cc1a6b2)",
                "HashTag(#338)",
                "RegularText(2%)",
                "RegularText(skreep,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(a4992688b449c2bdd6fa9c39a880d7fe27d5f5e3e9fd4c47d65d824588fd660f)",
                "HashTag(#339)",
                "RegularText(2%)",
                "RegularText(db830b…4bb85c,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(db830b864876a0f3109ae3447e43715711250d53f310092052aabb5bdc4bb85c)",
                "HashTag(#340)",
                "RegularText(2%)",
                "RegularText(UKNW22LINUX,)",
                "Email(uknwlinux@plebs.place)",
                "RegularText(-)",
                "RegularText(ab1ef3f15fc29b3da324eb401122382ceb5ea9c61adaad498192879fd9a5d057)",
                "HashTag(#341)",
                "RegularText(2%)",
                "RegularText(Satoshism,)",
                "Email(satoshism@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(e262ed3a22ad8c478b077ef5d7c56b2c3c7a530519ed696ed2e57c65e147fbcb)",
                "HashTag(#342)",
                "RegularText(2%)",
                "RegularText(William,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(8c55174d8fc29d4da650b273fdd18ad4dda478faa4b0ea14726d81ac6c7bef48)",
                "HashTag(#343)",
                "RegularText(2%)",
                "RegularText(thebitcoinyogi,)",
                "Email(jon@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(59c2e15ad7bc0b5c97b8438b2763a5c409ff76ab985ab5f1f47c4bcdd25e6e8d)",
                "HashTag(#344)",
                "RegularText(2%)",
                "RegularText(vake,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(547f45b91c1e6b4137917cde4fa1da867c8cdfe43d0f646c836a622769795a14)",
                "HashTag(#345)",
                "RegularText(2%)",
                "RegularText(hobozakki,)",
                "Email(hobozakki@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(29e31c4103b85fab499132fa71870bd5446de8f7e2ac040ec0372aa61ae22f98)",
                "HashTag(#346)",
                "RegularText(2%)",
                "RegularText(SirGalahodl,)",
                "Email(sirgalahodl@satstream.me)",
                "RegularText(-)",
                "RegularText(25ee676190e2b6145ad8dd137630eca55fc503dde715ce8af4c171815d018797)",
                "HashTag(#347)",
                "RegularText(2%)",
                "RegularText(1f6c76…ebb9c9,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(1f6c76ddbab213cdd43db2695b1474605639862302c7cfae35362be8caebb9c9)",
                "HashTag(#348)",
                "RegularText(2%)",
                "RegularText(greencandleit,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(3d4b358b50d20c3e4d855f273ff06c49bc6b3f6e62c42aed44f278742fd579da)",
                "HashTag(#349)",
                "RegularText(2%)",
                "RegularText(ichigo,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(477e0b3c0c6029e31562b39650efa8f871d52e3ab09145d72e99b9b74dd384d7)",
                "HashTag(#350)",
                "RegularText(2%)",
                "RegularText(Niko,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(636fdb4de194bca39ab30ab5793a38b8d15c1b1c0a968d04f7fe14eb1a6a8c42)",
                "HashTag(#351)",
                "RegularText(2%)",
                "RegularText(afa,)",
                "Email(victor@lnmarkets.com)",
                "RegularText(-)",
                "RegularText(8f6945b4726112826ac6abd56ec041c87d8bdc4ec02e86bb388a97481f372b97)",
                "HashTag(#352)",
                "RegularText(2%)",
                "RegularText(BushBrook,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(a39fd86ed75c654550bf813430877819beb77a3b670e01a9680a84a844db9620)",
                "HashTag(#353)",
                "RegularText(2%)",
                "RegularText(naoise,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(c4a9caef93e93f484274c04cd981d1de1424902451aca2f5602bd0835fe4393d)",
                "HashTag(#354)",
                "RegularText(2%)",
                "SchemelessUrl(smies.me)",
                "RegularText(,)",
                "Email(jacksmies@iris.to)",
                "RegularText(-)",
                "RegularText(cdecbc48e35a351582e3e030fd8cf5d5f44681613d2949353d9c6644d32d451f)",
                "HashTag(#355)",
                "RegularText(2%)",
                "RegularText(Chemaclass,)",
                "Email(chemaclass@snort.social)",
                "RegularText(-)",
                "RegularText(c5d4815c26e18e2c178133004a6ddba9a96a5f7af795a3ab606d11aa1055146a)",
                "HashTag(#356)",
                "RegularText(2%)",
                "RegularText(BTCingularity,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(aa1f96f685d0ac3e28a52feb87a20399a91afb3ac3137afeb7698dfcc99bc454)",
                "HashTag(#357)",
                "RegularText(2%)",
                "RegularText(the_man,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(dad77f3814964b5cdcd120a3a8d7b40c6218d413ae6328801b9929ed90123687)",
                "HashTag(#358)",
                "RegularText(2%)",
                "RegularText(jayson,)",
                "Email(jayson@tautic.com)",
                "RegularText(-)",
                "RegularText(7be5d241f3cc10922545e31aeb8d5735be2bc3230480e038c7fd503e7349a2cc)",
                "HashTag(#359)",
                "RegularText(2%)",
                "RegularText(jesterhodl,)",
                "Email(jesterhodl@jesterhodl.com)",
                "RegularText(-)",
                "RegularText(3c285d830bf433135ae61c721b750ce11ae5b2e187712d7a171afa7cda649e50)",
                "HashTag(#360)",
                "RegularText(2%)",
                "RegularText(06d694…c3ab96,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(06d6946fd1ff1fba6ac530e0b5683db4c73cdc11d6c42324246e10f4f2c3ab96)",
                "HashTag(#361)",
                "RegularText(2%)",
                "RegularText(sardin,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(f26470570bcb67a18a90890dbe02d565eadc6c955912977c64c99d4b9a7fd29f)",
                "HashTag(#362)",
                "RegularText(2%)",
                "RegularText(Bitcoin_Gamer_21,)",
                "Email(Bitcoin_Gamer_21@bitcoin-21.org)",
                "RegularText(-)",
                "RegularText(021df4103ede2cdc32de4058d4bdb29ffcbfd13070f05c4688f6974bd9a67176)",
                "HashTag(#363)",
                "RegularText(2%)",
                "RegularText(water-bot,)",
                "Email(water-bot@gourcetools.github.io)",
                "RegularText(-)",
                "RegularText(000000dd7a2e54c77a521237a516eefb1d41df39047a9c64882d05bc84c9d666)",
                "HashTag(#364)",
                "RegularText(1%)",
                "RegularText(ondorevillager,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(5d7b460173010efd682c0d7bc8cc36ca9bf7dcc7990288f642c04b8e05713c83)",
                "HashTag(#365)",
                "RegularText(1%)",
                "RegularText(Tomfantasia,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(d856af932000c292ad723dee490ebcf908a1031b486dea05267ee50b473349b2)",
                "HashTag(#366)",
                "RegularText(1%)",
                "RegularText(W3crypto,)",
                "Email(w3crypto@iris.to)",
                "RegularText(-)",
                "RegularText(d001bca923ab56b1c759fc9471fbe6baadac50aeba7d963155772ac7b6779027)",
                "HashTag(#367)",
                "RegularText(1%)",
                "RegularText(bradjpn,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(c4da3be8e10fa86128530885d18e455900cccff39d7a24c4a6ac12b0284f62b3)",
                "HashTag(#368)",
                "RegularText(1%)",
                "RegularText(@discretelog,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(03e4804b4a28c051f43185d6bf5b4643cb3f0d9632c4394b60a2ffad0f852340)",
                "HashTag(#369)",
                "RegularText(1%)",
                "RegularText(makaveli,)",
                "Email(makaveli@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(570469cbc969ea6c7e94c41c6496a2951f52d3399011992bf45f4b2216d99119)",
                "HashTag(#370)",
                "RegularText(1%)",
                "RegularText(JamieAnders,)",
                "Email(jamieanders@ln.tips)",
                "RegularText(-)",
                "RegularText(7601e743ad432d78471ac57178402a57cd3f3a92fb208be7de788af2d6a57669)",
                "HashTag(#371)",
                "RegularText(1%)",
                "RegularText(LightningVentures,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(37de18e08cdc01ce7ced1808b241ec0b4a69e754d576ce0e08f0cf3375bb0a6b)",
                "HashTag(#372)",
                "RegularText(1%)",
                "RegularText(Colorado)",
                "RegularText(Craig,)",
                "Email(cball@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(a2c20d6856545b145bc76cdfaffd04ddad4e58d73b2352dcc5de86aa4ba38e7b)",
                "HashTag(#373)",
                "RegularText(1%)",
                "RegularText(21fadb…3d8f6f,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(21fadb45755a5f41d1b84ecf4610657dd9336d24419d61efffb947aeec3d8f6f)",
                "HashTag(#374)",
                "RegularText(1%)",
                "RegularText(castaway,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(0cbde76a61cc539059f7da7b4fb19c0197f9f781674d307b52264cbb0144c739)",
                "HashTag(#375)",
                "RegularText(1%)",
                "RegularText(chames,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(a721f4370afd51fcbc7e2a685f24a454f14fea84448e1c2aa4a9a94b89f3ea7d)",
                "HashTag(#376)",
                "RegularText(1%)",
                "RegularText(laura,)",
                "Email(laura@nostrich.zone)",
                "RegularText(-)",
                "RegularText(ac2250f83aaa7c4a8503f9c15c0cc11ac992315e5ac3e634541223a8deb6c09c)",
                "HashTag(#377)",
                "RegularText(1%)",
                "RegularText(Kaz,)",
                "Email(kaz@reddirtmining.io)",
                "RegularText(-)",
                "RegularText(826d71153f4938c43b930f90cc3130f33430d1e069d43a2f705f9538450b9369)",
                "HashTag(#378)",
                "RegularText(1%)",
                "RegularText(Verismus,)",
                "Email(verismus@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(9e79aed207461f0d5ebc2c8b94e6875e2a6d5dd15990f8ea3ad2540786d07528)",
                "HashTag(#379)",
                "RegularText(1%)",
                "RegularText(cafc4f…107e85,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(cafc4fbaa558e466bba6c667fcf14506728ff70975f2817c8e5b6fb062107e85)",
                "HashTag(#380)",
                "RegularText(1%)",
                "RegularText(bitpetro,)",
                "Email(bitpetro@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(22470b963e71fa04e1f330ce55f66ff9783c7a9c4851b903d332a59f2327891e)",
                "HashTag(#381)",
                "RegularText(1%)",
                "RegularText(nossence,)",
                "Email(nossence@nossence.xyz)",
                "RegularText(-)",
                "RegularText(56899e6a55c14771a45a88cb90a802623a0e3211ea1447057e2c9871796ce57c)",
                "HashTag(#382)",
                "RegularText(1%)",
                "RegularText(The)",
                "RegularText(Progressive)",
                "RegularText(Bitcoiner,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(4870d5500a121e5187544a3e6e5c2fee1d0a03e1b85073f27edb710b110d6208)",
                "HashTag(#383)",
                "RegularText(1%)",
                "RegularText(orangepillstacker,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(affe861d3e4c42bb956a35d8f9d2c76a99ba16581f3d0dbf762d807e1de8e234)",
                "HashTag(#384)",
                "RegularText(1%)",
                "RegularText(Nostrdamus,)",
                "Email(manbearpig@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(84a42d3efa48018e187027e2bbdd013285a27d8faf970f83a35691d7e2e1a310)",
                "HashTag(#385)",
                "RegularText(1%)",
                "RegularText(JohnSmith,)",
                "Email(johnsmith@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(7c939a7211f1b818567d10b7e65bb03e2830420acf3d6f4f65a7320e2e66d97e)",
                "HashTag(#386)",
                "RegularText(1%)",
                "RegularText(Matty,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(1cb599e80e7933a7144bbebfb39168c6ee75a27bacd6d8a67e80c442a32a52a8)",
                "HashTag(#387)",
                "RegularText(1%)",
                "RegularText(epodrulz,)",
                "Email(bitcoin@bitcoinedu.com)",
                "RegularText(-)",
                "RegularText(a249234ba07c832c8ee99915f145c02838245499589a6ab8a7461f2ef3eec748)",
                "HashTag(#388)",
                "RegularText(1%)",
                "RegularText(paul,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(52b9e1aca3df269710568d1caa051abf40fbdf8c2489afb8d2b7cdb1d1d0ce6f)",
                "HashTag(#389)",
                "RegularText(1%)",
                "RegularText(0ec37a…ba5855,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(0ec37a784c894b8c8f96a0ccb6055d4ce7b8420482bc41d00e235723a9ba5855)",
                "HashTag(#390)",
                "RegularText(1%)",
                "RegularText(jor,)",
                "Email(knggolf@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(7c765d407d3a9d5ea117cb8b8699628560787fc084a0c76afaa449bfbd121d84)",
                "HashTag(#391)",
                "RegularText(1%)",
                "RegularText(Nighthaven,)",
                "Email(nighthaven@iris.to)",
                "RegularText(-)",
                "RegularText(510e0096e4e622e9f2877af7e7af979ac2fdf50702b9cd77021658344d1a682c)",
                "HashTag(#392)",
                "RegularText(1%)",
                "RegularText(00f454…929254,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(00f45459dcd6c6e04706ddafd03a9f52a28833efc04b3ff0a66b89146b929254)",
                "HashTag(#393)",
                "RegularText(1%)",
                "RegularText(XBT_fi,)",
                "Email(xbt_fi@iris.to)",
                "RegularText(-)",
                "RegularText(6e1bee4bdfc34056ffcde2c0685ae6468867aedd0843ed5d0cfcde41f64bfda8)",
                "HashTag(#394)",
                "RegularText(1%)",
                "RegularText(e9f332…6474aa,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(e9f33272af64080287624176253ed2b468d17cec5f2a3d927a3ee36c356474aa)",
                "HashTag(#395)",
                "RegularText(1%)",
                "RegularText(ulrichard,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(cd0ea239c10e2dbe12e5171537ff0b8619747bfcd8dcf939f4bceed340b38c87)",
                "HashTag(#396)",
                "RegularText(1%)",
                "RegularText(54ff28…d7090d,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(54ff28f1abbceddea50cf35cac69e5df32b982c3e872d40aa9ec035431d7090d)",
                "HashTag(#397)",
                "RegularText(1%)",
                "RegularText(GeneralCarlosQ17,)",
                "Email(gencarlosq17@iris.to)",
                "RegularText(-)",
                "RegularText(b13cc2d0b7b70ba41c13f09cc78dc6ce7f72049b1fe59a8194a237e23e37216e)",
                "HashTag(#398)",
                "RegularText(1%)",
                "RegularText(BitcoinIslandPH,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(b4ab403c8215e0606f11be21670126a501d85ea2027b6d15bf4b54c3236d0994)",
                "HashTag(#399)",
                "RegularText(1%)",
                "RegularText(rotciv,)",
                "Email(rotciv@plebs.place)",
                "RegularText(-)",
                "RegularText(b70c9bfb254b6072804212643beb077b6ba941609ed40515d9b10961d7767899)",
                "HashTag(#400)",
                "RegularText(1%)",
                "RegularText(Alfa,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(0575bc052fed6c729a0ab828efa45da77e28685da91bdfebc7a7640cb0728d12)",
                "HashTag(#401)",
                "RegularText(1%)",
                "RegularText(ben_dewaal,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(aac02781318dfc8c3d7ed0978ef9a7e8154a6b8ae6c910b3a52b42fd56875002)",
                "HashTag(#402)",
                "RegularText(1%)",
                "RegularText(cguida,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(2895c330c23f383196c0ef988de6da83b83b4583ed5f9c1edb0a559cecd1f900)",
                "HashTag(#403)",
                "RegularText(1%)",
                "RegularText(nout,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(52cb4b34775fa781b6a964bda0432dbcdfede7a59bf8dfc279cbff0ad8fb09ff)",
                "HashTag(#404)",
                "RegularText(1%)",
                "RegularText(Merlin,)",
                "Email(Merlin@bitcoinnostr.com)",
                "RegularText(-)",
                "RegularText(76dd32f31619b8e35e9f32e015224b633a0df8be8d5613c25b8838a370407698)",
                "HashTag(#405)",
                "RegularText(1%)",
                "RegularText(millymischiefx,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(868d9200af6e6fe1604a28d587b30c2712100b0edab76982551d56ebc6ae061f)",
                "HashTag(#406)",
                "RegularText(1%)",
                "RegularText(yegorpetrov(alternative),)",
                "Email(yeg0rpetrov@iris.to)",
                "RegularText(-)",
                "RegularText(2650f1f87e1dc974ffcc7b5813a234f6f1b1c92d56732f7db4fef986c80a31f7)",
                "HashTag(#407)",
                "RegularText(1%)",
                "RegularText(baloo,)",
                "Email(baloo@nostrpurple.com)",
                "RegularText(-)",
                "RegularText(c49f8402ef410fce5a1e5b2e6da06f351804988dd2e0bad18ae17a50fc76c221)",
                "HashTag(#408)",
                "RegularText(1%)",
                "RegularText(jamesgospodyn,)",
                "Email(jamesgospodyn@nostr.theorangepillapp.com)",
                "RegularText(-)",
                "RegularText(11edfa8182cf3d843ef36aa2fa270137d1aee9e4f0cd2add67707c8fc5ff2a0d)",
                "HashTag(#409)",
                "RegularText(1%)",
                "RegularText(Mysterious_Minx,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(381dbcc7138eab9a71e814c57837c9d623f4036ec0240ef302330684ffc8b38f)",
                "HashTag(#410)",
                "RegularText(1%)",
                "RegularText(878bf5…f7cb86,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(878bf5d63ed5b13d2dac3f463e1bd73d0502bd3462ebf2ea3a0825ca11f7cb86)",
                "HashTag(#411)",
                "RegularText(1%)",
                "RegularText(carl,)",
                "Email(carl@armadalabs.studio)",
                "RegularText(-)",
                "RegularText(cd1197bede3b3c0cdc7412d076228e3f48b5b66e88760f53142e91485d128e07)",
                "HashTag(#412)",
                "RegularText(1%)",
                "RegularText(NIMBUS,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(c48a8ced6dfcc450056bb069b4007607c68a3e93cf3ae6e62b75bf3509f78178)",
                "HashTag(#413)",
                "RegularText(1%)",
                "RegularText(btcportal,)",
                "Email(btcportal@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(9fc1e0ef750dba8cdb3b360b8a00ccad6dcef6b7ad7644f628e952ed8b7eebfb)",
                "HashTag(#414)",
                "RegularText(1%)",
                "RegularText(9652ba…ccd3f1,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(9652ba74b6981f69a3ffad088aa0f16c8af7fe38a72e5d82176878acdcccd3f1)",
                "HashTag(#415)",
                "RegularText(1%)",
                "RegularText(mjbonham,)",
                "Email(mjb@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(802afdddebfb60a516b39d649ea35401749622e394f85a687674907c4588dc7a)",
                "HashTag(#416)",
                "RegularText(1%)",
                "RegularText(⌜Jan⌝,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(fca142a3a900fed71d831aa0aa9c21bb86a5917a9e1183659857b684f25ae1ce)",
                "HashTag(#417)",
                "RegularText(1%)",
                "RegularText(DontTraceMeBruh,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(3fef59378dce7726d3ef35d4699f57becf76d3be0a13187677126a66c9ade3b8)",
                "HashTag(#418)",
                "RegularText(1%)",
                "RegularText(9a73c0…1707f2,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(9a73c0ecd5049ae38b50d0d9eaaabd49390cdd08c3d3d666d0d8476c411707f2)",
                "HashTag(#419)",
                "RegularText(1%)",
                "RegularText(esbewolkt,)",
                "Email(esbewolkt@nostr.fan)",
                "RegularText(-)",
                "RegularText(50ea483ddffeeed3231c6f41fddfe8fb71f891fa736de46e3e06f748bbdeb307)",
                "HashTag(#420)",
                "RegularText(1%)",
                "RegularText(morningstar,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(82671c61fa007b0f70496dec2420238efd3df2f76cdaf6c1f810def8ce95ba45)",
                "HashTag(#421)",
                "RegularText(1%)",
                "RegularText(Sweedgraffixx,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(ee5f4a67cb434317dd7b931d9d23cb2978ab728a008e4c4dcca9cc781d3ae576)",
                "HashTag(#422)",
                "RegularText(1%)",
                "RegularText(878492…165b4f,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(878492807168be8dfbae71d721a9b7f6833a9928fcf9acc3274dfdb113165b4f)",
                "HashTag(#423)",
                "RegularText(1%)",
                "RegularText(koukos,)",
                "Email(koukos@iris.to)",
                "RegularText(-)",
                "RegularText(4260122b8a141e888413082dea2d93568488bae4726358e9e6b7da741852dfc8)",
                "HashTag(#424)",
                "RegularText(1%)",
                "RegularText(nopara73,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(001892e9b48b430d7e37c27051ff7bf414cbc52a7f48f451d857409ce7839dde)",
                "HashTag(#425)",
                "RegularText(1%)",
                "RegularText(Be⚡BANK,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(fbfb3855d50c37866af00484a6476680ae1e2ff04ceb9dd8936465f70d39150b)",
                "HashTag(#426)",
                "RegularText(1%)",
                "RegularText(davekrock,)",
                "Email(davekrock@NostrVerified.com)",
                "RegularText(-)",
                "RegularText(e26b5f261cb29354def8a8ba6af49b137e3144388a81ef78eed8e77cfb18fd44)",
                "HashTag(#427)",
                "RegularText(1%)",
                "RegularText(BitcoinLoveLife,)",
                "Email(Bitcoinlovelife@BitcoinNostr.com)",
                "RegularText(-)",
                "RegularText(3c08d854ef6c86b1dc11159fdabc09209eaeba01790ce96690c55787daf3c415)",
                "HashTag(#428)",
                "RegularText(1%)",
                "RegularText(Steam,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(111a1ae50a7e30a465126b0ab10c3eac6ddaa3cca016a4117470e6715a2dfdef)",
                "HashTag(#429)",
                "RegularText(1%)",
                "RegularText(xolag,)",
                "Email(xolagl2@getalby.com)",
                "RegularText(-)",
                "RegularText(fb64b9c3386a9ababaf8c4f80b47c071c4a38f7b8acdc4dafb009875a64f8c37)",
                "HashTag(#430)",
                "RegularText(1%)",
                "RegularText(relay9may,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(1e7fd2177d20c97f326cda699551f085b8e7f93650b48b6e87a0bebcdfeebc8b)",
                "HashTag(#431)",
                "RegularText(1%)",
                "RegularText(f2c817…8a2f3b,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(f2c817a3bbf07517a38beac228a12e3460d18f1ec2ed928d2e6d2e67308a2f3b)",
                "HashTag(#432)",
                "RegularText(1%)",
                "RegularText(remoney,)",
                "Email(remoney@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(3939a929101b17f4782171b5e0e49996fbe2215b226bd847bd76be3c2de80e9a)",
                "HashTag(#433)",
                "RegularText(1%)",
                "RegularText(387eb9…a6f87f,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(387eb9a5c4f43e40e6abd1f6fe953477464ae5830d104e325f362209c2a6f87f)",
                "HashTag(#434)",
                "RegularText(1%)",
                "RegularText(846b76…539eca,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(846b763b1234c5652f1e327e59570dcb6535d2d20589c67c2a9a90b323539eca)",
                "HashTag(#435)",
                "RegularText(1%)",
                "RegularText(Shawn)",
                "RegularText(C.,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(83ea7cb5a3ab517f24eb2948b23f39466dd5f200fd4e6951fed43ba34e9a4a83)",
                "HashTag(#436)",
                "RegularText(1%)",
                "RegularText(roberto,)",
                "Email(roberto@bitcoiner.chat)",
                "RegularText(-)",
                "RegularText(319a588a77cd798b358724234b534bff3f3c294b4f6512bde94d070da93237c9)",
                "HashTag(#437)",
                "RegularText(1%)",
                "RegularText(LazyNinja,)",
                "Email(cryptolazyninja@stacker.news)",
                "RegularText(-)",
                "RegularText(ff444d454bc6ba2c16abdfd843124e6ad494297cf424fa81fb0604a24ee188e2)",
                "HashTag(#438)",
                "RegularText(1%)",
                "RegularText(e5ae7b…c8b2ef,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(e5ae7b9cc5177675654400db194878601ee8ff5c355acb85daa50f7551c8b2ef)",
                "HashTag(#439)",
                "RegularText(1%)",
                "RegularText(kimymt,)",
                "Email(kimymt@getalby.com)",
                "RegularText(-)",
                "RegularText(3009318aa9544a2caf401ece529fd772e26cdd7e60349ec175423b302dafd521)",
                "HashTag(#440)",
                "RegularText(1%)",
                "RegularText(z_hq,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(215e2d416a8663d5b2e44f30d6c46750db7254cdbd2cf87fea4c1549d97486d4)",
                "HashTag(#441)",
                "RegularText(1%)",
                "RegularText(Reza,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(e7c0d1e42929695b972e90e88fb2210b3567af45206aac51fff85ba011f79093)",
                "HashTag(#442)",
                "RegularText(1%)",
                "RegularText(benderlogic,)",
                "Email(benderlogic@rogue.earth)",
                "RegularText(-)",
                "RegularText(d656ffcaf523f15899db0ea3289d04d00528714651d624814695cabe9cb34114)",
                "HashTag(#443)",
                "RegularText(1%)",
                "RegularText(maestro,)",
                "Email(MAESTRO@BitcoinNostr.com)",
                "RegularText(-)",
                "RegularText(8c3e08bbc47297021be7e6e2c59dab237fab9056b3a5302a8cd2fc2959037466)",
                "HashTag(#444)",
                "RegularText(1%)",
                "RegularText(travis,)",
                "Email(travis@west.report)",
                "RegularText(-)",
                "RegularText(3dc0b75592823507f5f625f889d36ba2607487550b4f38335a603eda010f2bc2)",
                "HashTag(#445)",
                "RegularText(1%)",
                "RegularText(Coffee)",
                "RegularText(Lover,)",
                "Email(coffeelover@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(9ecbaa6dc307291c3cf205c8a79ad8174411874cf244ca06f58a5a73e491222c)",
                "HashTag(#446)",
                "RegularText(1%)",
                "RegularText(shadowysuperstore,)",
                "Email(shadowysuperstore@shadowysuperstore.com)",
                "RegularText(-)",
                "RegularText(7abbf3067536c6b70fbc8ac1965e485dce6ebb3d5c125aac248bc0fe906c6818)",
                "HashTag(#447)",
                "RegularText(1%)",
                "RegularText(bhaskar,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(5beb5d04939db36498e0736003771294317c1c018953d18433276a042bf9a39d)",
                "HashTag(#448)",
                "RegularText(1%)",
                "RegularText(kylum)",
                "RegularText(🟣,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(e651489d08a27970aac55b222b8a3ea5f3c00419f2976a3cf4006f3add2b6f3c)",
                "HashTag(#449)",
                "RegularText(1%)",
                "RegularText(特立独行的李员外,)",
                "Email(npub1wg2dsjnh0g7phheq23v288k0mj8x75fffmq7rghtkhv53027hnassf4w8t@nost.vip)",
                "RegularText(-)",
                "RegularText(7214d84a777a3c1bdf205458a39ecfdc8e6f51294ec1e1a2ebb5d948bd5ebcfb)",
                "HashTag(#450)",
                "RegularText(1%)",
                "RegularText(eynhaender,)",
                "Email(eynhaender@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(a21babb54929f10164ca8f8fcca5138d25a892c32fabc8df7d732b8b52b68d82)",
                "HashTag(#451)",
                "RegularText(1%)",
                "RegularText(8340fd…8c7a30,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(8340fd16fb4414765af8f59192ed68814920e7d33522709de2457490c28c7a30)",
                "HashTag(#452)",
                "RegularText(1%)",
                "RegularText(B1ackSwan,)",
                "Email(b1ackswan@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(1f695a6883cef577dcebf9c60041111772a64e3490cb299c3b97fc81ad3901f4)",
                "HashTag(#453)",
                "RegularText(1%)",
                "RegularText(91dac4…599398,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(91dac44e3f9d0e3b839aaf7fd81e6c19cf2ce02356fca5096af9e92f58599398)",
                "HashTag(#454)",
                "RegularText(1%)",
                "RegularText(356e99…fc3ba8,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(356e99a0f75e973c0512873cbdce0385df39712653020af825556ceb4afc3ba8)",
                "HashTag(#455)",
                "RegularText(1%)",
                "RegularText(mcdean,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(54def063abe1657a22cc886eaba75f6636845c601efe9ad56709b4cb3dcc62f1)",
                "HashTag(#456)",
                "RegularText(1%)",
                "RegularText(mrbitcoin,)",
                "Email(mrbitc0in@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(da41332116804e9c4396f6dbb77ec9ad338197993e9d8af18f332e53dcc1bfeb)",
                "HashTag(#457)",
                "RegularText(1%)",
                "RegularText(Jedi,)",
                "Email(jedi@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(246498aa79542482499086f9ab0134750a23047dad0cca38b696750f9ed8072c)",
                "HashTag(#458)",
                "RegularText(1%)",
                "RegularText(CloudNull,)",
                "Email(cloudnull@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(5f53baca8cb88a18320a032957bf0b6f8dc8b33db007310b0e2f573edf2703a3)",
                "HashTag(#459)",
                "RegularText(1%)",
                "RegularText(Mrwh0,)",
                "Email(Mrwh0@Mrwh0.github.io)",
                "RegularText(-)",
                "RegularText(d8dd77e3dff24bd8c2da9b4c4fb321f5f99e8713bad40dd748ab59656b5ed27d)",
                "HashTag(#460)",
                "RegularText(1%)",
                "RegularText(shinohai,)",
                "Email(shinohai@iris.to)",
                "RegularText(-)",
                "RegularText(4bc7982c4ee4078b2ada5340ae673f18d3b6a664b1f97e8d6799e6074cb5c39d)",
                "HashTag(#461)",
                "RegularText(1%)",
                "RegularText(awoi,)",
                "Email(awoi@iris.to)",
                "RegularText(-)",
                "RegularText(edc083016d344679566ae8205b362530ecbafc6e064e224a0c2df1850cecfb4a)",
                "HashTag(#462)",
                "RegularText(1%)",
                "RegularText(TheShopRat,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(8362e77d9fd268720a15840af33fd9ab5cdf13fabc66f0910111580960cd297a)",
                "HashTag(#463)",
                "RegularText(1%)",
                "RegularText(Dajjal,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(614aee83d7eaffc7bc6bbf02feda0cc53e7f97eeceac08a897c4cea3c023b804)",
                "HashTag(#464)",
                "RegularText(1%)",
                "RegularText(felipe,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(0ee8894f1f663fd76b682c16e6a92db0fe14ada98db35b4a4cfa5f9068be0b3a)",
                "HashTag(#465)",
                "RegularText(1%)",
                "RegularText(crypt0-j3sus,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(9a7b7cbe37b2caa703062c51b207eb6ec4c42d06bfa909d979aa2d5005ac3d65)",
                "HashTag(#466)",
                "RegularText(1%)",
                "RegularText(Just)",
                "RegularText(J,)",
                "Email(jcope101@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(5f6f376733b1a8682a0f330e07b6a6064d738fdd8159db6c8df44c6c9419ff88)",
                "HashTag(#467)",
                "RegularText(1%)",
                "RegularText(mmasnick,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(4d53de27a24feb84d6383962e350219fc09e572c22a17c542545a69cd35b067f)",
                "HashTag(#468)",
                "RegularText(1%)",
                "RegularText(Murmur,)",
                "Email(murmur@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(f7e84b92a5457546894daedaff9abd66f3d289f92435d6ac068a33cb170b01a4)",
                "HashTag(#469)",
                "RegularText(1%)",
                "RegularText(JD,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(1a9ba80629e2f8f77340ac13e67fdb4fcc66f4bb4124f9beff6a8c75e4ce29b0)",
                "HashTag(#470)",
                "RegularText(1%)",
                "RegularText(dario,)",
                "Email(dario@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(d9987652d3cbb2c0fa39b6305cc0f2d03ca987afc1e56bc97a81c79e138152a8)",
                "HashTag(#471)",
                "RegularText(1%)",
                "RegularText(leonwankum,)",
                "RegularText(@)",
                "Email(leonawankum@BitcoinNostr.com)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(652d58acafa105af8475c0fe8029a52e7ddbc337b2bd9c98bb17a111dc4cde60)",
                "HashTag(#472)",
                "RegularText(1%)",
                "RegularText(phil,)",
                "Email(phil@iris.to)",
                "RegularText(-)",
                "RegularText(8352b55a828a60bb0e86b0ac9ef1928999ebe636c905dcbe0cd3c0f95c61b83b)",
                "HashTag(#473)",
                "RegularText(1%)",
                "RegularText(hkmccullough,)",
                "Email(thatirdude@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(836059a05aeb8498dd53a0d422e04aced6b4b71eb3621d312626c46715d259d8)",
                "HashTag(#474)",
                "RegularText(1%)",
                "RegularText(BitBox,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(5a3de28ffd09d7506cff0a2672dbdb1f836307bcff0217cc144f48e19eea3fff)",
                "HashTag(#475)",
                "RegularText(1%)",
                "RegularText(5eff6c…60bd07,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(5eff6c1205c9db582863978b5b2e9c9aa73a57e6c1df526fddc2b9996060bd07)",
                "HashTag(#476)",
                "RegularText(1%)",
                "RegularText(nobody,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(2e472c6d072c0bcc28f1b260e0fc309f1f919667d238f4e703f8f1db0f0eb424)",
                "HashTag(#477)",
                "RegularText(1%)",
                "RegularText(K_hole,)",
                "Email(K_hole@ketamine.com)",
                "RegularText(-)",
                "RegularText(5ac74532e23b7573f8f6f3248fe5174c0b7230aec0b653c0ec8f11d540209fd7)",
                "HashTag(#478)",
                "RegularText(1%)",
                "RegularText(bitcoinIllustrated,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(90fb6b9607bba40686fe70aad74a07e5af96d152778f3a09fcda5967dcb0daba)",
                "HashTag(#479)",
                "RegularText(1%)",
                "RegularText(kingfisher,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(33d4c61d7354e1d5872e26218eda73170646d12a8e7b9cb6d3069a7058ebabfd)",
                "HashTag(#480)",
                "RegularText(1%)",
                "RegularText(cfc11e…b4f6e4,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(cfc11ef4b31e2ab18261a71b79097c60199f532605a0c3aa73ad36acc6b4f6e4)",
                "HashTag(#481)",
                "RegularText(1%)",
                "RegularText(d06848…2f86b3,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(d06848a9ea53f9e9c15cafaf41b1729d6d7b84083cfbac2c76a0506dd72f86b3)",
                "HashTag(#482)",
                "RegularText(1%)",
                "RegularText(nostrceo,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(3159e1a148ca235cb55365a2ffde608b17e84c4c3bff6ed309f3e320307d5ab3)",
                "HashTag(#483)",
                "RegularText(1%)",
                "RegularText(Lokuyow2,)",
                "Email(2@lokuyow.github.io)",
                "RegularText(-)",
                "RegularText(f5f02030cb4b22ed15c3d7cc35ae616e6ce6bb3fa537f6e9e91aaa274b9cd716)",
                "HashTag(#484)",
                "RegularText(1%)",
                "RegularText(fatushi,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(49a458319060806221990e90e6bf2b1654201f08a40828d1a5d215a85f449df0)",
                "HashTag(#485)",
                "RegularText(1%)",
                "RegularText(Omnia,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(026d2251aa211684ef63e7a28e21c611c087bb3131a9c90b11dff6c16d68ce77)",
                "HashTag(#486)",
                "RegularText(1%)",
                "RegularText(joey,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(5f8a5bbf8d26104547a3942e82d7a5159554b3a5a3bc1275c47674b5e8c4c1d7)",
                "HashTag(#487)",
                "RegularText(1%)",
                "RegularText(Hazey,)",
                "Email(hazey@iris.to)",
                "RegularText(-)",
                "RegularText(800e0fe3d8638ce3f75a56ed865df9d96fc9d9cd2f75550df0d7f5c1d8468b0b)",
                "HashTag(#488)",
                "RegularText(1%)",
                "RegularText(Milad)",
                "RegularText(Younis,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(64c24e0991f9bb6f59f9da486ba29242bc562b09ce051882f7b3bcc7fd055227)",
                "HashTag(#489)",
                "RegularText(1%)",
                "RegularText(jlgalley,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(920535dd1487975ccc75ed82b7b4753260ec4041dcf9ce24657623164f6586e3)",
                "HashTag(#490)",
                "RegularText(1%)",
                "RegularText(paulgallo28,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(690af9eed15cc3a7439c39b228bf194da134f75d64f40114a41d77bff6a60699)",
                "HashTag(#491)",
                "RegularText(1%)",
                "RegularText(HeineNon,)",
                "Email(HeineNon@tomottodx.github.io)",
                "RegularText(-)",
                "RegularText(64c66c231ea1c25ebd66b14fe4a0b1b39a6928d6824ad43e035f54aa667bc650)",
                "HashTag(#492)",
                "RegularText(1%)",
                "RegularText(a9b9ad…2b9f4c,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(a9b9ad000e2ada08326bbcc1836effcdfa4e64b9c937e406fe5912dc562b9f4c)",
                "HashTag(#493)",
                "RegularText(1%)",
                "RegularText(legxxi,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(8476d0dcdb53f1cc67efc8d33f40104394da2d33e61369a8a8ade288036977c6)",
                "HashTag(#494)",
                "RegularText(1%)",
                "RegularText(99f1b7…559c31,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(99f1b7b39201d0e142f9ec3c8101b6be0eee8a389d16d53667ca4f57b1559c31)",
                "HashTag(#495)",
                "RegularText(1%)",
                "RegularText(mbz,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(e5195850d4fed08183f0b274ca30777094daad67be235a5cd15548b9b0341031)",
                "HashTag(#496)",
                "RegularText(1%)",
                "RegularText(Titan,)",
                "Email(titan@nostrplebs.com)",
                "RegularText(-)",
                "RegularText(672b1637bd65b6206c7a603158c2ecee15599648e10dd15a82f2fcb4e47735bf)",
                "HashTag(#497)",
                "RegularText(1%)",
                "RegularText(Highlandhodl,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(f0c74190cd05d85d843cdc5f355afe0fbac6d30d18da91243d6cae30a69713f7)",
                "HashTag(#498)",
                "RegularText(1%)",
                "RegularText(CodeWarrior,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(21a7014db2ba17acc8bbb9496645084866b46e1ba0062a80513afda405450183)",
                "HashTag(#499)",
                "RegularText(1%)",
                "SchemelessUrl(baller.hodl)",
                "RegularText(,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(d8150dc0631f834a004f231f0747d5ec8409b1a9214d246f675dfef39807a224)",
                "HashTag(#500)",
                "RegularText(1%)",
                "RegularText(Now)",
                "RegularText(Playing)",
                "RegularText(on)",
                "RegularText(GM₿,)",
                "RegularText()",
                "RegularText(-)",
                "RegularText(9c6907de72e59daf5272103a34649bf7ca01050a68f402955520fc53dba9730d)",
                "RegularText()",
                "RegularText(Inspector monitor)",
                "RegularText()",
                "RegularText(New events inspected today: 720.71K (4.85GB))",
                "RegularText(Average events inspected per second: 8.34)",
                "RegularText(Uptime: Server 99.93%, NostrInspector: 99.93%)",
                "RegularText(Spam estimate:)",
                "RegularText(74.12 %)",
                "RegularText()",
                "RegularText(About the NostrInspector Report)",
                "RegularText()",
                "RegularText(✅ The 24 Hour NostrInspector Report is generated by listening for new events on the top relays using the Nostr Protocol. The statistics report that)",
                "RegularText(it generates includes de data layer as well as the social layer.)",
                "RegularText(💜 To support this free effort share, like, comment or zap.)",
                "RegularText(🫂 Thank you 🙏)",
                "RegularText()",
                "RegularText(🕵️ @nostrin \"The Nostr Inspector\")",
                "Bech(npub17m7f7q08k4x746s2v45eyvwppck32dcahw7uj2mu5txuswldgqkqw9zms7)",
            )

        state.paragraphs
            .map { it.words }
            .flatten()
            .forEachIndexed { index, seg ->
                assertEquals(
                    expectedResult[index],
                    "${seg::class.simpleName!!.replace("Segment", "")}(${seg.segmentText})",
                )
            }

        assertTrue(state.mediaForPager.isEmpty())
        assertTrue(state.mediaList.isEmpty())
        assertTrue(state.customEmoji.isEmpty())
        assertEquals(651, state.paragraphs.size)
    }

    @Test
    fun testShortTextToParse() {
        val state =
            RichTextParser()
                .parseText("Hi, how are you doing? ", EmptyTagList, null)
        assertTrue(state.urlSet.withoutScheme.isEmpty())
        assertTrue(state.urlSet.withScheme.isEmpty())
        assertTrue(state.urlSet.emails.isEmpty())
        assertTrue(state.mediaForPager.isEmpty())
        assertTrue(state.mediaList.isEmpty())
        assertTrue(state.customEmoji.isEmpty())
        assertEquals(
            "Hi, how are you doing?",
            state.paragraphs
                .firstOrNull()
                ?.words
                ?.firstOrNull()
                ?.segmentText,
        )
    }

    @Test
    fun testShortNewLinesTextToParse() {
        val state =
            RichTextParser().parseText("\nHi, \nhow\n\n\n are you doing? \n", EmptyTagList, null)
        assertTrue(state.urlSet.withoutScheme.isEmpty())
        assertTrue(state.urlSet.withScheme.isEmpty())
        assertTrue(state.urlSet.emails.isEmpty())
        assertTrue(state.mediaForPager.isEmpty())
        assertTrue(state.mediaList.isEmpty())
        assertTrue(state.customEmoji.isEmpty())
        assertEquals(
            "\nHi,\nhow\n\n\n are you doing?",
            state.paragraphs.joinToString("\n") { it.words.joinToString(" ") { it.segmentText } },
        )
    }

    @Test
    fun testTrailingWhitespaceIsRemoved() {
        val state =
            RichTextParser().parseText("Hi, how are you doing? \n\n  \n", EmptyTagList, null)

        assertEquals(1, state.paragraphs.size)
        assertEquals(
            "Hi, how are you doing?",
            state.paragraphs
                .firstOrNull()
                ?.words
                ?.firstOrNull()
                ?.segmentText,
        )
    }

    @Test
    fun testTrailingNewLinesAfterImageAreRemoved() {
        val state =
            RichTextParser().parseText(
                "Check this out\nhttps://cdn.nostr.build/image.jpg\n\n\n",
                EmptyTagList,
                null,
            )

        assertEquals(2, state.paragraphs.size)
        val lastWord =
            state.paragraphs
                .last()
                .words
                .single()
        assertEquals(
            "Image(https://cdn.nostr.build/image.jpg)",
            "${lastWord::class.simpleName!!.replace("Segment", "")}(${lastWord.segmentText})",
        )
    }

    @Test
    fun testWhitespaceOnlyContentProducesNoParagraphs() {
        val state = RichTextParser().parseText(" \n \n ", EmptyTagList, null)

        assertTrue(state.paragraphs.isEmpty())
    }

    @Test
    fun testMultiLine() {
        val text =
            """
            Did you know you can embed #Nostr live streams into #Nostr long-form posts? Sounds like an obvious thing, but it's only supported by nostr:npub1048qg5p6kfnpth2l98kq3dffg097tutm4npsz2exygx25ge2k9xqf5x3nf at the moment.

            See how it can be done here: https://lnshort.it/live-stream-embeds/

            https://nostr.build/i/fd53fcf5ad950fbe45127e4bcee1b59e8301d41de6beee211f45e344db214e8a.jpg
            """.trimIndent()

        val state =
            RichTextParser()
                .parseText(text, EmptyTagList, null)

        val urls = state.urlSet.withScheme.toList()
        val bech = state.urlSet.bech32s.toList()

        assertEquals(
            "https://lnshort.it/live-stream-embeds/",
            urls[0],
        )
        assertEquals(
            "https://nostr.build/i/fd53fcf5ad950fbe45127e4bcee1b59e8301d41de6beee211f45e344db214e8a.jpg",
            urls[1],
        )
        assertEquals(
            "nostr:npub1048qg5p6kfnpth2l98kq3dffg097tutm4npsz2exygx25ge2k9xqf5x3nf",
            bech[0],
        )
        assertEquals(
            "https://nostr.build/i/fd53fcf5ad950fbe45127e4bcee1b59e8301d41de6beee211f45e344db214e8a.jpg",
            state.mediaForPager.keys.firstOrNull(),
        )
        assertEquals(
            "https://nostr.build/i/fd53fcf5ad950fbe45127e4bcee1b59e8301d41de6beee211f45e344db214e8a.jpg",
            state.mediaList.firstOrNull()?.url,
        )
        assertTrue(state.customEmoji.isEmpty())

        printStateForDebug(state)

        val expectedResult =
            listOf<String>(
                "RegularText(Did)",
                "RegularText(you)",
                "RegularText(know)",
                "RegularText(you)",
                "RegularText(can)",
                "RegularText(embed)",
                "HashTag(#Nostr)",
                "RegularText(live)",
                "RegularText(streams)",
                "RegularText(into)",
                "HashTag(#Nostr)",
                "RegularText(long-form)",
                "RegularText(posts?)",
                "RegularText(Sounds)",
                "RegularText(like)",
                "RegularText(an)",
                "RegularText(obvious)",
                "RegularText(thing,)",
                "RegularText(but)",
                "RegularText(it's)",
                "RegularText(only)",
                "RegularText(supported)",
                "RegularText(by)",
                "Bech(nostr:npub1048qg5p6kfnpth2l98kq3dffg097tutm4npsz2exygx25ge2k9xqf5x3nf)",
                "RegularText(at)",
                "RegularText(the)",
                "RegularText(moment.)",
                "RegularText()",
                "RegularText(See)",
                "RegularText(how)",
                "RegularText(it)",
                "RegularText(can)",
                "RegularText(be)",
                "RegularText(done)",
                "RegularText(here:)",
                "Link(https://lnshort.it/live-stream-embeds/)",
                "RegularText()",
                "Image(https://nostr.build/i/fd53fcf5ad950fbe45127e4bcee1b59e8301d41de6beee211f45e344db214e8a.jpg)",
            )

        state.paragraphs
            .map { it.words }
            .flatten()
            .forEachIndexed { index, seg ->
                assertEquals(
                    expectedResult[index],
                    "${seg::class.simpleName!!.replace("Segment", "")}(${seg.segmentText})",
                )
            }
    }

    @Test
    fun testNewLineAfterImage() {
        val text =
            "That’s it ! That’s the #note https://cdn.nostr.build/i/1dc0726b6cb0f94a92bd66765ffb90f6c67e90c17bb957fc3d5d4782cbd73de7.jpg "

        val state =
            RichTextParser()
                .parseText(text, EmptyTagList, null)

        printStateForDebug(state)

        val expectedResult =
            listOf<String>(
                "RegularText(That’s)",
                "RegularText(it)",
                "RegularText(!)",
                "RegularText(That’s)",
                "RegularText(the)",
                "HashTag(#note)",
                "Image(https://cdn.nostr.build/i/1dc0726b6cb0f94a92bd66765ffb90f6c67e90c17bb957fc3d5d4782cbd73de7.jpg)",
            )

        state.paragraphs
            .map { it.words }
            .flatten()
            .forEachIndexed { index, seg ->
                assertEquals(
                    expectedResult[index],
                    "${seg::class.simpleName!!.replace("Segment", "")}(${seg.segmentText})",
                )
            }
    }

    @Test
    fun testSapceAfterImage() {
        val text =
            "That’s it! https://cdn.nostr.build/i/1dc0726b6cb0f94a92bd66765ffb90f6c67e90c17bb957fc3d5d4782cbd73de7.jpg That’s the #note"

        val state =
            RichTextParser()
                .parseText(text, EmptyTagList, null)

        printStateForDebug(state)

        val expectedResult =
            listOf<String>(
                "RegularText(That’s)",
                "RegularText(it!)",
                "Image(https://cdn.nostr.build/i/1dc0726b6cb0f94a92bd66765ffb90f6c67e90c17bb957fc3d5d4782cbd73de7.jpg)",
                "RegularText(That’s)",
                "RegularText(the)",
                "HashTag(#note)",
            )

        state.paragraphs
            .map { it.words }
            .flatten()
            .forEachIndexed { index, seg ->
                assertEquals(
                    expectedResult[index],
                    "${seg::class.simpleName!!.replace("Segment", "")}(${seg.segmentText})",
                )
            }
    }

    @Test
    fun testAVif() {
        val text =
            "Goon Night everybody :sleep:\n" +
                "81ca16-b665-4f57-80cb-11a58461fb61.avif\n" +
                "\n" +
                "https://bae.st/media/66b08dde784287ed8f92c455bc62076a04671ccb44097550626a532185a5d3ed.avif?name=81ca16-b665-4f57-80cb-11a58461fb61.avif"

        val state =
            RichTextParser()
                .parseText(text, EmptyTagList, null)

        printStateForDebug(state)

        val expectedResult =
            listOf<String>(
                "RegularText(Goon Night everybody :sleep:)",
                "Image(https://81ca16-b665-4f57-80cb-11a58461fb61.avif)",
                "Image(https://bae.st/media/66b08dde784287ed8f92c455bc62076a04671ccb44097550626a532185a5d3ed.avif?name=81ca16-b665-4f57-80cb-11a58461fb61.avif)",
            )

        state.paragraphs
            .map { it.words }
            .flatten()
            .forEachIndexed { index, seg ->
                assertEquals(
                    expectedResult[index],
                    "${seg::class.simpleName!!.replace("Segment", "")}(${seg.segmentText})",
                )
            }
    }

    @Test
    fun testUrlsEndingInPeriod() {
        val text = "That’s it! http://vitorpamplona.com/. That’s the note"

        val state =
            RichTextParser()
                .parseText(text, EmptyTagList, null)

        printStateForDebug(state)

        val expectedResult =
            listOf<String>(
                "RegularText(That’s)",
                "RegularText(it!)",
                "Link(http://vitorpamplona.com/)",
                "RegularText(.)",
                "RegularText(That’s)",
                "RegularText(the)",
                "RegularText(note)",
            )

        state.paragraphs
            .map { it.words }
            .flatten()
            .forEachIndexed { index, seg ->
                assertEquals(
                    expectedResult[index],
                    "${seg::class.simpleName!!.replace("Segment", "")}(${seg.segmentText})",
                )
            }
    }

    @Test
    fun testRelayUrls() {
        val text = "If you want to FreeFrom Official \uD80C\uDD66 stream or download the music from  nostr:npub1sctag667a7np6p6ety2up94pnwwxhd2ep8n8afr2gtr47cwd4ewsvdmmjm at wss://relay.damus.io can you here"

        val state =
            RichTextParser()
                .parseText(text, EmptyTagList, null)

        printStateForDebug(state)

        val expectedResult =
            listOf<String>(
                "RegularText(If)",
                "RegularText(you)",
                "RegularText(want)",
                "RegularText(to)",
                "RegularText(FreeFrom)",
                "RegularText(Official)",
                "RegularText(𓅦)",
                "RegularText(stream)",
                "RegularText(or)",
                "RegularText(download)",
                "RegularText(the)",
                "RegularText(music)",
                "RegularText(from)",
                "RegularText()",
                "Bech(nostr:npub1sctag667a7np6p6ety2up94pnwwxhd2ep8n8afr2gtr47cwd4ewsvdmmjm)",
                "RegularText(at)",
                "RelayUrl(wss://relay.damus.io)",
                "RegularText(can)",
                "RegularText(you)",
                "RegularText(here)",
            )

        state.paragraphs
            .map { it.words }
            .flatten()
            .forEachIndexed { index, seg ->
                assertEquals(
                    expectedResult[index],
                    "${seg::class.simpleName!!.replace("Segment", "")}(${seg.segmentText})",
                )
            }
    }

    @Test
    fun testJapaneseWithEmojis() {
        val tags =
            arrayOf(
                arrayOf("t", "ioメシヨソイゲーム"),
                arrayOf("emoji", "_ri", "https://media.misskeyusercontent.com/emoji/_ri.png"),
                arrayOf("emoji", "petthex_japanesecake", "https://media.misskeyusercontent.com/emoji/petthex_japanesecake.gif"),
                arrayOf("emoji", "ai_nomming", "https://media.misskeyusercontent.com/misskey/f6294900-f678-43cc-bc36-3ee5deeca4c2.gif"),
                arrayOf("proxy", "https://misskey.io/notes/9q0x6gtdysir03qh", "activitypub"),
            )
        val text =
            "\u200B:_ri:\u200B\u200B:_ri:\u200Bはﾍﾞｲｸﾄﾞﾓﾁｮﾁｮ\u200B:petthex_japanesecake:\u200Bを食べました\u200B:ai_nomming:\u200B\n" +
                "#ioメシヨソイゲーム\n" +
                "https://misskey.io/play/9g3qza4jow"

        val state =
            RichTextParser().parseText(text, ImmutableListOfLists(tags), null)

        printStateForDebug(state)

        val expectedResult =
            listOf<String>(
                "Emoji(\u200B:_ri:\u200B\u200B:_ri:\u200Bはﾍﾞｲｸﾄﾞﾓﾁｮﾁｮ\u200B:petthex_japanesecake:\u200Bを食べました\u200B:ai_nomming:\u200B)",
                "HashTag(#ioメシヨソイゲーム)",
                "Link(https://misskey.io/play/9g3qza4jow)",
            )

        state.paragraphs
            .map { it.words }
            .flatten()
            .forEachIndexed { index, seg ->
                assertEquals(
                    expectedResult[index],
                    "${seg::class.simpleName!!.replace("Segment", "")}(${seg.segmentText})",
                )
            }
    }

    @Test
    fun testNowhereLinkInTheMiddleOfAPost() {
        val text = "Check out my store https://nowhr.xyz/s#bYxNC4JAEEDRX7LbpcsirR9F1MW0jRDLPsEOMZaHinJTCzOl right now"
        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val nowhere = state.paragraphs.flatMap { it.words }.firstOrNull { it is NowhereLinkSegment } as? NowhereLinkSegment
        assertEquals("https://nowhr.xyz/s#bYxNC4JAEEDRX7LbpcsirR9F1MW0jRDLPsEOMZaHinJTCzOl", nowhere?.segmentText)
        assertEquals("nowhr.xyz", nowhere?.host)
        assertEquals("s", nowhere?.tool)
    }

    @Test
    fun testHostedNowhereSchemelessLink() {
        val text = "see hostednowhere.com/m#bYxNAB end"
        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val nowhere = state.paragraphs.flatMap { it.words }.firstOrNull { it is NowhereLinkSegment } as? NowhereLinkSegment
        assertEquals("hostednowhere.com/m#bYxNAB", nowhere?.segmentText)
        assertEquals("hostednowhere.com", nowhere?.host)
        assertEquals("m", nowhere?.tool)
    }

    @Test
    fun testNowhereLinkWithLargeBase64UrlFragment() {
        // Real nowhere links can be kilobytes long (a full store catalogue or forum thread
        // base64url-encoded). Verify both UrlDetector capture and our classifier survive a
        // ~5KB fragment containing every base64url-legal character (A-Z, a-z, 0-9, -, _).
        val alphabet = ('A'..'Z').joinToString("") + ('a'..'z').joinToString("") + ('0'..'9').joinToString("") + "-_"
        val payload = (0 until 80).joinToString("") { alphabet }
        val url = "https://nowhr.xyz/s#$payload"
        val text = "look at this big store $url and reply"
        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val nowhere = state.paragraphs.flatMap { it.words }.firstOrNull { it is NowhereLinkSegment } as? NowhereLinkSegment
        assertEquals(url, nowhere?.segmentText)
        assertEquals(payload.length, nowhere?.segmentText?.substringAfter('#')?.length)
        assertEquals("nowhr.xyz", nowhere?.host)
        assertEquals("s", nowhere?.tool)
    }

    @Test
    fun testPlainNowhereHostWithoutFragmentStaysAsLink() {
        // No fragment => not a nowhere site, fall through to LinkSegment.
        val text = "Visit https://hostednowhere.com/manage to manage"
        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val nowhere = state.paragraphs.flatMap { it.words }.firstOrNull { it is NowhereLinkSegment }
        assertEquals(null, nowhere)
    }

    private fun printStateForDebug(state: RichTextViewerState) {
        state.paragraphs.forEach { paragraph ->
            paragraph.words.forEach { seg ->
                println(
                    "\"${
                        seg::class.simpleName!!.replace(
                            "Segment",
                            "",
                        )
                    }(${seg.segmentText.replace("\n", "\\n").replace("\"", "\\")})\",",
                )
            }
        }
    }
}
