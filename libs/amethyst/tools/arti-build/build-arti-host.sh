#!/usr/bin/env bash
#
# Build the Arti JNI shim for the *host* (typically Linux x86_64) and stage it
# under amethyst/src/test/native-libs/<host-tag>/libarti_android.so so the JVM
# unit tests in TorArtiNativeIntegrationTest can `System.loadLibrary` it.
#
# Companion to build-arti.sh, which builds the *Android* targets for shipping
# in the APK. Same wrapper crate, same lib.rs — only the cargo target differs.
#
# Prerequisites:
#   - Rust toolchain with the host target installed (default after `rustup install stable`).
#   - The Arti source must already be cloned at .arti-source/ — run build-arti.sh
#     once first if this is a fresh checkout.
#
# Usage:
#   ./build-arti-host.sh
#
# Why this exists:
#   Tier-3 JVM integration tests in amethyst/src/test/.../tor/TorArtiNativeIntegrationTest
#   call the real Arti library. The checked-in .so under src/test/native-libs/x86_64-linux/
#   covers the most common dev/CI host. If you bump ARTI_VERSION or touch
#   tools/arti-build/src/lib.rs, regenerate the host .so with this script before
#   running the integration tests; otherwise you'll be testing the previous shim.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# Same canonical build path as build-arti.sh (see its comment for why).
ARTI_SOURCE_DIR="${ARTI_REPRO_DIR:-/tmp/amethyst-arti-build}/.arti-source"
WRAPPER_DIR="$ARTI_SOURCE_DIR/arti-android-wrapper"

if [ ! -d "$WRAPPER_DIR" ]; then
    echo "Arti source / wrapper not found at $WRAPPER_DIR."
    echo "Run ./build-arti.sh first (clones .arti-source and sets up the wrapper)."
    exit 1
fi

# Sync the latest wrapper sources into the .arti-source clone — build-arti.sh
# normally does this, but if you've only edited lib.rs the host build needs it too.
cp "$SCRIPT_DIR/src/lib.rs" "$WRAPPER_DIR/src/lib.rs"
# Keep the dependency lock in sync with the committed one (build is --locked).
cp "$SCRIPT_DIR/Cargo.lock" "$WRAPPER_DIR/Cargo.lock"

# Same deterministic build env as the Android path (path remapping, pinned epoch).
# shellcheck source=repro-env.sh
source "$SCRIPT_DIR/repro-env.sh"

HOST_TARGET="$(rustc -vV | sed -n 's/^host: //p')"
case "$HOST_TARGET" in
    x86_64-unknown-linux-gnu) DEST_TAG="x86_64-linux" ;;
    aarch64-unknown-linux-gnu) DEST_TAG="aarch64-linux" ;;
    x86_64-apple-darwin) DEST_TAG="x86_64-macos" ;;
    aarch64-apple-darwin) DEST_TAG="aarch64-macos" ;;
    *)
        echo "Unmapped host target $HOST_TARGET — add it to build-arti-host.sh."
        exit 1
        ;;
esac

OUT_DIR="$PROJECT_ROOT/amethyst/src/test/native-libs/$DEST_TAG"
mkdir -p "$OUT_DIR"

echo "Building Arti shim for $HOST_TARGET → $OUT_DIR/libarti_android.so"
cargo build --release --locked \
    --manifest-path "$WRAPPER_DIR/Cargo.toml" \
    --target "$HOST_TARGET"

# macOS Rust toolchains produce .dylib, not .so. Rename so the existing
# System.loadLibrary("arti_android") path keeps working.
case "$HOST_TARGET" in
    *-apple-darwin)
        src="$WRAPPER_DIR/target/$HOST_TARGET/release/libarti_android.dylib"
        ;;
    *)
        src="$WRAPPER_DIR/target/$HOST_TARGET/release/libarti_android.so"
        ;;
esac

cp "$src" "$OUT_DIR/libarti_android.so"
size=$(du -h "$OUT_DIR/libarti_android.so" | cut -f1)
echo "Built $OUT_DIR/libarti_android.so ($size)"
echo ""
echo "Run the smoke test:"
echo "  ./gradlew :amethyst:testPlayDebugUnitTest \\"
echo "      --tests com.vitorpamplona.amethyst.ui.tor.TorArtiNativeIntegrationTest"
echo ""
echo "Run the full bootstrap tests (needs Tor network egress):"
echo "  ./gradlew :amethyst:testPlayDebugUnitTest \\"
echo "      --tests com.vitorpamplona.amethyst.ui.tor.TorArtiNativeIntegrationTest \\"
echo "      -Pamethyst.arti.integration=true"
