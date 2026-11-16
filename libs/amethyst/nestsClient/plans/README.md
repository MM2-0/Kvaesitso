# nestsClient plans

_Audited 2026-06-30. 26 plans: 23 shipped (archived), 1 in-progress, 2 queued, 0 abandoned._

## In progress
| Plan | Summary |
| ---- | ------- |
| [2026-05-07-t16-closure-roadmap.md](2026-05-07-t16-closure-roadmap.md) | T16 closure roadmap: Priorities 1 & 2 closed and suite passes, but CI gating is deferred and the framesPerGroup rerun plus two upstream items remain open. |

## Queued
| Plan | Summary |
| ---- | ------- |
| [2026-05-07-cross-stack-interop-ci-gating.md](2026-05-07-cross-stack-interop-ci-gating.md) | Wire CI gating for the cross-stack interop suite; infra was built then removed by maintainer over wallclock cost, kept as the ready target for a revisit. |
| [2026-05-07-framespergroup-production-rerun.md](2026-05-07-framespergroup-production-rerun.md) | Re-run the HCgOY two-phone field tests against current production to settle the framesPerGroup test-pin (5) vs default (50); needs prod-rig access. |

## Archived (shipped)
| Plan | Summary |
| ---- | ------- |
| [archive/2026-04-26-audio-rooms-completion.md](archive/2026-04-26-audio-rooms-completion.md) | Audio-rooms completion: listener + speaker live on moq-lite, create-space flow and kind-10112 server list shipped. |
| [archive/2026-04-26-background-audio-audit.md](archive/2026-04-26-background-audio-audit.md) | Background-audio audit of the foreground service; 4/5 passed and the audio-focus fix landed. |
| [archive/2026-04-26-moq-lite-gap.md](archive/2026-04-26-moq-lite-gap.md) | Bridged the IETF→moq-lite protocol gap; Lite-03 listener + speaker landed against the real nostrnests stack. |
| [archive/2026-04-26-nostrnests-integration-audit.md](archive/2026-04-26-nostrnests-integration-audit.md) | Nostrnests feature-parity punchlist; split into the Tier 1-4 coding plans, all shipped. |
| [archive/2026-04-26-tier-plans-index.md](archive/2026-04-26-tier-plans-index.md) | Index of the four tier coding plans, all of which shipped. |
| [archive/2026-04-26-tier1-coding-plan.md](archive/2026-04-26-tier1-coding-plan.md) | Tier 1: presence aggregation, chat, reactions, roles, kick, scheduled rooms, listener counter — all in code. |
| [archive/2026-04-26-tier2-coding-plan.md](archive/2026-04-26-tier2-coding-plan.md) | Tier 2: participant grid, per-avatar context menu, zap entry points, naddr share — all in code. |
| [archive/2026-04-26-tier3-coding-plan.md](archive/2026-04-26-tier3-coding-plan.md) | Tier 3: room theming parsers (ColorTag/FontTag/BackgroundTag) and the background-audio audit. |
| [archive/2026-04-26-tier4-coding-plan.md](archive/2026-04-26-tier4-coding-plan.md) | Tier 4: token re-mint and moq-lite reconnect-with-backoff (ReconnectingNestsListener/Speaker + NestsReconnectPolicy). |
| [archive/2026-04-28-listener-survives-publisher-recycle.md](archive/2026-04-28-listener-survives-publisher-recycle.md) | Resolution log: all three reconnecting-listener interop scenarios pass against the real moq-rs relay. |
| [archive/2026-05-01-quic-stream-cliff-investigation.md](archive/2026-05-01-quic-stream-cliff-investigation.md) | QUIC stream-cliff investigation closed production-fixed via a `:quic` bug fix + broadcaster cadence tuning. |
| [archive/2026-05-06-cross-stack-interop-test-gap-matrix.md](archive/2026-05-06-cross-stack-interop-test-gap-matrix.md) | Maps each landed T# wire fix to its asserting hang/browser interop scenario (DoD #5). |
| [archive/2026-05-06-cross-stack-interop-test-results.md](archive/2026-05-06-cross-stack-interop-test-results.md) | Cross-stack interop results: 22/23 scenarios green and merged. |
| [archive/2026-05-06-cross-stack-interop-test.md](archive/2026-05-06-cross-stack-interop-test.md) | Cross-stack interop test (T16) spec; implemented and merged as the hang/browser suites. |
| [archive/2026-05-06-i4-stereo-cross-stack-scenario.md](archive/2026-05-06-i4-stereo-cross-stack-scenario.md) | I4 stereo scenario; production per-stream channel-count change merged (PR #2755) plus the test scenarios. |
| [archive/2026-05-06-phase4-browser-harness-results.md](archive/2026-05-06-phase4-browser-harness-results.md) | Phase 4 browser-harness landed results (the CI job was added then removed per maintainer ask). |
| [archive/2026-05-06-phase4-browser-harness.md](archive/2026-05-06-phase4-browser-harness.md) | Phase 4 browser-side cross-stack harness; BrowserInteropTest landed under `nestsClient/tests/browser-interop/`. |
| [archive/2026-05-07-framespergroup-reconciliation.md](archive/2026-05-07-framespergroup-reconciliation.md) | Documentation closing the test-pin (5) vs production-default (50) contradiction; both correct for their own relay cliffs. |
| [archive/2026-05-07-i7-post-reconnect-cliff-investigation.md](archive/2026-05-07-i7-post-reconnect-cliff-investigation.md) | Investigation-only record pinning the post-reconnect cliff on the upstream moq-relay forward queue. |
| [archive/2026-05-07-late-join-catalog-flake-investigation.md](archive/2026-05-07-late-join-catalog-flake-investigation.md) | Late-join catalog flake closed by merging `origin/main`'s 5 `:quic` commits; 55/55 tests pass. |
| [archive/2026-05-07-moq-relay-routing-investigation.md](archive/2026-05-07-moq-relay-routing-investigation.md) | Routing-race investigation closed; root cause was a `:quic` packet-acceptance bug, fixed by the merge. |
| [archive/2026-05-07-tighten-cross-stack-assertions.md](archive/2026-05-07-tighten-cross-stack-assertions.md) | Hardened 7 BrowserInteropTest scenarios + helper from soft- to hard-pass floors; 110/110 sweep. |
| [archive/2026-05-09-moq-lite-rfc-compliance.md](archive/2026-05-09-moq-lite-rfc-compliance.md) | moq-lite Lite-03 compliance audit; no wire-incompatibilities, nine spec tightenings + Lite-04 codec all shipped. |
