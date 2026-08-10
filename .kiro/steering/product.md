# PhoneProof — product rules

## Who this is for

A buyer standing in front of a stranger, about to hand over ₹18,000 for a used phone. They
have about three minutes, an audience, and no technical knowledge.

They are **not** a phone enthusiast browsing specs. Every existing app in this category serves
the enthusiast or the platform buying your phone. Serving the buyer is the entire differentiator,
and it is a positioning advantage that a 10-million-install spec viewer cannot copy without
breaking its own product.

## The three laws

### 1. Measure, never look up

Every value must be derived from the device at runtime. **No static per-model spec table is
ever the source of truth.**

A competitor's app reported a brand-new Galaxy S26 Ultra as having 3 GB RAM and a 12 MP camera
because it trusted a stale lookup table, and the reviewer called it fake. A measured value
cannot go stale.

A spec catalog may only be used for *discrepancy detection* — "the seller claims model X, the
device reports X, but the catalog says X ships with 8 GB and this device measures 4 GB."

### 2. Never a bare FAIL

Any negative outcome must carry:
- the **consequence** in real life, not the technical reading
- the **action** the buyer should take
- the **false-positive causes** — honest reasons the result might be wrong
- a one-tap **retest**

This is enforced in code by the `init` block of `CheckResult`. It is not a convention that can
be forgotten; it is a runtime invariant with tests behind it.

A rival app flagged a working proximity sensor as broken during a trade-in and it cost the
seller money. A check that cries wolf is worse than no check at all.

### 3. State the consequence, not the reading

Never: `digitiser: partial failure`
Always: *"A strip along the bottom-right never responded. You'll fight it every time you type.
Get ₹2,000 off, or walk away."*

## Outcome vocabulary

`PASS` · `CAUTION` · `FAIL` · `UNKNOWN`

`UNKNOWN` is a first-class, respectable answer. Battery state of health is privileged on
Android, IMEI has been unreadable by third-party apps since Android 10 — saying so plainly is
more useful than inventing a number. Confidence (`HIGH`/`MEDIUM`/`LOW`) is always reported
alongside. A `LOW`-confidence `FAIL` is forbidden; report it as `CAUTION`.

## Monetisation

- **The core is never rationed.** Every check, unlimited, free, forever. No quotas, no
  "2 tests remaining", no streaks.
- Free tier: 2 saved reports, ads in three safe slots, PDF export via opt-in rewarded ad.
- **Premium ₹99 one-time** — no ads, unlimited history, PDF export, comparison view.
- **Shop ₹999/year** — shop name and logo on the report card, bulk export. This tier is where
  the real revenue is; a reseller testing 20 handsets a day gets a sales tool.
- **Never a subscription for the consumer tier.** A rival moved its tools behind $15/week and
  the reviews are a graveyard. This app is used twice a year.

## Ad placement is a correctness constraint, not a taste preference

Ads are **forbidden** on these screens, and three of those are bugs rather than opinions:

| Screen | Why |
|---|---|
| Battery discharge test | An ad is uncontrolled CPU, network and screen load. It would corrupt our own mAh measurement. |
| Touch coverage grid | An ad overlay intercepts touch events and produces false dead zones. |
| Screen pattern tests | Full immersive at forced max brightness. An ad makes dead-pixel detection impossible. |
| Report card and verdict | This is the artifact the buyer shows the seller and shares. An ad destroys both trust and free marketing. |

Permitted: one adaptive banner on Home *below* the primary button; one interstitial after the
report is dismissed, capped to once per four hours; a native ad in the saved-reports list.

## Privacy copy

The app shows ads, so an advertising ID does leave the device. Therefore:

- ✅ "Your test results stay on this device"
- ❌ "Nothing leaves this device"

Never strengthen this wording. Overclaiming privacy is worse than not claiming it.
