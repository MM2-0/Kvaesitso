# Trace artefact: post-merge late-join now passes (2026-05-07)

After merging `origin/main` (commits `2a4c07ae`, `d5c854be`,
`b622d0c9`, `86a4727e`, `31d19258` — five `:quic` patches from
the QUIC team's recent work), the previously-flaking
`late_join_listener_still_decodes_tail` scenario now passes
in 5/5 sweeps. This file is the relay-side TRACE log for the
first sweep's late-join run, useful as a post-merge "what
healthy looks like" baseline against the
`2026-05-07-routing-race-disproven/` pre-merge failure trace.

The interesting span:

```
20:14:17.460567  conn{id=0}  subscribe started catalog.json
20:14:17.460585  conn{id=0}  encoding self=Subscribe …catalog.json
20:14:17.462141  conn{id=0}  decoded result=SubscribeOk         ← 1.6 ms RTT
20:14:17.462446  conn{id=0}  decoded result=Group seq=0
```

vs the pre-merge failing trace where the same span had 2.94 s of
silence followed by Ended (see
`../2026-05-07-routing-race-disproven/sweep-1-FAIL-relay-trace.trace.txt`).
