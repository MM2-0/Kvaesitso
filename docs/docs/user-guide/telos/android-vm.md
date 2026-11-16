---
sidebar_position: 41
---

# TelosAndroidVm

**TelosAndroidVm** is a **portable Android PC guest** on your computer. It is **not** the Telos APK and **not** [Telos Desktop](./linux-desktop). QEMU boots **BlissOS x86_64** with no QEMU window. **scrcpy** is the window you use. Closing scrcpy powers the guest off and stops QEMU.

Encrypted apps and settings live on a growable disk, `images/android-data.qcow2` (16 GiB virtual, small on disk until filled). Wrapper updates and an **opt-in** BlissOS ISO update **keep that disk**. The host never stores your PIN.

Sources: `tools/windows-qemu-android/` in the [Telos-Launcher](https://github.com/koukomin/Telos-Launcher) tree. Large ISOs and Windows QEMU binaries are not in git; you download them once on the machine that will run the VM.

## What you get

| | |
| --- | --- |
| Guest | BlissOS official FOSS Generic **x86_64** (pinned in `blissos.lock.json`, currently 16.9.7) |
| Display | scrcpy, borderless, stay-awake; drag-and-drop files into `/sdcard/Download` |
| RAM / CPU | 1 GiB RAM, 2 virtual cores |
| ADB | `127.0.0.1:5555` on the host only |
| First boot | Android Setup Wizard; you must set a lockscreen PIN, pattern, or password |
| Encryption | Guest `/data` on the QCOW2 (`ENCRYPT=1` / FBE when the image supports it). That credential unlocks later boots |

Telos as home app is optional: set `TELOS_APK` to a Telos APK path before start.

## Hosts

| Computer | How you start it | Acceleration |
| --- | --- | --- |
| **Windows 10/11 64-bit** | `TelosAndroidVm.exe` | WHPX, then Intel HAXM, then TCG |
| **Linux x86_64** | `python3 telos_android_vm.py` | **KVM** + host CPU when `/dev/kvm` works, else TCG |
| **macOS Apple Silicon** | `python3 telos_android_vm.py` | TCG (BlissOS is x86_64; Apple HVF does not accelerate a foreign CPU) |

Intel Macs are unsupported. Linux and macOS use **system** `qemu-system-x86_64`, `scrcpy`, and `adb` — they do not unpack the Windows QEMU installer.

Clone or copy `tools/windows-qemu-android/` (the scripts, `blissos.lock.json`, and on Windows the published `TelosAndroidVm.exe`). Then fetch the ISO and create the data disk.

```bash
cd tools/windows-qemu-android
./fetch-toolchain.sh    # BlissOS ISO (~2.3 GiB). On Windows also QEMU + scrcpy.
./prepare-qcow2.sh      # 16 GiB growable android-data.qcow2 (does not wipe an existing disk)
./patch-android-boot.sh # kernel/initrd overlay for Setup Wizard + ENCRYPT
```

On Windows PowerShell: `.\fetch-toolchain.ps1` then `.\prepare-qcow2.ps1`.

---

## Windows

1. **Windows Hypervisor Platform** (Settings → Apps → Optional features → More Windows features, or):

   ```powershell
   Enable-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform
   ```

   Reboot if Windows asks. Without WHPX the guest still runs (TCG), slowly.

2. From the bundle folder (after fetch + qcow2):

   ```powershell
   .\TelosAndroidVm.exe
   ```

   QEMU has no console window. Wait for the scrcpy window. First boot: complete Setup Wizard and set a lock.

3. Optional ISO update (keeps `android-data.qcow2`):

   ```powershell
   .\TelosAndroidVm.exe --update-bliss
   ```

   Confirm the dialog. `--yes` accepts without a prompt (scripts only).

4. Close the scrcpy window to shut the guest down.

Build the exe from a machine with the .NET 8 SDK (including Linux cross-publish):

```bash
dotnet publish src/TelosAndroidVm/TelosAndroidVm.csproj -c Release -r win-x64 --self-contained true -o .
```

---

## Linux

Needs **Python 3**, **QEMU x86_64**, **scrcpy**, **adb**, and **KVM** for speed. Add your user to group `kvm`, then log out and in:

```bash
sudo usermod -aG kvm "$USER"
# test: ls -l /dev/kvm
```

Then:

```bash
cd tools/windows-qemu-android
python3 telos_android_vm.py
# or
python3 telos_android_vm.py --update-bliss
python3 blissos_catalog.py --check
```

The runner prints `host=linux accel=kvm cpu=host` when KVM is available.

### Debian, Ubuntu, Linux Mint, Pop!_OS

```bash
sudo apt update
sudo apt install -y python3 qemu-system-x86 qemu-utils scrcpy adb 7zip gzip cpio
# Older Debian/Ubuntu used android-tools-adb; the package name is now adb.
# If scrcpy is missing (very old Ubuntu), install from https://github.com/Genymobile/scrcpy
sudo usermod -aG kvm "$USER"
```

`qemu-img` is in **qemu-utils**. `7zip` (or `p7zip-full` on older Ubuntu) is used by `patch-android-boot.sh`.

### Fedora, RHEL, CentOS Stream, Rocky, Alma

```bash
sudo dnf install -y python3 qemu-system-x86 qemu-img unzip gzip cpio p7zip-plugins
# Fedora 39+: 
sudo dnf install -y scrcpy android-tools
# RHEL/CentOS: android-tools from EPEL; scrcpy from the scrcpy GitHub release if the repo has no package
sudo usermod -aG kvm "$USER"
```

openSUSE Leap / Tumbleweed:

```bash
sudo zypper install python3 qemu scrcpy android-tools p7zip gzip cpio
sudo usermod -aG kvm "$USER"
```

### Arch Linux, Manjaro, CachyOS, EndeavourOS

```bash
sudo pacman -S --needed python qemu-desktop scrcpy android-tools p7zip gzip cpio
sudo usermod -aG kvm "$USER"
```

`qemu-system-x86` is enough if you prefer a smaller set than `qemu-desktop`.

### Gentoo

```bash
sudo emerge --ask app-emulation/qemu dev-util/android-tools app-arch/p7zip app-arch/gzip app-arch/cpio
# scrcpy: GURU overlay package gui-apps/scrcpy, or build from https://github.com/Genymobile/scrcpy
sudo usermod -aG kvm "$USER"
```

QEMU USE flags should include `qemu_softmmu_targets_x86_64` (default on amd64). KVM needs `kvm` in the kernel and the `kvm` group.

---

## macOS (Apple Silicon only)

Intel Macs are not supported.

```bash
brew install python qemu scrcpy android-platform-tools p7zip
cd tools/windows-qemu-android
./fetch-toolchain.sh
./prepare-qcow2.sh
./patch-android-boot.sh
python3 telos_android_vm.py
```

Expect **TCG** (`host=darwin-arm64 accel=tcg,thread=multi`). The same BlissOS x86_64 ISO is used; it is slower than KVM/WHPX. Close scrcpy to shut down.

---

## First boot and encryption

1. Wait until scrcpy shows Android (several minutes on TCG).
2. Finish the **Setup Wizard**. Set a **PIN, pattern, or password**. That secret unlocks encrypted `/data` on the next start.
3. Optional: install Telos with `TELOS_APK=/path/to/telos-arm64-v8a.apk` — the guest is x86_64, so use an **x86_64 / default** APK if you sideload Telos, not `armeabi-v7a`.

Later starts skip the wizard and ask for the same lock.

## BlissOS ISO update (opt-in)

The lockfile tracks the official FOSS Generic x86_64 image. BlissOS currently has **no newer official ISO** than 16.9.7; `--check` still queries SourceForge.

```bash
python3 blissos_catalog.py --check
python3 blissos_catalog.py --update          # asks [y/N]; never formats the qcow2
python3 telos_android_vm.py --update-bliss
```

- Only `Bliss-v*-x86_64-OFFICIAL-foss-*.iso` (no GApps, Go, Surface, unofficial).
- SHA-256 from the `.sha256` sidecar must match.
- Previous ISO → `images/blissos-x86_64.iso.bak`.
- **Major** 16 → 17 asks again: the disk is kept; `/data` might not unlock.
- After a new ISO, run `./patch-android-boot.sh` again so kernel/initrd match.

Updating `TelosAndroidVm.exe` or the Python runner does not touch the qcow2.

## Files from the host

Drop a file on the scrcpy window. It is pushed to **`/sdcard/Download`**. That is a copy, not a live mount of your home directory.

## Troubleshooting

| Symptom | What to try |
| --- | --- |
| `qemu-system-x86_64 not on PATH` | Install QEMU for your distro / `brew install qemu` / Windows fetch-toolchain |
| `scrcpy` / `adb` missing | Distro packages above, or [scrcpy releases](https://github.com/Genymobile/scrcpy/releases) |
| Linux `accel=tcg` | User not in `kvm`, or `/dev/kvm` missing (enable virtualization in firmware) |
| Windows slow | Enable Hypervisor Platform; reboot |
| scrcpy never appears | Wait for `sys.boot_completed`; check `telos-android-vm.log` next to the Windows exe |
| Guest asks for PIN after ISO update | Expected; same PIN. If a **major** BlissOS line changed, restore `blissos-x86_64.iso.bak` or keep a qcow2 backup |
| Want a new empty Android | Move `android-data.qcow2` aside and run `prepare-qcow2` again — this is the only way the data disk is replaced |

ADB is bound to **localhost**. Do not republish port 5555 on the LAN.

Internals for contributors: [developer guide](/docs/developer-guide/windows-qemu-scrcpy).
