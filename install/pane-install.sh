#!/usr/bin/env bash
# Pane installer for Fire TV / Android TV (macOS, Linux). Safe to re-run. No sudo.
# Usage: pane-install.sh [TV_IP[:PORT]] [--apk FILE] [--dry-run] [--help]
#        undo: adb uninstall tv.pane
set -euo pipefail
# Braced so bash reads the whole script before running it: with `curl | bash`, the script
# arrives on stdin, and any command that reads stdin (adb) would otherwise eat the rest.
{

PKG="${PANE_PKG:-tv.pane}"
APK_URL="${PANE_APK_URL:-https://github.com/Arya-A-Nair/pane-tv/releases/latest/download/pane.apk}"
ACTIVITY=".Pane"
PANE_CERT_SHA256="af7acaab7749f513708962e48c0442159790948c7dd42ca3f7608b2db4fa7e03"

ip="${PANE_TV:-}"
apk=""
dry=0

while [ $# -gt 0 ]; do
  case "$1" in
    --apk) [ $# -ge 2 ] || { echo "--apk needs a file path" >&2; exit 2; }; apk="$2"; shift 2 ;;
    --dry-run) dry=1; shift ;;
    -h|--help)
      cat <<'USAGE'
Pane installer for Fire TV / Android TV (macOS, Linux). Safe to re-run. No sudo.
Usage: pane-install.sh [TV_IP[:PORT]] [--apk FILE] [--dry-run] [--help]
       undo: adb uninstall tv.pane
USAGE
      exit 0 ;;
    -*) echo "Unknown option: $1" >&2; exit 2 ;;
    *) ip="$1"; shift ;;
  esac
done

say() { printf '%s\n' "$*"; }
die() { printf 'Error: %s\n' "$*" >&2; exit 1; }
# Anything that changes the TV or downloads goes through run().
run() { if [ "$dry" = 1 ]; then printf '[dry-run] %s\n' "$*"; else "$@"; fi; }

if [ "$dry" = 1 ]; then say "DRY RUN: read-only checks only; downloads, installs and grants are printed, not executed."; fi

# 1. adb
if ! command -v adb >/dev/null 2>&1; then
  say "adb (Android platform-tools) is not installed. Install it, then run this again:"
  case "$(uname -s)" in
    Darwin) say "  brew install android-platform-tools" ;;
    *) say "  Debian/Ubuntu:  sudo apt install adb"
       say "  Other Linux:    https://developer.android.com/tools/releases/platform-tools (unzip, add to PATH)" ;;
  esac
  exit 1
fi

# 2. TV address
if [ -z "$ip" ]; then
  say "What is your TV's IP address?"
  say "  Fire TV: Settings > My Fire TV > About > Network."
  say "  Also turn on Settings > My Fire TV > Developer options > ADB debugging."
  read -r -p "TV IP: " ip </dev/tty
fi
[ -n "$ip" ] || die "no IP given"
case "$ip" in *:*) serial="$ip" ;; *) serial="$ip:5555" ;; esac
case "$serial" in *[!A-Za-z0-9.:_-]*) die "that does not look like an IP address: $serial" ;; esac

A() { adb -s "$serial" "$@" </dev/null; }

# 3. connect; the TV shows an authorization prompt the first time
# adb connect can hang for minutes on a wrong IP, so bound it (macOS has no `timeout`).
connect() {
  adb connect "$serial" </dev/null 2>&1 &
  local pid=$! n=0
  while kill -0 "$pid" 2>/dev/null; do
    if [ "$n" -ge 100 ]; then
      kill "$pid" 2>/dev/null || true
      die "timed out connecting to $serial. Check the IP, and that ADB debugging is on (Settings > My Fire TV > Developer options)."
    fi
    sleep 0.1; n=$((n + 1))
  done
  wait "$pid" || true
}
say "Connecting to $serial..."
connect
state=""
for attempt in 1 2 3; do
  state="$(adb devices | awk -v s="$serial" '$1==s {print $2}')"
  case "$state" in
    device) break ;;
    unauthorized)
      say "The TV is asking to allow this computer. Choose 'Always allow' on the TV screen."
      if [ -r /dev/tty ]; then read -r -p "Press Enter once you have accepted... " _ </dev/tty; else sleep 10; fi
      connect >/dev/null 2>&1 || true ;;
    *)
      [ "$attempt" = 3 ] && break
      sleep 3
      connect >/dev/null 2>&1 || true ;;
  esac
done
[ "$state" = device ] || die "could not reach the TV at $serial (state: ${state:-not found}). Check the IP, that ADB debugging is on, and that the TV and this computer are on the same network."

# 4. device type
manufacturer="$(A shell getprop ro.product.manufacturer | tr -d '\r' | tr '[:upper:]' '[:lower:]')"
model="$(A shell getprop ro.product.model | tr -d '\r')"
chars="$(A shell getprop ro.build.characteristics | tr -d '\r')"
say "Found: ${manufacturer} ${model} (${chars})"
firetv=0
case "$manufacturer" in amazon*) firetv=1 ;; esac

# 5. APK
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
if [ -z "$apk" ]; then
  say "Downloading Pane..."
  apk="$tmp/pane.apk"
  if [ "$dry" = 1 ]; then
    run curl -fL --retry 3 -o "$apk" "$APK_URL"
  else
    curl -fL --retry 3 -o "$apk" "$APK_URL" || die "download failed: $APK_URL (is there a published release yet?)"
    curl -fsL --retry 3 -o "$tmp/pane.apk.sha256" "$APK_URL.sha256" || die "could not fetch $APK_URL.sha256, refusing to install an unverified APK"
    want="$(awk '{print $1}' "$tmp/pane.apk.sha256")"
    if command -v sha256sum >/dev/null 2>&1; then got="$(sha256sum "$apk" | awk '{print $1}')"; else got="$(shasum -a 256 "$apk" | awk '{print $1}')"; fi
    [ "$want" = "$got" ] || die "checksum mismatch, refusing to install"
    say "Checksum OK."
    # Pin the release signing certificate when apksigner is available (it ships with Android build-tools).
    if command -v apksigner >/dev/null 2>&1; then
      cert="$(apksigner verify --print-certs "$apk" 2>/dev/null | awk -F': ' '/certificate SHA-256 digest/ {print $2; exit}')"
      [ "$cert" = "$PANE_CERT_SHA256" ] || die "APK is not signed by Pane's release key (got ${cert:-none}), refusing to install"
      say "Signature OK."
    fi
  fi
elif [ "$dry" = 0 ] && [ ! -f "$apk" ]; then
  die "APK not found: $apk"
fi

# 6. install + setup
run adb -s "$serial" install -r "$apk"
if [ "$firetv" = 1 ]; then
  # Fire OS blocks custom launchers; the background watcher needs these once.
  run adb -s "$serial" shell pm grant "$PKG" android.permission.READ_LOGS
  run adb -s "$serial" shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
  run adb -s "$serial" shell dumpsys deviceidle whitelist "+$PKG"
fi
run adb -s "$serial" shell am start -n "$PKG/$ACTIVITY"

say ""
if [ "$dry" = 1 ]; then say "Dry run complete. Nothing was changed on the TV."; exit 0; fi
say "Pane is installed."
if [ "$firetv" = 1 ]; then
  say "Press the Home button on your remote: Pane should open."
else
  say "To make Pane your home screen: Settings > Apps > Default apps > Home screen app (wording varies), then choose Pane."
fi
say "To remove it:  adb -s $serial uninstall $PKG"
exit 0
}
