# Android standards

## Toolchain — do not change without checking Maven first

`gradle/libs.versions.toml` is the single source of every version. **Never bump a version
without first fetching `maven-metadata.xml` and confirming it exists.** Guessing versions is
how builds break, and a broken build is a wasted round trip.

```
Gradle 8.14.5 (wrapper)   AGP 8.13.2   Kotlin 2.2.21
compileSdk 36   targetSdk 36   minSdk 26   JVM toolchain 17
Compose BOM 2026.06.01  →  ui/foundation 1.11.4, material3 1.4.0
```

Android platforms are **minor-versioned** now (`android-37.0`, `android-37.1` — not `android-37`).

## Module boundaries

```
app/                thin: MainActivity, Application, navigation wiring only
core/model/         PURE KOTLIN. No Android imports, ever.
core/designsystem/  theme, tokens, shared components
checks/<name>/      PURE KOTLIN measurement + decision logic
feature/<name>/     Compose UI for one screen
```

**`core/model` and `checks/*` use the Kotlin JVM plugin, not the Android library plugin.** That
is deliberate and load-bearing: it makes an Android import a compile error rather than a code
review comment, and it keeps their tests running in milliseconds so the decision logic can be
tested exhaustively.

A `checks/*` module never imports Compose. A `feature/*` module never contains measurement logic.

Use `project(":path")` for dependencies, not type-safe accessors. Use `api()` when a type appears
in a public signature, `implementation()` otherwise.

## Permissions

- Declare **nothing** until the check that needs it is built.
- Request at the point of use, with the reason visible on screen.
- **Never** `READ_PHONE_STATE` — IMEI is privileged-only since Android 10 on all devices
  regardless of target SDK, so requesting it gains nothing and looks alarming.
- **Never** `QUERY_ALL_PACKAGES` — use a `<queries>` manifest element listing specific packages.
- Target set for v1: `CAMERA`, `RECORD_AUDIO`, `INTERNET` + `AD_ID` (ads only).

## Kotlin style

- Explicit types on public API. Internal by default; `public` is a decision.
- No `!!`. No swallowed exceptions.
- Data classes for state; sealed interfaces for outcomes.
- KDoc on anything non-obvious explains **why**, not what.
- No `runBlocking` outside tests.

## Compose

- Stateless composables take state + lambdas. Hoist state to a ViewModel.
- Every screen composable has a `@Preview` **and** a Roborazzi screenshot test.
- `remember` keys must be correct — an incorrect key is a real bug, not a style issue.
- No `Modifier.background` with a raw `Color`; use tokens.

## Testing

- `checks/*` and `core/model`: exhaustive JVM unit tests. Cheap, so no excuses.
- `feature/*`: Roborazzi screenshot tests rendered on the JVM via Robolectric.
- There is **no emulator** in this environment (no `/dev/kvm`). Instrumented tests run in CI only.
