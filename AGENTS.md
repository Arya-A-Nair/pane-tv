# Agent guide

For AI coding agents (Claude Code, Claude cloud agents, Codex, Copilot, etc.) and humans alike. This is the canonical guide; `CLAUDE.md` just points here.

## What Pane is

Pane is a small Android TV launcher, built for Fire TV and also working on Google TV and Android TV: a spatial glass home screen, live Today widgets, backgrounds, an all-apps page. It is plain Java, built with raw SDK tools (no Gradle), with no accounts, ads or tracking. Package id `tv.pane`.

## Code map

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

`assets/` holds backgrounds, the moon map and fonts; `tools/` has the scripts that regenerate them. `site/` is the GitHub Pages site. `docs/RELEASING.md` covers releases.

## Commands CI runs

Run these before opening a PR. You need JDK 17+, Android build-tools 35.0.0 and platform `android-25` in `$ANDROID_HOME`.

```sh
JAVAC_FLAGS="-Xlint:all,-options -Werror" ./build.sh   # build; any warning fails
java -cp out/classes tv.pane.MoonPath                   # MoonPath self-check
find . -name '*.sh' -not -path './out/*' -print0 | xargs -0 shellcheck
actionlint                                              # if you touched .github/workflows
ruff check --select E9,F tools                          # if you touched tools/
python3 tools/check_assets.py                           # if you touched assets/ or tools/
```

CI also fails a PR whose APK grows more than 15% over `main`.

## Hard rules

- Plain Java (`--release 11`) against the Android 25 SDK. **No AndroidX, no support libraries, no new dependencies.**
- No network calls beyond the weather services listed in [PRIVACY.md](PRIVACY.md); no analytics or tracking.
- Follow [DESIGN.md](DESIGN.md) for anything visual (tokens, motion, contrast, text size).
- Never commit keystores, APKs, secrets or `out/`. Never touch release signing.
- Do not bump `versionCode`/`versionName`; only the owner's release PR does (see `docs/RELEASING.md`).
- Update `CHANGELOG.md` under `[Unreleased]` for user-visible changes.
- Keep PRs small and focused; prefer deleting code to adding it; match the surrounding style.

## Testing without a TV

Create an **Android TV (1080p)** emulator (API 25+) in Android Studio's Device Manager, then `adb install -r pane.apk` and pick Pane as the home app. Everything works there except the Fire TV-only Home-button watcher (`Watcher`, `Boot`). In your PR, say what you tested on (device or emulator, Android/Fire OS version). If you are an agent with no emulator, say so plainly instead of claiming it was tested.

## PR checklist

- [ ] Build command above passes locally with `-Werror`, plus the MoonPath check
- [ ] Plain Java, no AndroidX or new dependencies, no new network calls
- [ ] `CHANGELOG.md` updated under `[Unreleased]` if user-visible
- [ ] PR description says what changed, why, and what it was tested on

## What happens after you open a PR

CI (`build`, `lint`) runs and a bot comments with the APK size and download link. A PR is never merged without the owner's approval; once the owner approves, it squash-merges itself when CI is green. See [CONTRIBUTING.md](CONTRIBUTING.md).
