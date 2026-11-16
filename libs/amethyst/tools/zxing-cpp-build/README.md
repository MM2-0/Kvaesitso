# zxing-cpp Android Build Tools

Custom-built [zxing-cpp](https://github.com/zxing-cpp/zxing-cpp) native library for Amethyst's QR
scanner. This replaces the prebuilt `io.github.zxing-cpp:android` AAR with the same library built
from source, the way [`tools/arti-build`](../arti-build) replaces the Guardian Project AAR.

## Why custom build?

Not for size — the published AAR's libraries are already stripped, and ours come out only slightly
smaller. **For verifiability.**

The AAR ships four `.so` files built by a third party on toolchains we cannot see, and nothing in
this repository could check them. A QR scanner is a thing you point at a stranger's phone, and the
binary that parses whatever comes back was one we took on trust. Amethyst already holds
`libarti_android.so` to a reproducible standard; there was no principled reason for the barcode
decoder to be exempt.

Building it ourselves also means F-Droid, Zapstore or any auditor can rebuild from this tag and
confirm the committed bytes, instead of being asked to trust a Maven artifact.

## Quick start

Pre-built `.so` files are committed to `amethyst/src/main/jniLibs/`. You only need to rebuild to
verify the binaries, bump the zxing-cpp version, or change the build flags.

```bash
./build-zxingcpp.sh                  # every ABI
./build-zxingcpp.sh --abi arm64-v8a  # one ABI, much faster
./verify-reproducible.sh             # build twice, compare bytes, compare against the commit
```

Prerequisites: `git`, `cmake`, `ninja`, and the exact NDK revision in
[`tools/arti-build/ANDROID_NDK_VERSION`](../arti-build/ANDROID_NDK_VERSION). That is the
repo's single NDK pin, not a copy: `build-zxingcpp.sh`, `build-arti.sh` and `:amethyst`'s
`ndkVersion` all read that one file, so one NDK install serves every native build and the
three can never drift apart.

## Reproducible builds

Six things have to be fixed, and each is:

| Source of non-determinism | Pinned by |
|---|---|
| compiler + linker version | [`tools/arti-build/ANDROID_NDK_VERSION`](../arti-build/ANDROID_NDK_VERSION); `build-zxingcpp.sh` refuses any other revision |
| upstream source | [`ZXING_CPP_COMMIT`](ZXING_CPP_COMMIT), the exact commit; [`ZXING_CPP_VERSION`](ZXING_CPP_VERSION) names the tag it was cloned from, and a fresh clone fails if the tag no longer points there. A reused clone is force-checked-out and cleaned, so local edits cannot ride along |
| absolute paths baked into `__FILE__`, assertions, debug records | `-ffile-prefix-map` / `-fdebug-prefix-map` in [`repro-env.sh`](repro-env.sh) |
| timestamps | `SOURCE_DATE_EPOCH`, derived from the pinned commit rather than from build time; clang takes `__DATE__`/`__TIME__` from it |
| codegen/link ordering keyed on the real build path | canonical build path (`/tmp/amethyst-zxingcpp-build`) |
| NDK install path, via the linker's build-id | `-Wl,--build-id=none` in [`repro-env.sh`](repro-env.sh) |

> **Why there is no build-id.** lld hashes the *unstripped* library, and its debug info still
> names the NDK's absolute install path (sysroot headers, libc++). The same NDK revision unpacked
> in a different directory therefore produced a different 20-byte `.note.gnu.build-id` and nothing
> else, which was enough to fail F-Droid's rebuild. The note is not used on Android (the library
> ships stripped and no symbols are uploaded), so it is dropped rather than path-mapped.

> **Why the NDK is pinned.** clang compiles the C++ and lld links the `.so`, and both stamp their
> versions into the binary's `.comment` section. Swapping the NDK changes the bytes exactly as
> swapping compilers would. `build-zxingcpp.sh` reads each candidate directory's
> `source.properties` and keeps looking until it finds the pinned revision — no wildcards. Picking
> "some NDK" is precisely how arti's committed binaries ended up built by r25b while its README
> told everyone to install r27; that lesson is borrowed here rather than re-learned.

> **Why the output is re-checked.** After each build the script decodes the library's own
> `.note.android.ident`, which records the min SDK and the NDK release and build number, and fails
> if they are not what was asked for. The revision gate checks the *input*; this checks the
> *output*, which is what catches a stale CMake cache or an overriding environment variable that
> slipped a different toolchain past the gate.

`verify-reproducible.sh` builds twice, the second time against a copy of the NDK in another
directory, and also fails if either output contains the NDK path, the build root, the checkout or
another host path. Building twice from one NDK path could not have caught the build-id leak above,
and an unchecked dependency on where the NDK lives is exactly what F-Droid's rebuild trips on.

Verified: all four ABIs built against the pinned NDK unpacked at two different paths produced
identical bytes (`arm64-v8a` is `067c0837…3e828f`), matching the committed libraries.

## What is built

`wrappers/android/zxingcpp/src/main/cpp/CMakeLists.txt` from the pinned tag, which pulls in
zxing-cpp's `core/` and compiles one JNI file. Readers only (`ZXING_WRITERS=OFF`) — Amethyst
encodes its QR codes with ZXing core on the JVM, and never writes one natively.

All four ABIs, unlike arti's two. Tor is an optional feature that can be absent; a QR scanner that
fails to load is a broken core feature, and the decoder this replaced was pure Java and worked
everywhere. Dropping an ABI would mean a silent regression on those devices unless the ZXing-Java
fallback behind `BarcodeDecoder` were wired up too.

## The Kotlin half

The AAR shipped the `zxingcpp.BarcodeReader` class alongside the `.so`, so dropping the AAR means
carrying that too: it is vendored verbatim at `amethyst/src/main/java/zxingcpp/BarcodeReader.kt`.

Two things there are load-bearing and easy to break:

- **The package and class name.** The native library exports
  `Java_zxingcpp_BarcodeReader_readYBuffer`, so moving or renaming the class breaks the JNI lookup
  at runtime with no build error.
- **`-keep class zxingcpp.** { *; }`** in `amethyst/proguard-rules.pro`. This used to arrive as
  the AAR's consumer rule. Without it R8 renames the class in release builds and the scanner fails
  to start — in release only, which is the worst place to find out.

The vendored file keeps its upstream Apache-2.0 header and is excluded from spotless in the root
`build.gradle.kts`, so our MIT header is never stamped onto it.

## Updating zxing-cpp

1. Change [`ZXING_CPP_VERSION`](ZXING_CPP_VERSION) to the new tag and
   [`ZXING_CPP_COMMIT`](ZXING_CPP_COMMIT) to the commit it points at
   (`git ls-remote https://github.com/zxing-cpp/zxing-cpp.git 'refs/tags/<tag>*'`; for an
   annotated tag, take the `^{}` line).
2. Run `./build-zxingcpp.sh`.
3. Re-copy `BarcodeReader.kt` from the pinned clone
   (`/tmp/amethyst-zxingcpp-build/.zxing-cpp-source/wrappers/android/zxingcpp/src/main/java/zxingcpp/`)
   so the two halves cannot drift — a Kotlin wrapper from one version against a `.so` from another
   fails at the JNI boundary, not at compile time.
4. Run `./verify-reproducible.sh` and commit the `.so` files with the hash it reports.

## Licensing

zxing-cpp is Apache-2.0 — permissive, so linking it into Amethyst's MIT-licensed artifacts is
fine, and no linking-exception question arises. The vendored Kotlin file carries its upstream
`SPDX-License-Identifier: Apache-2.0` and copyright line unchanged.
