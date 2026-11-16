# NIP-5D and NAP chase — 2026-10-27

Status: implemented and automated checks passed; interactive device/UI verification outstanding

## Pinned evidence

- Previous chase: `4f6d6acefd` and `94b679fe7c`, merged by upstream PR #3858.
- Resynced from clean previous branch to `origin/main` `3643795269`; 3,876 commits behind, no divergent commits.
- Work branch: `codex/chase-nip5d-naps-20261027`.
- NIP-5D: https://github.com/dskvr/nips/blob/5560820fa4f37f45b8ab742a11aeb3001b17dc67/5D.md (living upstream PR nostr-protocol/nips#2303).
- NAP registry: https://github.com/napplet/naps/tree/5aadf1bf5d048cb231d683587d8eeef54f961492 .
- Quartz is now an external Maven dependency (`2.3.0`). Its NIP-5D readers/builders still implement path aggregates. Shared current-schema handling belongs in commons until Quartz offers that contract; no unpublished dependency is required.
- Follow-up (2026-10-28): quartz 2.6.0 (vitorpamplona/quartz#45) models the current schema in `nip5dNapplets` (laid out like `nip88Polls`: `root/`, `named/`, `snapshot/`, shared `NappletManifest` and `tags/`). The interim commons reader/writer (`NappletManifestData`) is removed; Amethyst reads the quartz accessors and keeps only its own policy in `commons/napplet/NappletManifestPolicy.kt` (`exposedDomains`, `unsupportedRequired`, `isLaunchable`).

## Gap and implementation inventory

| Area | Upstream change / observed gap | Work |
| --- | --- | --- |
| Manifest | Exactly one lowercase artifact `x`, description in content, `R`/`O`, independent icon hash/MIME, source URL rules, archetype and intent metadata | Shared reader/writer, reject invalid execution input, migrate all app/CLI consumers |
| Identity/loading | Artifact sha256 replaces NIP-5A aggregate; signed event and blob both verified before srcdoc | Reuse signature verification, blob cache/prefetch verification, sandbox IPC and resolver; derive one index path from signed x |
| Capabilities | Complete required set checked locally; unavailable optional domains never block | Reject unsupported R; expose supported declared R/O subject to existing broker consent |
| Icons | Hash verify and decode PNG/JPEG/WebP; bad icon never blocks launch | Reuse content-addressed fetch, verify actual decode and declared type, render local verified bytes |
| CSP | WebAssembly allowed through wasm-unsafe-eval, never unsafe-eval | Update both shell inherited CSP and srcdoc policy; exercise in browser |
| Theme | onChanged local subscription; automatic updates for every exposed theme domain | Add shim delivery and host lifecycle-scoped push |
| Identity | Verified endpoint binding and optional resource clarified; operations unchanged | Preserve launch-account binding and resource policy; audit |
| Shell | Optional environment/readiness; no mandatory handshake | Not exposed by Amethyst; remain absent |
| Intent | Manifest-based dispatch, retained delivery, parameter contracts, naddr recommendations | Implemented 2026-11-01 (`2026-11-01-nap-upload-common-intent.md`): favorites as the catalog, delivery on `intent.ready` |
| INC | Authenticated endpoint IDs replace dTag routing | Implemented 2026-11-01 (`2026-11-01-nap-inc.md`): topics and channels under runtime endpoint ids on Android and desktop |
| Relay | Draft PR #2 aligns availability; read envelopes and optional sidecar remain | Existing event wrapper and cleartext broker preserved; no prefetch enabled |
| Storage | Draft PR #3 adds manifest-kind/snapshot-id scope | Include signed kind and event id in runtime identity; separate root, named, snapshot, publisher and artifact |
| Resource | Draft PR #80 changes blob wire encoding, BUD-10 URIs, isolation/data rules | Canonical padded base64 blob fields, web binding decoding, BUD-10 parser/hints through policy, exact data percent bytes, cancellation retained |
| Other domains | keys/media/notify/config/upload/value/outbox/cvm/link/pow/display not exposed | Preserve truthful domain absence; no unrelated new capability implementation |
| Outbox (2026-10-30) | NAP-OUTBOX draft ([napplet/naps#32](https://github.com/napplet/naps/pull/32), `8865f73`): getEvent/query/subscribe/publish/resolveRelays over NIP-65 | Implemented: `OUTBOX` capability, `AccountNappletOutboxGateway` + `NappletOutboxRouting` (authors' write relays to read, inboxes to deliver, missing kind 10002 lists fetched from the indexers, hints on private networks dropped, bounded), `outbox.event`/`outbox.closed` streaming, `window.napplet.outbox`. A publish signs the template once and is gated as `relay.publish` is; it answers when every relay has, 1.5 s after the first one took it, or at 6 s (`NappletOutboxPublish`), since the Game Boy emulator gives a save 10 s and one silent relay held the old 15 s wait. The user's own events are read from and delivered to the account's own relays (`homeRelays`, `notificationRelays`), where the app writes them. Events go out as signed and verified, never decrypted (unlike NAP-RELAY): the napplet picks where its next read goes, so a decrypted message could leave in a filter; the cache's unwrapped DMs carry no signature and never verify. Reads cap at 500 events and 10 filters; a surface holds at most 16 subscriptions, which end only when every relay closed them; authors a route leaves out (invalid, past 100, or cut by the 30-relay limit) are reported, so the read is incomplete or the delivery fails. Hostnames that resolve to private addresses (`*.nip.io`) still pass the hint check, as they do app-wide. Checked live: a Game Boy save and a Freedoom save both reload after the desktop app is killed (`NappletStorageKillTest` covers storage under SIGKILL). Unlocks the published Minesweeper and Game Boy Color napplets; the NES one also requires `fs` |
| Files (2026-10-31) | NAP-FS draft ([napplet/naps#88](https://github.com/napplet/naps/pull/88), `bd1b9e9`, from a fork; not yet in the registry table): a virtual filesystem with pickers, range reads and writes | Implemented as user-mediated picks only: `FS` capability, `NappletFsSession` per running surface, `window.napplet.fs`. See `2026-10-31-nap-fs.md`. Unlocks PhotoHop ("Fotohop", kind 15129), which requires `fs` |
| GAMEPAD/NAAT | GAMEPAD reverted upstream; NAAT registry deprecated | No new implementation or registry dependency |

Merged NAP diff since the August 4 chase: five domain specs changed (IDENTITY, THEME, SHELL, INTENT, INC), plus projection/registry/governance. The registry also links open draft PRs: these are part of the chase, not just the default-branch diff. Exposed draft domains are pinned to:

- RELAY #2: `9f30b3505335a52c574ff570576687d6467021ff`.
- STORAGE #3: `4f4cf0ca7ecbd24c284a5d63bcd85e3de6af37ca`.
- RESOURCE #80: `ae5b9c64ab8a4d8909f288829a01e99d6bd713bc` (supersedes the old #13 registry link).

Other open drafts observed include navigation, location, motion, mesh, worker, catalog, capture, DM, hashtree, torrent, Blossom, count, lists, common actions, system, BLE, serial and WebRTC. They add optional domains that Amethyst does not expose. GAMEPAD was reverted on master and reopened as #130; it remains unavailable. Updates to unsupported domains do not justify falsely advertising them or rewriting dormant legacy broker operations as new supported capabilities. Current NIP-5D changes affect both Android launch surfaces, feed/favorite/connected-app metadata, and CLI fetch/list/serve/publish.

## Verification

- 113 common napplet tests passed: current schema (all three kinds), artifact policy, capability/broker rules, storage identity, unknown-message silence and malformed resource errors.
- 3 shared UI prelude tests passed: injection order including adversarial HTML and CSP.
- All 89 CLI tests passed; current manifest writer/reader round trips are covered by common tests. No existing CLI shell fixture references the removed napplet aggregate fields.
- 52 Android napplet unit tests passed: wire conformance, base64 blob/error encoding, resource URL/data/private-address policies and existing SDK contracts.
- Both `:amethyst:assemblePlayDebug` and `:amethyst:assembleFdroidDebug` succeeded.
- `./gradlew spotlessApply` succeeded; `git diff --check` clean.
- Four browser tests passed in installed headless Chrome using the production shell/shim and a fake native bridge: opaque srcdoc, namespace timing, storage/network/NIP-07 denial, JavaScript-eval denial, WebAssembly success, theme callbacks/close without handshake/get, resource base64 decoding/rejection, optional-domain absence.

Final Gradle command:

```sh
./gradlew :commons:jvmTest --tests '*Napplet*' \
  :commonsUI:jvmTest --tests '*NappletWebContract*' :cli:test \
  :amethyst:testPlayDebugUnitTest --tests '*Napplet*' \
  :amethyst:assemblePlayDebug :amethyst:assembleFdroidDebug --console=plain
```

Initial baseline Gradle invocation could not download Gradle 9.8.2 (connection timeout). Downloaded with curl and verified the exact wrapper-pinned SHA-256 (`21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`), then reused the wrapper cache. No build configuration change was needed.

## Compatibility and policy

- Old manifest execution/publication is intentionally replaced by the current schema. Republish legacy multi-file/path-aggregate manifests as a bundled index.html with nonempty content description and R/O tags.
- Existing NIP-5A websites keep their separate posture and NIP-07 surface.
- Stored permission ledger coordinate/property names remain compatible. Current NIP-5D storage gets the new manifest-kind/artifact scope and does not reuse legacy namespaces.
- Icons are fetched by signed hash from declared servers, hash checked again and actually decoded in the declared PNG/JPEG/WebP format. Cosmetic failures use generic artwork. Limits: 2 MiB encoded icon and 4096 pixels per dimension.
- Resource policy continues to deny raw SVG rather than deliver active XML; no SVG rasterizer is exposed. Hashtree is not advertised and remains unsupported. Optional resource cache/sidecar prefetch stays off; HTTP cache is disabled for resource requests. BUD-10 hints never bypass existing HTTPS, private-address, size, and hash checks.
- 2026-11-01: `resource.bytes` no longer limits types to a short image/audio allowlist, which refused glTF models and gzip files (the SeedHammer 3D viewer, kind 35129 `sh-viewer`). It refuses SVG, HTML and XML (by declared type, `<svg` in the first 8 KiB, or a WHATWG HTML opener or `<?xml` at the start) and delivers everything else under its sniffed type, a declared JSON/text type the bytes bear out, or `application/octet-stream`. BUD-10 references may omit the extension (`blossom:<sha256>?xs=…`), as `BlossomUri.parse` already allowed.
- No shell/intent/INC negotiation or routing is newly advertised. Unknown message types are silent regardless of correlation id.

## Runtime coverage limits and handoff

- Final automated Gradle run succeeded (`verify5.log`); formatting and browser evidence are under `/tmp/amethyst-nip5d-chase-20261027/` on the implementation machine. These logs are not repository dependencies.
- `adb devices` reports no attached device. Neither flavor was installed; Android icon decoding, native theme lifecycle delivery, and interactive full-screen/embedded consent flows remain unverified on a device.
- Attempted the repository's desktop smoke runner with an isolated scratch profile. It exits because Xvfb is not installed on this macOS machine; shared metadata cards were not visually inspected in the running application. Browser checks cover the shell/shim, not Compose rendering.
- No live relay publication was performed. Run the steps in `tools/napplet-test/README.md` on a device: open a current manifest, check required/optional domain behavior, toggle app/system themes on both launch surfaces, confirm generic fallback for bad icons, and confirm storage isolation across artifacts/snapshots.
- The branch is local; no push or PR was performed. Breaking manifest and CLI JSON changes are documented in `cli/README.md` and the harness README.
