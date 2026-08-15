# Design system

## The governing idea

This app looks like an **instrument**, not a landing page. A buyer has to hold it up in front of
a seller and be taken seriously.

A reviewer of a competing diagnostic app praised its function but said the interface felt like a
cheap third-party app, and asked for better design *specifically so people could trust it*. The
answer to that is precision, not decoration. Aurora gradients and glowing hero animations make a
diagnostic tool look like an advertisement.

Reference sensibility: neutral palettes, hairline borders instead of shadows, dense precise data.
Not: WebGL colour fields, animated backgrounds, flashy section reveals.

## Colour tokens

Defined once in `core/designsystem/PhoneProofColors`. Never hardcode a hex value in a feature.

```
Background      #0A0A0B   not pure black; pure black smears on OLED
Surface         #111113
SurfaceRaised   #18181B
Border          #FFFFFF at 8%   ← hairline borders, NOT elevation shadows
TextPrimary     #FAFAFA
TextSecondary   #A1A1AA
TextTertiary    #71717A

Pass            #22C55E   fill 12%, border 40%
Caution         #F59E0B
Fail            #F43F5E   fill 12%, border 40%
Unknown         #52525B   used constantly and without embarrassment
Accent          #3B82F6   exactly one accent colour in the app
```

### Hard rule: never state a result with colour alone

Every outcome carries **icon + colour + word**. Two reasons: colour-blind users, and the report
card gets photographed and screenshotted, sometimes in greyscale.

## Typography

- UI text: `FontFamily.Default`
- **Every number: `FontFamily.Monospace`** with `FontFeatureSetting("tnum")`

All measurements — mAh, Hz, µA, cycles, MB/s, °C, cell counts — render in tabular monospace so
digits do not shift as live values update. This single choice does more for perceived
credibility than any animation.

Bundling Inter and JetBrains Mono is deferred polish, not a blocker.

## Motion

Motion **only** confirms an action or reveals a result. Never decoration.

```
Standard transition   spring(dampingRatio = 0.85f, stiffness = 380f)
Touch grid cell fill  90 ms linear tween, NO spring — must feel mechanical and instant
Result row reveal     40 ms stagger per row, fade + 8 dp rise
Verdict reveal        spring(dampingRatio = 0.60f) — the one overshoot in the app, once per run
Haptics               CONFIRM on pass, REJECT on fail
```

### Rule: no looping animation, with exactly one documented exception

> The guide diagrams were a **second** exception for a while, undocumented: `rememberInfiniteTransition`
> looping every open card forever. The product owner ruled that the rule wins, so they hold still, each
> at its own `GuideDiagram.stillFrame`. Do not reintroduce the loop — and if a diagram reads poorly,
> change the frame it stops on rather than making it move.


This is not a style preference. The app measures battery discharge under a load it controls.
A perpetually animating background is an uncontrolled load and **would corrupt the measurement**.

#### The exception: a `FAIL` card breathes

A result card with outcome `FAIL` pulses its border continuously, because a walk-away finding —
a rooted phone, an unlocked bootloader, a lender's device-owner lock — must not be scrollable past.

It is constrained, and the constraints are the reason it is allowed:

- **0.7 Hz** (a 700 ms tween on `RepeatMode.Reverse`, so a 1.4 second cycle).
  [W3C WCAG 2.3.1](https://www.w3.org/WAI/WCAG21/Understanding/three-flashes-or-below-threshold)
  sets the photosensitive-seizure threshold at **three flashes per second**, and notes that people
  are *more* sensitive to red flashing than to any other colour, with a separate stricter test for
  saturated red. This runs an order of magnitude below that line.
- **It ramps smoothly rather than switching on and off**, so it reads as a breathe, not a flash.
- **It is a border and a faint fill**, never the whole card and never the text.
- `CheckResultCard(emphasise = false)` **must** be used anywhere a measurement is running. That is
  not optional: the battery check cannot produce an honest reading next to an animating surface.

Nothing else in the app loops. If a second exception is ever wanted, it needs the same three things:
a rate below the flash threshold, a way to switch it off during measurement, and a written reason.

## Window insets — every screen, every time

`MainActivity` calls `enableEdgeToEdge()`, so **nothing is inset for you**. A screen that does not
consume insets draws its content underneath the status bar and the navigation bar. That shipped
once: the app title collided with the clock, and the bottom row of buttons sat on top of the
navigation keys.

Every screen root applies:

```kotlin
Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
```

`safeDrawing` rather than `statusBars`, because it also covers the navigation bar, gesture areas
and display cutouts — so it holds on a notch phone, a punch-hole phone and a 3-button phone
without being tuned to any one handset.

**The one deliberate exception:** the touch-coverage canvas takes *no* inset. The test must reach
the true physical edges of the screen, and insetting it would leave the strips beneath the system
bars untestable — which is exactly where dead touch zones tend to be. Only the overlay on top of
that canvas is inset.

Drawing to the edge is not enough on its own, and that took a while to accept. Touches there still
went to the system: a swipe at the top opened the shade, a swipe at the bottom went home. So the
touch test now also **hides the system bars for its duration** (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`,
restored on dispose) and applies **`Modifier.systemGestureExclusion()`** to the canvas. The product
owner's ruling is that the edges are part of the test; the app's job is to claim them, not to excuse
them.

Where the system still wins, the verdict reports the gap as **unattributable** — `UNKNOWN`, with an
instruction to sweep again — and never as a fault. Gesture exclusion covers only the back swipe on the
left and right, is capped at 200 dp per edge, and OEM skins vary in how much of the home and shade
gesture they keep while immersive, so that fallback is load-bearing rather than defensive.

⚠️ Robolectric reports no system bars, so **inset behaviour, immersive mode and gesture exclusion
cannot be verified by screenshot test.** They have to be checked on a device. Do not claim any of it
is verified from a render.

## Screen-level notes

- **Home** — not a dashboard. One 72 dp primary action. Privacy line beneath it.
- **Touch grid** — full-bleed `Canvas`, no chrome. Live count in tabular mono. Uncovered cells
  pulse once at the end; a single pulse, not a loop.
- **Screen patterns** — true immersive, brightness forced to max, auto-brightness disabled.
- **Report card** — fixed aspect ratio so the screenshot looks deliberate in WhatsApp. Largest
  type in the entire app is the negotiation figure.
