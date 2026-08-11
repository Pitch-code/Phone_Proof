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

## Environment

Two environment variables, and nothing else:

```
export JAVA_HOME=/root/.local/share/mise/installs/java/21
export ANDROID_HOME=/projects/sandbox/.android-sdk
```

`./gradlew` honours `JAVA_HOME` for the daemon, and the foojay resolver in `settings.gradle.kts`
downloads the JDK 17 toolchain the modules ask for. **No machine-level `~/.gradle/gradle.properties`
is needed** — that was a previous workaround, and it silently disappeared once, which cost a cycle
and produced a failure message consisting only of the string `25.0.2`. If a build ever fails with a
bare JDK version and no explanation, the daemon picked up a JDK that AGP rejects: export `JAVA_HOME`.

`local.properties` holds `sdk.dir` and is gitignored.

## Trust the build, and make the build trustworthy

If a build fails for a reason unrelated to the change, fix the build rather than re-running it. A
flaky build teaches you to retry instead of to read the error, and then a real failure gets
retried too.

One instance is already handled: the Kotlin compile daemon runs in its own JVM and does not inherit
`org.gradle.jvmargs`. At its default heap it died under parallel compilation with
`Daemon compilation failed: null`, which then surfaced as a bogus "unresolved reference" in whichever
module happened to be compiling. `kotlin.daemon.jvmargs` in `gradle.properties` fixes it. Verify
changes to build configuration by running the full pipeline **twice** with `--rerun-tasks`; a single
green run does not prove a race is gone.
