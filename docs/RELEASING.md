# Releasing Pane

Releases are built by `.github/workflows/release.yml` when a `v*` tag is pushed. It builds with `scripts/ci-build.sh` (same steps as `build.sh`), signs `pane.apk` with your release key, and publishes a GitHub Release with `pane.apk`, `pane.apk.sha256` and the matching CHANGELOG section as notes.

## One-time setup

### 1. Create the permanent release keystore

Do this once, on your own machine. Never commit it (`*.keystore` and `*.jks` are gitignored).

```sh
keytool -genkeypair -v -keystore pane-release.keystore -alias pane \
  -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Pane, O=Arya Nair"
```

It asks for a keystore password and a key password (you can reuse the same one). Use long random passwords.

### 2. Store it safely

**If you lose this keystore or its passwords, nobody can ever update an installed Pane in place; users would have to uninstall and reinstall.** If it leaks, anyone can ship updates that Pane users' TVs will accept.

- Keep at least two copies, offline or in a password manager (for example an encrypted attachment), separate from the repo and from GitHub.
- Store the passwords in the same password manager.

### 3. Add the four GitHub secrets

Repo > Settings > Secrets and variables > Actions > New repository secret, or with the `gh` CLI:

```sh
base64 < pane-release.keystore | tr -d '\n' | gh secret set PANE_KEYSTORE_BASE64 --repo Arya-A-Nair/pane-tv
gh secret set PANE_KEYSTORE_PASSWORD --repo Arya-A-Nair/pane-tv
gh secret set PANE_KEY_ALIAS --repo Arya-A-Nair/pane-tv        # pane
gh secret set PANE_KEY_PASSWORD --repo Arya-A-Nair/pane-tv
```

| Secret | Value |
|---|---|
| `PANE_KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `PANE_KEYSTORE_PASSWORD` | keystore password |
| `PANE_KEY_ALIAS` | `pane` (the `-alias` above) |
| `PANE_KEY_PASSWORD` | key password |

## Each release

1. **Bump the version** in `AndroidManifest.xml`, on the `<manifest>` element:
   `android:versionCode` (integer, must increase every release) and `android:versionName` (for example `1.0.0`, should match the tag).
2. **Update `CHANGELOG.md`**: move items from `[Unreleased]` into a new `## [1.0.0]` section. The workflow uses that exact section as the release notes and fails if it is missing. The tag `v1.0.0` maps to `## [1.0.0]`.
3. Commit, then tag and push:
   ```sh
   git commit -am "Release 1.0.0"
   git tag v1.0.0
   git push origin main v1.0.0
   ```
4. Watch the Actions run. A tag with a hyphen (`v1.1.0-rc1`) is published as a pre-release.

If a run fails after the tag exists, fix the problem, then delete and re-push the tag (`git tag -d v1.0.0 && git push origin :refs/tags/v1.0.0`).

## How users get updates

Every APK is signed with the same key and package id, so `adb install -r` (which `install/pane-install.sh` uses) upgrades in place and keeps settings. Users update by re-running the installer, which downloads `releases/latest/download/pane.apk`. The installer also verifies `pane.apk.sha256`.

A `versionCode` that is not higher than the installed one makes Android refuse the upgrade, so always bump it.
