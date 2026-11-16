# Deterministic build environment for libarti_android.so.
#
# Sourced by build-arti.sh (Android targets) and build-arti-host.sh (host target)
# so the two paths stay in lockstep. Makes the Rust build byte-for-byte
# reproducible, which is what lets F-Droid / Zapstore / any third party rebuild
# the shipped .so from this tag and confirm it matches.
#
# The caller must already have set:
#   SCRIPT_DIR        — tools/arti-build
#   ARTI_SOURCE_DIR   — the Arti clone (.arti-source)
#
# The three things that otherwise make a Rust cdylib non-reproducible, and the
# fix for each:
#   1. Compiler version   -> pinned by rust-toolchain.toml (rustup auto-installs).
#   2. Dependency versions -> pinned by the committed Cargo.lock + `cargo --locked`.
#   3. Absolute build paths embedded in panic locations / strings
#                          -> --remap-path-prefix rewrites them to stable virtual
#                             paths so two machines with different $HOME / checkout
#                             dirs produce identical bytes.

# Disable incremental compilation — its on-disk cache can perturb codegen order.
export CARGO_INCREMENTAL=0

# Where Cargo caches the crates.io registry + git deps (absolute, host-specific).
export CARGO_HOME="${CARGO_HOME:-$HOME/.cargo}"

# Rewrite every host-specific absolute prefix that rustc would otherwise bake
# into the binary. (Rust's own std is already remapped to /rustc/<hash> by the
# distributed toolchain, so pinning the version in rust-toolchain.toml covers it.)
REPRO_RUSTFLAGS="--remap-path-prefix=${CARGO_HOME}=/cargo"
REPRO_RUSTFLAGS="${REPRO_RUSTFLAGS} --remap-path-prefix=${ARTI_SOURCE_DIR}=/arti"
REPRO_RUSTFLAGS="${REPRO_RUSTFLAGS} --remap-path-prefix=${SCRIPT_DIR}=/arti-build"
export RUSTFLAGS="${RUSTFLAGS:-} ${REPRO_RUSTFLAGS}"

# Pin SOURCE_DATE_EPOCH to the commit the Arti tag points at — deterministic for
# a given ARTI_VERSION, and independent of when the build actually runs.
if [ -d "${ARTI_SOURCE_DIR}/.git" ]; then
    _epoch="$(git -C "${ARTI_SOURCE_DIR}" log -1 --format=%ct 2>/dev/null || true)"
    [ -n "${_epoch}" ] && export SOURCE_DATE_EPOCH="${_epoch}"
    unset _epoch
fi
