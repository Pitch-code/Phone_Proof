# Google Play policy constraints

Check this file **before** designing a feature, not after it is built. Every item below is a
verified restriction, not a guess.

## Hard API blockers — do not attempt these

| Want | Reality |
|---|---|
| Read IMEI / MEID / serial | **Impossible.** Restricted to `READ_PRIVILEGED_PHONE_STATE` (platform-signed and privileged system apps only) since Android 10, on *all* Android 10+ devices regardless of target SDK. Third-party apps get a `SecurityException`. |
| Battery state of health, manufacturing date, first-use date | Added in Android 14 but **not public** — privileged only. Coach the user to Settings → About phone → Battery information instead. |
| Battery cycle count | ✅ **Public** from Android 14. Survives a factory reset because it lives in the fuel gauge. Use it. |
| Hours used / hours charged | **Impossible.** A factory reset wipes usage history, and we require the seller to reset in front of the buyer. |
| OEM-unlocking toggle state, carrier lock | Not readable. Coach the user. |
| Device Owner / Device Admin present | ✅ Readable via `DevicePolicyManager`. This is how India's EMI-finance locks work, so it is the highest-value automated check in the app. **See the null trap below.** |
| Name of an admin app | ⚠️ Needs a `<queries>` entry for `DEVICE_ADMIN_ENABLED`, otherwise Android 11+ package visibility hides it and only the raw package id is available. Never use `QUERY_ALL_PACKAGES` for this. |
| `Build.VERSION.SECURITY_PATCH` | ✅ Readable. Answers "is this phone still getting updates" with a hard fact. |

## Platform traps found on real devices

### `getActiveAdmins()` returns null when there are none

Not an empty list — **null**. Treating that null as "the query failed" made a perfectly clean
realme handset on Android 16 report `CAN'T TELL` instead of `PASS`, which is the least useful
answer this app can give someone holding cash.

The general rule this stands for: **when reading anything from the platform, work out what its
"nothing found" value is before deciding what counts as a failure.** A null, an empty list, `-1`,
and `0` all mean different things depending on the API, and conflating "absent" with "unreadable"
turns a good result into a shrug. `DeviceAdminSnapshot.from` exists purely to hold that distinction
in one tested place.

### Verify against a device that is *healthy*

Every screenshot test covered the interesting outcomes — device owner, profile owner, caution,
can't-tell — and the bug above still shipped, because the boring case of *a normal phone with
nothing wrong* was only ever constructed by hand and never actually read from a platform. The most
common real-world state deserves a real-world check.

## Permissions

- SMS and Call Log permission groups are limited to apps registered as the **default SMS, Phone
  or Assistant handler**. We are none of those, so never request them.
- `QUERY_ALL_PACKAGES` is treated as sensitive and invites review scrutiny. Use a narrowly scoped
  `<queries>` element instead — the app resolves admin app names through an `<intent>` filter for
  `android.app.action.DEVICE_ADMIN_ENABLED`.

## Content and claims

- **Never accuse a named company or seller.** Describe the measurement and the tactic, never
  "brand X is fraudulent".
- Battery health is an **estimate** derived from measured discharge. It must be labelled as an
  estimate everywhere it appears. Overclaiming here is exactly how a rival earned a review about
  a falsely-failed sensor.
- Do not imply the app can confirm a phone is stolen. It can validate an IMEI checksum locally
  and deep-link to the official CEIR portal at `ceir.sancharsaathi.gov.in`. **Only link the
  official government portal** — never third-party IMEI-lookup sites.
- No trademark misuse. Manufacturer names appear only as factual descriptions of the device
  under test, never in the app name, icon, or store graphics.

## Ads and data safety

Adding AdMob has consequences that must be honoured, not glossed:

- Requires `INTERNET` and `AD_ID`
- The Data Safety form must declare **collecting Advertising ID**
- Google's UMP consent SDK is required for EEA/UK users
- A privacy policy URL is mandatory
- The home-screen privacy line must therefore read "Your test results stay on this device" and
  never "nothing leaves this device"

## Release hygiene

- Keystores, `keystore.properties` and signing credentials never enter the repo. `.gitignore`
  covers `*.jks`, `*.keystore`, `keystore.properties`.
- Release builds run R8 with `isMinifyEnabled` and `isShrinkResources` on.
- Cloud backup and device transfer are fully excluded in `data_extraction_rules.xml` — local
  reports must not silently sync and contradict the privacy line.
- Check the current closed-testing requirement in the Play Console before promising a launch
  date. It is a calendar constraint no amount of coding speed shortens.
