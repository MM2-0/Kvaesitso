# Trace artefacts: `late_join_listener_still_decodes_tail` flake (2026-05-07)

These three files are the evidence that disproves the
"moq-relay 0.10.x per-broadcast subscribe-routing race" hypothesis
in `nestsClient/plans/2026-05-07-moq-relay-routing-investigation.md`.

Captured by Step 1 of that plan: per-test moq-relay trace logging
(`-DnestsHangInteropTraceRelay=true`) over a 5× sweep of
`HangInteropTest`. Failure rate observed: 3/5 sweeps, all in
`late_join_listener_still_decodes_tail`. Sweeps 1, 2, 3 failed;
4, 5 passed.

## Files

- `sweep-1-FAIL-relay-trace.trace.txt` — moq-relay subprocess stderr
  with `RUST_LOG=info,moq_relay=trace,moq_lite=trace,moq_native=debug`
  for the failing scenario in sweep 1 (broadcast suffix
  `6d60532f…`). 39 lines; ANSI stripped.
- `sweep-1-FAIL-speaker-NestTx.trace.txt` — `Log.d("NestTx")` lines from
  the JUnit XML `<system-err>` filtered to the failing test's time
  window (`18:34:52`–`18:34:57`).
- `sweep-4-PASS-relay-trace.trace.txt` — same scenario, sweep 4, where
  the speaker DID respond to the relay's upstream SUBSCRIBE. Use
  this for the diff.

## How to read them

The crucial claim is: in the FAIL trace, the relay opens a
peer-initiated bidi to the speaker at 18:34:54.152 and writes
a complete `Subscribe { id:0, track:"catalog.json" }` message
to it (lines containing `subscribe started` and
`encoding self=Subscribe`). The speaker's NestTx log has NO
matching `SUBSCRIBE inbound id=0`. Therefore the wire SUBSCRIBE
message is lost between QUIC's bidi accept path and
`MoqLiteSession.handleInboundBidi`.

The PASS trace shows the same scenario completing the
relay→speaker SubscribeOk round-trip in ~1.94 ms, confirming the
speaker-side handler IS capable of processing this bidi when it
manages to reach the application.

## What this rules out

- moq-relay 0.10.x's `Origin::announced()` → `consume_broadcast`
  race. The relay's lookup succeeds and the upstream subscribe
  IS opened.
- Speaker-side hook installation race. The speaker logs
  `ANNOUNCE inbound prefix=''` correctly at T=0 but the LATER
  bidi never reaches handleInboundBidi at all.
- Test framework / test ordering. Same failure recurs across
  per-method `resetShared()` boots and survives sweep 5's
  successful run vs sweep 1's failure on the same harness.

## What it points to

The QUIC stack's path from peer-initiated bidi acceptance →
`WtPeerStreamDemux.readyStreams.trySend(...)` →
`incomingStrippedStreams.consumeAsFlow()` →
`MoqLiteSession.pumpInboundBidis`. One of those handoffs drops the
bidi 40-60 % of the time when bidi #2 arrives ~2 s after bidi #1
on the same connection.
