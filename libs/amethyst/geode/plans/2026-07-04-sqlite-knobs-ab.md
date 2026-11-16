# Server-side SQLite knobs — A/B verdict: no winner on container hardware

**Status: closed (negative result recorded).** Backlog items 4–5 of the
relay performance campaign.

## What was tested

The knobs the campaign had previously reverted as noise-inconclusive,
re-tested under the A/B protocol — both variants in ONE relayBench run
(identical container conditions), then the run repeated with the relay
order reversed to expose order bias. 50k synthetic corpus, all four
knobs on the candidate at once:

```toml
[database]
readers = 8                     # reader pool (default 4)
mmap_size = 268435456           # 256 MiB
temp_store_memory = true
optimize_interval_seconds = 15  # PRAGMA optimize between ingest and queries
```

## Result

Every observed delta flipped with run order. In BOTH runs the
second-running relay won most query scenarios and ingest latency —
regardless of which variant it was:

- run 1 (plain → knobs): knobs won 7/10 query scenarios, better ingest
  p99 (8.2 vs 16.0 ms).
- run 2 (knobs → plain): plain won 6/10 query scenarios, better ingest
  p50/p99/throughput (8,552 vs 8,015 ev/s).

Storage size identical (±1 MiB). The periodic `PRAGMA optimize` also
did not move the author-archive/planner-sensitive scenarios.

## Verdict

**All knobs stay off by default.** The `[database]` config plumbing
ships anyway (readers / mmap_size / temp_store_memory /
optimize_interval_seconds) because their value is hardware-dependent —
an operator on NVMe with a large page cache or a memory-constrained VPS
should measure on their own box — and `optimize_interval_seconds`
remains sensible operationally for long-running relays even without a
measurable win at 50k-fresh-database scale.

**Do not re-benchmark these on container-class 4-core hardware** without
first fixing the run-order bias: the protocol note in
`quartz/plans/2026-07-03-incremental-negentropy-storage.md` applies —
same-run A/B plus order reversal is the minimum, and anything that
doesn't survive the order flip is noise.
