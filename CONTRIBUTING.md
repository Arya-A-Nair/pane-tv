# Contributing

Thanks for helping. Pane is intentionally small, so please keep changes small too.

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md). Using an AI coding agent? Point it at [AGENTS.md](AGENTS.md), the agent-friendly version of this guide.

## Good first contributions

- Try Pane on a device we haven't tested (another Fire TV model, a Google TV, a Chromecast) and [report how it went](https://github.com/Arya-A-Nair/pane-tv/issues/new/choose), with a screenshot.
- Translations of the few on-screen strings.
- Accessibility: contrast, text size, reduced motion. [DESIGN.md](DESIGN.md) lists the rules, and `LayoutAudit` checks some of them.
- Issues labelled [`good first issue`](https://github.com/Arya-A-Nair/pane-tv/labels/good%20first%20issue).

## No TV? Use an emulator

Android Studio's Device Manager can create an **Android TV (1080p)** emulator on API 25 or newer. Start it, then `adb install -r pane.apk` and choose Pane as the home app. The Fire TV-only parts (the Home-button watcher) don't apply there, and everything else works.

## Build

Plain Java, no Gradle. You need a JDK (17+), Android build-tools 35.0.0 and platform `android-25` in `$ANDROID_HOME` (default `~/Library/Android/sdk`). Then:

```sh
./build.sh                      # produces pane.apk, signed with a local debug keystore
PANE_TV=192.168.1.50 ./deploy.sh   # installs on your TV over adb
```

`build.sh` creates a local keystore on first run; it is gitignored. Release signing is separate, see [docs/RELEASING.md](docs/RELEASING.md).

## Where things live

All the code is in `src/tv/pane/`, one class per page or part:

| File | What it is |
|---|---|
| `Pane.java` | The activity: holds the room, the five pages, the bottom bar and the screensaver together, and routes the remote. |
| `HomePage`, `AppsPage`, `TodayPage`, `BackgroundsPage`, `SettingsPage` | The five pages. `Page` is their base class. |
| `BottomBar`, `AppTile`, `GlassPanel`, `SkyArc`, `RollingText` | Shared components. |
| `Room`, `Env`, `LivingSmoke` | The background: environments, the frosted copy glass samples from, and the smoke shader. |
| `Moon`, `MoonPath`, `Weather` | Real data: the moon's phase and path (worked out offline), weather and air quality. |
| `Motion`, `Ui` | Design tokens and motion from `DESIGN.md`. |
| `Watcher`, `Boot` | Fire TV only: bring Pane forward when Amazon's home screen opens. |
| `Apps` | Installed apps, icons, favourites and recents. |
| `Screensaver`, `LayoutAudit` | Idle mode, and a layout checker for development. |

`assets/` holds the backgrounds, the moon map and fonts. `tools/` has the scripts that regenerate them (see `tools/README.md`). Read [DESIGN.md](DESIGN.md) before changing anything visual.

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

### What happens after you open a PR

1. CI runs `build` and `lint`, and a bot posts one comment with the APK size and a link to the run's `pane-apk` artifact.
2. The owner reviews. **Nothing is ever merged without a human approval**; untrusted code is never auto-merged.
3. When the owner approves, squash auto-merge is switched on and the PR merges itself as soon as CI is green (for PRs from forks the owner clicks **Enable auto-merge** instead, because GitHub gives workflows a read-only token there). The branch is deleted afterwards.
4. Dependabot updates to GitHub Actions (patch and minor) are approved and auto-merged by a workflow once CI passes.
5. If the merge bumps the version in `AndroidManifest.xml`, a signed release is published automatically; see [docs/RELEASING.md](docs/RELEASING.md). Edits to `site/` deploy to GitHub Pages on merge.

Contributors: add a line under `[Unreleased]` in `CHANGELOG.md` for user-visible changes, and do not bump the version yourself; the owner's release PR does that.

### Testing a PR build

CI uploads each PR's APK as the `pane-apk` artifact (kept 14 days). Download it from the link in the bot's PR comment (or the run's summary page), unzip, then:

```sh
adb connect <tv-ip>
adb install -r pane.apk
```

It is signed with a throwaway key, so if a release Pane is installed, uninstall it first (`adb uninstall tv.pane`; check the package id in `AndroidManifest.xml`). The adb test extras (see the source for the list) only work on this debug build, never on release builds.
