# Design system

## Foundation

Chalna owns its visual language using Compose Runtime, UI, Foundation, Animation, Graphics, and Canvas. Material components, themes, icons, ripples, and semantic defaults are forbidden. Custom primitives must explicitly implement interaction states, focus, semantics, minimum targets, and accessibility.

## Tokens

Use semantic tokens rather than literal values at call sites: `canvas`, `surface`, `surfaceRaised`, `contentPrimary`, `contentSecondary`, `accentReady`, `accentRecording`, `border`, `focus`, `danger`; type roles `display`, `title`, `body`, `label`, `monoTimer`; spacing on a 4 dp base; radii `small/medium/large/pill`; elevations expressed through restrained tonal separation, not platform Material elevation.

The canonical ARGB tokens live in `ChalnaTheme.kt`; component typography, spacing, and geometry live in `ChalnaApp.kt`. UI QA run 31311630522 rendered both palettes with the embedded variable font. Original-resolution inspection approved the refined hierarchy and contrast; physical display calibration remains device-dependent.

## Components

- Status orb/indicator: shape + label + motion, never color alone.
- Primary/secondary buttons: custom press/focus/disabled feedback, 48 dp minimum target.
- Setting row/toggle: whole-row label relationship, explicit on/off semantics.
- Permission/readiness card: requirement, current status, single recovery action.
- Timer: tabular/monospaced digits where available, stable width, accessible elapsed-time description.
- Notification: unmistakable recording title, elapsed state where reliable, and Stop action.
- Dialog/sheet: custom focus containment and back/outside-dismiss behavior documented per use.

## Themes and layout

Day and night palettes must maintain semantic parity. Layout responds to available width and insets; avoid hard-coded full-screen assumptions. Support RTL mirroring except inherently directional media/time content. Large fonts may increase height and reflow; never shrink critical copy below the user’s scale.

Chalna uses the Noto Sans Korean variable family from Google Fonts for consistent Korean/Latin rhythm and legible metrics. Weight comes from the variable axis, and numeric status text requests tabular numerals. Its OFL 1.1 license is preserved in `licenses/NotoSansKR-OFL.txt`.

## Validation

The API 34 instrumentation suite verifies custom-control semantics and navigation and renders deterministic day/night, setup, state, settings, diagnostics, help, and privacy captures. `docs/screenshots/home-ready-night.png` is the canonical inspected result. Spoken TalkBack order, 200% system font, RTL, and physical-display contrast remain in the device plan rather than being inferred from screenshots.
