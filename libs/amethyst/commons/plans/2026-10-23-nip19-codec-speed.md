# NIP-19 codec: faster, lighter Bech32 + TLV

_2026-10-23. Shipped: quartz 2.2.0 (vitorpamplona/quartz#14), and Amethyst bumped to it with the
call-site changes below. The benchmark is `commons/src/jvmTest/.../prodbench/nip19/Nip19Benchmark.kt`._

## Status

- **quartz 2.2.0:** single-pass Bech32, in-place TLV, the 255-byte TLV fix and the audit fixes
  (lone surrogates, non-hex keys, the generator-table index), in vitorpamplona/quartz#14. The
  public API keeps every old signature and only adds new ones. The behaviour changes are listed
  under "API decisions" below.
- **Amethyst:** `quartzKmp` is 2.2.0. The call sites that cite someone else's d-tag use
  `Address.toNIP19OrFallback` / `toNostrUriOrFallback`. These give the `naddr`, else the loaded
  event's `nevent`, else the raw address, so `AddressableNote.toNAddr()` and `AntiSpamFilter`
  (which runs on ingest) never throw on an attacker-chosen long d-tag. `Note`/`User`/channel
  `toNostrUri()` use the one-buffer `createNostrUri` helpers.
- The prototype codec that this plan measured (`FastNip19.kt`) was deleted once quartz carried
  the real one. `Nip19Benchmark` now times quartz alone.

Measured with quartz built from the merged change (`-PquartzDir`), 2.1.0 → 2.2.0:

| | 2.1.0 | 2.2.0 |
| --- | --- | --- |
| `Note.toNostrUri()` | 3,339 ns, 3,559 B | 1,653 ns, 1,314 B (relay-hint lookup is 696 ns of it) |
| `User.pubkeyNpub()` / `pubkeyDisplayHex()` | 918 / 921 ns | 285 / 311 ns |
| `nostr:nevent` encode | 2,344 ns, 2,915 B | 688 ns, 728 B |
| `npub` → entity | 1,150 ns, 1,686 B | 352 ns, 558 B |
| `nevent` → entity | 3,739 ns, 4,846 B | 1,359 ns, 1,177 B |
| `uriToRoute` nevent | 6,175 ns | 3,511 ns (the regex is now most of it) |

### API decisions

- `TlvBuilder` throws `IllegalArgumentException` for a value over 255 bytes
  (`TlvBuilder.MAX_VALUE_SIZE`).
- `NAddress.create` throws for a d-tag over 255 bytes. New `createOrNull` and
  `createNostrUriOrNull` return null instead.
- Relay hints over 255 bytes are dropped from `nevent`/`nprofile`/`naddr`.
- `Event.toNIP19` / `Event.toNostrUri` fall back to the event's `nevent` when its d-tag can't
  fit an `naddr`.
- New, additive: `Bech32.encodeBytes(…, prefix)`, `encodeHex`, `decodeBytesOrNull`;
  `NPub`/`NEvent`/`NProfile.createNostrUri`; `Hex.encode(bytes, from, to)`.
- Stricter inputs:
  - An ASCII non-hex char in a key passed to `create` now throws. Before, it encoded garbage.
    (Odd lengths and non-ASCII chars already threw.)
  - In `decodeBytes(noChecksum = true)`, a char outside the alphabet now throws instead of
    decoding as `l`.
  - `NAddress.parse(String)` checks that the hrp is exactly `naddr`.

## Why

`Note.toNostrUri()` is the `callbackUri` of almost every rendered note (`RenderRegularTextNote`,
`Text`, `Video`, `Chat`, `RenderEncryptedFile`, …). Most call sites compute it during composition
without `remember`, so it runs on each recomposition. `CachedRichTextParser` keys its cache on
it, so the call can't be skipped. `User.pubkeyNpub()` / `NPub.create` (≈120 call sites) back
every short display name. On the decode side, `Nip19Parser.parseAll` runs `parseComponents`
on every `nostr:` reference of every ingested note.

## Use cases surveyed

| Entity | Encode (app) | Decode (app) |
| --- | --- | --- |
| `npub` | `NPub.create` ×61, `toNpub()` ×59, `pubkeyNpub`/`pubkeyDisplayHex` in composition | `decodePublicKeyAsHexOrNull` ×22, login, search, `parseAll` |
| `nsec` | `toNsec()` ×10 (backup, export) | login |
| `note` | `NNote.create` ×8 | `parseAll` (legacy refs) |
| `nevent` | `Note.toNEvent/toNostrUri`, `NEvent.create` ×27, tag `toNEvent()` | `parseAll`, `uriToRoute` (navigation, clickable routes) |
| `nprofile` | `User.toNProfile/toNostrUri` (QR, share, mentions) | `parseAll`, `uriToRoute` |
| `naddr` | `AddressableNote.toNAddr`, `NAddress.create` ×18, live activities | `parseAll`, `NAddress.parse` |
| `nrelay`, `nembed` | none / `NEmbed.create` (gzip-dominated) | `parseAll` |

## Measured (JVM, `commons/src/jvmTest/.../prodbench/nip19/Nip19Benchmark.kt`)

Run with `./gradlew :commons:jvmTest --tests "*nip19.Nip19Benchmark*"`. The figures are best of 5
rounds over 1,024 varied inputs, on an unloaded cloud container. `outputsMatchQuartz` asserts that
both codecs produce identical strings and identical entities.

| Operation | quartz 2.1.0 | prototype | |
| --- | --- | --- | --- |
| `npub` encode | 870 ns, 998 B | 206 ns, 198 B | 4.2× faster, 5.0× less alloc |
| `nevent` encode (id, author, kind, relay) | 2,255 ns, 2,680 B | 630 ns, 516 B | 3.6×, 5.2× |
| `nprofile` encode (0–3 relays) | 1,880 ns, 2,147 B | 505 ns, 439 B | 3.7×, 4.9× |
| `naddr` encode | 2,264 ns, 2,568 B | 643 ns, 492 B | 3.5×, 5.2× |
| `nostr:nevent` (NIP-21 uri) | 2,434 ns, 2,891 B | 668 ns, 528 B | 3.6×, 5.5× |
| `npub` → entity | 1,141 ns, 1,686 B | 277 ns, 262 B | 4.1×, 6.4× |
| `nevent` → entity | 3,684 ns, 4,856 B | 1,258 ns, 622 B | 2.9×, 7.8× |
| `naddr` → entity | 3,341 ns, 4,553 B | 925 ns, 515 B | 3.6×, 8.8× |
| `Note.toNostrUri()` end to end | 3,322 ns, 3,535 B | — | `relayHintUrl()` is 661 ns of it, the encode is the rest |

`uriToRoute` (regex find + decode) costs 2.2 µs for an `npub`, where the decode alone is 0.3 µs
with the prototype.

## Where quartz spends it

1. **Boxed 5-bit values.** `eight2five` returns `ArrayList<Byte>` and `decode` builds `Array<Byte>`.
   Both are `Object[]` of `java.lang.Byte`. `addChecksum` then copies again for `expand(hrp) + data`.
2. **Several full copies per string.** `CharArray` → `concatToString()` → `hrp + "1" + …`, then
   `"nostr:" + …` at the caller. On top of that, `encode` allocates `hrp.lowercase()` and
   `hrp.uppercase()` on every call just to check the case.
3. **Branchy polymod.** Five conditional xors per step, instead of one lookup in a 32-entry table.
4. **TLV building.** `hexToByteArray()` per key, `encodeToByteArray()` per string, then a
   growable `ByteArrayOutputStream` and a final `toByteArray()` copy.
5. **TLV parsing.** `Tlv.parse` builds a `Map<Byte, MutableList<ByteArray>>` holding a copy of
   every value, before the entity reads two or three of them. `decode` also `filter`s the input,
   `take`s and lowercases the hrp, and `copyOfRange`s the data.

## The proposal

Prototype: `commons/src/jvmTest/.../prodbench/nip19/FastNip19.kt`. It is pure common Kotlin, so it
can go into quartz's `commonMain` as it stands.

- **Single-pass encode.** Size the output exactly. Regroup 8→5 bits and fold each group into the
  checksum as it is written, with no intermediate arrays. Write the result into one ASCII
  `ByteArray`, then `decodeToString()`. On the JVM that was clearly faster than a `CharArray`
  (npub 441 → 206 ns), because an ASCII byte array copies straight into a Latin-1 compact string.
- **Encode hex without decoding it first.** `npub`/`nsec`/`note` regroup the hex nibbles directly
  (`encodeHex`).
- **Optional `prefix` parameter.** `"nostr:"` is written into the same buffer, so
  `toNostrUri()` stops making a second copy.
- **Table polymod and a precomputable hrp seed.** The checksum state after the hrp depends only on
  the prefix.
- **Exact-size TLV writer.** Compute the UTF-8 length first, decode hex into place, and copy
  ASCII strings char by char.
- **Single-pass decode.** Ignore chars outside `33..126` as quartz does, split at the last `1`,
  and run the checksum and the 5→8 regrouping together into an exact-size array. A `start, end`
  range lets `Nip19Parser` decode straight from the scanned content, without the
  `substring` + `type + key` concatenation it does today.
- **Read TLV in place.** One walk over the records: hex-encode and `decodeToString(from, to)`
  each slice, keeping only the records the entity uses.

Keep `Bech32.encode/decode/eight2five/five2eight` as the public API (other modules, and the
BOLT12 code in this repo, call `Bech32.encode`). Implement them on top of the new core, and move
`ByteArray.toNpub()`, `NPub.create`, `NEvent.create`, `NProfile.create`, `NAddress.create` and the
`parse(bytes)` functions onto the fast paths. Add `toNostrUri`-style overloads that take a
prefix. Then point `Note.toNostrUri()`, `User.toNostrUri()`, `PublicChatChannel.toNostrUri()` and
`LiveActivitiesChannel.toNostrUri()` at them.

## Bugs found on the way (fix in the same quartz PR)

- **A TLV value over 255 bytes produces an `naddr` no one can decode.** `TlvBuilder.add` writes
  `byteArray.size.toByte()`. Checked: `NAddress.create(30023, pk, "a".repeat(300), [])` gives a
  560-char `naddr` that quartz's own `Nip19Parser.uriToRoute` decodes to `null`. Long d-tags exist
  (slugs, URLs used as d-tags). Relay hints over 255 bytes should be dropped. A d-tag that long
  has no valid encoding, so `create` should fail loudly (or return null) rather than emit a
  corrupt string.
- `NAddress.parse(String)` logs `$this` (the companion object) instead of the input.
- `to32BitByteArray` masks with `0xFFFF`. That is harmless because `toByte()` truncates anyway,
  but the mask is misleading.

## App-side follow-ups (this repo)

- `Note.relayHintUrl()` costs 661 ns per call. `author.outboxRelays()` re-parses the author's
  kind:10002 tags and builds a `Set` on every call. `User.relayHints()` does the same work (488 ns).
  Caching the parsed outbox list per `AdvertisedRelayListEvent` (on the `User`, invalidated when
  `nip65RelayListNote.event` changes) removes most of it.
- Don't memoize the URI string on each `Note`. It would hold an extra string for every note ever
  rendered, which works against the memory goal. With the encode at ~0.6 µs, it's not needed.
- `uriToRoute` still uses the ICU-backed regex that `parseAll` moved away from. It runs 38 times
  in the app (navigation, clickable routes). It could reuse the hand-written scanner.

## Not verified

- Only measured on the JVM. ART's `String(byte[], UTF_8)` and `String(char[])` are native, so
  the `ByteArray` vs `CharArray` choice should be re-checked on a device (the `benchmark`
  module). Kotlin/Native was not measured.
