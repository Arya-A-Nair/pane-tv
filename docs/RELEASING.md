# Releasing Pane

Releases are automatic: when a PR that raises `versionName` in `AndroidManifest.xml` lands on `main`, `auto-release.yml` sees that no `v<versionName>` tag exists and calls `release.yml` to publish it. Pushing a `v*` tag by hand still works too. `release.yml` builds with `scripts/ci-build.sh` (same steps as `build.sh`), signs `pane.apk` with your release key, and publishes a GitHub Release with `pane.apk`, `pane.apk.sha256` and the matching CHANGELOG section as notes.

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

## The automated flow, end to end

1. **Contributors** open PRs and add a line under `## [Unreleased]` in `CHANGELOG.md` for user-visible changes. They never bump the version.
2. CI (`build`, `lint`) runs and a bot comments the APK size and download link. See [CONTRIBUTING.md](CONTRIBUTING.md).
3. **You review and approve.** `automerge.yml` turns on squash auto-merge when the repo owner approves, and GitHub merges once CI is green. Nothing from an outside contributor is ever merged without your approval; this is deliberate, since a merge ends up in a signed release.
4. **To release**, open a release PR that:
   - bumps `android:versionCode` (integer, must increase every release) and `android:versionName` (for example `1.1.0`) on the `<manifest>` element of `AndroidManifest.xml`;
   - moves the `[Unreleased]` items into a new `## [1.1.0]` section of `CHANGELOG.md` (that section becomes the release notes, and the release fails if it is missing).
5. When that PR merges, `auto-release.yml` runs, builds with `scripts/ci-build.sh`, signs with your key, and creates the tag `v1.1.0` and the GitHub Release (`pane.apk`, `pane.apk.sha256`). A hyphenated version (`1.1.0-rc1`) is published as a pre-release.

If the release run fails (for example a missing CHANGELOG section), fix it with a new PR, or re-run the failed run from the Actions tab. The tag is only created on success, so there is nothing to clean up. You can also trigger **Auto release** by hand from the Actions tab. (Pushing a `v*` tag yourself also releases; if that run fails, delete the tag with `git push origin :refs/tags/v1.1.0` and push it again.)

## Things only you can do

- **Approve PRs.** Auto-merge needs your approval; Dependabot's bot approval does not satisfy the code-owner rule, so Dependabot PRs also wait for your approval unless you change the `main` ruleset.
- **Fork PRs:** after approving, click **Enable auto-merge** (or **Squash and merge**). The approval workflow skips forks because GitHub gives workflows a read-only token on `pull_request_review` for them.
- **Your own PRs** (including release PRs) cannot be approved by you. Once CI is green, merge them as a repo admin using the ruleset bypass (**Merge without waiting for requirements**).
- The release keystore and the four secrets in setup above.

## How users get updates

Every APK is signed with the same key and package id, so `adb install -r` (which `install/pane-install.sh` uses) upgrades in place and keeps settings. Users update by re-running the installer, which downloads `releases/latest/download/pane.apk`. The installer also verifies `pane.apk.sha256`.

A `versionCode` that is not higher than the installed one makes Android refuse the upgrade, so always bump it.
