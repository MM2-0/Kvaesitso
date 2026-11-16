# geode plans

_Audited 2026-06-30. 5 plans: 4 shipped (archived), 0 in-progress, 0 queued, 1 closed (negative result)._

Performance-focused design docs for future work. Each file is a
self-contained sketch — problem statement, observed numbers, proposed
fix, how to verify, risks. None of these are committed work; they're
the queue.

Ordered roughly by expected impact:

| Plan | Headline gain | Status |
| ---- | ------------- | ------ |
| [archive/2026-05-07-event-ingestion-batching.md](archive/2026-05-07-event-ingestion-batching.md) | 5–10× write EPS via SQLite group commit + ingest pipelining | shipped (archived) |
| [archive/2026-05-07-live-broadcast-fanout-index.md](archive/2026-05-07-live-broadcast-fanout-index.md) | >10× fanout speedup at >2 000 subscribers | shipped (archived) |
| [archive/2026-05-07-connection-scaling.md](archive/2026-05-07-connection-scaling.md) | 2 000 → 10 000+ concurrent connections | shipped (archived) |
| [archive/2026-05-07-negentropy-large-corpus.md](archive/2026-05-07-negentropy-large-corpus.md) | 25× lower memory + faster NEG-OPEN on M-event corpora | shipped (archived) |

Verification target for each plan is a new method on
`geode.perf.LoadBenchmark` (gated by `-DrunLoadBenchmark=true`) so
regressions show up in the regular CI matrix once they're enabled.

## Archived (shipped)

- [archive/2026-05-07-event-ingestion-batching.md](archive/2026-05-07-event-ingestion-batching.md)
- [archive/2026-05-07-live-broadcast-fanout-index.md](archive/2026-05-07-live-broadcast-fanout-index.md)
- [archive/2026-05-07-connection-scaling.md](archive/2026-05-07-connection-scaling.md)
- [archive/2026-05-07-negentropy-large-corpus.md](archive/2026-05-07-negentropy-large-corpus.md)

## Closed (negative result)

| Plan | Verdict |
| ---- | ------- |
| [2026-07-04-sqlite-knobs-ab.md](2026-07-04-sqlite-knobs-ab.md) | readers/mmap/temp_store/PRAGMA-optimize knobs: no winner on container hardware; every delta flipped with run order. Knobs ship config-gated, off by default. |
