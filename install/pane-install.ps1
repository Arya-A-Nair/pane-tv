# Pane installer for Fire TV / Android TV (Windows PowerShell 5.1+ / PowerShell 7). Safe to re-run.
# Usage: .\pane-install.ps1 [-Ip 192.168.1.50[:5555]] [-Apk C:\path\pane.apk] [-DryRun]
#        undo: adb uninstall tv.nova
param(
  [Parameter(Position = 0)][string]$Ip = $env:PANE_TV,
  [string]$Apk = "",
  [switch]$DryRun
)
$ErrorActionPreference = 'Stop'

$Pkg = if ($env:PANE_PKG) { $env:PANE_PKG } else { 'tv.nova' }
$ApkUrl = if ($env:PANE_APK_URL) { $env:PANE_APK_URL } else { 'https://github.com/Arya-A-Nair/pane-tv/releases/latest/download/pane.apk' }
$Activity = '.Nova'

function Die($msg) { Write-Host "Error: $msg" -ForegroundColor Red; exit 1 }
# Anything that changes the TV goes through Run-Adb.
function Run-Adb {
  if ($DryRun) { Write-Host "[dry-run] adb $($args -join ' ')"; return }
  & adb @args
  if ($LASTEXITCODE -ne 0) { Die "adb $($args -join ' ') failed" }
}
function Adb-Out { (& adb -s $script:Serial @args | Out-String).Trim() }

if ($DryRun) { Write-Host 'DRY RUN: read-only checks only; downloads, installs and grants are printed, not executed.' }

# 1. adb
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
  Write-Host 'adb (Android platform-tools) is not installed. Install it, then run this again:'
  Write-Host '  1. Download https://dl.google.com/android/repository/platform-tools-latest-windows.zip'
  Write-Host '  2. Unzip it (for example to C:\platform-tools)'
  Write-Host '  3. In PowerShell:  $env:Path += ";C:\platform-tools"   (or add it to PATH permanently)'
  exit 1
}

# 2. TV address
if (-not $Ip) {
  Write-Host "What is your TV's IP address?"
  Write-Host '  Fire TV: Settings > My Fire TV > About > Network.'
  Write-Host '  Also turn on Settings > My Fire TV > Developer options > ADB debugging.'
  $Ip = Read-Host 'TV IP'
}
if (-not $Ip) { Die 'no IP given' }
$Serial = if ($Ip -match ':') { $Ip } else { "${Ip}:5555" }
if ($Serial -notmatch '^[A-Za-z0-9._:-]+$') { Die "that does not look like an IP address: $Serial" }

# 3. connect; the TV shows an authorization prompt the first time
& adb connect $Serial
$state = ''
foreach ($attempt in 1..3) {
  $line = (& adb devices) | Where-Object { $_ -match "^$([regex]::Escape($Serial))\s" } | Select-Object -First 1
  $state = if ($line) { ($line -split '\s+')[1] } else { '' }
  if ($state -eq 'device') { break }
  if ($state -eq 'unauthorized') {
    Write-Host "The TV is asking to allow this computer. Choose 'Always allow' on the TV screen."
    Read-Host 'Press Enter once you have accepted' | Out-Null
  } elseif ($attempt -lt 3) {
    Start-Sleep -Seconds 3
  }
  & adb connect $Serial | Out-Null
}
if ($state -ne 'device') {
  Die "could not reach the TV at $Serial (state: $(if ($state) { $state } else { 'not found' })). Check the IP, that ADB debugging is on, and that the TV and this computer are on the same network."
}

# 4. device type
$manufacturer = (Adb-Out shell getprop ro.product.manufacturer).ToLower()
$model = Adb-Out shell getprop ro.product.model
$chars = Adb-Out shell getprop ro.build.characteristics
Write-Host "Found: $manufacturer $model ($chars)"
$firetv = $manufacturer.StartsWith('amazon')

# 5. APK
$tmp = Join-Path ([IO.Path]::GetTempPath()) ("pane-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $tmp | Out-Null
try {
  if (-not $Apk) {
    Write-Host 'Downloading Pane...'
    $Apk = Join-Path $tmp 'pane.apk'
    if ($DryRun) {
      Write-Host "[dry-run] download $ApkUrl -> $Apk"
    } else {
      [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
      try { Invoke-WebRequest -UseBasicParsing -Uri $ApkUrl -OutFile $Apk }
      catch { Die "download failed: $ApkUrl (is there a published release yet?)" }
      $shaFile = Join-Path $tmp 'pane.apk.sha256'
      try {
        Invoke-WebRequest -UseBasicParsing -Uri "$ApkUrl.sha256" -OutFile $shaFile
        $want = ((Get-Content $shaFile -TotalCount 1) -split '\s+')[0].ToLower()
        $got = (Get-FileHash -Algorithm SHA256 $Apk).Hash.ToLower()
        if ($want -ne $got) { Die 'checksum mismatch, refusing to install' }
        Write-Host 'Checksum OK.'
      } catch {
        Write-Host '(no checksum file found, skipping verification)'
      }
    }
  } elseif (-not $DryRun -and -not (Test-Path -LiteralPath $Apk)) {
    Die "APK not found: $Apk"
  }

  # 6. install + setup
  Run-Adb -s $Serial install -r $Apk
  if ($firetv) {
    # Fire OS blocks custom launchers; the background watcher needs these once.
    Run-Adb -s $Serial shell pm grant $Pkg android.permission.READ_LOGS
    Run-Adb -s $Serial shell appops set $Pkg SYSTEM_ALERT_WINDOW allow
    Run-Adb -s $Serial shell dumpsys deviceidle whitelist "+$Pkg"
  }
  Run-Adb -s $Serial shell am start -n "$Pkg/$Activity"
}
finally {
  Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host 'Pane is installed.'
if ($firetv) {
  Write-Host 'Press the Home button on your remote: Pane should open.'
} else {
  Write-Host 'To make Pane your home screen: Settings > Apps > Default apps > Home screen app (wording varies), then choose Pane.'
}
Write-Host "To remove it:  adb -s $Serial uninstall $Pkg"
