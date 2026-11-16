# geode release & distribution

**Status:** shipped (2026-07-24)

## Goal

Give geode a real release process so people can run it without cloning the repo
and invoking Gradle. Reuse the amy CLI's proven pipeline wherever possible —
geode is the same kind of module (a Gradle `application`-plugin JVM app) — and
add the pieces a long-running **server daemon** needs that a one-shot CLI does
not (a container image, a systemd unit).

## Why the CLI pipeline maps onto geode

`cli/build.gradle.kts` already models everything geode needs for the
tarball/deb/rpm channels: `jlinkRuntime` (minimal JRE) → an image task
(portable flat `bin/`+`lib/`+`runtime/`) → `jpackageDeb`/`jpackageRpm`, driven
by the `build-cli` matrix in `create-release.yml` and named through
`scripts/asset-name.sh`. geode is `application`-plugin + JVM 21 too, so the same
task shapes drop in.

Two simplifications vs. the CLI:

- **No Compose to exclude.** amy drags the Compose UI render stack in
  transitively via `:commons` and has to exclude skiko/compose.ui from the
  runtime classpath (plus a CI assertion). geode depends only on `:quartz`, so
  there's nothing to strip and no assertion to add.
- **A wider jlink module set.** amy is compute-only; geode is a network server
  with a SQLite store, so its module list adds `java.management`, `java.sql`,
  `java.net.http`, and `java.security.jgss`. A too-tight list links fine but
  fails at runtime, so the `build-geode` job **boots the image** (`--version`
  plus a serve + NIP-11 curl) as a smoke test before shipping.

## What shipped

| Piece | File |
| --- | --- |
| `--version` / `--help` (testable exit-0 command) | `geode/src/main/kotlin/.../Main.kt` |
| jlink + jpackage + `geodeImage` tasks | `geode/build.gradle.kts` |
| Portable app-image seeds (config + unit under `share/geode/`) | `geode/build.gradle.kts` (geodeImage) |
| Multi-stage Docker image | `geode/Dockerfile`, `.dockerignore` |
| systemd unit | `geode/packaging/systemd/geode.service` |
| macOS hardened-runtime entitlements | `geode/packaging/macos/geode.entitlements` |
| Reference Homebrew formula | `geode/packaging/homebrew/geode.rb` |
| Asset naming (`geode-<ver>-<fam>-<arch>`) | `scripts/asset-name.sh` |
| Release matrix + GHCR push | `.github/workflows/create-release.yml` (`build-geode`, `docker-geode`) |
| Homebrew formula auto-sync | `.github/workflows/bump-homebrew-geode-formula.yml` |
| geode tests in CI (dedicated `test-geode` job) | `.github/workflows/build.yml` |
| Operator docs | `geode/README.md` |

## Distribution channels

1. **Docker → GHCR** (`ghcr.io/vitorpamplona/geode:<version>` + `:latest`) —
   the primary channel for relay operators. Built from `geode/Dockerfile`.
2. **`.deb` / `.rpm`** with a bundled JRE, install to `/opt/geode`, plus the
   shipped systemd unit for `systemctl enable --now geode`.
3. **Portable tarball** with a jlink'd JRE — no system Java needed.
4. **Homebrew** (no-JRE jar bundle + `depends_on openjdk`) for local testing.

All four (except the container) are named by `scripts/asset-name.sh` and
uploaded to the GitHub Release on `v*` tags.

## Follow-ups / not done

- **Multi-arch Docker.** `docker-geode` builds `linux/amd64` only. arm64 under
  QEMU emulation would run a full Gradle build per arch (slow / timeout-prone);
  add a native arm64 runner leg when one is available.
- **jpackage doesn't auto-install the systemd unit or create the `geode`
  user.** The `.deb`/`.rpm` install the binary; service wiring is a documented
  manual step (unit ships in the package under `share/geode/`). A post-install
  scriptlet via jpackage `--resource-dir` could automate it later.
- **homebrew-core bootstrap.** The first `geode` submission to homebrew-core is
  a manual new-formula PR; the auto-bump workflow only keeps the in-repo
  reference formula synced until then (same state as `amy`).
- **CI size budget** is set to 200 MB per asset (matches amy). geode should be
  well under that — tighten once the first release confirms the real size.
