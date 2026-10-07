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

- **Material 3 Expressive**, tuned. We use M3 components, shapes and the
  motion scheme, and override color, type and spacing.
- Needs Compose Material3 1.4+ (`MaterialExpressiveTheme`, `MotionScheme`).
  The current BOM (2024.12.01, M3 1.3) is too old, so bump it before UI
  work starts.
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

- **Custom**: a hue/tone picker. Generate the light and dark variants from
  the picked color with HCT (`material-color-utilities`) so contrast holds
  in both themes.

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
| `displaySmall` | Battery % in the hero | 36 sp, Mono Medium |
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
│                          │
│        ◖      ◗          │  hero: earbud illustration
│                          │
│      Space Travel        │  device name
│   ● Connected    80 %    │  status dot, battery (mono)
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

- **Device picker** (not connected): the same hero area with a dimmed
  illustration, a list of paired devices, Moondrop devices first. Errors
  show inline above the list, with the fix ("take them out of the case,
  close MOONDROP Link").
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

## Hero illustration

- An **original, simple vector** of two earbuds (`ImageVector`, not a
  photo). Don't trace Moondrop product shots or use their logos.
- Reacts to state:

| State | Look |
|---|---|
| Disconnected | Outline only, 40 % opacity |
| Connecting | Outline, slow breathing pulse (opacity 40 → 80 %) |
| Connected | Filled; buds ease together a few dp, accent ring draws in |
| Battery | Accent ring around the buds fills to the battery level |
| Low battery (≤ 20 %) | Ring turns error color |
| EQ change | A short ripple from the buds in the accent color |

- Idle: a very slow float (2–3 dp, ~6 s period). Stops when reduced motion
  is on or the app is in the background.

## Motion

### Feel

**Snappy springs.** Fast, low-bounce, physics-based. The UI should feel
attached to your finger, never like it's playing a clip.

- Use springs for anything spatial (position, size, shape) so interrupted
  animations keep their velocity.
- Use short tweens only for fades and color.
- Nothing longer than ~350 ms except the hero idle loop.

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
  haptic tick, hero ripple. Applied optimistically; if the earbuds report a
  different preset, it animates back.
- **Volume slider:** thumb grows while dragging; number next to it updates
  live with a vertical roll on each step.
- **Battery %:** digits roll when the value changes (`AnimatedContent`,
  slide up/down by direction).
- **Connect:** device row → hero is a shared-element transition; the
  status dot crossfades gray → accent.
- **Disconnect / error:** controls fade and slide down 8 dp; the hero dims.
- **Screens:** shared-axis horizontal slide + fade, predictive back
  supported (Android 14+ back gesture scrubs the transition).
- **Lists:** `animateItem()` for rows appearing or reordering.

### Reduced motion

If the system animation scale is 0 or "Remove animations" is on:

- Replace spatial springs with 150 ms crossfades.
- Turn off the hero idle float, pulse and ripple.
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
- Every control has a content description; the hero is a single node
  ("Space Travel, connected, battery 80 percent").
- Supports font scale up to 200 % without clipping; the hero shrinks first.
- State is never shown by color alone (status dot also has a label).

## Settings → Appearance

- Theme: System / Light / Dark
- True black (dark only)
- Accent: System (12+) / presets / Custom
- Haptics: on/off

## Implementation notes

- `ui/theme/`: `Color.kt` (neutral palettes, accent presets, HCT
  generation), `Type.kt` (Inter + JetBrains Mono), `Motion.kt` (tokens),
  `Theme.kt` (`OpenDropTheme` that merges neutrals + accent source).
- Store appearance settings with DataStore.
- Replace the current `Card` + `RadioButton` EQ selector with M3
  connected buttons, and remove the confirmation dialog in favour of the
  inline hint.

## Open questions

- Exact hero illustration (needs a quick sketch pass).
- Whether a home-screen widget and QS tile follow the same accent (likely
  yes, via Glance theming).
- App icon.
