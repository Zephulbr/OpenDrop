# OpenDrop Design

How OpenDrop should look, move and feel. The short version: **clean, fast,
smooth.** Neutral surfaces, one accent color, snappy spring motion, and
nothing on screen the earbuds can't actually do.

## Principles

1. **Fast first.** Every tap answers within a frame. Show the change right
   away and confirm it when the earbuds reply; never block on a spinner if
   we can avoid it.
2. **Quiet surfaces, one accent.** Grays and black carry the layout. The
   accent color only marks what's active or interactive.
3. **Motion explains state.** Animations show where something came from
   and what changed. No decoration-only motion, and every animation can be
   interrupted mid-flight.
4. **Honest UI.** Show only what the connected device supports (see
   [ROADMAP.md](ROADMAP.md)). No greyed-out ANC toggle, no fake left/right
   battery. One battery value in 10 % steps is what we have, so that's what
   we show.
5. **Native, not generic.** Built on Material 3 so it feels at home on
   Android, but tuned (type, spacing, shapes, motion) so it doesn't look
   like a stock settings screen.

## Foundation

- **Material 3**, tuned toward Expressive. We use M3 components and
  override color, type, shapes and motion.
- The app is on Compose Material3 1.3 for now. The Expressive APIs
  (`MaterialExpressiveTheme`, `MotionScheme`, connected button groups) need
  a newer Material3, compileSdk and AGP, so until that upgrade the motion
  tokens and the segmented control are our own (`ui/theme/Motion.kt`,
  `ui/components/Components.kt`). They follow the specs below, so switching
  later is a swap, not a redesign.
- Prefer flat sections on the background over cards. Use a container only
  when it groups controls that belong together.

## Color

### Model

Neutral surfaces plus a single accent. Surfaces are never tinted by the
accent.

| Role | Light | Dark | True black |
|---|---|---|---|
| Background | `#FAFAFA` | `#121212` | `#000000` |
| Surface (sections, sheets) | `#FFFFFF` | `#1C1C1C` | `#0E0E0E` |
| Surface raised (pressed, chips) | `#EFEFEF` | `#262626` | `#1A1A1A` |
| Outline | `#DADADA` | `#333333` | `#262626` |
| Text primary | `#111111` | `#F2F2F2` | `#F2F2F2` |
| Text secondary | `#5C5C5C` | `#A3A3A3` | `#A3A3A3` |
| Error | `#C62828` | `#FF6B6B` | `#FF6B6B` |

These map onto the M3 `ColorScheme` (`background`, `surface`,
`surfaceContainer*`, `outline`, `onSurface`, `onSurfaceVariant`, `error`).
Exact values get tuned on a real device; the rule (neutral, no hue) stays.

### Accent

The accent fills `primary`, `onPrimary`, `primaryContainer` and
`onPrimaryContainer`. Nothing else uses color.

Source, in order:

1. **User pick** from the accent picker, if they made one.
2. **System accent** (Android 12+): take only the primary roles from
   `dynamicLight/DarkColorScheme()` and drop the rest, so surfaces stay
   neutral.
3. **Hot pink** fallback (Android 8–11, or dynamic turned off).

### Accent picker

Settings → Appearance → Accent:

- **System** (default on Android 12+; hidden below 12)
- Presets:

| Preset | Dark theme | Light theme |
|---|---|---|
| **Hot pink** (default fallback) | `#FF2E88` | `#D6006A` |
| Moon violet | `#B69CFF` | `#6B4FD8` |
| Signal orange | `#FF8A3D` | `#C24E00` |
| Cool cyan | `#4DD8F0` | `#007A8F` |
| Lime | `#C6F24E` | `#4F7A00` |

- **Custom**: hue and vibrance sliders. Brightness isn't user-set: for each
  theme it's moved until the accent reaches 4.5:1 against the background,
  so contrast holds in both themes without pulling in a color library.

Each preset has a separate tone per theme because a neon that pops on
black is unreadable on white. Contrast targets: accent against background
≥ 4.5:1, text on accent ≥ 4.5:1. Hot pink: `#FF2E88` on black is about 6:1
(use black text on it); `#D6006A` on white is about 5.2:1 (use white text).

### Theme modes

- **System** (default), **Light**, **Dark**.
- **True black** toggle, applies in dark mode only (AMOLED).

## Typography

- **UI:** Inter (variable). Rounded, neutral, reads well small.
- **Numbers and technical text:** JetBrains Mono. Battery %, volume level,
  firmware version, codec, packet log.
- Both fonts are OFL and **bundled in `res/font`**, not downloaded through
  Google Play Services, so the app works on F-Droid and de-Googled phones.
- Use tabular figures for anything that changes (battery, volume) so the
  layout doesn't shift.

Scale (Compose `Typography` overrides):

| Style | Use | Size / weight |
|---|---|---|
| `headlineSmall` | Device name | 24 sp, Inter SemiBold |
| `titleMedium` | Section titles | 16 sp, Inter SemiBold |
| `bodyLarge` | Row labels | 16 sp, Inter Regular |
| `bodyMedium` | Secondary text | 14 sp, Inter Regular |
| `labelLarge` | Buttons, chips | 14 sp, Inter Medium |
| `labelSmall` | Hints, mono metadata | 11 sp, Mono Regular |

## Layout

### Device screen: hero + controls

```
┌──────────────────────────┐
│ Basshead                 │  preset name
│ ▁▇▆▄▂▁▁▁▁▂▃▂▁            │  hero: EQ curve
│                          │
│ Space Travel             │  device name
│ ● Connected ▮▮▮▮▮▮▮▮▯▯ 80%│  status dot, 10-step battery meter
│                          │
├──────────────────────────┤
│ EQ                       │
│ ┌──────┬────────┬───────┐│  segmented / connected buttons
│ │ Ref  │ Bass   │Monitor││
│ └──────┴────────┴───────┘│
│ Switching may pop briefly│  inline hint, no dialog
│                          │
│ Volume  ━━━━━━━●───── 9  │
│                          │
│ Touch controls         › │  rows appear only when supported
│ Codec            AAC   › │
│ Device info            › │
└──────────────────────────┘
```

- **Hero** takes the top third and collapses into a compact top bar
  (name + battery) as the user scrolls.
- **Controls** are flat sections on the background, separated by spacing
  and section titles, not by dividers or cards.
- Rows that open a sub-screen end with `›` and a current value.
- The packet log moves to **Device info → Developer**, out of the main screen.

### Other screens

- **Auto-connect**: OpenDrop remembers the last device it connected to
  and connects on launch, and again whenever Android reports the earbuds
  connected to the phone. A failed automatic attempt is quiet (no error
  card). An explicit Disconnect pauses this until the user connects again.
- **Device picker** (not connected): the same hero area with a flat,
  dashed curve, a list of paired devices, Moondrop devices first. Errors
  show inline above the list, with the fix ("take them out of the case,
  close MOONDROP Link").
- **Connection notification**: while connected or reconnecting, a quiet
  ongoing notification ("Space Travel — Connected · Basshead · 80 %") with a
  Disconnect action; tapping it opens the app. It belongs to the foreground
  service that keeps the connection alive in the background. The status bar
  icon is the EQ curve. Android 13+ asks for notification permission once,
  on the first connection.
- **Permission**: one sentence on why, one button. No multi-page onboarding
  until Phase 5.
- **Sub-screens** (touch controls, codec, PEQ later) slide in as full
  screens with shared-element transitions from their row.

### Spacing and shape

- 4 dp grid. Screen gutter 20 dp. Section gap 28 dp. Row height 56 dp min.
- Corner radius: 12 dp for rows/containers, full (pill) for chips, buttons
  and the slider thumb. EQ segments morph shape on select (M3 Expressive
  connected buttons).
- Touch targets ≥ 48 dp.

## Hero: EQ curve and battery meter

The hero shows the two things the earbuds tell us: the EQ preset and the
battery level. An earbud drawing was tried first and dropped; it read as
binoculars and said nothing about state.

**EQ curve**

- A line plot of the selected preset's sound signature over a log
  frequency axis (20 Hz to 20 kHz), with a soft accent fill under it.
- The curves are **illustrative**: hand-shaped from each preset's
  character (Basshead lifts the bass, Monitor dips the bass and lifts the
  presence region), not measured. Device info says so. No dB or frequency
  labels until we have measured data; swap in real curves then.
- The other presets show as faint lines behind, so the difference is
  readable at a glance.
- The 0 line sits at about two thirds of the plot height, because the
  presets mostly boost; that leaves no empty band under the curves.

| State | Look |
|---|---|
| Disconnected | Flat dashed line, no fill, label "No EQ" |
| Connecting | Flat gray line, slow pulse |
| Connected | Preset curve in the accent color with fill; other presets faint |
| EQ change | The curve springs from the old shape to the new one |

**Battery meter**

- Ten pills next to the connection status, plus the percentage in mono.
  Ten because the earbuds report battery in 10 % steps; the meter shows
  exactly the resolution we have.
- When the level first shows, pills light up left to right (35 ms
  stagger). Red at 20 % or less.

## Motion

### Feel

**Snappy springs.** Fast, low-bounce, physics-based. The UI should feel
attached to your finger, never like it's playing a clip.

- Use springs for anything spatial (position, size, shape) so interrupted
  animations keep their velocity.
- Use short tweens only for fades and color.
- Nothing longer than ~350 ms except the connecting pulse.

### Tokens

Defined once (`ui/Motion.kt`) and used everywhere, mapped onto the M3
`MotionScheme`:

| Token | Use | Spec |
|---|---|---|
| `spatialFast` | Chips, toggles, thumb, press scale | `spring(dampingRatio = 0.9f, stiffness = 1400f)` |
| `spatialDefault` | Section expand, row reveal, hero collapse | `spring(0.85f, 700f)` |
| `spatialSlow` | Screen transitions, shared elements | `spring(0.9f, 380f)` |
| `effectsFast` | Color, opacity on small things | `tween(120, FastOutSlowIn)` |
| `effectsDefault` | Crossfades, content swaps | `tween(200, FastOutSlowIn)` |

Starting values, to tune on a real device.

### Key interactions

- **Press:** buttons and rows scale to 0.97 with `spatialFast`.
- **EQ preset:** the selected segment morphs (pill grows, corner radius
  changes), accent fill slides from the old segment to the new one, light
  haptic tick, and the hero curve morphs. Applied optimistically; if the earbuds report a
  different preset, it animates back.
- **Volume slider:** thumb grows while dragging; number next to it updates
  live with a vertical roll on each step.
- **Battery:** meter pills fill left to right when the level appears.
- **Connect:** device row → hero is a shared-element transition; the
  status dot crossfades gray → accent.
- **Disconnect / error:** controls fade and slide down 8 dp; the curve flattens.
- **Screens:** shared-axis horizontal slide + fade, predictive back
  supported (Android 14+ back gesture scrubs the transition).
- **Lists:** `animateItem()` for rows appearing or reordering.

### Reduced motion

If the system animation scale is 0 or "Remove animations" is on:

- Replace spatial springs with 150 ms crossfades.
- Turn off the connecting pulse and the meter stagger.
- Keep state changes instant and clear.

## Haptics

Subtle, **on by default**, toggle in Settings.

| Event | Haptic |
|---|---|
| EQ preset selected | `SEGMENT_TICK` / light click |
| Volume slider step | `CLOCK_TICK`-style detent (only on value change) |
| Connected | `CONFIRM` |
| Connection failed | `REJECT` |

Use `View.performHapticFeedback` constants so it follows the user's system
haptic settings. No haptics on scroll.

## Performance ("fast")

- **Cold start** to first frame under ~500 ms on a mid-range phone. Ship a
  **baseline profile** for startup and the device screen.
- No splash delay beyond the system splash. Show the last known device and
  state immediately, then refresh.
- Optimistic updates for EQ and volume (see Motion).
- Keep the slider and battery text in their own small composables so
  dragging doesn't recompose the screen. Read fast-changing state in
  lambdas (`Modifier.graphicsLayer { }`, `drawBehind { }`).
- Smooth at 90/120 Hz: no allocations in draw, no layout work during
  animation frames.

## Accessibility

- All text and accent contrast ≥ 4.5:1 (see Color).
- Every control has a content description; the curve is a single node
  ("EQ curve, Basshead") and the meter reads "Battery 80 percent".
- Supports font scale up to 200 % without clipping; the hero shrinks first.
- State is never shown by color alone (status dot also has a label).

## Settings → Appearance

- Theme: System / Light / Dark
- True black (dark only)
- Accent: System (12+) / presets / Custom
- Haptics: on/off

## App icon

An adaptive icon (`mipmap-anydpi-v26`): the Basshead curve in hot pink with
a soft fill and a gray baseline, on the dark background `#121212`. A
monochrome layer (the curve alone) serves Android 13+ themed icons. The
notification icon is the same curve in white.

## Implementation notes

- `ui/theme/`: `Color.kt` (neutral palettes, accent presets, contrast
  solving), `Type.kt` (Inter + JetBrains Mono), `Motion.kt` (tokens and
  reduced-motion detection), `Haptics.kt`, `Theme.kt` (`OpenDropTheme`
  merges neutrals + accent source and provides motion and haptics).
- `ui/components/`: press scale, rows, segmented selector, rolling number,
  gradient slider.
- `settings/Appearance.kt`: appearance settings in DataStore.
- The window background (`res/values*/themes.xml`) matches the neutral
  background, so there's no flash before the first frame.

Not built yet: baseline profile, shared-element row → hero transition,
widget/tile theming.

## Open questions

- Measured preset curves, to replace the illustrative ones.
- Whether a home-screen widget and QS tile follow the same accent (likely
  yes, via Glance theming).
