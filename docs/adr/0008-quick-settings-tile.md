# ADR 0008: Quick Settings fallback

## Context

Some OEM buttons are not routed to the selected Assistant role.

## Decision

Provide an opt-in official Quick Settings Tile and Android's supported add-tile prompt. The Tile submits to the same dispatcher and respects keyguard.

## Alternatives

Accessibility interception, overlays, undocumented Samsung components, and private intents were rejected.

## Consequences

Users must add the Tile and locked behavior remains under Android security policy.

## Verification

Manifest binding permission, tile lifecycle/state tests, and locked-device physical plan.
