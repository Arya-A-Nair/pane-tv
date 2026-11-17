# Changelog

All notable changes to Pane are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.0] - 2026-11-17

### Added
- Rearrange Home: Menu on an app opens a small glass sheet with Move left, Move right, Remove from Home and Cancel; tiles slide into place and the order is kept.
- Hide apps: in Apps, Menu now opens a sheet (Add to or Remove from Home, Hide this app, Cancel). Hidden apps leave Apps and Home and are never pinned automatically; Settings has a "Hidden apps" row to show them again.

### Changed
- The clock follows the TV's 12/24-hour setting; temperatures show in °F in the US, Liberia and Myanmar.
- The app is now named Pane (it was "Home") in the launcher and the watcher notification.
- The installer times out after 10 s on an unreachable TV, refuses to install without a published checksum, and checks the release signing certificate when `apksigner` is installed.
- Backgrounds captions now read "Colour field" instead of "Original artwork".

## [1.0.0] - 2026-11-17

First public release.

### Added
- Spatial glass home screen with round app icons.
- Live Today widgets: weather, hourly forecast, air quality, real moon phase and moon path, sunrise and sunset.
- Backgrounds picker.
- All apps screen.
- Settings, including one-tap shortcuts to Fire TV settings.
- Home-button redirect on Fire TV, so Pane opens instead of Amazon's launcher.
- Idle ambient mode.
- Automatically adds newly installed apps to the home screen.
- No ads, no accounts, no tracking.
- One-command installer for macOS, Linux and Windows.

[Unreleased]: https://github.com/Arya-A-Nair/pane-tv/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/Arya-A-Nair/pane-tv/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/Arya-A-Nair/pane-tv/releases/tag/v1.0.0
