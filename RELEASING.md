# Releasing

Two different artefacts, for two different purposes. Confusing them wastes an afternoon.

| | For | Package | Signed with |
| --- | --- | --- | --- |
| `phoneproof-vX.Y.Z.apk` | Sideloading — tap the link on a phone and it installs | `com.phoneproof.app.debug` | the committed debug key |
| `phoneproof-vX.Y.Z-for-play-upload.aab` | Uploading to the Play Console | `com.phoneproof.app` | your upload key |

An `.aab` **cannot be installed on a phone.** Android will refuse to open it. It exists only to be uploaded to Play, which builds the per-device APKs from it.

The APK's package deliberately ends in `.debug`, so a sideloaded test build can sit alongside a Play install without either overwriting the other.

## One-time setup: the upload key

Play signs what it distributes with a key it holds (Play App Signing). What you create here is the **upload key** — the one that proves an upload came from you. If it is ever lost, Google can reset it; if the *app signing* key were lost, the app could never be updated again. That is why this is the safer arrangement, and it is the default for new apps.

### 1. Generate it

```bash
keytool -genkeypair -v \
  -keystore upload-key.jks \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias phoneproof-upload
```

It asks for a keystore password, then a name and organisation (any accurate values are fine — this is not shown to users), then offers to reuse the keystore password for the key. Note down both passwords.

`10000` days is deliberate. Play requires a key valid past 2033, and a key that expires mid-life is a problem with no good solution.

**This file must never enter the repository.** `.gitignore` already excludes `*.jks`, `*.keystore` and `keystore.properties`, but keep your own copy somewhere durable — a password manager attachment, not just your laptop.

### 2. Give it to CI

Convert it to text, because a GitHub secret holds text and a keystore is binary:

```bash
base64 -w0 upload-key.jks > upload-key.b64
```

Then in the repository, **Settings → Secrets and variables → Actions → New repository secret**, add four:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | the entire contents of `upload-key.b64` |
| `RELEASE_KEYSTORE_PASSWORD` | the keystore password |
| `RELEASE_KEY_ALIAS` | `phoneproof-upload` |
| `RELEASE_KEY_PASSWORD` | the key password |

Delete `upload-key.b64` afterwards. Keep `upload-key.jks`.

Until `RELEASE_KEYSTORE_BASE64` exists, tagging still works — the release simply carries the APK alone, and the build log says so as a warning rather than failing.

### 3. Optional: signing on your own machine

Create `keystore.properties` at the repo root (git-ignored):

```properties
storeFile=upload-key.jks
storePassword=...
keyAlias=phoneproof-upload
keyPassword=...
```

`storeFile` is resolved from the repo root. Then `./gradlew bundleRelease` produces a signed bundle locally. Without this file the release variant still builds — it is just unsigned, which is all that is needed to check that it compiles and survives R8.

All four values are required together. Supplying some but not all **fails the build with a message naming the missing ones**, rather than quietly producing an unsigned bundle that Play would reject days later.

## Cutting a release

1. **Bump the version.** Edit `version.properties`:

   ```properties
   versionName=0.6.0
   ```

   `versionCode` is derived from it (`MAJOR*10000 + MINOR*100 + PATCH`), so there is nothing else to change. Commit it.

2. **Wait for CI to be green on `main`.**

3. **Write the release notes first.** Create the release in the GitHub UI as a draft, or let the tag create it — but note the tag build will not overwrite notes that already exist.

4. **Tag it, matching `version.properties` exactly:**

   ```bash
   git tag v0.6.0 && git push origin v0.6.0
   ```

   CI checks the tag against `version.properties` **before building anything**. A mismatch fails in seconds, because an APK handed out as v0.6.0 while reporting 0.5.0 inside is worse than no release.

5. CI attaches both artefacts to the release.

## Uploading to Play

The Console needs these to exist before the app can be sold, and the app will not behave correctly without them:

- **One in-app product**, id exactly `phoneproof_premium`, type **one-time** (not a subscription). A missing or misspelled id is not an error the app can report usefully — the billing library returns an empty product list, which simply looks like "nothing for sale".

  **Do not create `phoneproof_shop_yearly`.** An earlier version of this document said to, which was wrong: the app does not offer the Shop tier, so a product for it would sit in the Console unsold and unreviewable. The reason it is not offered is worth recording, because it is a design constraint rather than unfinished work — **a Play purchase belongs to a Google account, and this app runs on the phone being inspected.** That phone is the customer's, signed in to the customer's account, so a shop's own purchase would never be recognised on it. Selling a Shop tier under those rules would be selling something that does not work. If it is ever offered, it needs a licence code the shop types in, which means a server.
- **An internal testing track with licence testers**, or purchases cannot be tested without real money. Test purchases only work for accounts on that list.
- **Data safety** and **privacy policy**: `docs/privacy.html` is in this repo and served from GitHub Pages.

### Still open before a first submission

- **The debug tier switcher must be gone from release builds.** It is source-set split (`app/src/release` has the do-nothing twin), and `assembleRelease` in CI compiles that twin on every push — so this is enforced rather than remembered. Worth confirming on the release build by eye before submitting.
- **Home mentions ads, and there is no ads SDK.** Either the copy or the claim has to go; Play's data-safety form asks directly.
