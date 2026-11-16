#Requires -Version 5.1
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Images = Join-Path $Root "images"
New-Item -ItemType Directory -Force -Path $Images | Out-Null
$Disk = Join-Path $Images "android-data.qcow2"
$Size = if ($env:QCOW2_SIZE) { $env:QCOW2_SIZE } else { "16G" }

$qemuImg = Get-ChildItem (Join-Path $Root "vendor") -Recurse -Filter qemu-img.exe -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $qemuImg) { throw "qemu-img.exe not found. Run fetch-toolchain.ps1 first." }
if (Test-Path $Disk) {
    Write-Host "already have $Disk"
} else {
    & $qemuImg.FullName create -f qcow2 -o cluster_size=65536,preallocation=off,lazy_refcounts=on $Disk $Size
}
& $qemuImg.FullName info $Disk
