# amethyst plans

_Audited 2026-06-30. 21 plans: 19 shipped (archived), 1 in-progress, 1 queued, 0 abandoned._

## In progress
| Plan | Summary |
| ---- | ------- |
| [2026-09-07-generic-local-filter.md](2026-09-07-generic-local-filter.md) | Local note search moved onto `LocalCache.filter(Filter)` so the search box's tokens narrow local and relay results alike; steps 1–6 shipped, `FilterMatcher` left untouched via a predicate parameter. |
| [2026-05-24-ios-support.md](2026-05-24-ios-support.md) | Incremental KMP-to-iOS port; quartz/commons iOS targets are configured (Phase 1) but no `iosApp` module exists yet. **Superseded 2026-10-09** by [commons/plans/2026-10-09-ios-app.md](../../commons/plans/2026-10-09-ios-app.md). |

## Queued
| Plan | Summary |
| ---- | ------- |
| [2026-10-28-lightcompressor-2.3.0-upgrade.md](2026-10-28-lightcompressor-2.3.0-upgrade.md) | Bump LightCompressor-enhanced 2.2.3 → 2.3.0 (developed against a local `2.3.0-SNAPSHOT`, pinned to the JitPack tag 2026-10-29): compile-level upgrade is a one-line version change; decisions on the new HDR default (`hdrMode`) and on calling `VideoCompressor.cancel()` on timeout now that cancel is per batch; a line in the upload dialog, from the new `VideoCompressor.probeHdr`, saying whether a picked HDR video stays HDR or is converted to SDR; an Android `probeVideo` override so the quality slider names the picked video's resolution; device checks for HLG uploads. |
| [2026-09-19-cordn-ui.md](2026-09-19-cordn-ui.md) | Every user-facing feature cordn-web has, mapped onto Amethyst — the account-level cordn runtime (the real gate), group list/chat/info screens, welcomes, join requests, message features and settings. Marmot is frozen and the two bindings must stay independently deletable, enforced by isolation tests rather than a README; flags the 1009-vs-1010 edit-kind divergence. |
| [2026-09-15-qr-reader-overhaul.md](2026-09-15-qr-reader-overhaul.md) | QR reader rebuilt on CameraX + zxing-cpp in-app (replacing the Camera1 zxing-android-embedded activity) — continuous AF, zoom, torch, full-frame decode, explicit failure feedback, and gallery/clipboard import. |
| [2026-07-23-push-notification-redesign.md](2026-07-23-push-notification-redesign.md) | Per-kind tray notification redesign — accent colors, status-bar icons, MessagingStyle/BigPictureStyle/colorized zap cards, aggregation, Conversations/Bubbles; closes nutzap/onchain/repost/badge parity gaps. |
| [2026-06-20-napplet-inter-applet.md](2026-06-20-napplet-inter-applet.md) | NAP-INC / NAP-INTENT inter-applet messaging — deferred; prerequisites (multi-applet hosting, archetype registry, `MESSAGING` capability) not yet built. NAP-INC shipped 2026-11-01 ([nap-inc](../../commons/plans/2026-11-01-nap-inc.md)); NAP-INTENT shipped 2026-11-01 ([nap-upload-common-intent](../../commons/plans/2026-11-01-nap-upload-common-intent.md)). |

## Archived (shipped)
| Plan | Summary |
| ---- | ------- |
| [archive/2026-05-14-onchain-zaps.md](archive/2026-05-14-onchain-zaps.md) | NIP-BC (kind 8333) onchain Bitcoin zaps — hand-rolled `quartz/nipBCOnchainZaps/` consensus layer plus Android send/receive/display. |
| [archive/2026-05-25-appfunctions-signer-prompts.md](archive/2026-05-25-appfunctions-signer-prompts.md) | How AppFunctions write verbs acquire signatures across the three signer types; write verbs now ship. |
| [archive/2026-05-26-appfunctions-gemini-discovery.md](archive/2026-05-26-appfunctions-gemini-discovery.md) | Verifying Gemini-side discovery of Amethyst's AppFunctions; description-based (`isDescribedByKDoc`) discovery shipped. |
| [archive/2026-05-26-appfunctions-screens-as-verbs.md](archive/2026-05-26-appfunctions-screens-as-verbs.md) | Map every Amethyst screen's feed filter to an AppFunction/MCP verb; 46 verbs now ship. |
| [archive/2026-05-26-avif-implementation-plan.md](archive/2026-05-26-avif-implementation-plan.md) | Task-by-task plan for AVIF support via a `MediaMimeTypes` helper across the upload pipeline. |
| [archive/2026-05-26-avif-support.md](archive/2026-05-26-avif-support.md) | Comprehensive design making AVIF a first-class image format on every Amethyst surface. |
| [archive/2026-05-27-avif-instrumented-tests-design.md](archive/2026-05-27-avif-instrumented-tests-design.md) | Design for on-device AVIF upload-pipeline regression tests with committed fixtures. |
| [archive/2026-05-27-avif-instrumented-tests-plan.md](archive/2026-05-27-avif-instrumented-tests-plan.md) | Implementation plan for the AVIF instrumented + JVM unit tests and their fixtures. |
| [archive/2026-06-01-dm-live-tail-and-history-slices.md](archive/2026-06-01-dm-live-tail-and-history-slices.md) | DM loading split into a fixed live tail plus per-relay backward history paging (`WindowLoadTracker` / `RelayLoadingCursors`). |
| [archive/2026-06-19-napplet-sandbox-host.md](archive/2026-06-19-napplet-sandbox-host.md) | Keyless `:napplet`-process WebView host with brokered, consent-gated capabilities for NIP-5A/5D content. |
| [archive/2026-06-20-napplet-ecosystem-audit.md](archive/2026-06-20-napplet-ecosystem-audit.md) | Audit of our napplet shell against the upstream `@napplet` SDK; wire-compat gaps subsequently closed. |
| [archive/2026-06-21-napplet-code-audit.md](archive/2026-06-21-napplet-code-audit.md) | Code audit of the napplet subsystem recording correctness/perf/refactor fixes and deferred items. |
| [archive/2026-06-21-napplet-sdk-conformance-audit.md](archive/2026-06-21-napplet-sdk-conformance-audit.md) | Feature-by-feature SDK conformance audit; the four conformance breakers were fixed and pinned by tests. |
| [archive/2026-06-22-napplet-nsite-security.md](archive/2026-06-22-napplet-nsite-security.md) | Security review of the nsite/napplet attack surface; launch-token identity and per-applet origins landed. |
| [archive/2026-06-23-napplet-nap-theme-notify-inc.md](archive/2026-06-23-napplet-nap-theme-notify-inc.md) | Add the `theme`, `notify`, and `inc` NAP domains so demo napplets boot; capabilities + `NappletIncBus` shipped. |
| [archive/2026-06-24-napplet-embedded-tabs.md](archive/2026-06-24-napplet-embedded-tabs.md) | Embedded warm bottom-bar napplet/nsite/browser tabs via `SurfaceControlViewHost`, in the new `:nappletHost` module. |
| [archive/2026-06-25-embed-text-selection-native-parity.md](archive/2026-06-25-embed-text-selection-native-parity.md) | Host-drawn text selection (handles, magnifier, toolbar, IME proxy) for embedded sandboxed surfaces. |
| [archive/2026-06-25-web-app-naming.md](archive/2026-06-25-web-app-naming.md) | Naming overhaul for web-app / nApplet / nSite / favorite surfaces (route, screen, controller renames). |
| [archive/2026-06-26-nsite-napplet-favorite-icons.md](archive/2026-06-26-nsite-napplet-favorite-icons.md) | Derive favorited nSite/nApplet bottom-nav icons from verified manifest blobs (`NappletIconPath`). |
