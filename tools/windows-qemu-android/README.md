# Telos portable Windows Android VM (QEMU + scrcpy)

Headless QEMU runs BlissOS x86_64 (or another android-x86 ISO) with a 16 GiB growable QCOW2 data disk. The guest is shown in a borderless scrcpy window. `TelosAndroidVm.exe` starts QEMU without a console, waits for `sys.boot_completed`, then scrcpy. Closing scrcpy sends `reboot -p` and stops QEMU.

This is **not** Telos Desktop (`:desktop-linux`). It does not replace the Compose Windows `.exe`. Do not add a Gradle dependency from `:app:app` to this folder.

## First-boot encryption and Setup Wizard

- Boot options patched by `patch-android-boot.sh`: `SETUPWIZARD=1 DATA=vda ENCRYPT=1`.
- Overlay `overlay/telos-crypto.prop` + `fstab.telos` request **file-based encryption** (`ro.crypto.type=file`, `fileencryption=aes-256-xts:aes-256-cts:v2`).
- `scripts/first-boot-lock.sh` (pushed over ADB) keeps SUW enabled and `locksettings set-disabled false`.
- The user must set a PIN, pattern, or password in the wizard. That credential unwraps CE keys on later boots. The host never chooses or stores the PIN.

Android-x86/BlissOS FBE support varies by build. If the guest only offers FDE at install time, `ENCRYPT=1` still encrypts `/data`; the lockscreen credential is the unlock secret. Full fstab injection needs a writable `system` (or the initrd overlay when gzip/cpio succeed).

## Host requirements (Windows)

- 64-bit Windows 10/11.
- **WHPX**: enable “Windows Hypervisor Platform” (and optionally Hyper-V). Fallback: Intel HAXM, then TCG (slow).
- 1 GiB RAM and 2 SMP cores are what the runner allocates.

## Setup

On Linux (this repo) or Windows:

```bash
cd tools/windows-qemu-android
./fetch-toolchain.sh          # QEMU Windows binaries, scrcpy+adb, BlissOS ISO
./prepare-qcow2.sh            # 16G qcow2 (needs qemu-img when possible)
./patch-android-boot.sh
# publish TelosAndroidVm.exe (from a machine with the .NET 8 SDK):
dotnet publish src/TelosAndroidVm/TelosAndroidVm.csproj -c Release -o .
```

On Windows after copying `vendor/`, `images/`, `scripts/`, and `TelosAndroidVm.exe`:

```powershell
.\fetch-toolchain.ps1
.\prepare-qcow2.ps1
.\TelosAndroidVm.exe
```

Optional: set `TELOS_APK` to a Telos APK path to sideload and request it as home.

## Layout

| Path | Role |
| --- | --- |
| `vendor/qemu/` | Windows `qemu-system-x86_64.exe` + `qemu-img.exe` (not committed) |
| `vendor/scrcpy/` | scrcpy + adb (not committed) |
| `images/android-data.qcow2` | 16 GiB growable disk (not committed) |
| `images/blissos-x86_64.iso` | Guest ISO (not committed) |
| `TelosAndroidVm.exe` | Single-file win-x64 wrapper |

QEMU uses `-display none` (no QEMU window). `-nographic` would steal the VGA device and break scrcpy.

## Licenses

Vendored tools stay on disk for local use; they are gitignored. See `THIRD_PARTY_NOTICES.md` (QEMU GPL-2.0, scrcpy Apache-2.0, BlissOS / android-x86).
