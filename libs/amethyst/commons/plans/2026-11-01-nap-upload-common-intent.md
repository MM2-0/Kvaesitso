# NAP-UPLOAD, NAP-COMMON and NAP-INTENT — 2026-11-01

Status: implemented; shared code and the desktop host checked; Android compiled and unit-tested, not run on a device.

## Why

After NAP-INC (`2026-11-01-nap-inc.md`), the napplet.run napplets still refused were Emoji Pack
Creator (`R common`, `R upload`) and Feed Manager (`R intent`). Their cards hid Open without saying
why; a card now names the required domains Amethyst doesn't support.

## Specs

- NAP-UPLOAD: draft [napplet/naps#33](https://github.com/napplet/naps/pull/33) at `f5cc981e`.
- NAP-COMMON: draft [napplet/naps#67](https://github.com/napplet/naps/pull/67) at `8cd0eae8`.
- NAP-INTENT: [`naps/NAP-INTENT.md`](https://github.com/napplet/naps/blob/43cb3a4e855570ec4b90b9cc05f0c721e5ad0aab/naps/NAP-INTENT.md).

## Upload

- Rail `blossom` only: `AccountNappletUploadGateway` (`commons/jvmAndroid`) puts the bytes on the first
  server of the account's kind 10063 list with a kind 24242 auth the account signs, through the app's
  upload HTTP client (its Tor rule applies). A server that reports another hash is a failure.
- Replies follow the draft: `upload.upload.result{result}`, `upload.status.result{status}`,
  `upload.info.result{info}`, and a top-level `error` when no upload was created (`unsupported rail`,
  `file too large`, `no server configured`, `upload failed`). Uploads complete before the reply, so
  `upload.status` returns the finished record (kept per napplet, 64 in all) and no
  `upload.status.changed` is pushed.
- `upload.info` and `upload.status` never prompt; `upload.upload` asks under `upload`.
- Size: 128 KiB on Android (the bytes cross a Binder transaction from the sandbox, as NAP-FS writes
  do), 16 MiB on desktop.
- The shim sends a Blob, ArrayBuffer or typed array as base64; the old Amethyst-only `upload.blob()` is gone.

## Common

- `encodeNip19` / `decodeNip19` (`NappletCommonNip19`): public types only; `nsec` is refused both ways;
  a value must be one whole identifier; `nrelay` is encoded with one TLV record, as quartz only decodes it.
- `getProfile` and `follows` ask once under `common`. A profile is read through the outbox gateway
  (cache first, NIP-65 routing, verified events) and returned as the metadata JSON plus NAP-RELAY's
  `RelayEventResult`.
- `follow`, `unfollow`, `react`, `report` are built by the shell (quartz's reaction and report
  builders; the kind 3 keeps every other tag and the content) and become a `Publish` *before* the
  broker's gates, under `common`. So the signer prompt shows the actual event, and it is signed and
  sent as any publish. A change that changes nothing (following someone already followed) publishes
  nothing. Reactions need a native event that can be found; event reports need its author.
- They are held while the napplet is unattended, as `relay.publish` is.

## Intent

- Catalog (`NappletIntentCatalog`): the napplets pinned to the favorites, in the user's order. A
  napplet is a handler when it is launchable here, declares `intent` (or it has no `onDelivery`), and
  has a `z` role with an `i` contract `napplet:<role>/<action>`. Catalog ids follow the spec: named by
  publisher, kind and `d`; root by publisher and kind; snapshots by event id.
- Default: the first installed handler of an archetype. The user changes it by reordering favorites;
  a napplet can't. An explicit `handler` must be an installed candidate and never falls back;
  `choose` resolves only with a single candidate, as there is no chooser yet; recommendations are
  ignored, which the spec allows.
- Delivery (`NappletIntentDispatcher`, one per host): an accepted invoke opens the handler (desktop: a
  window; Android: the full-screen host started from the broker service) and keeps the delivery for
  its endpoint until the target's binding posts `intent.ready` (sent by the shim when it installs the
  domain; deliveries then wait in the shim until `onDelivery`), for up to 120 s. `sender` is the
  source's catalog id, from its verified launch identity.
- `intent.invoke` is held while the source is unattended: it opens another app.
- `intent.changed` is never pushed (SHOULD).

## Verification

- `:commons:jvmTest --tests '*Napplet*'`: upload rules, NAP-COMMON (NIP-19 round trips and `nsec`
  refusal, follow-list merge and idempotence, react and report validation), intent catalog,
  resolution and delivery, router.
- Node, against the real shim: intent URI normalization and `naddr` hint decoding, buffered
  deliveries, `intent.ready`, upload base64, common results.
- Desktop app, napplets opened by searching their event ids (see the PR).

## Not done

- A handler chooser and a settings screen for per-archetype defaults (today: favorites order).
- Reusing a running target instead of opening a new one; `intent.changed`.
- Upload progress (`upload.status.changed`) and other rails (NIP-96).
