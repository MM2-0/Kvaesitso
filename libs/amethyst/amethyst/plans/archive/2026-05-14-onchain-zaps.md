# Onchain Zaps in Amethyst

> **Status:** shipped — Full `quartz/nipBCOnchainZaps/` consensus + builder + verify layer ships with Android model/UI wiring (`OnchainZapResolver`, `OnchainZapEvent` view, `ReusableZapButton`).
> _Audited 2026-06-30._

**Date:** 2026-05-14
**Status:** Active

Implementation plan for NIP-BC (kind 8333) onchain Bitcoin zaps in Amethyst Android.
Quartz now ships the `OnchainZapEvent` data model; this document describes the
rest of the system.

## Design decisions

- **Wallet model.** Non-custodial. The user's Nostr pubkey IS the BIP-341
  internal key of a P2TR output, so every account has exactly one onchain
  address derived from its identity. No seed phrase, no separate wallet
  creation.
- **Signers.** v1 supports `NostrSignerInternal` (local keypair) and
  `NostrSignerExternal` (NIP-55 Android external signer). The NIP-55 path is
  broken until Amber implements `sign_psbt`; surface "update your signer"
  there. `NostrSignerRemote` (NIP-46) is deferred.
- **Chain backend.** User-configured Esplora-compatible API
  (mempool.space, blockstream.info, self-hosted). The configured server sees
  the user's UTXO queries — accepted tradeoff for v1. **Spec PR
  nostr-protocol/nips#2332 adds optional `["block", …]` and
  `["proof", …]` tags on `kind:8333` that let a verifier with only the
  Bitcoin header chain check a zap end-to-end without trusting the
  explorer. Amethyst already parses both tags (see `BlockTag.kt` /
  `ProofTag.kt`) but no production code path produces or consumes them
  yet — see the "Inline SPV proofs" section below.** The explorer
  endpoint is shared with OpenTimestamps via `BitcoinExplorerEndpoint`:
  same user-configured server, same Tor-aware default selection.
- **Scope.** Full send + receive + display loop on Android. Desktop is out of
  scope for v1.

### Architecture decision: hand-rolled Bitcoin consensus code (2026-05-14)

**Decision:** keep the hand-rolled Bitcoin consensus layer in
`quartz/.../nipBCOnchainZaps/{psbt,taproot}/` — the transaction codec,
serialization/txid, BIP-341 sighash, BIP-174 PSBT codec/signer/finalizer, and
BIP-341/350 address derivation. Do **not** pull in `fr.acinq.bitcoin-kmp`.

**Considered alternative:** replace the `psbt/` + transaction + sighash layer
with `fr.acinq.bitcoin-kmp` (mature, same vendor as the `secp256k1` binding
already in the build).

**Rationale for keeping it hand-rolled:**
- It is a deliberately small, constrained subset — single-key-path P2TR only,
  no script trees, one transaction shape.
- It is pinned to authoritative external test vectors at *every* layer:
  BIP-341 sighash (all 7 vectors + ANYONECANPAY), the BIP-341 tweak, the full
  BIP-341 witness *signature bytes*, the 7 BIP-341/350 P2TR mainnet addresses,
  and tx serialization against the genesis coinbase. It is "matches the
  authoritative vectors," not "trust our code."
- Consistent with the project's stance on minimal dependencies (cf. the
  from-scratch `quic` module).
- No new transitive dependencies or version-conflict surface.

**Consequence / what this commits us to:** we own the correctness of this code
forever. If the scope ever expands beyond single-key-path P2TR (script-path
spends, multisig, PSBT fields we don't model), revisit this decision — at that
point a vetted library is the better trade. The `nipBCOnchainZaps/{psbt,taproot}/`
packages carry a pointer back to this section.


## Architecture

| Layer | Concerns | Location |
|---|---|---|
| Quartz | Address derivation, PSBT codec, Esplora client, NIP-BC verifier, `signPsbt` on signer hierarchy | `quartz/.../nipBCOnchainZaps/{taproot,psbt,chain,build,verify}/` |
| Commons | Send/wallet ViewModels, shared composables | `commons/.../onchain/` |
| Amethyst | `OnchainSection` in existing `WalletScreen`, `LocalCache.consume(OnchainZapEvent)`, kind list edits in 8 filter files, NIP-55 intent plumbing | `amethyst/.../ui/onchain/`, `amethyst/.../model/LocalCache.kt`, existing filter files |

## Merging into the existing wallet UI

The existing `WalletScreen` is a NIP-47 NWC multi-wallet manager. Onchain wallet
fits as a separate top section, since it has different semantics (single
deterministic wallet per account, no NWC URI, chain-backed).

```
WalletScreen
├── TopAppBar
├── BitcoinSection           ← NEW, single card
│   └── OnchainWalletCard    (balance, bc1p address, tap → OnchainWalletDetailScreen)
└── LightningSection         ← existing MultiWalletHomeContent
    ├── NoWalletSetup
    └── NwcWalletCard × N
```

The "+" `Add wallet` icon still adds NWC entries only — onchain is implicit.
Section labels: "Bitcoin" and "Lightning".

## Subscription edits — extend existing kind lists

No new assemblers. Add `OnchainZapEvent.KIND` (8333) to the existing
`ZapReceiptEvent.KIND` (9735) sites:

| File | Edit |
|---|---|
| `amethyst/.../FilterRepliesAndReactionsToNotes.kt:48-60` | Add to `RepliesAndReactionsKinds` (note `#e`) |
| `amethyst/.../FilterUserProfileZapReceived.kt:30` | Add to `UserProfileZapReceiverKinds` (profile `#p`) |
| `amethyst/.../zaps/dal/UserProfileZapsViewModel.kt:55` | Add to inline `kinds = listOf(...)` |
| `amethyst/.../FilterNotificationsToPubkey.kt:58-65` | Add to `SummaryKinds` (notifications `#p`) |
| `amethyst/.../NotificationFeedFilter.kt:123` | Add to `NOTIFICATION_KINDS` |
| `amethyst/.../NotificationDispatcher.kt:98` | Add to `NOTIFICATION_KINDS` |
| `amethyst/.../FilterMessagesToLiveStream.kt:46` | Add to live-activity zap kinds (`#a`) |
| `amethyst/.../FilterGoalForLiveActivity.kt:58` | Add to goal-zap kinds (`#e`) |

## Display path — fold into `Note.zapsAmount`

Today: `LocalCache.consume(ZapReceiptEvent)` → `Note.addZap()` → `Note.updateZapTotal()`
sums lightning amounts into `Note.zapsAmount`, which `ReactionsRow` /
`ObserveZapAmountText` / `SlidingAnimationAmount` render. We add onchain zap
sats to the same `Note.zapsAmount` — no UI changes required.

- `commons/.../model/Note.kt:154-157, 621-632`
  - Add `var onchainZaps = mapOf<...>()` (separate map from `zaps`).
  - Extend `updateZapTotal()` to add **verified** onchain sats. Unverified or
    pending tx amounts are NOT counted.
- `amethyst/.../model/LocalCache.kt` (after the `consume(ZapReceiptEvent)` block ~line 1667)
  - New `consume(event: OnchainZapEvent)` handler.
  - Reject self-zap.
  - Enqueue verification against the configured `OnchainBackend`.
  - On success: `Note.addOnchainZap(event, verifiedSats)` on each `repliesTo`.
  - On failure or zero verified amount: discard.
  - Dedupe by `(txid, target)`.

## Quartz additions

- `nipBCOnchainZaps/taproot/`
  - `SegwitAddress.kt` — bech32m segwit address encoder/decoder
  - `TaprootAddress.kt` — Nostr pubkey → bc1p P2TR address via BIP-341
    key-path-only tweak (uses `Secp256k1Instance.pubKeyTweakAdd`)
- `nipBCOnchainZaps/chain/`
  - `OnchainBackend` interface — `getTx`, `getUtxos`, `broadcast`,
    `tipHeight`, `feeEstimates`
  - `BitcoinTx`, `BitcoinTxOutput`, `Utxo` data models
  - `BitcoinTxParser` — minimal raw-tx parser (just enough for verification)
  - `EsploraBackend` (jvmAndroid) — OkHttp impl
- `nipBCOnchainZaps/verify/`
  - `OnchainZapVerifier` — implements all spec rules: reject self-zap, sum
    only outputs paying the derived recipient address, dedupe `(txid, target)`,
    cap claimed amount at verified amount
- `nipBCOnchainZaps/psbt/` (Phase A.2)
  - Minimal BIP-174 codec
  - `PsbtTaprootKeyPathSigner` — BIP-341 TapTweak + Schnorr sign
- `nipBCOnchainZaps/build/` (Phase A.2)
  - `OnchainZapBuilder` — given (sender, recipient, sats, feeRate, utxos),
    build a PSBT with change back to sender's Taproot
- `NostrSigner.signPsbt` (Phase A.2)
  - Internal: signs directly
  - External (NIP-55): launches `sign_psbt` intent — needs Amber update
  - Remote (NIP-46): `NotSupportedException` stub

## Commons additions

- `OnchainWalletViewModel` — derived address, balance, UTXO list
- `OnchainZapSendViewModel` — build → sign → broadcast → publish kind 8333
- `OnchainZapSendDialog`, `OnchainAddressQrCard`, `FeeRatePicker`

## Account state

In `AccountSettings.kt` next to `nwcWallets`:

```kotlin
val onchainEsploraEndpoint: MutableStateFlow<String>   // default mempool.space
val onchainDefaultFeeTier: MutableStateFlow<FeeTier>   // SLOW / NORMAL / FAST
```

Derived `Account.onchainBalance: StateFlow<Long?>` populated by
`OnchainWalletViewModel`.

## Send-from-note merge

Existing `ZapAmountChoicePopup` (`ReactionsRow.kt:1877-1977`) stays as the
Lightning fast path. Two minimal hooks:

1. Append `[ ⛓ Onchain… ]` row to the popup. Tapping it opens
   `OnchainZapSendDialog` (fee picker + confirmation), separate from the
   instant-tap Lightning UX.
2. Optional settings toggle "Show onchain zap option" — defaults off until a
   balance is observed.

## Phased delivery

| Phase | Deliverable | Status |
|---|---|---|
| **A.1** | Quartz foundation (receive side): taproot address, bech32m segwit, Esplora client, verifier + tests | **Shipped** |
| **C**   | Receive + display: `OnchainSection` in `WalletScreen` (address + live balance), Esplora sync, `LocalCache.consume(OnchainZapEvent)`, `updateZapTotal` fold-in, kind-list edits in all 7 in-scope filter files | **Shipped** |
| **A.2** | Quartz foundation (send side): BIP-174 PSBT codec, BIP-341 sighash, `OnchainZapBuilder`, `signPsbt` on the `NostrSigner` hierarchy. All crypto validated against BIP-341 wallet test vectors (31 tests). | **Shipped** |
| **D**   | Send flow: `OnchainZapSender` orchestrator, `Account.sendOnchainZap`, `OnchainZapSendDialog`, "Send" button on the wallet `OnchainSection`. | **Shipped** |
| **B**   | NIP-55 `sign_psbt` Intent + ContentResolver contract, wired through `NostrSignerExternal.signPsbt`. Works once the external signer app (Amber etc.) ships `sign_psbt` support — older signers reply with no `result`, surfaced as a send failure. | **Shipped** |

### Phase A.2 — shipped

- `nipBCOnchainZaps/psbt/`: `BitcoinIO` (LE byte codec + varint), `BitcoinTransaction`
  (legacy + segwit serialization, witness-stripped txid), `TaprootSigHash`
  (BIP-341 SigMsg + TapSighash, all sighash types), `Psbt` (BIP-174 subset,
  unknown-record-preserving), `PsbtSigner` (key-path signing), `PsbtFinalizer`.
- `nipBCOnchainZaps/build/OnchainZapBuilder`: largest-first coin selection +
  unsigned-PSBT assembly with dust-aware change.
- `TaprootAddress.tweakSecretKey`: BIP-341 `taproot_tweak_seckey`.
- `Secp256k1Instance.privKeyNegate`: new primitive (commonMain + 3 actuals).
- `NostrSigner.signPsbt`: real impl on internal signer; delegated by the
  client-tag wrapper; `UnsupportedMethodException` stubs on NIP-46 / NIP-55.

### Phase D — shipped

- `commons/onchain/OnchainZapSender`: stateless orchestrator —
  load UTXOs → `OnchainZapBuilder.build` → `signer.signPsbt` →
  `PsbtFinalizer` → `OnchainBackend.broadcast` → publish kind:8333 via an
  injected callback. Per-stage `OnchainZapSendResult.Failure`; broadcast txid
  preserved when only the receipt-publish stage fails.
- `Account.sendOnchainZap`: binds the account signer, `LocalCache.onchainBackend`,
  and `signAndComputeBroadcast` into the orchestrator.
- `OnchainZapSendDialog`: recipient npub (or fixed recipient + `zappedEvent`
  for a future note-zap-menu entry), amount, fee tier from the backend's
  estimates, comment; runs the send and shows progress + result.
- `OnchainSection`: "Send" button on the wallet-screen Bitcoin card.

### Phase B — shipped

- `CommandType.SIGN_PSBT` (`sign_psbt`) — also usable in NIP-55 `perms` lists
  via `Permission`, which wraps `CommandType` directly.
- `SignPsbtResult` result type; `SignPsbtQuery` (background ContentResolver),
  `SignPsbtRequest` / `SignPsbtResponse` (foreground Intent), mirroring the
  `derive_key` string-in/string-out shape — the PSBT hex rides the
  `nostrsigner:` URI, the signed PSBT comes back in `result`.
- `BackgroundRequestHandler.signPsbt` / `ForegroundRequestHandler.signPsbt`;
  `NostrSignerExternal.signPsbt` now does the real background-then-foreground
  query instead of throwing. External signers that predate `sign_psbt` reply
  with no `result` → `CouldNotPerformException`, surfaced by the send dialog.

### What's still pending

1. **Note-zap-menu entry point.** `OnchainZapSendDialog` already accepts
   `recipientPubKey` + `zappedEvent`; wiring an "Onchain" option into the
   existing `ZapAmountChoicePopup` is the remaining UI hook for event zaps.
2. **NIP-46 `sign_psbt`.** `NostrSignerRemote.signPsbt` still throws
   `UnsupportedMethodException` — the bunker-side command is not standardized
   yet.
3. **Send-side `block` + `proof` emit.** See "Inline SPV proofs" below.
4. **Receive-side SPV verification.** See "Inline SPV proofs" below.

## Inline SPV proofs (`block` + `proof` tags) — pending

### Spec status (2026-05-19)

`nostr-protocol/nips#2332` adds two new optional tags on `kind:8333`:

```
["block", "<block-hash-hex>", "<height>"]
["proof", "<raw-tx-hex>", "<merkle-proof-hex>"]
```

Together they let a verifier with only the Bitcoin header chain check a
zap end-to-end without trusting the chain backend. The recipient hashes
`raw-tx-hex` to recover the txid, walks the merkle proof from that leaf
up to the header's `merkleRoot`, parses outputs from `raw-tx-hex`, and
sums those that pay the recipient's derived Taproot script.

**One spec ambiguity outstanding** (raised back to the PR author):
direction-at-each-step encoding for `<merkle-proof-hex>` — Bitcoin's
merkle tree is tx-order-sensitive, so concatenated siblings alone don't
specify left vs right. Suggested resolution: `1-byte direction || 32-byte
sibling` per step (33 B stride). This blocks Phase G.2 (walker
implementation), nothing else.

### Current state in Amethyst

| Layer | Status | Location |
|---|---|---|
| `BlockTag(blockHashHex, height: Long)` | ✅ shipped | `quartz/.../nipBCOnchainZaps/zap/tags/BlockTag.kt` |
| `ProofTag(rawTxHex, merkleProofHex)` | ✅ shipped | `…/zap/tags/ProofTag.kt` |
| `OnchainZapEvent.block()` / `.proof()` accessors | ✅ shipped | `…/zap/OnchainZapEvent.kt:89-93` |
| TagArray + TagArrayBuilder helpers | ✅ shipped | `…/zap/TagArrayExt.kt`, `…/zap/TagArrayBuilderExt.kt` |
| Tx parser + witness-stripped txid (reusable for the walker) | ✅ shipped | `…/psbt/BitcoinTransaction.kt` |
| Recipient scriptPubKey derivation | ✅ shipped | `taproot/TaprootAddress.scriptPubKeyHexForRecipient` |
| `OnchainZapSender` populates `block` / `proof` after broadcast | ❌ gap | `commons/.../onchain/OnchainZapSender.kt:209-214` (calls `build()` / `buildProfileZap()` with no SPV tags) |
| `OnchainBackend.getMerkleProof(txid)` to source the proof on send | ❌ gap | `chain/OnchainBackend.kt` |
| Merkle-proof parser + walker | ❌ gap | new `verify/MerkleProofVerifier.kt` |
| `OnchainZapVerifier` prefers inline proof when present | ❌ gap | `verify/OnchainZapVerifier.kt` |
| Validated `header.merkleRoot` lookup by block hash | ❌ gap | depends on the headers-explorer plan (S1) at `quartz/plans/2026-05-08-local-headers-explorer.md` |

### Send-side: Design B (two-publish)

`OnchainZapSender` publishes the `kind:8333` receipt **immediately** after
`backend.broadcast(rawTxHex)` returns, when confirmations = 0. The chosen
design keeps that instant UX and adds a second publish after the tx
confirms:

1. Today's path stays. Publish `kind:8333` right after broadcast, with
   `i`/`p`/`amount`/`alt` only — no `block`, no `proof`.
2. A new post-confirmation worker watches the tx via the backend. When it
   sees ≥1 confirmation:
   - Call `backend.getMerkleProof(txid)` → `(block_hash, height, siblings, pos)`.
   - Encode `siblings + pos` into the spec's `<merkle-proof-hex>` wire format
     (depends on the resolved spec encoding).
   - Build a second `kind:8333` referencing the same `i` tag, with `block`
     and `proof` filled in. Publish.
3. Verifiers MUST dedupe by `(txid, target)` and prefer the variant
   carrying a valid SPV proof. (This dedupe rule is also in the suggested
   spec amendment.)

Two relay broadcasts per zap, ~2 KB extra each. The post-confirmation
worker is per-account, persists across app restarts, and exits cleanly
once each pending tx is either confirmed (publish second receipt) or
abandoned (after some timeout — TBD).

### Receive-side

`OnchainZapVerifier.verify(event)` gets a new fast path:

1. If `event.block() != null && event.proof() != null`:
   - Look up header by hash via `LocalHeadersBitcoinExplorer.byHash()` (S1).
     If unavailable yet, fall through to the existing `backend.getTx()` path.
   - `BitcoinTransaction.parse(rawTxHex)` → tx; recompute
     `dsha256(stripWitness(tx))` → expected_txid; check it matches the
     `i` tag's txid.
   - `MerkleProofVerifier.verify(expected_txid, proofBytes, header.merkleRoot)` → boolean.
   - Parse outputs from the same parsed tx; sum outputs paying the
     recipient's `scriptPubKeyHexForRecipient`, skipping outputs paying
     the sender's own derived script (change).
   - Confirmations = `tip.height − block.height + 1`.
   - Return `Confirmed`.
2. If the inline path fails verification (bad proof, missing header,
   tx-mismatch), the event is treated as if `block` / `proof` were
   absent — fall back to the existing Esplora path. **Never** treat a
   failed SPV proof as a hard-reject of the whole event; that would let
   a buggy producer poison legitimate zaps.
3. If neither tag is present (old-shape events), the existing
   `backend.getTx()` path runs unchanged.

The end state: a strict-mode user with HTTP fallback disabled can verify
any zap whose sender included `block` + `proof`, and only those.

### Phased delivery for the SPV work

| Phase | Deliverable | Depends on | Effort |
|---|---|---|---|
| **G.1** | `MerkleProofVerifier` in `nipBCOnchainZaps/verify/`; test vectors per the resolved spec encoding | spec encoding locked | ½ d |
| **G.2** | `OnchainZapVerifier` consumes inline proof, falls back on failure; needs `LocalHeadersBitcoinExplorer.byHash()` from S1 | S1 Phase 2 (storage) | ½ d |
| **G.3** | `OnchainBackend.getMerkleProof(txid)` interface + `EsploraBackend` impl (`/tx/{txid}/merkle-proof`) | — | ½ d |
| **G.4** | Post-confirmation worker + second-publish flow in `OnchainZapSender` | G.3 | 1 d |
| **G.5** | Dedupe by `(txid, target)` preferring SPV-attached variant; `Note.zapsAmount` only counts the winner | — | ½ d |
| **G.6** | End-to-end tests: real mainnet block + proof + zap; negative cases (wrong root, truncated, wrong direction) | G.1 | 1 d |
| **G.7** | (Optional) Settings toggle "Verify zaps trustlessly when possible" — off by default until S1 ships | S1 Phase 7 | ½ d |
| **Total** | | | ~3–4 d (after S1 ships and spec encoding lands) |

S1 (the headers explorer at `quartz/plans/2026-05-08-local-headers-explorer.md`)
is the long pole. The SPV work itself is small — every primitive needed
already exists in the codebase, the only new thing is the merkle walker.

## Risks / open questions

- **secp256k1-kmp tweak coverage** — pubKeyTweakAdd exists on JVM/Android/JNI
  but not on the pure-Kotlin native impl. iOS support deferred until upstream
  ships it or we contribute.
- **PSBT correctness** — single-key-path-only is a small surface; still needs
  thorough testing against BIP-174 test vectors before any user funds move.
- **Esplora privacy** — the configured server sees user UTXO queries. Default
  to a reputable provider, make user-configurable, consider future Tor option.
- **NIP-55 ecosystem** — Amber must implement `sign_psbt` for external signer
  accounts to use this feature.
- **`Note.zaps` shape** — separate `onchainZaps` map vs sealed-type fold-in.
  Leaning separate map for v1.
- **Verification network calls from `LocalCache`** — `consume` runs on the
  relay thread; verification needs a coroutine scope + `OnchainBackend`
  instance injected from `Account` on app start.
