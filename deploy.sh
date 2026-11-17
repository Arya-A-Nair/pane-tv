#!/bin/sh
# Install the locally built pane.apk on a TV (dev). Safe to re-run.
# Usage: PANE_TV=192.168.1.16:5555 ./deploy.sh     or     ./deploy.sh 192.168.1.16:5555
# undo: adb uninstall tv.pane
set -e
cd "$(dirname "$0")"
S=${1:-$PANE_TV}
[ -n "$S" ] || { echo "usage: $0 TV_IP[:PORT]   (or set PANE_TV)" >&2; exit 2; }
exec install/pane-install.sh --apk pane.apk "$S"
