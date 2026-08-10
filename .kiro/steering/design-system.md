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

### Hard rule: no looping or continuous animation, anywhere

This is not a style preference. The app measures battery discharge under a load it controls.
A perpetually animating background is an uncontrolled load and **would corrupt the measurement**.
An infinite animation is a correctness bug in this codebase.

## Screen-level notes

- **Home** — not a dashboard. One 72 dp primary action. Privacy line beneath it.
- **Touch grid** — full-bleed `Canvas`, no chrome. Live count in tabular mono. Uncovered cells
  pulse once at the end; a single pulse, not a loop.
- **Screen patterns** — true immersive, brightness forced to max, auto-brightness disabled.
- **Report card** — fixed aspect ratio so the screenshot looks deliberate in WhatsApp. Largest
  type in the entire app is the negotiation figure.
