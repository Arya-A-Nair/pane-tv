#!/usr/bin/env bash
# CI twin of build.sh: same toolchain steps, signing from env instead of a local keystore.
# Needs: java 17+ on PATH, ANDROID_HOME with build-tools 35.0.0 + platforms/android-25,
#        PANE_KEYSTORE (path), PANE_KEYSTORE_PASSWORD, PANE_KEY_ALIAS, PANE_KEY_PASSWORD.
# Output: pane.apk in the repo root.
set -euo pipefail
cd "$(dirname "$0")/.."

: "${ANDROID_HOME:?set ANDROID_HOME}"
: "${PANE_KEYSTORE:?set PANE_KEYSTORE}"
: "${PANE_KEYSTORE_PASSWORD:?set PANE_KEYSTORE_PASSWORD}"
: "${PANE_KEY_ALIAS:?set PANE_KEY_ALIAS}"
: "${PANE_KEY_PASSWORD:?set PANE_KEY_PASSWORD}"
export PANE_KEYSTORE_PASSWORD PANE_KEY_PASSWORD

BT="$ANDROID_HOME/build-tools/35.0.0"
JAR="$ANDROID_HOME/platforms/android-25/android.jar"

rm -rf out && mkdir -p out/classes out/res
"$BT/aapt2" compile --dir res -o out/res.zip
"$BT/aapt2" link --manifest AndroidManifest.xml -I "$JAR" -A assets -o out/unsigned.apk out/res.zip --java out/gen
find src out/gen -name '*.java' > out/sources.txt
javac -Xlint:-options --release 11 -cp "$JAR" -d out/classes @out/sources.txt
jar cf out/classes.jar -C out/classes .
"$BT/d8" --release --min-api 22 --lib "$JAR" --output out out/classes.jar
(cd out && zip -q unsigned.apk classes.dex)
"$BT/zipalign" -f 4 out/unsigned.apk out/aligned.apk
"$BT/apksigner" sign --ks "$PANE_KEYSTORE" --ks-key-alias "$PANE_KEY_ALIAS" \
  --ks-pass env:PANE_KEYSTORE_PASSWORD --key-pass env:PANE_KEY_PASSWORD --out pane.apk out/aligned.apk
"$BT/apksigner" verify pane.apk
echo "built pane.apk ($(du -h pane.apk | cut -f1))"
