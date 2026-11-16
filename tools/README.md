# Telos repo tooling (Python and helpers)

Python that ships with the repo lives **here**, not in the repository root.

| Path | Purpose |
| --- | --- |
| `tools/i18n/` | Extract missing string keys and write locale XML (`extract.py`, `build.py`) |
| `tools/translations/` | CI inventory / activate / validate changed UI strings |
| `tools/validation/` | Manifest and route checks, security Gradle init scripts |
| `tools/performance/` | APK size helpers |
| `tools/release-*.py` | Version / APK / Shizuku release gates |
| `tools/windows/` | Optional HKCU Telos Desktop shell (`install-telos-shell.ps1`) |
| `tools/windows-qemu-android/` | TelosAndroidVm: Windows `.exe`, Linux KVM / macOS ARM Python runner, BlissOS opt-in ISO update |
| `docs/scripts/` | Docs screenshot prep (VitePress site only) |
| `.github/scripts/` | CI screenshot capture |

One-shot agent `patch_*.py` / `fix_*.py` scripts must not accumulate in the repo root. Change Kotlin/XML sources directly, or promote a reusable script into one of the folders above with a short README.
