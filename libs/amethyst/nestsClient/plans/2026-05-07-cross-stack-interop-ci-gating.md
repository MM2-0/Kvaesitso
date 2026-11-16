# Plan: wire CI gating for the cross-stack interop suite

> **Status:** queued — infra was wired (`21947bc5`) then DEFERRED/removed by maintainer over wallclock cost; kept in-tree as the ready-made target for a future CI-gating revisit.
> _Audited 2026-06-30._

**Status:** ⏸ DEFERRED 2026-05-07.
The infrastructure to wire CI is ready — both jobs landed in
commit `21947bc5` and a 10/10 stability sweep × 22 tests =
220/220 passed on the agent rig. A maintainer review then
judged the wallclock cost too high for the change-pattern and
removed the jobs (cold cache: ~10 min hang, ~13 min browser;
most PRs don't touch audio / MoQ / QUIC).

The suites are now run manually before merging changes that
touch the relevant code paths. Developer-facing docs:
[`../tests/README.md`](../tests/README.md). This plan stays
in the tree for the next time someone wants to revisit CI
gating — the YAML shapes below are the exact reverse-merge
target, and the stability bar (10/10 sweep) has already been
demonstrated on this branch.

**Depended on:**
- `2026-05-07-moq-relay-routing-investigation.md` closed (✅)
- `2026-05-07-tighten-cross-stack-assertions.md` closed (✅)
- 10/10 sweep stability verified (✅)

## What's needed

### A) `.github/workflows/build.yml` — the hang-interop job

The job was originally part of this branch but removed per
maintainer ask in commit `6829ab727` ("ci(nests): drop hang-interop
job from build.yml") because the suite was flaky. Resurrect the
exact same shape:

```yaml
hang-interop:
  needs: lint
  runs-on: ubuntu-latest
  timeout-minutes: 30
  steps:
    - uses: actions/checkout@v6
    - uses: actions/setup-java@v5
      with: { distribution: 'zulu', java-version: 21 }
    - uses: gradle/actions/setup-gradle@v4
      with:
        cache-read-only: ${{ github.ref != 'refs/heads/main' }}
    - uses: dtolnay/rust-toolchain@stable
    - uses: actions/cache@v4
      with:
        path: |
          ~/.cargo/registry
          ~/.cargo/git
          nestsClient/tests/hang-interop/target
          ~/.cache/amethyst-nests-interop/hang-interop-cargo
        key: ${{ runner.os }}-cargo-${{ hashFiles('nestsClient/tests/hang-interop/Cargo.lock', 'nestsClient/tests/hang-interop/REV') }}
        restore-keys: |
          ${{ runner.os }}-cargo-
    - name: Run cross-stack interop suite
      run: ./gradlew :nestsClient:jvmTest -DnestsHangInterop=true
    - uses: actions/upload-artifact@v7
      if: failure()
      with:
        name: Hang Interop Test Reports
        path: nestsClient/build/reports/tests/jvmTest/
```

The `git show 6829ab727 -- .github/workflows/build.yml` reverse
gives the exact diff to re-add. Linux-only is correct: the cargo
install of moq-relay 0.10.x has nontrivial native deps
(aws-lc-sys, ring) that take 5+ min cold; cached runs ~30 s.
macOS / Windows would double matrix cost without catching new
defects.

### B) `.github/workflows/build.yml` — the browser-interop job

Same shape as A, plus bun + Playwright caching:

```yaml
browser-interop:
  needs: lint
  runs-on: ubuntu-latest
  timeout-minutes: 30
  steps:
    # ...same checkout + JDK + Gradle + Rust + cargo cache as hang-interop...
    - uses: oven-sh/setup-bun@v2
      with: { bun-version: 1.3.11 }
    - uses: actions/cache@v4
      with:
        path: |
          nestsClient/tests/browser-interop/node_modules
          nestsClient/tests/browser-interop/dist
        key: ${{ runner.os }}-bun-${{ hashFiles('nestsClient/tests/browser-interop/package.json', 'nestsClient/tests/browser-interop/bun.lock') }}
    - uses: actions/cache@v4
      with:
        path: ~/.cache/ms-playwright
        key: ${{ runner.os }}-playwright-${{ hashFiles('nestsClient/tests/browser-interop/package.json') }}
    - name: Run browser cross-stack interop suite
      run: |
        ./gradlew :nestsClient:jvmTest \
            --tests "com.vitorpamplona.nestsclient.interop.native.BrowserInteropTest" \
            -DnestsHangInterop=true \
            -DnestsBrowserInterop=true
    - uses: actions/upload-artifact@v7
      if: failure()
      with:
        name: Browser Interop Test Reports
        path: |
          nestsClient/build/reports/tests/jvmTest/
          nestsClient/tests/browser-interop/test-results/
          nestsClient/tests/browser-interop/playwright-report/
```

Same `git show b94737de7 -- .github/workflows/build.yml` reverse
gives the exact diff (`feat/nests-browser-interop`'s removal
commit).

### C) Cross-link with `:cli` interop tests

The existing `nests-interop` opt-in pattern already lives in
`cli/tests/nests/nests-interop.sh`. Confirm both new jobs run
in parallel with that without resource contention. They use
different ports (NativeMoqRelayHarness reserves `ServerSocket(0)`)
so they're independent at the network level.

## Stability bar — verified ✅

```
for i in 1..10; do
  ./gradlew :nestsClient:jvmTest \
    --tests HangInteropTest --tests BrowserInteropTest \
    -DnestsHangInterop=true -DnestsBrowserInterop=true --rerun-tasks
done
```

Result: **10/10 BUILD SUCCESSFUL × 22 tests = 220/220 pass.**
~5m 28s steady state per sweep on the agent rig.

## CI runtime budget

- Hang-interop job: ~3-4 min on warm cache (one suite run, 60 s
  long-broadcast scenario dominates), ~8 min cold (cargo install
  moq-relay).
- Browser-interop job: ~5-7 min warm (Chromium boot × N
  scenarios), ~10 min cold (Playwright install).
- Both run in parallel after `lint`.

Total CI overhead: ~5-10 min on the critical path beyond the
existing build matrix. Acceptable.

## Documentation updates

After CI is green:

1. `nestsClient/plans/2026-05-06-cross-stack-interop-test-results.md`
   — replace the "CI integration: Not wired" section with "wired,
   tracking flake-rate at 0/N runs".
2. `nestsClient/plans/2026-05-06-cross-stack-interop-test-gap-matrix.md`
   — replace `#6 CI integration: ⏸ deferred` with `✅ live`.
3. Pick a maintainer to monitor the first 2 weeks of CI runs and
   bisect any new flake immediately (don't let it accumulate as
   "known flake" again).

## Acceptance criteria

- Both jobs added to `build.yml` and merge to main.
- 10/10 sweep before merge.
- First 2 weeks post-merge: ≥ 95% green rate. If lower, the
  routing investigation isn't really done — pull the jobs again
  until it is.

## Optional follow-ups

- **Add I-12 GOAWAY scenario IF an IETF moq-transport target lands.**
  Currently N/A in moq-lite-03 per
  `cross-stack-interop-test-results.md`'s I12 section. If an IETF
  target ever ships, this is the cross-stack regression test.
- **Surface `framesPerGroup` as a per-deployment config** if the
  framesPerGroup-rerun outcome shows the two rigs can't converge
  (see `2026-05-07-framespergroup-production-rerun.md`).
