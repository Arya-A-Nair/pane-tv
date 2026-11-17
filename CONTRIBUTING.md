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
