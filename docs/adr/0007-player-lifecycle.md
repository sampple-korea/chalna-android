# ADR 0007: Screen-scoped local player

## Context

An Activity-lifetime ExoPlayer consumed decoder/memory before Player and competed with capture.

## Decision

Create ExoPlayer lazily for a validated local capture ID, use SurfaceView, pause for background/noisy/capture, and release on screen exit.

## Alternatives

An application singleton or playback foreground service was rejected because Chalna is not a background media app.

## Consequences

Opening Player has a small initialization cost; startup/capture resource use improves.

## Verification

Player tests cover lazy creation, lifecycle pause/release, recording conflict, missing source, seek/speed/resume, and fullscreen back.
