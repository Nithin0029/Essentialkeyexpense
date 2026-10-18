# Releasing Essential Expense Tracker

The app is distributed as a sideloaded APK through GitHub Releases. There is no Play Store
listing, which is the only reason losing the original signing key is recoverable at all — Play
would never accept a re-signed upload for an existing listing.

---

## Part 1 — One-time: create a new signing key

The original `expense_tracker_release.jks` (alias `expense_tracker`, PKCS12, created 29 Jul 2026)
is unusable because its password is lost. There is no way to recover or reset it; that is the
point of the format. A new key is required.

Run this yourself — it prompts for a password, which must not be typed into a shared session:

```bash
"C:/Program Files/Android/Android Studio/jbr/bin/keytool.exe" -genkeypair -v -keystore expense_tracker_release_v2.jks -storetype PKCS12 -keyalg RSA -keysize 2048 -validity 10000 -alias expense_tracker
```

Then create `keystore.properties` in the project root from the template:

```bash
cp keystore.properties.example keystore.properties
```

Fill in `storePassword`, `keyAlias` (`expense_tracker`) and `keyPassword`, and set
`storeFile=expense_tracker_release_v2.jks`. Both files are gitignored and must never be committed.

**Back the keystore up off this machine, and put the password in a password manager.** It is the
only unbackupable artifact in the project. Losing it again means every user must reinstall again.

Keep the old `.jks` too. It is useless for signing, but it costs nothing and removes any chance of
discarding it should the password ever resurface.

---

## Part 2 — One-time: register the new key with Firebase

`GoogleAuthManager` reads `default_web_client_id`, which the Google Services plugin generates from
`app/google-services.json`. That file pins the **old** certificate hash
(`d6feed0b3a7d63d699fe8b5a151cdcdb11f9e265`). Until the new key is registered, Google Sign-In — and
therefore all Sheets sync — will fail on the new build.

Get the new fingerprint:

```bash
"C:/Program Files/Android/Android Studio/jbr/bin/keytool.exe" -list -v -keystore expense_tracker_release_v2.jks -alias expense_tracker
```

Then in the Firebase console, project **expense-tracker-1bf68** → Project settings → Your apps →
`com.nothing.expensetracker` → **Add fingerprint**, paste the SHA-1, and download the regenerated
`google-services.json` over `app/google-services.json`.

### Optional: unblock debug builds

`app/src/debug/google-services.json` is currently a local build aid — a copy of the production
config with the package name rewritten to `com.nothing.expensetracker.debug` so the Google Services
plugin stops failing the debug variant. It is not a real registration, so Sign-In does not work in
debug. To fix that properly, add a second Android app in the same Firebase project with the
`.debug` package name and its debug SHA-1, then replace that file with the real one.

---

## Part 3 — Per-release checklist

### 1. Bump the version in two places

They must agree, or the updater misbehaves.

- `app/build.gradle.kts` → `versionCode` and `versionName`
- `version.json` → `versionCode`, `versionName`, `apkUrl`, `releaseNotes`

`versionCode` must strictly increase; the updater compares it against `BuildConfig.VERSION_CODE`.

### 2. Build the signed APK

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew clean :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

If `keystore.properties` is missing, the build still succeeds but produces
`app-release-unsigned.apk`, which Android refuses to install. Confirm signing before shipping:

```bash
"$LOCALAPPDATA/Android/Sdk/build-tools/36.0.0/apksigner.bat" verify --print-certs app/build/outputs/apk/release/app-release.apk
```

It must print certificate details, not `DOES NOT VERIFY`.

### 3. Publish the GitHub release first

Tag `vX.Y.Z` on `master`, and upload the APK **renamed to match `apkUrl` exactly**. The convention
is `EssentialExpenseTracker-vX.Y.Z.apk`.

A mismatch here is how the v3.5.0 release broke: `version.json` pointed at
`EssentialExpenseTracker-v3.5.0.apk` while the uploaded asset was named
`Essential.Expense.Tracker.v3.5.0-beta.apk`, so every in-app update attempt 404'd.

### 4. Only then push `version.json` to `master`

The updater reads `version.json` from the `master` branch. Pushing it before the release asset
exists advertises a download URL that 404s to anyone who checks in the gap.

### 5. Verify

```bash
curl -sI "$(python -c "import json;print(json.load(open('version.json'))['apkUrl'])")" | head -1
```

Follow the redirect to a `200`. Also confirm `version.json` is reachable on the branch the app
actually reads:

```bash
curl -s https://raw.githubusercontent.com/Nithin0029/Essentialkeyexpense/master/version.json
```

---

## This release (3.6.0) is a breaking one

The signing key changes, so **existing installs cannot be upgraded in place**. Android rejects an
APK signed with a different key, and users see "App not installed". The in-app updater cannot help
either — it installs an APK, and that install hits the same signature check.

Every user must:

1. Open the app and let it sync, so the spreadsheet is current.
2. Uninstall the old app. **This wipes local data.**
3. Install the new APK.
4. Sign in to Google. The new two-way sync restores transactions, categories, friends and budgets
   from the existing spreadsheet.

Step 4 is what makes this survivable. Before 3.6.0 the sync was push-only and a reinstall meant
permanent data loss. Do not ship a key change to anyone on 3.5.0 or earlier without telling them to
sync first — their data exists only on the device until they do.

Put that warning at the top of the release notes, not just in the body.

### Note on the updater

Existing 3.5.0 installs will never show an update prompt regardless. `RetrofitClient` pointed at the
`main` branch while the repository's default branch is `master`, so the fetch 404'd and no update
was ever offered. That is fixed in 3.6.0, but the fix only takes effect once a user is *running*
3.6.0 — this release has to be distributed by telling people directly. From 3.7.0 onward the
in-app updater will work for anyone on 3.6.0+ (subject to the signature rule above, which no
longer applies once everyone is on the new key).
