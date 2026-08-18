# How PhoneProof makes money

This file exists because the model was **reversed** partway through the project, and the reversal
needs to be recorded rather than rediscovered as a surprise.

## The current model, set by the product owner

| Tier | Price | Scans | Advisory screens | Reports |
|---|---|---|---|---|
| Free trial | — | **2, then blocked** | locked | last 2 kept |
| Premium | ₹99 one-time | unlimited | unlocked | all kept, PDF, compare |
| Shop | ₹999/year | unlimited | unlocked | everything, plus shop branding on reports |

"Advisory screens" means **Claimed against measured** and **Eight things only you can check** (named
once, in `MANUAL_CHECKS_TITLE`). They are advice and comparison rather than measurements of the
phone, which is why they sit behind the paywall while the measurements do not.

Both are gated on the **tier alone** (`Entitlement.hasAdvisoryTools`), never on the scan count. A
free-trial install with both scans unused still finds them locked, so lock copy must say the trial
does not include them — never that the scans have run out. That sentence is written once, in
`ADVISORY_TRIAL_EXCLUSION`.

The scan limit lives in exactly one place, `Entitlement.FREE_SCAN_LIMIT`. Do not add a second copy:
the number shown on the button, the number in the block message and the number enforced must be the
same constant, or the app will eventually promise two scans and give one.

## What was reversed, and why it is written down

The project originally held that **scanning would be unlimited and free forever**, on the reasoning
that rationing the core function of a trust-focused app teaches people to distrust it. That position
was argued once and overruled by the product owner, whose reasoning was commercial and reasonable:
a trial that measures everything, unlimited, gives nobody a reason to pay.

Do not silently revert this. If it is revisited, it should be revisited deliberately.

## What must not be rationed, even now

The limit is **how many times a scan runs, never how honestly it reports.**

- No tier gets a different verdict from the same evidence.
- No measurement, consequence, action or false-positive cause is withheld from a paying-or-not user
  on a scan that has already run.
- No result the app has already measured is hidden behind a price. A buyer who can see a PASS but not
  the FAIL underneath it has been actively misled, which is worse than not scanning at all.

## When the trial runs out

Scans are **blocked** with an explanation and a route to the plans in Settings. Blocked, not degraded:
a half-scan reporting fewer checks would look like a clean phone.

Nothing already saved is taken away. Existing reports stay readable on the device.

## Entitlement is not yet real

`Entitlement` is stored in local DataStore. It is a switch that decides what to show, not proof of
purchase, and anyone with a rooted phone can edit it. It **must** be replaced by a verified Play
Billing purchase before anything is charged for. The debug-only tier switcher in Settings exists
because Play Billing cannot complete a purchase in a sideloaded build.

## How a purchase survives a factory reset

Asked directly by the product owner, along with "is it through mobile number login?".

**No login. Play Billing ties a purchase to the Google account, not to the device or the install**, so
the case solves itself: the user signs back in with the same account, the app calls
`queryPurchasesAsync()` on first launch, and the entitlement comes back silently. No account creation,
no phone number, no backend, no server bill.

### Mobile-number login was considered and rejected

It solves a problem Play already solves, and it costs:

- **It contradicts the product.** The pitch to a stranger in a shop is "your results stay on this
  device". Asking for a phone number ends that, and it is the reason a seller hands over their phone.
- **It is personal data**, which changes the Data Safety declaration, the privacy policy, and brings
  DPDP Act obligations in India. Today the app collects nothing.
- **It costs money forever** — a backend plus per-SMS charges on every login.
- **It is friction at the worst moment** — an OTP typed in while a seller watches.
- **It is trivially shared.** One number, unlimited shops.

Do not reintroduce it as a convenience. If a future feature genuinely needs an identity, that is a
product decision to be argued on its own terms, not a side effect of billing.

### What "the user must never hit this" requires in code

- **Query purchases on every cold start**, not only after a purchase, so restores, refunds and
  revocations all apply on their own.
- **A manual re-check button** as a fallback for a stale Play Store cache. Never the primary path.
- **Handle `PENDING`.** UPI and net-banking are normal in India and often sit pending for minutes.
  Entitlement is granted on `PURCHASED` only, and the screen must not look broken while it waits.
- **Acknowledge within three days** or Play automatically refunds the purchase. This is the single most
  common billing bug there is: the user pays, gets access, then silently loses both.

## The shop-on-someone-else's-phone problem, and what was chosen

The factory-reset question uncovered a structural one. **This app runs on the phone being inspected.**

- A buyer checking a stranger's handset installs it on *that* phone, where the **seller's** Google
  account is signed in. Their premium is invisible exactly when they want it.
- A shop hits this on every handset. The same root cause also scatters their reports across twenty
  phones they are about to sell.

So account-tied entitlement works perfectly for the reset case and badly for the repeat users premium
is aimed at. Three options were put to the product owner:

1. **Accept it.** Premium is per Google account, aimed at someone testing their own phone or one they
   have just bought; the two free scans cover the one-off buyer. No backend.
2. **Licence keys for shops** — works on any device, needs a backend to issue and validate, real abuse
   surface.
3. **Two-device mode** — the shop's phone as console, the tested phone paired over Bluetooth or local
   network. The honest answer for a shop, and it fixes the scattered reports too. A large feature.

**Option 1 was chosen.** Ship a single account-tied `PREMIUM` product through Play Billing, and defer
the `SHOP` tier until shops are demonstrably a market. If they are, option 3 is the real solution and
option 2 is the shortcut.

### One deliberate non-fix

The free-scan counter is local, so a factory reset or clearing app data returns the two free scans.
**Leave it.** Closing that gap needs accounts or a server, and the machinery would cost more trust than
the leaked scans are worth — while to the user, getting their trial back after a reset reads as fair.

## Refunds: what is said, and what was declined

The product owner asked for **no refund option, stated when someone takes Premium or Shop**. The first
half is done. The second was declined, and the reasoning is recorded here rather than argued again.

### What the purchase screen now says

`PURCHASE_TERMS` in `feature:settings`, under the plan cards, before anyone pays:

> One payment, taken by Google Play — this app never sees your card and cannot take or return money
> itself. Any refund is Google's decision and is requested through Google Play; if a purchase is
> refunded, the paid features switch off again.

Every clause is something the developer can stand behind: Play takes the payment; the app genuinely
cannot refund (no server, no merchant account, no payment relationship); refunds are Google's to give;
and a refund really does remove access, because entitlement is recomputed from Play on every launch.

It also sends the commonest support question — "how do I get my money back" — to the only party who can
act on it, which is the practical benefit the request was after.

### Why "no refunds" is not written anywhere

- **The developer cannot make it true.** Google refunds Play purchases at its own discretion, and a
  buyer can charge back through their bank regardless. An app announcing no refunds is describing a
  policy it has no power to enforce, and the first refund Google grants makes the app a liar.
- **Play requires accurate disclosures.** Misstating refund terms is a review risk, on the one screen
  where a rejection is most expensive.
- **It would be false where this app can least afford it.** The same reasoning already weakened the
  privacy line on Home (ads mean an advertising ID does leave the device) and forced
  `ADVISORY_TRIAL_EXCLUSION` to name the real reason a screen is locked. A paywall that misstates its
  own terms is precisely the pattern those decisions exist to prevent.

`PurchaseTermsTest` enforces both halves: the terms must name Play, must say the app cannot refund, must
say a refund removes access — and must not contain "non-refundable", "no refunds", "all sales final" or
any relative, nor quote a refund window, since Google's window is Google's to change.

**There is no refund flow in the app, and there should never be one.** Not as a policy stance — the app
has no way to move money, so a refund button could only ever be a dead end or a lie.

If this is to be revisited, revisit it deliberately. Do not let it arrive as a tightening of wording.

## A failed payment grants nothing

Asked directly, so it is written down. Two independent safeguards, either sufficient on its own:

1. **Only `PURCHASED` counts.** `PurchaseReconciler` filters on it, so a declined card, an abandoned
   checkout or an expired UPI payment produces nothing to grant. There is no optimistic unlock anywhere.
2. **The failure callback never grants.** Play's purchase listener is used only as a trigger to re-ask
   Play directly; the authoritative answer always comes from `queryPurchases()`. A spurious or malformed
   callback therefore cannot unlock a tier.

And `setEntitlement` is called from exactly one place in the billing layer, with `PaidTierWritesTest`
failing the build if anything else anywhere writes a paid tier.

## The testing switcher must be impossible to ship

Raised by the product owner: *"the TESTING ONLY section in settings should be removed when the final
APK is uploaded, otherwise every user will use premium features without paying."*

Correct, and "remember to remove it" is not a mechanism. It is now **structural**:

- `feature/settings/src/debug/.../TierOverride.kt` draws a switcher for every tier.
- `feature/settings/src/release/.../TierOverride.kt` has the same signature and draws nothing.

A release build compiles the second one, so the shipped APK contains no code capable of granting a paid
tier. There is no flag to invert and nothing to forget.

This replaced a `BuildConfig.DEBUG` check. That check worked — but the switcher, its strings and the
write to storage all shipped inside the release APK, one careless edit from being live.

Two things hold the arrangement in place, and both must stay:

- **`PaidTierWritesTest`** scans the source of every module and fails if anything outside a debug source
  set or the billing layer writes a paid tier, or offers a tier switcher.
- **CI assembles the release variant.** Without that, the do-nothing twin is never compiled and could
  rot unnoticed until someone tried to publish.

Do not "simplify" the two files back into one guarded by a flag.
