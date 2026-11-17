## What and why

<!-- One or two sentences. Link the issue if there is one. -->

## Tested on

<!-- Device and Fire OS / Android version, or emulator. Screenshots for UI changes. -->

## Checklist

- [ ] `./build.sh` succeeds and CI is green (`build` and `lint`)
- [ ] Plain Java, no AndroidX or new dependencies
- [ ] No new network calls or tracking
- [ ] `CHANGELOG.md` updated under `[Unreleased]` (if user-visible)

<!-- Reviewers: download the `pane-apk` artifact from the CI run, then `adb install -r pane.apk`. adb test extras only work on this debug build. -->
