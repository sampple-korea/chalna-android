# ADR 0009: Cached layered invocation Glow

## Context

A flat rotating border looked mechanical and allocation in `onDraw` risked jank and capture latency.

## Decision

Render a cached hot core, chromatic bloom, atmosphere, and tangent-aligned asymmetric streaks. Dispatch precedes view/animation work.

## Alternatives

Bitmap blur, full-screen offscreen buffers, system overlays, and a uniform sweep border were rejected.

## Consequences

The renderer is custom and requires API 29 fallback/geometry tests; failure is visually isolated from capture.

## Verification

Deterministic keyframes, allocation/frame benchmark, lifecycle release checks, and dark/light/cutout visual inspection.
