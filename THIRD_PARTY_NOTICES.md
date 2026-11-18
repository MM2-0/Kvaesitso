# Third-party notices

Telos Launcher is licensed under the GNU General Public License 3.0 (see `LICENSE.txt`). It contains
code, designs and behaviour taken from the projects below. Their copyright notices are preserved
here, as their licenses require. This list is kept up to date together with the "Projects Telos is
based on" table in `readme.md`.

| Project | License | Copyright notice found in the project |
| --- | --- | --- |
| [Guava 33.8.0](https://github.com/google/guava) | Apache-2.0 | Copyright The Guava Authors. Used as unmodified Maven artifacts: `-android` in application dependencies and `-jre` in build-tool dependencies. The full upstream license is retained in `app/app/src/main/assets/licenses/guava-33.8.0-LICENSE.txt`. |
| [jsoup 1.24.1](https://github.com/jhy/jsoup) | MIT | Copyright (c) 2009–2026 Jonathan Hedley. Used as an unmodified Maven dependency. The full upstream copyright and license are retained in `app/app/src/main/assets/licenses/jsoup-1.24.1-LICENSE.txt`. |
| [Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next) (server, starter, JNI; based on [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) and thedjchi's fork) | Apache-2.0 | Copyright of RikkaW / RikkaApps, thedjchi, rushiranpise and Shizuku-Next contributors. Vendored under `libs/shizuku-next`. |
| [Kvaesitso](https://github.com/MM2-0/Kvaesitso) | GPL-3.0 | Copyright (C) 2021-2026 MM2-0 and the Kvaesitso contributors |
| [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) | Apache-2.0 | Copyright 2020-2026 JetBrains s.r.o. and Kotlin Programming Language contributors. Used only by `:desktop-linux` |
| [QEMU](https://www.qemu.org/) (Windows builds from [qemu.weilnetz.de](https://qemu.weilnetz.de/)) | GPL-2.0 | Copyright the QEMU authors and Stefan Weil. Downloaded by `tools/windows-qemu-android/fetch-toolchain.sh` into gitignored `vendor/qemu`; not shipped in the Android APK. |
| [scrcpy](https://github.com/Genymobile/scrcpy) | Apache-2.0 | Copyright Genymobile / Romain Vimont. Downloaded into gitignored `vendor/scrcpy` with platform-tools adb. |
| [BlissOS x86](https://blissos.org/) / [android-x86](https://www.android-x86.org/) | Apache-2.0 / GPL | Guest ISO downloaded into gitignored `images/`; not redistributed by the Telos APK. |
| [labwc](https://github.com/labwc/labwc) | GPL-2.0 | Host Wayland compositor for the Telos Linux session. Not bundled; the user installs the distro package. |
| [Right Dialer (Goodwy/Dialer)](https://github.com/Goodwy/Dialer) | GPL-3.0 | Based by its author on [Simple Dialer](https://github.com/SimpleMobileTools/Simple-Dialer) and [Fossify Phone](https://github.com/FossifyOrg/Phone) |
| [Secure Dialer](https://github.com/Secure-Phone-apps/Secure-Dialer) | GPL-3.0 | Copyright (C) 2026 MovStore |
| [Ever Dialer](https://github.com/hari161008/Ever-Dialer) | GPL-3.0 | Copyright (c) 2024-2026 Ever Dialer Contributors; parts Copyright (C) 2026-present kitsumed (Med) |
| [Thor](https://github.com/trinadhthatakula/Thor) | GPL-3.0-or-later | Copyright (c) 2025-2026 Trinadh Thatakula |
| [Undead Wallpaper](https://github.com/maocide/UndeadWallpaper) | GPL-3.0 | The Undead Wallpaper authors |
| [Aurora Store](https://gitlab.com/AuroraOSS/AuroraStore) | GPL-3.0-or-later | Aurora OSS; installer protocol reference, independently implemented |
| [Obtainium](https://github.com/ImranR98/Obtainium) | GPL-3.0 | The Obtainium authors |
| [Stremio add-on SDK (protocol documentation)](https://github.com/Stremio/stremio-addon-sdk) and [stremio-core](https://github.com/Stremio/stremio-core) (request formats of the account service) and [stremio-watched-bitfield](https://github.com/Stremio/stremio-watched-bitfield) (watched-state format) | MIT | Copyright © 2019 SmartCode OOD. Only the protocol description and the documented request formats were used as a specification; Telos' Stremio client is its own code. Not affiliated with Stremio |
| [iptv-org (API and database)](https://github.com/iptv-org/api) | Unlicense (public domain) | Data only, downloaded at run time (channel list, logos, stream addresses); nothing is bundled. The streams belong to third parties |
| [Free-TV/IPTV](https://github.com/Free-TV/IPTV), [greektvm3u](https://github.com/filipposfilippides/greektvm3u), XMLTV guides | no license stated | Data only, fetched at run time (playlists and programme guide); nothing is bundled or redistributed |
| [Gopeed](https://github.com/GopeedLab/gopeed) | GPL-3.0 | Design ideas only for Telos Downloads, no code copied (copyright: the Gopeed authors) |
| [Ketch](https://github.com/linroid/Ketch) | Apache-2.0 | Design ideas only for Telos Downloads, no code copied (copyright: the Ketch authors) |
| [LibreTorrent](https://github.com/proninyaroslav/libretorrent) | GPL-3.0-or-later | Design ideas only for the torrent part of Telos Downloads (feature set and screens), no code copied (copyright: the LibreTorrent authors) |
| [Kite](https://github.com/zenzer0s/kite) | GPL-3.0 | Design ideas only for the video and audio sites part of Telos Downloads, no code copied (copyright: the Kite authors) |
| [AIO Video Downloader](https://github.com/shibaFoss/AIO-Video-Downloader) | custom licence (text not verified) | Ideas only, **no code used** (copyright: the AIO Video Downloader authors) |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Unlicense (public domain) | Used unchanged, inside the optional media build (see below) |
| [youtubedl-android](https://github.com/yausername/youtubedl-android) (fork io.github.junkfood02.youtubedl-android) | GPL-3.0 | Copyright (c) the youtubedl-android authors (yausername, JunkFood02 and contributors), used unchanged as a Maven dependency in the optional media build |
| [RethinkDNS](https://github.com/celzero/rethink-app) | Apache-2.0 | Copyright (c) Celzero / The Rethink DNS Open Source Project (full notice below) |
| [NetGuard](https://github.com/M66B/NetGuard) | GPL-3.0-only | Copyright © Marcel Bokhorst (M66B) / FairCode B.V. Feature ideas only for Telos Network; no NetGuard source was copied |
| [ADNS](https://github.com/eyalm2000/adns) | MIT | Copyright (c) eyalm2000 and ADNS contributors. Feature ideas only for the native NextDNS editor and Android system Private DNS controls; no ADNS source was copied |
| [FairEmail](https://github.com/M66B/FairEmail) | GPL-3.0 | Copyright © 2018–2026 Marcel Bokhorst. Feature ideas only for Ermis; no FairEmail source was copied |
| [SD Maid SE](https://github.com/d4rken-org/sdmaid-se) | GPL-3.0 | Copyright the SD Maid SE authors (d4rken). Feature ideas for Fill the bin (caches, leftovers, scheduler); no SD Maid source was copied |
| [GAMA](https://github.com/palincat/gama) | see project | Copyright the GAMA authors (palincat). Feature idea for the Vulkan/OpenGL HWUI switch; no GAMA source was copied |
| [firestack](https://github.com/celzero/firestack) | MPL-2.0 | Copyright (c) Celzero / The Rethink DNS Open Source Project; used unchanged as a Maven dependency |
| [WireGuard](https://www.wireguard.com) | see project | Implemented inside firestack; WireGuard is a registered trademark of Jason A. Donenfeld |
| [Transistor](https://codeberg.org/y20k/transistor) | MIT | Copyright (c) 2015-22 - Y20K.org (full notice below) |
| [TagLib](https://github.com/taglib/taglib) | LGPL-2.1 / MPL-1.1 | Audio tag library bundled by the Kyant0 wrapper below |
| [SQLCipher](https://github.com/sqlcipher/sqlcipher) | BSD-style (Zetetic) | Copyright (c) 2025 ZETETIC LLC. Encrypted database for Telos Phone data, using [SQLCipher for Android](https://github.com/sqlcipher/sqlcipher-android) |
| [Dhizuku-API](https://github.com/iamr0s/Dhizuku-API) | MIT | Copyright (c) 2023 R0S. Device-owner delegation for freeze |
| [WiFi Password Manager](https://github.com/Khh-vu/wifi-password-manager) | MIT | Copyright (c) 2025-2026 Khh-vu. Privileged Wi-Fi management approach used in Telos System |
| [TagLib wrapper (Kyant0/taglib)](https://github.com/Kyant0/taglib) | Apache-2.0 | Copyright 2025 Kyant. Bundles TagLib (LGPL-2.1 / MPL-1.1 upstream) |
| [PaperKnife+](https://github.com/potatameister/PaperKnifePlus) | GPL-3.0-or-later | Copyright (C) potatameister and PaperKnife+ contributors. PDF tools of Telos Viewer (feature set, tool logic) adapted from it |
| [LibreCuts](https://github.com/tharunbirla/LibreCuts) | GPL-3.0 | Copyright (c) Tharun Birla. Video and audio trimming, speed control and clip editing features adapted from it |
| [Open Camera](https://opencamera.org.uk/) | GPL-3.0 | Feature ideas only for Telos Camera; no source copied |
| [Libre Camera](https://github.com/iakdis/librecamera) | GPL-3.0 | Feature ideas only; no source copied |
| [CamX](https://github.com/uncannyRishabh/camx) | Apache-2.0 | Feature ideas only; no source copied |
| [Photon Camera](https://github.com/bjzhou/PhotonCamera) | GPL-3.0 | Feature ideas only; LUT/AI stack not implemented; no source copied |
| [Open Camera OMT](https://github.com/ysdede/opencamera-omt) | GPL-3.0 | Idea only for LAN streaming; Telos uses its own MJPEG server |
| [Spy Camera OS](https://github.com/wolkenschieber/spy-camera-os) | CC-BY-NC-3.0 | Feature ideas only for discreet capture; no source copied |
| [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) | Apache-2.0 | Copyright the Apache PDFBox authors and Tom Roush; used unchanged as a Maven dependency |
| [baresip](https://github.com/baresip/baresip), [baresip-studio](https://github.com/juha-h/baresip-studio) | BSD-3-Clause | Planned, not yet included |
| [Papirus icon theme](https://github.com/PapirusDevelopmentTeam/papirus-icon-theme) | GPL-3.0 | Copyright (C) Papirus Development Team. Optional desktop download catalog entry only; not bundled. |
| [Tela icon theme](https://github.com/vinceliuice/Tela-icon-theme) | GPL-3.0 | Copyright (C) vinceliuice. Optional desktop download catalog entry only; not bundled. |
| [candy-icons](https://github.com/EliverLara/candy-icons) | GPL-3.0 | Copyright (C) Eliver Lara. Optional desktop download catalog entry only; not bundled. Distinct from Telos built-in CandyCyber Compose glyphs. |

## Trademarks

The name "Thor" and the Thor logo and icon are trademarks of Trinadh Thatakula and are not licensed
under the GPL (see Thor's `TRADEMARK.md`). Telos does not use them. Thor is only referred to by name
to say that parts of Telos are based on it. Telos is not affiliated with or endorsed by Thor.

## RethinkDNS and firestack (Telos Network)

Telos Network is based on RethinkDNS, https://github.com/celzero/rethink-app, Copyright (c) Celzero / The Rethink DNS Open Source Project,
licensed under the Apache License, Version 2.0. Its concept, feature set and structure were adapted; where code was adapted, the
original copyright notice is kept in the source file. Apache-2.0 is compatible with GPL-3.0. The Apache-2.0 license text:
http://www.apache.org/licenses/LICENSE-2.0

```
Copyright (c) Celzero / The Rethink DNS Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

firestack, https://github.com/celzero/firestack (the Go engine, built as `com.celzero:firestack`), is licensed under the Mozilla Public
License 2.0 (https://www.mozilla.org/MPL/2.0/). Telos uses it unchanged as a Maven dependency and has not modified any of its files.
Its source is available at the address above. Rethink, RethinkDNS and Celzero are names of their owners; Telos is not affiliated with them.

WireGuard is a registered trademark of Jason A. Donenfeld. The WireGuard protocol is implemented inside firestack.

## Transistor (MIT License)

```
The MIT License (MIT)

Copyright (c) 2015-22 - Y20K.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

## baresip-studio (SIP engine bridge)

Used in `services/comms/src/main/cpp/baresip.c`, `logger.h` and
`services/comms/src/main/java/com/tutpro/baresip/Api.kt` (JNI bridge to baresip).
Project: https://github.com/juha-h/baresip-studio

```
BSD 3-Clause License

Copyright (c) 2018, TutPro Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of the copyright holder nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```


## Block lists offered by Telos (downloaded on request, not bundled)

Telos contains no copy of these lists. After the user switches a list on, the app downloads it directly from the
address below. The lists belong to their authors.

| List | Address | Licence |
| --- | --- | --- |
| StevenBlack unified hosts | https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts | MIT |
| AdGuard DNS filter | https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt | GPL-3.0 |
| Peter Lowe's ad and tracking server list | https://pgl.yoyo.org/adservers/ | the site invites combining and redistributing the list; no formal licence is stated |
| OISD small | https://small.oisd.nl/domainswild | GPL-3.0 |
| EasyList | https://easylist.to/easylist/easylist.txt | GPL-3.0 or CC BY-SA 3.0 |
| URLhaus host file | https://urlhaus.abuse.ch/downloads/hostfile/ | abuse.ch terms of use, https://urlhaus.abuse.ch/api/ |
| Naunter BT_BlockLists | https://raw.githubusercontent.com/Naunter/BT_BlockLists/master/bt_blocklists.gz | Unlicense for the project (its LICENSE file). The README says it is not actively maintained (notice 2024-10-22) while an automatic workflow regenerates the list. The entries come from other lists (for example iBlocklist, the Transmission lists of codebucket.de and others); their terms were not checked |
| Spamhaus DROP | https://www.spamhaus.org/drop/drop.txt | Free of charge (https://www.spamhaus.org/drop/): when used in a product, credit must be given to The Spamhaus Project, and the date and copyright text must stay with the file and data. Do not fetch automatically more than once an hour (Telos at most daily) |
| FireHOL level 1 | https://raw.githubusercontent.com/firehol/blocklist-ipsets/master/firehol_level1.netset | A combination of DShield (CC BY-NC-SA 2.5, see the header of the DShield file), Feodo Tracker (CC0), Spamhaus DROP (terms above) and bogons. FireHOL (its scripts GPL v2) says that some lists may have special licences and that the source site must be checked before use (https://iplists.firehol.org). The list is fetched by the user's phone from FireHOL's repository; Telos does not redistribute it |

Lists that were looked at on 2026-10-08 and are **not** offered: the Transmission list of codebucket.de (last modified 2025-01-17),
abuse.ch Feodo Tracker IP blocklist (CC0; last updated 2026-03-04 with 5 entries), DShield block list (CC BY-NC-SA 2.5; also part of
FireHOL level 1), FireHOL level 2 and level 3, Emerging Threats compromised IPs, CINS Army, blocklist.de and Bluetack/iBlocklist (terms not
verified), Tor exit lists (informational only). Details: docs/docs/user-guide/telos/downloads/torrents.md.

## libtorrent4j and libtorrent (torrent streaming in Telos Video, torrent downloads in Telos Downloads)

libtorrent4j, https://github.com/aldenml/libtorrent4j (MIT), used unchanged as a Maven dependency.

```
Copyright (c) 2018-2025 Alden Torres

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

libtorrent, https://www.libtorrent.org (BSD-3-Clause), bundled in the native libraries of libtorrent4j:
Copyright (c) 2003-2020, Arvid Norberg. All rights reserved.

## yt-dlp, youtubedl-android, FFmpeg and Python (optional media build of Telos Downloads)

Only in builds made with `-Ptelos.media=true`. The default build does not contain any of this.

- yt-dlp, https://github.com/yt-dlp/yt-dlp: Unlicense (public domain dedication), the version that ships in the library and later ones the user downloads from GitHub with "Update the downloader". yt-dlp itself bundles and depends on other Python packages with their own licences (see the yt-dlp repository).
- youtubedl-android, https://github.com/yausername/youtubedl-android, fork https://github.com/JunkFood02/youtubedl-android (Maven: `io.github.junkfood02.youtubedl-android:library` and `:ffmpeg`, version 0.18.1): GPL-3.0 as stated in its Maven POM.
- FFmpeg, https://ffmpeg.org, as built in the `ffmpeg` artifact of youtubedl-android: LGPL-2.1+ or GPL, depending on the build options of that artifact (not verified here; the artifact page and the FFmpeg legal notes apply).
- Python (CPython), https://www.python.org: PSF licence, bundled in the `library` artifact as `libpython`.

## TMDB and OpenSubtitles (Telos Video online services)

This product uses the TMDB API but is not endorsed or certified by TMDB (https://www.themoviedb.org).
Subtitles are searched and downloaded through the OpenSubtitles API (https://www.opensubtitles.com).
Both services are used with the user's own API key.


## NextLib (FFmpeg decoders in Telos Media)

NextLib, https://github.com/anilbeesetti/nextlib (GPL-3.0), used unchanged as a Maven dependency
(`io.github.anilbeesetti:nextlib-media3ext`). It bundles FFmpeg (LGPL-2.1+, https://ffmpeg.org) and
dav1d (BSD-2-Clause, https://code.videolan.org/videolan/dav1d).

## Next Player

The gestures, speed control and track choice of the Telos Video player follow the feature set of
Next Player, https://github.com/anilbeesetti/nextplayer (GPL-3.0). No code was copied.

## Nova Video Player

The network storage playback, library scanning from network folders and the subtitle search of Telos Video
are inspired by Nova Video Player, https://github.com/nova-video-player/aos-AVP (Apache-2.0, derived from
the Archos Video Player, Copyright Archos SA and the Nova Video Player contributors). It is implemented
independently with Media3 and Telos' own code. No source code and none of its prebuilt binaries (FFmpeg,
dav1d, torrentd) were copied. The Apache-2.0 license text: https://www.apache.org/licenses/LICENSE-2.0

## OpenSubtitles and Podnapisi

Telos Video can search subtitles through the OpenSubtitles REST API (https://www.opensubtitles.com and
https://rest.opensubtitles.org) and the public search of Podnapisi (https://www.podnapisi.net). The
OpenSubtitles file hash is computed with the algorithm published by OpenSubtitles. Telos is not endorsed
or certified by these services, and their terms of use apply to the subtitles. Subtitle sources are
contacted only when the user searches or has switched on automatic download.

## Trakt.tv

Telos Video uses the Trakt API (https://trakt.docs.apiary.io) with the user's own application
credentials. Telos is not endorsed or certified by Trakt.


## Libraries used by Telos Files

sshj and smbj (Apache-2.0, Copyright Jeroen van Erp and contributors), Apache Commons Net and Apache
Commons Compress (Apache-2.0, Copyright The Apache Software Foundation), OkHttp (Apache-2.0,
Copyright Square, Inc.), Bouncy Castle (MIT-style license, Copyright The Legion of the Bouncy Castle
Inc.), Zip4j (Apache-2.0, Copyright 2010 Srikanth Reddy Lingala; password-protected zip files),
XZ for Java (0BSD, Copyright The XZ for Java authors and contributors, Lasse Collin, Igor Pavlov and
Brett Okken; the LZMA, LZMA2 and XZ decoders that Commons Compress uses for 7z, xz and tar.xz). All are
used unchanged as Maven dependencies. The Apache-2.0 and 0BSD licences are compatible with GPL-3.0-or-later.

libarchive-android 1.1.7 (me.zhanghai.android.libarchive, Apache-2.0, Copyright Hai Zhang, https://github.com/zhanghai/libarchive-android)
with the libarchive it bundles (BSD-2-Clause, Copyright Tim Kientzle and the libarchive contributors,
https://libarchive.org) reads RAR 3, 4 and RAR5 archives in Telos Files. The prebuilt native library is used
unchanged and also contains zlib, bzip2, liblzma (0BSD/public domain), lz4 (BSD-2-Clause), zstd (BSD-3-Clause
or GPL-2.0, used under BSD-3-Clause) and Mbed TLS (Apache-2.0 or GPL-2.0-or-later). Only the RAR and RAR5 readers
are switched on in Telos. It is part of the `default` flavor only; the `fdroid` flavor does not contain it. RAR
encryption is not supported. All these licences are compatible with GPL-3.0-or-later.

The UnRAR source code (RARLAB) and junrar (UnRar licence) are not used: that licence forbids using the code to
recreate the RAR compression algorithm and is not compatible with the GPL.

7-Zip and 7-Zip-JBinding (LGPL-2.1 with the unRAR restriction) are not part of Telos. They were evaluated for
RAR and more formats and are not shipped.

## Cryptomator vault format

Reading Cryptomator vaults (https://cryptomator.org) is implemented from the published security
architecture and vault format description. No Cryptomator code was copied.

## Solid Explorer and MiXplorer

The layout and feature set of Telos Files are inspired by these file managers. No code was copied.


## Flow — selected YouTube components

Copyright © 2025–2026 A-EDev. GPL-3.0. Upstream: https://github.com/Flow-Tube/Flow at commit `4c93b64790c3d89eed57ba96abc997bb955c05f2`. License retained at `third-party/flow/LICENSE`. Adapted files: `utils/YouTubeLink.kt`, `player/stream/AudioStreamSelector.kt`, and `data/repository/NewPipeDownloader.kt`, located under `services/comms/.../youtube/flow/`. NewPipe/Rhino rules in `services/comms/consumer-rules.pro` are adapted from Flow `app/proguard-rules.pro` (GPL-3.0-or-later). Telos supplies its own repository, Compose screens and Media3 session. The downloader uses bounded response reads; Flow proxy-specific dependencies are not copied. Media layouts take inspiration from Flow while using the Telos theme.

## NewPipeExtractor

Copyright NewPipe contributors. GPL-3.0-or-later. https://github.com/TeamNewPipe/NewPipeExtractor, version v0.26.5. Used as a dependency for YouTube search, metadata and stream extraction.


## Tor Android / Tor

Telos uses the unmodified `info.guardianproject:tor-android:0.4.9.14` AAR
(https://github.com/guardianproject/tor-android), distributed by the Guardian Project.
Copyright (c) 2009-2010 Nathan Freitas, The Guardian Project. BSD-3-Clause.
Tor: Copyright Roger Dingledine, Nick Mathewson, The Tor Project, Inc. and contributors.
The upstream license and bundled-component notices are reproduced verbatim in
`core/base/src/main/res/raw/license_tor_android.txt` and displayed in the app license screen.
The upstream notice also describes components used by Orbot; this integration does not bundle
Orbot, Privoxy or obfs4. No upstream Tor source files are modified or copied into Telos.

## Native Ermis mail transport

[Jakarta Mail for Android 1.6.7](https://github.com/eclipse-ee4j/mail) (`com.sun.mail:android-mail`) and [Jakarta Activation for Android 1.6.7](https://github.com/eclipse-ee4j/jaf) (`com.sun.mail:android-activation`) provide the native IMAP/SMTP and MIME transports. Copyright Oracle and contributors; distributed under EPL-2.0 with the secondary GNU GPL version 2 with Classpath Exception option described in the upstream artifact license. Their license notices apply to these libraries, not to the independently written Ermis UI. FairEmail remains a feature reference; its source is not bundled.

## Akou music fingerprinting: Audile / SongRec / InterTune

Akou uses on-device Shazam-compatible fingerprinting, adapted from InterTune's pure Kotlin port:

- InterTune, Copyright (C) 2026 InterTune, GPL-3.0. Source: https://github.com/ItzSkyeYT/InterTune/tree/a99111df5205de3c2af49d158958c8cf82543ac2/fingerprint
- SongRec by marin-m, GPL-3.0, reference tag 0.7.3, commit `5afbf7361fcd72a3edaaeed24dc0ea150fd79385`: https://github.com/marin-m/SongRec
- Audile by Aleksey Saenko, GPL-3.0, `songrecfp` and Shazam request interoperability reference, inspected commit `e7435eeeab7af75891146c0282b776d56b40f342`: https://github.com/AudileTeam/Audile

Original copyright/SPDX notices remain in `services/comms/.../akou/fingerprint/`. Changes: package relocation; Telos foreground PCM capture, bounded cancellable HTTP transport, result/history integration. No proprietary Shazam application code or API key is included. The third-party matching endpoint is unofficial and availability is outside Telos's control.

Test-only audio excerpt: “Sneaky Snitch” by Kevin MacLeod, https://incompetech.com/music/royalty-free/mp3-royaltyfree/Sneaky%20Snitch.mp3, Creative Commons Attribution 4.0, https://creativecommons.org/licenses/by/4.0/. Converted to 10-second mono 16 kHz PCM excerpt as documented in `services/comms/src/test/resources/ASSET_LICENSE.md`; not packaged in the application.

The complete Mail/Activation artifact license and notice texts are bundled in `assets/licenses/android-mail-activation-1.6.7-NOTICES.txt` in the APK.

## Frosted surfaces

The shared cached-wallpaper glass renderer and bounded box blur are independently implemented Telos code under the project license. The former Haze runtime dependency has been removed; no Haze source code is copied into this renderer.

Video glass instrumentation uses the existing AndroidX Test JUnit extension (Apache-2.0) and the already pinned AndroidX Test runner 1.6.2 as test-only dependencies; release APK dependencies are unchanged.


## UAD-NG package descriptions

The unmodified `resources/assets/uad_lists.json` from Universal-Debloater-Alliance/
universal-android-debloater-next-generation is bundled with gzip compression in
`services/freeze/src/main/assets/uad_lists.json.gz`. Snapshot: upstream commit
`e0af3336d8f71fcb2b65ad4949051cf25c654f61` (2026-10-19), GPL-3.0; copyright
Universal Debloater Alliance and its contributors. The full GPL-3.0 text is in
`LICENSE.txt`; original source is available at
https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/tree/e0af3336d8f71fcb2b65ad4949051cf25c654f61.
Runtime updates use that same project’s main-branch JSON. Attribution remains visible in app details.

Canta (samolego/Canta, LGPL-3.0) was inspected to identify its UAD-NG source URL;
no Canta source code is copied. Thor commit `6dbf7ee4f3777b8cb26790facb8846f3706b3699`
(2026-10-21, GPL-3.0-or-later) was reviewed for freezer behavior; the additional
safety policy and updater here are independently implemented. Existing Thor attribution above remains.

## AnyAscii 0.3.3

Offline Unicode romanization for multilingual Morse. Source: https://github.com/anyascii/anyascii/tree/0.3.3. ISC, compatible with GPL-3.0. Full license is also bundled at `app/ui/src/main/assets/licenses/anyascii-ISC.txt`.

```text
ISC License

Copyright (c) 2020-2025, Hunter WB <hunterwb.com>

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
```


## AppOpsNext embedded component

[AppOpsNext](https://github.com/1zumiii/AppOpsNext), by 1zumiii, GPL-3.0. The complete upstream application source, tests and native daemon are adapted under `libs/appops-next`; the exact source commit and version are recorded in its `UPSTREAM.md`, with the full GPL license retained. The source is embedded in Telos rather than depending on an installed companion. Its Phosphor icon license is retained in `src/main/assets/licenses/phosphor-icons.txt`. Telos-specific changes include host-owned lifecycle/storage, internal per-package navigation, shared Shizuku UserService process transport and inherited Telos typography. The native ARM64 daemon is built from source; other ABIs retain the UserService backend. Integration validation and additional locale coverage are in progress; this notice is not a device-validation claim.

## Desktop cursor themes (optional downloads)

Telos Desktop can optionally download GPL-compatible XCursor packs into the
portable/vault Freedesktop icons directory (`…/icons/<Theme>/cursors`). Nothing
from these projects is bundled in the repository or APK; archives are fetched
only when the user taps Download in Settings → Cursor.

| Project | License | Notes |
| --- | --- | --- |
| [BreezeX_Cursor](https://github.com/ful1e5/BreezeX_Cursor) (ful1e5) | GPL-3.0-or-later | Breeze-inspired XCursor themes offered as optional downloads |
| [Bibata_Cursor](https://github.com/ful1e5/Bibata_Cursor) (ful1e5) | GPL-3.0-or-later | Bibata Modern/Original Classic XCursor themes offered as optional downloads |
| [KDE Breeze cursors](https://invent.kde.org/plasma/breeze) | LGPL-2.1-or-later (upstream Plasma) | Discovered from distro `/usr/share/icons` when installed; not redistributed by Telos |

## Camera recognition and searchable PDF

- [Tesseract4Android 4.9.0](https://github.com/adaptech-cz/Tesseract4Android/tree/4.9.0), Copyright Adaptech s.r.o., Robert Pösel, Google Inc., Robert Theis and contributors, Apache-2.0. Used as a pinned Android dependency for local OCR and searchable PDF rendering. The Apache-2.0, Leptonica, libpng and Independent JPEG Group license texts are retained explicitly in `app/app/src/main/assets/licenses/tesseract4android-4.9.0-NOTICES.txt`, independently of META-INF packaging exclusions.
- [tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast/tree/87416418657359cb625c412a48b6e1d6d41c29bd), Tesseract contributors, Apache-2.0. Language models are optional verified downloads, not bundled APK assets. The glyphless PDF font comes from [tessconfigs](https://github.com/tesseract-ocr/tessconfigs/tree/3decf1c8252ba6dbeef0bf908f4b0aab7f18d113), under its upstream Apache-2.0 distribution.
- [ZXing 3.5.4](https://github.com/zxing/zxing), Copyright ZXing authors, Apache-2.0. The existing dependency is reused for CameraX QR/linear/matrix-code decoding.

### Wireless ADB pairing

The existing Shizuku-Next JNI pairing code is built and bound to Telos's `PairingContext`.
Protocol and lifecycle behavior were reviewed against Shizuku-Next
`8508e1d50a293cec82eb60465bcc17022d7a5926` and Shizuku+
`80330704be4ebc46e282334b9c542d4a7d071b1b` (Apache-2.0; RikkaW/RikkaApps and their fork contributors).
The Telos ADB transport and UI changes are GPL-3.0-or-later.

`io.github.vvb2060.ndk:boringssl:20251124` supplies the static crypto prefab for SPAKE2.
The packaging repository is https://github.com/vvb2060/BoringSSL_Android; its source submodule is
BoringSSL `79048f1f1d8e6b7f9ca59b95c24486c8149122a4`. The compiled headers carry Apache-2.0
notices (BoringSSL Authors/OpenSSL Project Authors). Its Maven POM still lists the older
OpenSSL/ISC license metadata; the pinned source and shipped license are authoritative.
The library uses the non-LTO artifact with the pinned NDK. See bundled BoringSSL license and AUTHORS.

The encrypted software-RSA identity follows Shizuku-Next's `AdbKey` approach; no private-key
logging is carried over. Certificate construction uses the project's existing pinned Bouncy Castle
`bcpkix-jdk18on` version (`libs.versions.bouncycastle`), under the Bouncy Castle MIT-style license.
No separate crypto version is introduced.

The now-built `rish` JNI library comes from the Shizuku API/Rish project (MIT, copyright RikkaW,
2021); its full license is retained under `libs/shizuku-next/rish/LICENSE` and in APK assets.
The Apache-2.0 Shizuku-Next license is included in full. Statically linked NDK 27 C++ runtime
licenses (Apache-2.0 with LLVM exceptions and legacy notices) are reproduced in the bundled
NDK consolidated notices; this asset is retained to preserve all runtime attributions.


## Amethyst — embedded Aether source (integration in progress)

Source: https://github.com/vitorpamplona/amethyst
Pinned revision: `75bb07fa3adf4ef5d590bb5379d87f01c139a430`.
Copyright (c) 2023 Vitor Pamplona; individual source headers retain their
respective years and notices. MIT license, reproduced in full in
`libs/amethyst/LICENSE`. The source is adapted into an Android library for
Aether; the development branch connects it to Aether, but it has not shipped in a release.
Telos-specific adapters retain their GPL-3.0-or-later headers.
The native Arti and zxing-cpp source/build instructions and license information
remain under `libs/amethyst/tools`. The embedded build aligns jsoup to 1.24.1 (MIT) and Jackson to 2.23.0
(Apache-2.0), matching the launcher dependency catalog. Quartz is pinned to
JitPack commit `009e4c8dcb23578638f10f66ebc42c04ebaf0c88`; the Android APK
selects Tink Android 1.17.0 rather than packaging its duplicate JVM artifact.
The F-Droid source set is selected; proprietary Play-only integrations are not
shipped. Original native notices and source/build instructions remain included.


Documentation site: Vue and `@vue/server-renderer` are aligned at 3.5.45 (MIT)
to use a single Vue runtime during static rendering. Existing Vue copyright and
license notices remain in their distributed packages.
