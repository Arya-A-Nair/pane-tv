# Contributing

Thanks for helping. Pane is intentionally small, so please keep changes small too.

## Build

Plain Java, no Gradle. You need a JDK (17+), Android build-tools 35.0.0 and platform `android-25` in `$ANDROID_HOME` (default `~/Library/Android/sdk`). Then:

```sh
./build.sh                      # produces pane.apk, signed with a local debug keystore
PANE_TV=192.168.1.50 ./deploy.sh   # installs on your TV over adb
```

`build.sh` creates a local keystore on first run; it is gitignored. Release signing is separate, see [docs/RELEASING.md](docs/RELEASING.md).

## Code style

- Plain Java (`--release 11`) against the Android 25 SDK. **No AndroidX, no support libraries, no new dependencies.**
- Keep it small: prefer deleting code to adding it, and avoid abstractions with a single user.
- Match the surrounding style. No network calls beyond the weather services listed in [PRIVACY.md](PRIVACY.md), and no analytics.
- Test on a real TV or emulator and say which one in your PR.

## Pull requests

Open an issue first for anything big. Update `CHANGELOG.md` under `[Unreleased]` for user-visible changes.

**CI must pass before merge.** Every PR runs `build` (compiles with `-Xlint:all -Werror`, so new warnings fail; runs the `MoonPath` self-check; fails if the APK grows more than 15% vs `main`) and `lint` (shellcheck, actionlint, ruff, `tools/check_assets.py`). Run the same locally with:

```sh
JAVAC_FLAGS="-Xlint:all,-options -Werror" ./build.sh && java -cp out/classes tv.pane.MoonPath
```

A code-owner review is also required on `main`.

### Testing a PR build

CI uploads each PR's APK as the `pane-apk` artifact (kept 14 days). Download it from the run's summary page, unzip, then:

```sh
adb connect <tv-ip>
adb install -r pane.apk
```

It is signed with a throwaway key, so if a release Pane is installed, uninstall it first (`adb uninstall tv.pane`; check the package id in `AndroidManifest.xml`). The adb test extras (see the source for the list) only work on this debug build, never on release builds.
