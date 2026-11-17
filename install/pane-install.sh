#!/usr/bin/env bash
# Pane installer for Fire TV / Android TV (macOS, Linux). Safe to re-run. No sudo.
# Usage: pane-install.sh [TV_IP[:PORT]] [--apk FILE] [--dry-run] [--help]
#        undo: adb uninstall tv.pane
set -euo pipefail

PKG="${PANE_PKG:-tv.pane}"
APK_URL="${PANE_APK_URL:-https://github.com/Arya-A-Nair/pane-tv/releases/latest/download/pane.apk}"
ACTIVITY=".Pane"

ip="${PANE_TV:-}"
apk=""
dry=0

while [ $# -gt 0 ]; do
  case "$1" in
    --apk) [ $# -ge 2 ] || { echo "--apk needs a file path" >&2; exit 2; }; apk="$2"; shift 2 ;;
    --dry-run) dry=1; shift ;;
    -h|--help) sed -n '2,4p' "$0"; exit 0 ;;
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
  read -r -p "TV IP: " ip
fi
[ -n "$ip" ] || die "no IP given"
case "$ip" in *:*) serial="$ip" ;; *) serial="$ip:5555" ;; esac
case "$serial" in *[!A-Za-z0-9.:_-]*) die "that does not look like an IP address: $serial" ;; esac

A() { adb -s "$serial" "$@"; }

# 3. connect; the TV shows an authorization prompt the first time
adb connect "$serial" 2>&1 || true
state=""
for attempt in 1 2 3; do
  state="$(adb devices | awk -v s="$serial" '$1==s {print $2}')"
  case "$state" in
    device) break ;;
    unauthorized)
      say "The TV is asking to allow this computer. Choose 'Always allow' on the TV screen."
      if [ -t 0 ]; then read -r -p "Press Enter once you have accepted... " _; else sleep 10; fi
      adb connect "$serial" >/dev/null 2>&1 || true ;;
    *)
      [ "$attempt" = 3 ] && break
      sleep 3
      adb connect "$serial" >/dev/null 2>&1 || true ;;
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
    if curl -fsL -o "$tmp/pane.apk.sha256" "$APK_URL.sha256"; then
      want="$(awk '{print $1}' "$tmp/pane.apk.sha256")"
      if command -v sha256sum >/dev/null 2>&1; then got="$(sha256sum "$apk" | awk '{print $1}')"; else got="$(shasum -a 256 "$apk" | awk '{print $1}')"; fi
      [ "$want" = "$got" ] || die "checksum mismatch, refusing to install"
      say "Checksum OK."
    else
      say "(no checksum file found, skipping verification)"
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
