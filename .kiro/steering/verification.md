# Verification — the rule that matters most

## Why this file exists

The previous project in this account took 72 commits. Its own CI file admits the cause:

> "Manual trigger only... while the project has no automated device tests to gate on."

The loop was: code written blind → CI builds an APK → the human downloads it → the human
installs it → the human finds the bug → the human describes it back. **The human was doing the
verification.** That is the bug being fixed here, and it is a process bug, not a code bug.

## Definition of done

A task is **not** done until all four are true, with real command output as evidence:

1. `./gradlew compileDebugKotlin` (or `compileKotlin`) succeeds for every touched module
2. `./gradlew test` passes for every touched module
3. Every changed screen has a **rendered PNG** in `screenshots/`, and it has been looked at
4. Nothing in the diff is unused, duplicated, or left behind

## Never claim success without evidence

- A command exiting 0 is not proof the feature works. It is proof the command exited.
- Never say "this should work". Run it.
- If something cannot be verified in this environment, **say so explicitly** rather than
  implying it passed. Instrumented tests and real-hardware behaviour fall in this bucket.
- Report the actual numbers: "37 tests, 0 failures", not "tests pass".

## The screenshot pipeline replaces installing APKs

There is no emulator here (`/dev/kvm` is absent). Roborazzi renders Compose on the JVM through
Robolectric, writing PNGs to `screenshots/` at the repo root. Those PNGs are **committed on
purpose** so a screen can be reviewed on GitHub or in the file explorer.

Run:

```
export ANDROID_HOME=/projects/sandbox/.android-sdk
./gradlew recordRoborazziDebug
```

If a UI change ships without a fresh screenshot, the change has not been reviewed.

## Environment gotchas that will otherwise waste a cycle

- mise pins `java=25` globally; AGP rejects it. `~/.gradle/gradle.properties` (machine-level,
  never committed) sets `org.gradle.java.home` to JDK 21 and lists mise's JDK paths in
  `org.gradle.java.installations.paths` so toolchain 17 resolves.
- `ANDROID_HOME=/projects/sandbox/.android-sdk` must be exported for every Gradle invocation.
- `local.properties` holds `sdk.dir` and is gitignored.
