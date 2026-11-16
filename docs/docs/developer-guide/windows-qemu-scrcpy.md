---
sidebar_position: 9
---

# TelosAndroidVm (QEMU + scrcpy)

User-facing install and per-distro packages: **[TelosAndroidVm](/docs/user-guide/telos/android-vm)**.

`tools/windows-qemu-android/` boots BlissOS x86_64 in **headless QEMU** (`-display none`) and shows it with **scrcpy**. Closing scrcpy runs `adb shell reboot -p` and stops QEMU. Encrypted userdata is `images/android-data.qcow2` and is **never** deleted by a wrapper or opt-in ISO update.

`:app:app` must not depend on this tree. This is separate from [`:desktop-linux`](./desktop-linux). Vendor ISOs and Windows QEMU bits are gitignored.

## Layout

| Path | Role |
| --- | --- |
| `telos_android_vm.py` | Linux KVM / macOS Apple Silicon runner (system qemu, scrcpy, adb) |
| `src/TelosAndroidVm/` | Windows `TelosAndroidVm.exe` (WHPX → HAXM → TCG) |
| `blissos.lock.json` | Pinned official FOSS Generic x86_64 ISO |
| `blissos_catalog.py` | SourceForge RSS catalog, SHA-256, opt-in replace ISO |
| `scripts/first-boot-lock.sh` | SUW + lockscreen after `boot_completed` |
| `overlay/` | FBE/SUW props injected into initrd when patching |
| `tests/test_blissos_catalog.py` | Filename/RSS/lock unit tests |

## Hosts

| Host | Runner | Acceleration | Guest |
| --- | --- | --- | --- |
| Windows x64 | `TelosAndroidVm.exe` | WHPX → HAXM → TCG | BlissOS x86_64 |
| Linux x86_64 | `python3 telos_android_vm.py` | KVM + `-cpu host` → TCG | BlissOS x86_64 |
| macOS Apple Silicon | `python3 telos_android_vm.py` | TCG (`thread=multi`, `-cpu max`) | BlissOS x86_64 |

Intel macOS is unsupported. Linux/macOS never unpack `qemu-w64-setup.exe`.

## BlissOS pin and update

Catalog channels: `Official/BlissOS16|17|18/FOSS/Generic`. Accepted names: `Bliss-v*-x86_64-OFFICIAL-foss-*.iso` only.

```bash
python3 blissos_catalog.py --check
python3 blissos_catalog.py --update
python3 telos_android_vm.py --update-bliss
TelosAndroidVm.exe --update-bliss
```

`--yes` skips the prompt. A major version jump warns that `/data` may not unlock; the qcow2 is still kept. SHA-256 comes from the SourceForge sidecar. Old ISO → `blissos-x86_64.iso.bak`. Re-run `patch-android-boot.sh` after a new ISO.

## First boot

Kernel append / overlay: `SETUPWIZARD=1 DATA=vda ENCRYPT=1`. `first-boot-lock.sh` keeps SUW enabled and `locksettings set-disabled false`. The user sets PIN/pattern/password in the wizard.

## Resources

- RAM 1 GiB, 2 SMP, ADB `127.0.0.1:5555`.
- scrcpy: `--window-borderless --stay-awake --push-target /sdcard/Download`.

## Checks

```bash
cd tools/windows-qemu-android
python3 tests/test_blissos_catalog.py
python3 blissos_catalog.py --check
dotnet publish src/TelosAndroidVm/TelosAndroidVm.csproj -c Release -r win-x64 --self-contained true -o .
```
