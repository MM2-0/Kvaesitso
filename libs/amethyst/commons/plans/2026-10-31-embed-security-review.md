# Security review of the embed surfaces (napplets, nSites, webxdc, in-app browser, carts)

Date: 2026-10-31. Scope: NIP-5D napplets, NIP-5A nSites, webxdc apps, the in-app browser, feed
prefetching, the desktop Chromium host, and the cart player (with kiko-kmp's runtime). Attacker: a
stranger whose event reaches the victim's feed, the servers that event names, and other apps on the
device.

Each finding below says what was fixed, which test shows it, and what is still open. Line numbers are
as of the review.

## Fixed

| # | Severity | Finding | Fix | Test |
|---|---|---|---|---|
| 1 | Critical | `relay.query`/`relay.subscribe` decrypted every DM, gift-wrap seal and cached NIP-17 rumor for any app holding `relay` (one "Connect" under Reasonable), and decrypted user-authored events against their first `p` tag: an auto-signed reaction carrying any ciphertext came back decrypted | `NappletBroker.mayDecrypt` (standing, session or FULL_TRUST decrypt grant, or the narrow one; never a prompt) gates every decryption; the peer of a user-authored non-DM is the user; the gateway returns only events whose signature verifies, at most 500 | `NappletBrokerSecurityTest`, `NappletRelayCleartextTest` (3 fail on the old code) |
| 2 | High | `nostr.signEvent` was gated on `RELAY`, so NIP-5D napplets got raw signatures with any `created_at` | Gated on `SIGNER`, which only the website posture holds | `aNip5dNappletCannotGetARawSignature` |
| 3 | High | The in-app browser leaked the real IP over WebRTC while a site was on Tor | `NappletWebRtcGuard.sync` follows the page's route on every browser surface (activity, embedded tab, popups, onion and omnibox switches) | Android only; not run on a device |
| 4 | High | Site cards prefetched every `path` from every server as they scrolled by, with no global limit, ignoring the media setting, through a client that reached the LAN | One gate and a 64 MB / 10 min budget; 16 files, 8 MB per card, 4 MB per file; https servers only; a client that refuses non-public addresses (DNS and IP literals, every hop) and https→http redirects; skipped when images wouldn't load | `NappletBlobPrefetcherTest` (the IP-literal case failed first) |
| 5 | High | kiko: a `__call` chain looped forever past abort and the watchdog | kiko-kmp `0e404b0`: capped chain with abort checks | kiko `HostileCartTest` |
| 6 | Medium | Webxdc apps got WebRTC back in a frame they made themselves (desktop: guard matched only the shell's origin) | Webxdc apps live under `*.xdc.napplet.local`; the desktop guard covers it; Android refuses napplets and webxdc when the WebView can't run a script in every frame | `CefNappletSessionTest.aWebxdcAppKeepsItsStorageAndUpdatesButHasNoNetwork` (`frameRtc` was `function`) |
| 7 | Medium | Every app shares the `napplet.local` cookie jar (cross-app, cross-account, cross-route tracking) | `COOKIE_GUARD_JS` in every served page and every frame (Android document-start script, desktop extension) | Same CEF test (`cookie` was `xdcprobe=1`); `NappletWebContractTest` |
| 8 | Medium | One app could open unlimited relay subscriptions and filters | 64 relay subscriptions per surface, 10 filters per request, closed-id memory capped | `tooManyFiltersAreRefused` |
| 9 | Medium | kiko kept a stopped game's heap; checked memory too rarely | kiko-kmp `90b4aa8`, `baf17bd`; Amethyst keeps at most 2 paused games | kiko tests, `CartSaveIsolationTest.onlyTheMostRecentPlayersStayAlive` |
| 10 | Low | Prompt spamming after a dismissal | Declined prompts back off 5 s, doubling to 5 min; at most 1,000 requests wait per app | `aDeclinedPromptStaysQuietForAWhile`, `anAppCannotPileUpRequestsBehindAnOpenDialog` |
| 11 | Low | "Block" denied only the capabilities declared at that moment | Denies every capability | `blockingAnAppDeniesCapabilitiesItHasNotDeclaredYet` |
| 12 | Low | A stranger's note could run an older signed version of a trusted app inline, with today's grants | Inline launches run the newest version this device has seen | `NappletNewestVersionTest` |
| 13 | Low | Instance storage got a fresh 512 KB per inline run; a new key's name was free | One quota per app version, names charged | `NappletStorageQuotaTest` |
| 14 | Low | File chooser handed pages any URI, including the app's own providers | Only other apps' `content://` URIs and this request's captures | `NappletPickedFilesTest` |
| 15 | Low | NIP-07 offered to plain `http://` pages | https, `.onion` and the device itself only | `BrowserOriginPolicyTest` |
| 16 | Low | Browser fullscreen hid every trace of the site | A toast names the site | Android only |
| 17 | Low | Inline runs could open the picker; parked embedded tabs could pop share sheets | Refused | Android only |
| 18 | Low | Any cart could read and overwrite any other cart's saves; the store grew without bound | Saves kept per cart (sha256 of its bytes); 1,024 ids in all | `CartSaveIsolationTest` |
| 19 | Low | Webxdc bundles were never evicted (1 GB each, permanent on desktop) | Least recently opened go past 1 GB | `bundlesOpenedLongestAgoAreLetGoPastTheBudget` |
| 20 | Low | Concord kind-3310 signals: 2,000 per channel from any member, no byte cap | 4 MB per channel (one signal is already bounded by NIP-44's 64 KiB) | `aFloodOfBigSignalsIsBoundedInBytes` |
| 21 | Low | `selfAddr` was documented as a pseudonym that names no one | Docs corrected: anyone who knows the poster and instance can test known pubkeys | n/a |
| 22 | Medium | Desktop `dns-prefetch`/`preconnect` outside CSP | `net.network_prediction_options = 2` | The engine starts with it; the effect is not measured |

## Open

- **Critical: desktop Chromium has no renderer sandbox** (JCEF hard-codes `no_sandbox`). A renderer
  exploit runs as the user beside the keys. Needs Chromium in a child process without keys (the
  deferred item of `desktopApp/plans/2026-06-21-napplet-desktop-host.md`, whose text is now
  corrected) or OS-level confinement of `jcef_helper`. The spike of 2026-11-10
  (`desktopApp/plans/2026-11-10-chromium-sandbox.md`) narrows it: a child process alone doesn't
  help (the keyring is open to any process of the user on Linux and Windows); Chromium's own
  sandbox does, and JCEF turns it off with one line, which on Linux is all that stands in the way.
- **Medium: `:napplet` shares the app's Linux uid.** A WebView browser-process escape reads the key
  stores; the broker also trusts `MSG_SET_SITE_PERMISSION`, `MSG_SET_WEB_TOR` and
  `MSG_MINT_BROWSER_TOKEN` from the sandbox. WebView can't run in an `isolatedProcess`, so the
  Chromium sandbox is the real boundary there.
- **Android `about:blank` timing:** whether a document-start script runs before a page reads a fresh
  `about:blank` iframe's globals is not verified on a device. If it doesn't, the WebRTC and cookie
  guards have the same gap on Android that desktop had. Needs an instrumented test.
- **Android `dns-prefetch`/`preconnect`** from napplets and webxdc apps: not measured.
- **Low:** the JCEF bundle downloads without a pinned hash and outside Tor; a click in a Tor-routed
  desktop site opens the system browser over clearnet; outbox consent doesn't list the relays; the
  seal consent shows the rumor, not the seal's tags.
- **Release:** the kiko fixes are on kiko-kmp's `claude/gracious-allen-jk19t2`; Amethyst pins kiko
  0.2.0 until a release carries them.
