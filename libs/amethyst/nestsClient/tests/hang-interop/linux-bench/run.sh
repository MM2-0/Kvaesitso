#!/usr/bin/env bash
# Run AudioLatencyComparisonTest inside a Linux container so the numbers
# match what a Linux production host would see — different OS, same JDK,
# same rustc, same sidecar binaries. The mac host's `nestsClient/build`
# and cargo `target/` are NOT touched: a fresh tarball of the repo is
# unpacked into the container's filesystem, with two symlinks pointing
# the gradle and cargo output dirs at named volumes so successive runs
# don't pay full rebuild cost.
#
# Prereqs:
#   - docker desktop (or any docker engine) on the host
#   - the test itself is gated by -DnestsHangInterop=true; this script
#     passes that flag.
#
# Usage from repo root:
#   ./nestsClient/tests/hang-interop/linux-bench/run.sh
#
# Output: stats table on stdout. JUnit XML lands in
#   nestsClient/tests/hang-interop/linux-bench/out/.
#
# First-run cost: ~7-15 min (image build + full cargo + moq-relay
# install + gradle compile). Cached runs: ~3-5 min.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../../../.." && pwd)
PLATFORM="${PLATFORM:-linux/arm64}"
IMAGE_TAG="amethyst-linux-bench:$(echo "$PLATFORM" | tr / -)"
OUT_DIR="$SCRIPT_DIR/out"

# Cache volumes — named per-platform so arm64 and amd64 don't share
# binaries that wouldn't run. Stored under the user cache dir so they
# survive across repo clones / git clean.
CACHE_ROOT="${LINUX_BENCH_CACHE:-$HOME/.cache/amethyst-linux-bench}"
CARGO_REGISTRY_VOL="$CACHE_ROOT/$PLATFORM/cargo-registry"
CARGO_TARGET_VOL="$CACHE_ROOT/$PLATFORM/cargo-target"
GRADLE_BUILD_VOL="$CACHE_ROOT/$PLATFORM/gradle-build"
GRADLE_CACHE_VOL="$CACHE_ROOT/$PLATFORM/gradle-cache"
mkdir -p "$CARGO_REGISTRY_VOL" "$CARGO_TARGET_VOL" "$GRADLE_BUILD_VOL" "$GRADLE_CACHE_VOL" "$OUT_DIR"

echo ">>> building image $IMAGE_TAG ($PLATFORM)"
docker build --platform "$PLATFORM" -t "$IMAGE_TAG" "$SCRIPT_DIR"

echo ">>> running benchmark"
docker run --rm --platform "$PLATFORM" \
    -v "$REPO_ROOT":/src:ro \
    -v "$OUT_DIR":/out \
    -v "$CARGO_REGISTRY_VOL":/opt/cargo/registry \
    -v "$CARGO_TARGET_VOL":/persistent-cargo-target \
    -v "$GRADLE_BUILD_VOL":/persistent-gradle-build \
    -v "$GRADLE_CACHE_VOL":/root/.gradle \
    -e GRADLE_OPTS="-Xmx4g" \
    "$IMAGE_TAG" \
    bash -c '
        set -e
        rm -rf /repo; mkdir /repo
        # Filter out build / cache dirs at copy time so the macOS-host
        # state never lands in the Linux container.
        tar -C /src --exclude=./.git --exclude="*/build" --exclude="*/.gradle" --exclude="*/target" --exclude="*/node_modules" -cf - . \
            | tar -C /repo -xf -
        # Redirect both gradle build/ and cargo target/ at named
        # volumes so repeated runs reuse compiled artefacts. The
        # default repo-relative paths still resolve via the symlinks,
        # so the existing NativeMoqRelayHarness sidecar lookups stay
        # unchanged.
        rm -rf /repo/nestsClient/tests/hang-interop/target
        ln -s /persistent-cargo-target /repo/nestsClient/tests/hang-interop/target
        rm -rf /repo/nestsClient/build
        ln -s /persistent-gradle-build /repo/nestsClient/build
        cd /repo
        echo "=== toolchain ==="
        rustc --version; cmake --version | head -1; java -version 2>&1 | head -1
        ./gradlew :nestsClient:jvmTest -DnestsHangInterop=true \
            --tests "com.vitorpamplona.nestsclient.interop.native.AudioLatencyComparisonTest" \
            2>&1 | tail -10
        # Surface the test report XML to the host so we can grep
        # printed stats without rebuilding the image.
        rm -f /out/*.xml
        cp /repo/nestsClient/build/test-results/jvmTest/TEST-*AudioLatencyComparisonTest*.xml /out/
    '

echo ""
echo ">>> stats"
for f in "$OUT_DIR"/TEST-*.xml; do
    # Pull the CDATA-wrapped stdout block out without needing python on
    # the host — sed range, then strip the CDATA wrappers.
    sed -n '/<system-out><!\[CDATA\[/,/]]><\/system-out>/p' "$f" \
        | sed -e 's/^<system-out><!\[CDATA\[//' -e 's/]]><\/system-out>$//'
done
