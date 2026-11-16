# TelosAndroidVm (QEMU + scrcpy)

Headless QEMU + scrcpy for BlissOS x86_64. Encrypted userdata is `images/android-data.qcow2` and survives wrapper and opt-in ISO updates.

**Full user guide** (Windows, Debian/Ubuntu, Fedora/RHEL, Arch, Gentoo, macOS Apple Silicon): [docs/docs/user-guide/telos/android-vm.md](../../docs/docs/user-guide/telos/android-vm.md).

Not Telos Desktop (`:desktop-linux`). Do not depend `:app:app` on this folder.

## Hosts

- **Windows x64:** `TelosAndroidVm.exe` (WHPX / HAXM / TCG), vendored QEMU + scrcpy.
- **Linux:** `python3 telos_android_vm.py` — system `qemu-system-x86_64` with **KVM** and `-cpu host`, system `scrcpy` + `adb`.
- **macOS Apple Silicon:** same Python runner; `brew install qemu scrcpy android-platform-tools`. Guest is still x86_64 BlissOS via **TCG** (HVF is same-arch only). Intel Macs are unsupported.

## BlissOS update (keeps encrypted data)

Pinned in `blissos.lock.json`. The user must confirm:

```bash
python3 blissos_catalog.py --update
# or
python3 telos_android_vm.py --update-bliss
TelosAndroidVm.exe --update-bliss
```

Only official FOSS Generic x86_64 ISOs. SHA-256 checked. `android-data.qcow2` is never formatted. Major line changes warn that `/data` may not unlock.

## Setup

```bash
./fetch-toolchain.sh
./prepare-qcow2.sh
./patch-android-boot.sh
python3 telos_android_vm.py          # Linux / macOS ARM
```

Windows: `.\fetch-toolchain.ps1`, `.\prepare-qcow2.ps1`, `.\TelosAndroidVm.exe`.

Optional: `TELOS_APK` for sideload. Drag-and-drop onto scrcpy goes to `/sdcard/Download`.
