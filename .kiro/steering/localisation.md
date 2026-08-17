# Localisation

The app ships English only today. Nothing about that is permanent, and nothing about it is an accident
either: **the language picker does not appear until at least one other language is genuinely complete.**
A picker that changes half a screen is worse than no picker, and this app does not overclaim elsewhere.

## The size of the job, measured rather than guessed

| Where | Roughly | Can reach Android resources? |
|---|---|---|
| `feature/*` and `core/designsystem` — screen chrome, instructions, buttons | 330 strings | yes |
| `checks/*` — the verdicts: headlines, consequences, actions, false-positive causes | 450 strings | **no** |

The second row is the problem, and it is the more important half. It is the text a buyer actually acts
on — "a loose socket charges fine while you watch and gives up overnight" — and all nine `checks/*`
modules are `kotlin.jvm`, with no Android on the classpath and therefore no `stringResource`.

That is a deliberate boundary, not an oversight. It is why several hundred rules tests run in seconds
without Robolectric, and why the check logic can be reasoned about without a device.

## Phase 1: the UI layer (in progress)

Straightforward. Every string in a `@Composable` moves to its module's `res/values/strings.xml` and is
read with `stringResource`.

Conventions:

- **Name by location, then purpose**: `charging_waiting_headline`, `radios_bluetooth_limit_note`. The
  module prefix keeps names unique across modules that each own a `strings.xml`.
- **Never concatenate translated text.** Word order differs between languages, so a sentence assembled
  from fragments in English becomes wrong elsewhere. Use positional arguments: `%1$d of %2$d`.
- **Counted things use `<plurals>`, never an `if`.** `"1 scan left"` / `"N scans left"` looks like two
  cases in English and is not: Hindi, Tamil and Telugu do not all agree with English on where the
  boundaries fall, and Urdu differs again. The existing `nounFor()` helper and every
  `if (n == 1) "" else "s"` in the codebase is a bug waiting for its first translation.
- **Apostrophes and `%` must be escaped** in XML (`\'`, `%%`), which is exactly the kind of thing the
  ratchet test below is for.

## Phase 2: the verdicts (not started, needs its own PR)

Three ways to give `checks/*` translatable copy, and the choice matters:

1. **Make them Android libraries.** Rejected. Every `evaluate()` would need a `Context`, and every rules
   test would need Robolectric — trading a fast, honest test suite for a resource lookup.
2. **Return keys instead of prose, render in the UI.** Architecturally clean, but the rules tests assert
   on the *actual words* a buyer reads (`headline).contains("negotiated an address")`), and that has
   caught real copy faults. Replacing those with key comparisons would lose the check that the words are
   right.
3. **Inject a `CheckStrings` interface.** Checks stay pure JVM and take an interface; the Android layer
   implements it from resources, and tests use the English implementation so assertions on real words
   still work.

**Option 3 is the intended direction.** It keeps the boundary that makes the tests fast, keeps the tests
reading like English, and still puts every verdict in a resource file.

## The ratchet

`app/src/test/.../HardcodedStringsTest.kt` walks the source of every module and fails on a hardcoded
literal in a `@Composable`. Converted modules are enforced; the rest sit in a named allowlist that only
ever gets shorter. Progress cannot silently reverse, and the list doubles as the to-do.
