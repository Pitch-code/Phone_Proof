# Inspection passes: why a code exists at all

Decided by the product owner after the problem below was explained. Recorded because the *reason* is
easy to lose, and once lost, someone will "simplify" this back into an account-tied purchase and
quietly break the product.

## The problem this solves

**A Google Play purchase belongs to a Google account. This app runs on the phone being inspected.**

That phone is the seller's. It is signed in to the seller's Google account. So a buyer who has paid
₹99 installs PhoneProof on the phone they are about to inspect and is shown the **free trial** — the
app cannot see their purchase, because it is not running on their phone.

This is not a bug and no amount of billing code fixes it. It is the shape of Play purchases meeting
the shape of this app. It is also what killed the Shop tier, for the same reason and more severely.

## The model

**₹99 buys a pack of inspection passes. A pass unlocks one phone for 24 hours.**

1. On **his own** phone, the buyer purchases a pack through Google Play.
2. The app sends Google's purchase token to the licence server, which verifies it with Google.
3. The server issues a code — `PP-XXXX-XXXX` — and records the number of passes left.
4. The buyer keeps the code. A screenshot is expected and fine.
5. At the phone being inspected: install PhoneProof (free), tap **I have a code**, type it.
6. The server validates, decrements the count, and returns a **24-hour** pass.
7. The phone is Premium for 24 hours, then lapses. **Nothing permanent is left on someone else's
   phone.**

## What was rejected, and why it matters

### One code locked to one device, pay again for another

Asked for explicitly, and it would have broken the app on the first inspection. The first device a
code is used on is **the seller's phone**. Locking the licence there means the buyer's ₹99 dies on a
stranger's handset, and they pay again per inspection. "Device" is the wrong unit for an app whose
entire purpose is running on phones the payer does not own.

It is also likely a Play problem: purchases are expected to follow the buyer's account across their
devices, and charging twice for the same entitlement invites a policy review.

### Counting activations instead

Sharing is what device-locking was meant to prevent, and a finite pack prevents it better. A shared
code burns through its passes and stops. A seller who reads the code over the buyer's shoulder gets
only the remainder. No punishment falls on the legitimate use.

### OTP / phone-number sign-up

Rejected. **The code is the identity** — there is nothing to sign up for, no phone number to hold, and
no personal data to protect. In India it would also have required DLT registration of a business
entity, sender ID and template before a single message could be delivered, for no gain.

### Taking payment by UPI directly

Rejected. Google takes a service fee even under its alternative-billing arrangements, and a payment
gateway adds its own, so it is very likely more expensive than Play's fee at this size — while adding
refunds, reconciliation and fraud handling that Play currently does for free. Collecting outside the
permitted programme risks removal. Verify current terms in the Console before revisiting.

## Rules the implementation must keep

**A pass expires. It is never made permanent**, however convenient that seems. The promise "nothing is
left on the seller's phone" is the reason a buyer is willing to type a code onto a stranger's handset.

**Re-opening the same phone within the window costs nothing.** Same code plus same phone inside 24
hours is *one* pass. A buyer who closes the app by accident must not be charged an inspection for it;
that would feel like being fined for a slip.

**A typo must fail offline, instantly.** The code carries a check character, so a mistyped code is
rejected before any network call. Someone standing in front of an impatient seller should not wait on
a round trip to be told they typed it wrong.

**The device identifier is scoped to the code.** The server needs to recognise "this same phone
again" to honour the 24-hour rule, and nothing more. So what it stores is a hash of the device id
**salted with the code**, which means the same phone under a different code is unrecognisable. The
server can therefore never build a picture of which phones a person inspected, or link one phone
across two buyers. This is a deliberate privacy limit, not an accident of implementation: the phones
being identified belong to third parties who never agreed to anything.

**Redeeming needs the network; measuring must not.** Once a pass is granted it is held locally and
the checks run offline. A shop with no signal is a known weakness of the redeem step only.

**The checksum algorithm exists twice** — Kotlin in the app, JavaScript on the server — because the
app must reject typos without a round trip. Two implementations of one algorithm drift. They are
therefore both tested against **one committed set of vectors**, `licensing/code-test-vectors.txt`. Do
not change the algorithm without regenerating that file, and never let one side "fix" a vector it
disagrees with.

## What is still unbuilt

The server, the Play product, and the redeem screen. Until they exist, `Entitlement` remains
account-tied and the app behaves exactly as it did. The pure logic landed first on purpose: it is the
part that can be verified without a Play Console, a server, or a phone.
