#Requires -Version 5.1
param([switch]$SkipIso)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Vendor = Join-Path $Root "vendor"
$Images = Join-Path $Root "images"
New-Item -ItemType Directory -Force -Path (Join-Path $Vendor "qemu"), (Join-Path $Vendor "scrcpy"), $Images, (Join-Path $Vendor "dl") | Out-Null

$QemuUrl = if ($env:QEMU_URL) { $env:QEMU_URL } else { "https://qemu.weilnetz.de/w64/2026/qemu-w64-setup-20260811.exe" }
$ScrcpyUrl = if ($env:SCRCPY_URL) { $env:SCRCPY_URL } else { "https://github.com/Genymobile/scrcpy/releases/download/v5.0.1/scrcpy-win64-v5.0.1.zip" }
$IsoUrl = if ($env:ISO_URL) { $env:ISO_URL } else { "https://downloads.sourceforge.net/project/blissos-x86/Official/BlissOS16/FOSS/Generic/Bliss-v16.9.7-x86_64-OFFICIAL-foss-20241011.iso" }

function Get-File($Url, $Dest) {
    if (Test-Path $Dest) { Write-Host "already have $Dest"; return }
    Write-Host "GET $Url"
    Invoke-WebRequest -Uri $Url -OutFile "$Dest.partial"
    Move-Item -Force "$Dest.partial" $Dest
}


$scrcpyExe = Join-Path $Vendor "scrcpy\scrcpy.exe"
if (-not (Test-Path $scrcpyExe)) {
    Get-File $ScrcpyUrl (Join-Path $Vendor "dl\scrcpy-win64.zip")
    $unpack = Join-Path $Vendor "dl\scrcpy-unpack"
    Expand-Archive -Force -Path (Join-Path $Vendor "dl\scrcpy-win64.zip") -DestinationPath $unpack
    $found = Get-ChildItem $unpack -Recurse -Filter scrcpy.exe | Select-Object -First 1
    Copy-Item -Recurse -Force (Join-Path $found.DirectoryName "*") (Join-Path $Vendor "scrcpy")
}

$qemuExe = Join-Path $Vendor "qemu\qemu-system-x86_64.exe"
if (-not (Test-Path $qemuExe)) {
    Get-File $QemuUrl (Join-Path $Vendor "dl\qemu-w64-setup.exe")
    $setup = Join-Path $Vendor "dl\qemu-w64-setup.exe"
    $unpack = Join-Path $Vendor "dl\qemu-unpack"
    New-Item -ItemType Directory -Force -Path $unpack | Out-Null
    $seven = Get-Command 7z -ErrorAction SilentlyContinue
    if ($seven) {
        & 7z x -y "-o$unpack" $setup
    } else {
        Write-Warning "7-Zip not on PATH. Starting NSIS installer silently into vendor\qemu"
        Start-Process -FilePath $setup -ArgumentList "/S","/D=$(Join-Path $Vendor 'qemu')" -Wait
    }
    $found = Get-ChildItem $unpack -Recurse -Filter qemu-system-x86_64.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) {
        Copy-Item -Recurse -Force (Join-Path $found.DirectoryName "*") (Join-Path $Vendor "qemu")
    }
}

$iso = Join-Path $Images "blissos-x86_64.iso"
if (-not $SkipIso -and -not (Test-Path $iso)) {
    Write-Host "GET BlissOS ISO (~2.3 GiB)"
    Invoke-WebRequest -Uri $IsoUrl -OutFile "$iso.partial"
    Move-Item -Force "$iso.partial" $iso
}

foreach ($required in @($qemuExe, (Join-Path $Vendor "qemu\qemu-img.exe"), $scrcpyExe, (Join-Path $Vendor "scrcpy\adb.exe"))) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Toolchain download/extraction did not provide $required" }
}
Write-Host "Done. Run prepare-qcow2.ps1 then TelosAndroidVm.exe"
