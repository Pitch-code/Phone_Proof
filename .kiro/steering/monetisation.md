# How PhoneProof makes money

This file exists because the model was **reversed** partway through the project, and the reversal
needs to be recorded rather than rediscovered as a surprise.

## The current model, set by the product owner

| Tier | Price | Scans | Advisory screens | Reports |
|---|---|---|---|---|
| Free trial | — | **2, then blocked** | locked | last 2 kept |
| Premium | ₹99 one-time | unlimited | unlocked | all kept, PDF, compare |
| Shop | ₹999/year | unlimited | unlocked | everything, plus shop branding on reports |

"Advisory screens" means **Claimed against measured** and **Check these by hand**. They are advice
and comparison rather than measurements of the phone, which is why they sit behind the paywall while
the measurements do not.

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
