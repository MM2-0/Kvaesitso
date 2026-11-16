---
sidebar_position: 9
---

# TelosAndroidVm (QEMU + scrcpy)

`tools/windows-qemu-android/` boots BlissOS x86_64 in **headless QEMU** and shows it with **scrcpy**. Closing scrcpy runs `adb shell reboot -p` and stops QEMU. Encrypted userdata lives in `images/android-data.qcow2` and is **never** deleted by an ISO or wrapper update.

`:app:app` must not depend on this tree. Vendor ISOs and Windows QEMU binaries are gitignored. This is separate from [`:desktop-linux`](./desktop-linux).

## Hosts

| Host | Runner | Acceleration | Guest |
| --- | --- | --- | --- |
| Windows x64 | `TelosAndroidVm.exe` | WHPX → HAXM → TCG | BlissOS x86_64 |
| Linux x86_64 | `python3 telos_android_vm.py` | **KVM** (`-cpu host`) → TCG | BlissOS x86_64 |
| macOS Apple Silicon | `python3 telos_android_vm.py` | TCG (x86_64 guest; HVF does not run a foreign architecture) | BlissOS x86_64 |

Intel macOS is unsupported. Linux uses distro `qemu-system-x86_64`, `scrcpy`, and `adb` (no Windows QEMU tree). macOS ARM: `brew install qemu scrcpy android-platform-tools`.

## Resources

- RAM 1 GiB, 2 SMP cores.
- ADB: `hostfwd=tcp:127.0.0.1:5555-:5555`.
- Disk: growable QCOW2, 16 GiB virtual (`images/android-data.qcow2`).
- scrcpy: `--window-borderless --stay-awake --push-target /sdcard/Download`.

## BlissOS pin and opt-in update

`blissos.lock.json` pins the official FOSS Generic x86_64 ISO (currently 16.9.7). BlissOS is not publishing newer official images; the catalog still polls SourceForge RSS for `Official/BlissOS16|17|18/FOSS/Generic` and accepts only `Bliss-v*-x86_64-OFFICIAL-foss-*.iso` (no GApps, Go, Surface, unofficial).

```bash
python3 blissos_catalog.py --check          # latest vs lock
python3 blissos_catalog.py --update         # prompt; keeps qcow2
python3 telos_android_vm.py --update-bliss  # same, then boot
TelosAndroidVm.exe --update-bliss           # Windows MessageBox
```

`--yes` skips the prompt (automation). A **major** version jump (16 → 17) asks a second time: the qcow2 is still kept, but `/data` may not unlock. SHA-256 is verified from the SourceForge `.sha256` sidecar before the ISO is renamed to `blissos-x86_64.iso`; the previous ISO becomes `blissos-x86_64.iso.bak`.

## First boot (SUW + encryption)

`patch-android-boot.sh` / kernel append: `SETUPWIZARD=1 DATA=vda ENCRYPT=1`. Overlay props request FBE. After boot, `scripts/first-boot-lock.sh` keeps the Setup Wizard enabled. The user sets PIN/pattern/password; that credential unwraps CE keys on later boots.

## Build

```bash
cd tools/windows-qemu-android
./fetch-toolchain.sh          # ISO; Windows QEMU/scrcpy zips only on Windows
./prepare-qcow2.sh
python3 tests/test_blissos_catalog.py
# Windows wrapper (cross-publish from Linux):
dotnet publish src/TelosAndroidVm/TelosAndroidVm.csproj -c Release -r win-x64 --self-contained true -o .
python3 telos_android_vm.py   # Linux / macOS ARM
```
