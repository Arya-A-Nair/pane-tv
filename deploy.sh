#!/bin/sh
# Install NOVA on the Fire TV and enable the Home redirect. Safe to re-run.
# Usage: ./deploy.sh [serial]        undo: adb uninstall tv.nova
set -e
S=${1:-192.168.1.16:5555}
A="adb -s $S"
cd "$(dirname "$0")"
$A install -r nova.apk >/dev/null
$A shell pm grant tv.nova android.permission.READ_LOGS
$A shell appops set tv.nova SYSTEM_ALERT_WINDOW allow
$A shell dumpsys deviceidle whitelist +tv.nova >/dev/null
$A shell settings put secure enabled_notification_listeners tv.nova/tv.nova.Media  # now playing
$A shell pm list packages | grep -q tv.homeredirect && $A uninstall tv.homeredirect >/dev/null || true  # superseded
$A shell am start -n tv.nova/.Nova >/dev/null
echo "NOVA deployed"
