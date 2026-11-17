#!/bin/sh
# Build pane.apk with plain SDK tools (no Gradle).
# Needs: brew openjdk, Android build-tools 35.0.0 and platform 25 in ~/Library/Android/sdk.
set -e
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$(brew --prefix openjdk)/libexec/openjdk.jdk/Contents/Home}"
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
BT=$SDK/build-tools/35.0.0
JAR=$SDK/platforms/android-25/android.jar
rm -rf out && mkdir -p out/classes out/res
"$BT"/aapt2 compile --dir res -o out/res.zip
"$BT"/aapt2 link --debug-mode --manifest AndroidManifest.xml -I "$JAR" -A assets -o out/unsigned.apk out/res.zip --java out/gen
find src out/gen -name '*.java' > out/sources.txt
"$JAVA_HOME"/bin/javac -Xlint:-options --release 11 -cp "$JAR" -d out/classes @out/sources.txt
"$JAVA_HOME"/bin/jar cf out/classes.jar -C out/classes .
"$BT"/d8 --release --min-api 22 --lib "$JAR" --output out out/classes.jar
(cd out && zip -q unsigned.apk classes.dex)
"$BT"/zipalign -f 4 out/unsigned.apk out/aligned.apk
[ -f pane.keystore ] || "$JAVA_HOME"/bin/keytool -genkeypair -keystore pane.keystore -storepass pane-dev -keypass pane-dev \
  -alias pane -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Pane" >/dev/null 2>&1
"$BT"/apksigner sign --ks pane.keystore --ks-pass pass:pane-dev --out pane.apk out/aligned.apk 2>/dev/null
echo "built pane.apk ($(du -h pane.apk | cut -f1))"
