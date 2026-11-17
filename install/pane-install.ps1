# Pane installer for Fire TV / Android TV (Windows PowerShell 5.1+ / PowerShell 7). Safe to re-run.
# Usage: .\pane-install.ps1 [-Ip 192.168.1.50[:5555]] [-Apk C:\path\pane.apk] [-DryRun]
#        undo: adb uninstall tv.pane
param(
  [Parameter(Position = 0)][string]$Ip = $env:PANE_TV,
  [string]$Apk = '',
  [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'  # the progress bar makes Invoke-WebRequest crawl on 5.1

$Pkg = if ($env:PANE_PKG) { $env:PANE_PKG } else { 'tv.pane' }
$ApkUrl = if ($env:PANE_APK_URL) { $env:PANE_APK_URL } else { 'https://github.com/Arya-A-Nair/pane-tv/releases/latest/download/pane.apk' }
$Activity = '.Pane'
$CertSha256 = 'af7acaab7749f513708962e48c0442159790948c7dd42ca3f7608b2db4fa7e03'

function Die($msg) { [Console]::Error.WriteLine("Error: $msg"); exit 1 }

# Captures adb's stdout only. stderr is left alone: on 5.1, redirecting native stderr under
# ErrorActionPreference=Stop becomes a terminating error, and adb logs "daemon started" there.
function Get-Adb {
  param([string[]]$Arguments)
  $ErrorActionPreference = 'Continue'
  $out = & adb @Arguments | Out-String
  if ($out) { $out.Trim() } else { '' }
}

# Anything that changes the TV goes through Invoke-Adb, which honours -DryRun.
function Invoke-Adb {
  param([string[]]$Arguments)
  if ($DryRun) { Write-Output "[dry-run] adb $($Arguments -join ' ')"; return }
  $ErrorActionPreference = 'Continue'
  & adb @Arguments
  if ($LASTEXITCODE -ne 0) { Die "adb $($Arguments -join ' ') failed" }
}

function Get-DeviceState {
  $devices = Get-Adb -Arguments @('devices')
  if ($devices -match ('(?m)^' + [regex]::Escape($Serial) + '\s+(\S+)')) { return $Matches[1] }
  return ''
}

function Get-Prop {
  param([string]$Name)
  Get-Adb -Arguments @('-s', $Serial, 'shell', 'getprop', $Name)
}

if ($DryRun) { Write-Output 'DRY RUN: read-only checks only; downloads, installs and grants are printed, not executed.' }

# 1. adb
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
  Write-Output 'adb (Android platform-tools) is not installed. Install it, then run this again:'
  Write-Output '  1. Download https://dl.google.com/android/repository/platform-tools-latest-windows.zip'
  Write-Output '  2. Unzip it (for example to C:\platform-tools)'
  Write-Output '  3. In PowerShell:  $env:Path += ";C:\platform-tools"   (or add it to PATH permanently)'
  exit 1
}

# 2. TV address
if (-not $Ip) {
  Write-Output "What is your TV's IP address?"
  Write-Output '  Fire TV: Settings > My Fire TV > About > Network.'
  Write-Output '  Also turn on Settings > My Fire TV > Developer options > ADB debugging.'
  $Ip = Read-Host 'TV IP'
  if ($Ip) { $Ip = $Ip.Trim() }
}
if (-not $Ip) { Die 'no IP given' }
$Serial = if ($Ip -match ':') { $Ip } else { "${Ip}:5555" }
if ($Serial -notmatch '^[A-Za-z0-9._:-]+$') { Die "that does not look like an IP address: $Serial" }

# 3. connect; the TV shows an authorization prompt the first time
# adb connect can hang on a wrong IP, so run it as a job and give up after 10 s.
function Connect-Tv {
  $job = Start-Job -ScriptBlock { param($s) & adb connect $s 2>&1 | Out-String } -ArgumentList $Serial
  if (Wait-Job $job -Timeout 10) { Receive-Job $job; Remove-Job $job }
  else { Remove-Job $job -Force; Die "timed out connecting to $Serial. Check the IP, and that ADB debugging is on (Settings > My Fire TV > Developer options)." }
}
Write-Output "Connecting to $Serial..."
Connect-Tv
$state = ''
for ($attempt = 1; $attempt -le 3; $attempt++) {
  $state = Get-DeviceState
  if ($state -eq 'device') { break }
  if ($state -eq 'unauthorized') {
    Write-Output "The TV is asking to allow this computer. Choose 'Always allow' on the TV screen."
    Read-Host 'Press Enter once you have accepted' | Out-Null
  } elseif ($attempt -eq 3) {
    break
  } else {
    Start-Sleep -Seconds 3
  }
  Connect-Tv | Out-Null
}
if ($state -ne 'device') {
  $shown = if ($state) { $state } else { 'not found' }
  Die "could not reach the TV at $Serial (state: $shown). Check the IP, that ADB debugging is on, and that the TV and this computer are on the same network."
}

# 4. device type
$manufacturer = (Get-Prop -Name 'ro.product.manufacturer').ToLower()
$model = Get-Prop -Name 'ro.product.model'
$chars = Get-Prop -Name 'ro.build.characteristics'
Write-Output "Found: $manufacturer $model ($chars)"
$firetv = $manufacturer.StartsWith('amazon')

# 5. APK
$tmp = Join-Path ([IO.Path]::GetTempPath()) ('pane-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $tmp | Out-Null
try {
  if (-not $Apk) {
    Write-Output 'Downloading Pane...'
    $Apk = Join-Path $tmp 'pane.apk'
    if ($DryRun) {
      Write-Output "[dry-run] download $ApkUrl -> $Apk"
    } else {
      [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
      try { Invoke-WebRequest -UseBasicParsing -Uri $ApkUrl -OutFile $Apk }
      catch { Die "download failed: $ApkUrl (is there a published release yet?)" }
      $shaFile = Join-Path $tmp 'pane.apk.sha256'
      try { Invoke-WebRequest -UseBasicParsing -Uri "$ApkUrl.sha256" -OutFile $shaFile }
      catch { Die "could not fetch $ApkUrl.sha256, refusing to install an unverified APK" }
      $want = ''
      $shaLine = Get-Content -Path $shaFile -TotalCount 1
      if ($shaLine) { $want = ($shaLine.Trim() -split '\s+')[0].ToLower() }
      $got = (Get-FileHash -Algorithm SHA256 -Path $Apk).Hash.ToLower()
      if ($want -ne $got) { Die 'checksum mismatch, refusing to install' }
      Write-Output 'Checksum OK.'
      # Pin the release signing certificate when apksigner is available.
      if (Get-Command apksigner -ErrorAction SilentlyContinue) {
        $certLine = (& apksigner verify --print-certs $Apk 2>$null | Select-String 'certificate SHA-256 digest' | Select-Object -First 1)
        $cert = if ($certLine) { ($certLine.Line -split ': ')[1].Trim().ToLower() } else { 'none' }
        if ($cert -ne $CertSha256) { Die "APK is not signed by Pane's release key (got $cert), refusing to install" }
        Write-Output 'Signature OK.'
      }
    }
  } elseif (-not $DryRun -and -not (Test-Path -LiteralPath $Apk)) {
    Die "APK not found: $Apk"
  }

  # 6. install + setup
  Invoke-Adb -Arguments @('-s', $Serial, 'install', '-r', $Apk)
  if ($firetv) {
    # Fire OS blocks custom launchers; the background watcher needs these once.
    Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'pm', 'grant', $Pkg, 'android.permission.READ_LOGS')
    Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'appops', 'set', $Pkg, 'SYSTEM_ALERT_WINDOW', 'allow')
    Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'dumpsys', 'deviceidle', 'whitelist', "+$Pkg")
  }
  Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'am', 'start', '-n', "$Pkg/$Activity")
}
finally {
  Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}

Write-Output ''
if ($DryRun) { Write-Output 'Dry run complete. Nothing was changed on the TV.'; exit 0 }
Write-Output 'Pane is installed.'
if ($firetv) {
  Write-Output 'Press the Home button on your remote: Pane should open.'
} else {
  Write-Output 'To make Pane your home screen: Settings > Apps > Default apps > Home screen app (wording varies), then choose Pane.'
}
Write-Output "To remove it:  adb -s $Serial uninstall $Pkg"
